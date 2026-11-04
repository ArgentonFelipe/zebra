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
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Ponte Flutter <-> Zebra DataWedge.
 *
 * Isolamento entre apps (correção do vazamento de leituras):
 * 1. cada app usa uma ação de intent exclusiva (`<packageName>.zebra.SCAN`) e, quando possível,
 *    entrega explícita por pacote+assinatura (`intent_component_info`);
 * 2. o receiver só fica registrado enquanto há ouvinte no Dart **e** a activity está em
 *    primeiro plano (RESUMED). App em segundo plano nunca recebe leitura.
 */
class ZebraPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private lateinit var context: Context
    private lateinit var methodChannel: MethodChannel
    private lateinit var scanChannel: EventChannel
    private lateinit var commands: DataWedgeCommandClient
    private lateinit var scanAction: String

    private var scanSink: EventChannel.EventSink? = null
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

        methodChannel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL)
        methodChannel.setMethodCallHandler(this)

        scanChannel = EventChannel(binding.binaryMessenger, SCAN_CHANNEL)
        scanChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        scanChannel.setStreamHandler(null)
        scanSink = null
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

    // region EventChannel (leituras)

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        scanSink = events
        updateScanReceiver()
    }

    override fun onCancel(arguments: Any?) {
        scanSink = null
        updateScanReceiver()
    }

    private fun updateScanReceiver() {
        if (scanSink != null && activityResumed) registerScanReceiver() else unregisterScanReceiver()
    }

    private fun registerScanReceiver() {
        if (scanReceiver != null) return
        val receiver = ScanReceiver(scanAction) { scan -> scanSink?.success(scan) }
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

    // region MethodChannel

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "configureProfile" -> configureProfile(call, result)
                "softTrigger" -> runtimeCommand(result) {
                    val action = when (call.argument<String>("action")) {
                        "stop" -> DW.STOP_SCANNING
                        "toggle" -> DW.TOGGLE_SCANNING
                        else -> DW.START_SCANNING
                    }
                    commands.fire(DW.SOFT_SCAN_TRIGGER, action)
                }
                "suspendScanner" -> runtimeCommand(result) { commands.fire(DW.SCANNER_INPUT_PLUGIN, DW.SUSPEND_PLUGIN) }
                "resumeScanner" -> runtimeCommand(result) { commands.fire(DW.SCANNER_INPUT_PLUGIN, DW.RESUME_PLUGIN) }
                "getInfo" -> getInfo(result)
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error(ErrorCodes.COMMAND_FAILED, e.message ?: e.javaClass.simpleName, null)
        }
    }

    private inline fun runtimeCommand(result: MethodChannel.Result, block: () -> Unit) {
        if (!ensureDataWedge(result)) return
        block()
        result.success(null)
    }

    private fun ensureDataWedge(result: MethodChannel.Result): Boolean {
        if (AppIdentity.isDataWedgeInstalled(context)) return true
        result.error(ErrorCodes.DATAWEDGE_NOT_AVAILABLE, "DataWedge não está instalado neste aparelho", null)
        return false
    }

    private fun configureProfile(call: MethodCall, result: MethodChannel.Result) {
        val profileName = call.argument<String>("profileName")?.trim()
        if (profileName.isNullOrEmpty()) {
            result.error(ErrorCodes.INVALID_ARGUMENT, "profileName é obrigatório", null)
            return
        }
        if (!ensureDataWedge(result)) return

        val secureDelivery = call.argument<Boolean>("secureDelivery") ?: true
        val force = call.argument<Boolean>("force") ?: false
        val config = ProfileConfig(
            profileName = profileName,
            packageName = context.packageName,
            scanAction = scanAction,
            signature = if (secureDelivery) AppIdentity.signatureSha1(context) else null,
            decoders = call.argument<Map<String, Boolean>>("decoders"),
            sameBarcodeTimeoutMs = call.argument<Int>("sameBarcodeTimeoutMs"),
            hapticFeedback = call.argument<Boolean>("hapticFeedback"),
        )
        val fingerprint = config.fingerprint()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun reply(applied: Boolean) = result.success(
            mapOf("applied" to applied, "scanAction" to scanAction, "secureDelivery" to (config.signature != null)),
        )

        fun apply() {
            commands.sendWithResult(DW.SET_CONFIG, ProfileBundleBuilder.build(config), SET_CONFIG_TIMEOUT_MS) { r ->
                when (r) {
                    is CommandResult.Success -> {
                        prefs.edit().putString(PREF_FINGERPRINT, fingerprint).apply()
                        reply(applied = true)
                    }
                    is CommandResult.Failure -> {
                        prefs.edit().remove(PREF_FINGERPRINT).apply()
                        result.error(r.code, r.message, null)
                    }
                }
            }
        }

        if (force || prefs.getString(PREF_FINGERPRINT, null) != fingerprint) {
            apply()
            return
        }
        // Config igual à última aplicada: só reaplica se o perfil não existir mais.
        commands.query(DW.GET_PROFILES_LIST, DW.RESULT_GET_PROFILES_LIST, QUERY_TIMEOUT_MS) { r ->
            val profiles = (r as? CommandResult.Success)?.intent?.getStringArrayExtra(DW.RESULT_GET_PROFILES_LIST)
            if (profiles?.contains(profileName) == true) reply(applied = false) else apply()
        }
    }

    private fun getInfo(result: MethodChannel.Result) {
        val installed = AppIdentity.isDataWedgeInstalled(context)
        val info = mutableMapOf<String, Any?>(
            "isZebraDevice" to Build.MANUFACTURER.contains("zebra", ignoreCase = true),
            "dataWedgeInstalled" to installed,
            "dataWedgeVersion" to null,
            "dataWedgeEnabled" to null,
            "scanAction" to scanAction,
        )
        if (!installed) {
            result.success(info)
            return
        }
        commands.query(DW.GET_VERSION_INFO, DW.RESULT_GET_VERSION_INFO, QUERY_TIMEOUT_MS) { version ->
            val bundle: Bundle? = (version as? CommandResult.Success)?.intent?.getBundleExtra(DW.RESULT_GET_VERSION_INFO)
            info["dataWedgeVersion"] = bundle?.getString(DW.VERSION_DATAWEDGE)
                ?: bundle?.getStringArray(DW.VERSION_DATAWEDGE)?.joinToString(", ")
            commands.query(DW.GET_DATAWEDGE_STATUS, DW.RESULT_GET_DATAWEDGE_STATUS, QUERY_TIMEOUT_MS) { status ->
                val value = (status as? CommandResult.Success)?.intent?.getStringExtra(DW.RESULT_GET_DATAWEDGE_STATUS)
                info["dataWedgeEnabled"] = value?.equals("enabled", ignoreCase = true)
                result.success(info)
            }
        }
    }

    // endregion

    private companion object {
        const val TAG = "ZebraPlugin"
        const val METHOD_CHANNEL = "br.com.srssistemas/zebra"
        const val SCAN_CHANNEL = "br.com.srssistemas/scan"
        const val PREFS = "br.com.srssistemas.zebra"
        const val PREF_FINGERPRINT = "profile_fingerprint"
        const val SET_CONFIG_TIMEOUT_MS = 10_000L
        const val QUERY_TIMEOUT_MS = 5_000L
    }
}
