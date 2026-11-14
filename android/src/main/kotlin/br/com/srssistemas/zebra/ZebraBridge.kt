package br.com.srssistemas.zebra

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep

/**
 * Ponto de entrada chamado pelo Dart via JNIgen (interop nativa, sem platform channel).
 *
 * - Métodos estáticos (`@JvmStatic`): o binding gerado os chama direto por JNI.
 * - O Dart chama da thread de UI dele, que no Android não é a main thread. Como o
 *   [ZebraPlugin] e o [DataWedgeCommandClient] só rodam na main thread, todo
 *   método repassa o trabalho para ela.
 * - Sem plugin anexado (engine sem o plugin registrado), o callback recebe
 *   `DATAWEDGE_NOT_AVAILABLE`, como o `MissingPluginException` da versão com canais.
 */
@Keep
object ZebraBridge {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * @param enabledDecoders nulo junto com [disabledDecoders] mantém os padrões do DataWedge.
     * @param sameBarcodeTimeoutMs `-1` mantém o padrão do DataWedge.
     * @param hapticFeedback `1` liga, `0` desliga, `-1` mantém o padrão.
     */
    @JvmStatic
    fun configureProfile(
        profileName: String,
        enabledDecoders: Array<String>?,
        disabledDecoders: Array<String>?,
        sameBarcodeTimeoutMs: Int,
        hapticFeedback: Int,
        secureDelivery: Boolean,
        force: Boolean,
        callback: ProfileCallback,
    ) {
        val decoders = if (enabledDecoders == null && disabledDecoders == null) {
            null
        } else {
            enabledDecoders.orEmpty().associateWith { true } + disabledDecoders.orEmpty().associateWith { false }
        }
        val request = ProfileRequest(
            profileName = profileName.trim(),
            decoders = decoders,
            sameBarcodeTimeoutMs = sameBarcodeTimeoutMs.takeIf { it >= 0 },
            hapticFeedback = when (hapticFeedback) {
                0 -> false
                1 -> true
                else -> null
            },
            secureDelivery = secureDelivery,
            force = force,
        )
        onMain(callback::onError) { plugin -> plugin.configureProfile(request, callback) }
    }

    /** @param action `start`, `stop` ou `toggle` (nome de `ZebraTrigger` no Dart). */
    @JvmStatic
    fun softTrigger(action: String, callback: CommandCallback) {
        val value = when (action) {
            "stop" -> DW.STOP_SCANNING
            "toggle" -> DW.TOGGLE_SCANNING
            else -> DW.START_SCANNING
        }
        onMain(callback::onError) { plugin -> plugin.runtimeCommand(DW.SOFT_SCAN_TRIGGER, value, callback) }
    }

    @JvmStatic
    fun suspendScanner(callback: CommandCallback) =
        onMain(callback::onError) { plugin -> plugin.runtimeCommand(DW.SCANNER_INPUT_PLUGIN, DW.SUSPEND_PLUGIN, callback) }

    @JvmStatic
    fun resumeScanner(callback: CommandCallback) =
        onMain(callback::onError) { plugin -> plugin.runtimeCommand(DW.SCANNER_INPUT_PLUGIN, DW.RESUME_PLUGIN, callback) }

    @JvmStatic
    fun getInfo(callback: InfoCallback) {
        onMain(
            onUnavailable = { _, _ -> callback.onInfo(false, false, null, -1, "") },
        ) { plugin -> plugin.getInfo(callback) }
    }

    /**
     * Liga (listener não nulo) ou desliga (nulo) a escuta de leituras. O receiver nativo
     * só fica registrado com listener **e** activity em primeiro plano.
     */
    @JvmStatic
    fun setScanListener(listener: ScanListener?) {
        runOnMain {
            ZebraRegistry.scanListener = listener
            ZebraRegistry.plugin?.onScanListenerChanged()
        }
    }

    private fun onMain(onUnavailable: (String, String) -> Unit, block: (ZebraPlugin) -> Unit) {
        runOnMain {
            val plugin = ZebraRegistry.plugin
            if (plugin == null) {
                onUnavailable(ErrorCodes.DATAWEDGE_NOT_AVAILABLE, "Plugin Zebra indisponível nesta plataforma")
                return@runOnMain
            }
            try {
                block(plugin)
            } catch (e: Exception) {
                onUnavailable(ErrorCodes.COMMAND_FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}

/** Estado compartilhado entre [ZebraBridge] (chamado pelo Dart) e o [ZebraPlugin] anexado. Main thread. */
internal object ZebraRegistry {
    var plugin: ZebraPlugin? = null
        private set

    var scanListener: ScanListener? = null

    fun attach(plugin: ZebraPlugin) {
        this.plugin = plugin
        plugin.onScanListenerChanged()
    }

    fun detach(plugin: ZebraPlugin) {
        if (this.plugin === plugin) this.plugin = null
    }
}

/** Pedido de configuração vindo do Dart, antes de resolver pacote/assinatura do app. */
internal data class ProfileRequest(
    val profileName: String,
    val decoders: Map<String, Boolean>?,
    val sameBarcodeTimeoutMs: Int?,
    val hapticFeedback: Boolean?,
    val secureDelivery: Boolean,
    val force: Boolean,
)
