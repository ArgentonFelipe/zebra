package br.com.srssistemas.zebra

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference

/**
 * Lado Android do plugin. O Dart chega aqui por [ZebraBridge] (JNIgen), sem platform channel.
 *
 * Isolamento entre apps (correção do vazamento de leituras):
 * 1. cada app usa uma ação de intent exclusiva (`<packageName>.zebra.SCAN`) e, quando possível,
 *    entrega explícita por pacote+assinatura (`intent_component_info`);
 * 2. o receiver só fica registrado enquanto há ouvinte no Dart **e** a activity está em
 *    primeiro plano (RESUMED). App em segundo plano nunca recebe leitura.
 *
 * Todos os métodos rodam na main thread ([ZebraBridge] garante isso).
 */
class ZebraPlugin : FlutterPlugin, ActivityAware {

    private lateinit var context: Context
    private lateinit var commands: DataWedgeCommandClient
    private lateinit var scanAction: String

    private var scanReceiver: ScanReceiver? = null

    private var lifecycle: Lifecycle? = null
    private var activityResumed = false
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            activityResumed = true
            updateScanReceiver()
        }

        override fun onPause(owner: LifecycleOwner) {
            activityResumed = false
            updateScanReceiver()
        }
    }

    // region FlutterPlugin

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        scanAction = AppIdentity.scanAction(context)
        commands = DataWedgeCommandClient(context)
        ZebraRegistry.attach(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        ZebraRegistry.detach(this)
        unregisterScanReceiver()
        commands.dispose()
    }

    // endregion

    // region ActivityAware

    override fun onAttachedToActivity(binding: ActivityPluginBinding) = attachLifecycle(binding)

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = attachLifecycle(binding)

    override fun onDetachedFromActivityForConfigChanges() = detachLifecycle()

    override fun onDetachedFromActivity() = detachLifecycle()

    private fun attachLifecycle(binding: ActivityPluginBinding) {
        detachLifecycle()
        val reference = binding.lifecycle as? HiddenLifecycleReference ?: return
        // addObserver entrega os eventos até o estado atual (inclusive ON_RESUME, se já resumida).
        lifecycle = reference.lifecycle.also { it.addObserver(lifecycleObserver) }
    }

    private fun detachLifecycle() {
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        activityResumed = false
        updateScanReceiver()
    }

    // endregion

    // region Leituras

    /** Chamado quando o Dart liga/desliga a escuta ([ZebraBridge.setScanListener]). */
    internal fun onScanListenerChanged() = updateScanReceiver()

    private fun updateScanReceiver() {
        if (ZebraRegistry.scanListener != null && activityResumed) registerScanReceiver() else unregisterScanReceiver()
    }

    private fun registerScanReceiver() {
        if (scanReceiver != null) return
        val receiver = ScanReceiver(scanAction) { scan ->
            ZebraRegistry.scanListener?.onScan(
                scan["data"] as String,
                scan["labelType"] as String?,
                scan["source"] as String?,
                scan["timestamp"] as Long,
            )
        }
        val filter = IntentFilter(scanAction).apply { addCategory(DW.CATEGORY_DEFAULT) }
        // EXPORTED é obrigatório: o DataWedge é outro app. A ação exclusiva + entrega
        // explícita por pacote impedem que leituras de outro app cheguem aqui.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scanReceiver = receiver
    }

    private fun unregisterScanReceiver() {
        val receiver = scanReceiver ?: return
        scanReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver de leitura já estava desregistrado", e)
        }
    }

    // endregion

    // region Comandos

    internal fun runtimeCommand(extraName: String, value: String, callback: CommandCallback) {
        if (!AppIdentity.isDataWedgeInstalled(context)) {
            callback.onError(ErrorCodes.DATAWEDGE_NOT_AVAILABLE, DATAWEDGE_MISSING)
            return
        }
        commands.fire(extraName, value)
        callback.onDone()
    }

    internal fun configureProfile(request: ProfileRequest, callback: ProfileCallback) {
        if (request.profileName.isEmpty()) {
            callback.onError(ErrorCodes.INVALID_ARGUMENT, "profileName é obrigatório")
            return
        }
        if (!AppIdentity.isDataWedgeInstalled(context)) {
            callback.onError(ErrorCodes.DATAWEDGE_NOT_AVAILABLE, DATAWEDGE_MISSING)
            return
        }

        val config = ProfileConfig(
            profileName = request.profileName,
            packageName = context.packageName,
            scanAction = scanAction,
            signature = if (request.secureDelivery) AppIdentity.signatureSha1(context) else null,
            decoders = request.decoders,
            sameBarcodeTimeoutMs = request.sameBarcodeTimeoutMs,
            hapticFeedback = request.hapticFeedback,
        )
        val fingerprint = config.fingerprint()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun reply(applied: Boolean) = callback.onApplied(applied, scanAction, config.signature != null)

        fun apply() {
            commands.sendWithResult(DW.SET_CONFIG, ProfileBundleBuilder.build(config), SET_CONFIG_TIMEOUT_MS) { r ->
                when (r) {
                    is CommandResult.Success -> {
                        prefs.edit().putString(PREF_FINGERPRINT, fingerprint).apply()
                        reply(applied = true)
                    }
                    is CommandResult.Failure -> {
                        prefs.edit().remove(PREF_FINGERPRINT).apply()
                        callback.onError(r.code, r.message)
                    }
                }
            }
        }

        if (request.force || prefs.getString(PREF_FINGERPRINT, null) != fingerprint) {
            apply()
            return
        }
        // Config igual à última aplicada: só reaplica se o perfil não existir mais.
        commands.query(DW.GET_PROFILES_LIST, DW.RESULT_GET_PROFILES_LIST, QUERY_TIMEOUT_MS) { r ->
            val profiles = (r as? CommandResult.Success)?.intent?.getStringArrayExtra(DW.RESULT_GET_PROFILES_LIST)
            if (profiles?.contains(request.profileName) == true) reply(applied = false) else apply()
        }
    }

    internal fun getInfo(callback: InfoCallback) {
        val isZebra = Build.MANUFACTURER.contains("zebra", ignoreCase = true)
        if (!AppIdentity.isDataWedgeInstalled(context)) {
            callback.onInfo(isZebra, false, null, -1, scanAction)
            return
        }
        commands.query(DW.GET_VERSION_INFO, DW.RESULT_GET_VERSION_INFO, QUERY_TIMEOUT_MS) { version ->
            val bundle: Bundle? = (version as? CommandResult.Success)?.intent?.getBundleExtra(DW.RESULT_GET_VERSION_INFO)
            val dataWedgeVersion = bundle?.getString(DW.VERSION_DATAWEDGE)
                ?: bundle?.getStringArray(DW.VERSION_DATAWEDGE)?.joinToString(", ")
            commands.query(DW.GET_DATAWEDGE_STATUS, DW.RESULT_GET_DATAWEDGE_STATUS, QUERY_TIMEOUT_MS) { status ->
                val value = (status as? CommandResult.Success)?.intent?.getStringExtra(DW.RESULT_GET_DATAWEDGE_STATUS)
                val enabled = when {
                    value == null -> -1
                    value.equals("enabled", ignoreCase = true) -> 1
                    else -> 0
                }
                callback.onInfo(isZebra, true, dataWedgeVersion, enabled, scanAction)
            }
        }
    }

    // endregion

    private companion object {
        const val TAG = "ZebraPlugin"
        const val PREFS = "br.com.srssistemas.zebra"
        const val PREF_FINGERPRINT = "profile_fingerprint"
        const val SET_CONFIG_TIMEOUT_MS = 10_000L
        const val QUERY_TIMEOUT_MS = 5_000L
        const val DATAWEDGE_MISSING = "DataWedge não está instalado neste aparelho"
    }
}
