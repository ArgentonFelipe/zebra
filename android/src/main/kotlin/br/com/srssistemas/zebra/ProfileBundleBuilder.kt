package br.com.srssistemas.zebra

import android.os.Bundle

/** Configuração de perfil recebida do Dart, já resolvida para o app atual. */
internal data class ProfileConfig(
    val profileName: String,
    val packageName: String,
    val scanAction: String,
    /** SHA-1 (hex maiúsculo) da assinatura do app. Nulo = sem entrega explícita por componente. */
    val signature: String?,
    /** Mapa `decoder_xxx` -> habilitado. Nulo = mantém os padrões do DataWedge. */
    val decoders: Map<String, Boolean>?,
    val sameBarcodeTimeoutMs: Int?,
    val hapticFeedback: Boolean?,
) {
    /** Hash estável usado para evitar reaplicar o mesmo perfil a cada inicialização. */
    fun fingerprint(): String = listOf(
        CONFIG_SCHEMA_VERSION,
        profileName,
        packageName,
        scanAction,
        signature.orEmpty(),
        decoders?.toSortedMap()?.toString().orEmpty(),
        sameBarcodeTimeoutMs?.toString().orEmpty(),
        hapticFeedback?.toString().orEmpty(),
    ).joinToString("|")

    companion object {
        /** Incrementar sempre que o conteúdo gerado pelo builder mudar. */
        const val CONFIG_SCHEMA_VERSION = "2"
    }
}

/**
 * Monta um único SET_CONFIG (DataWedge 7.1+) com:
 * - CONFIG_MODE=CREATE_IF_NOT_EXIST, perfil habilitado e associado somente ao pacote do app;
 * - BARCODE habilitado (scanner_selection=auto + parâmetros opcionais);
 * - INTENT com ação exclusiva do app e entrega por broadcast (explícita, quando há assinatura);
 * - KEYSTROKE desabilitado (evita digitação da leitura em campos focados).
 */
internal object ProfileBundleBuilder {

    fun build(config: ProfileConfig): Bundle = Bundle().apply {
        putString(DW.PROFILE_NAME, config.profileName)
        putString(DW.PROFILE_ENABLED, "true")
        putString(DW.CONFIG_MODE, DW.CONFIG_MODE_CREATE_IF_NOT_EXIST)
        putParcelableArrayList(
            DW.PLUGIN_CONFIG,
            arrayListOf(barcodePlugin(config), intentPlugin(config), keystrokePlugin()),
        )
        putParcelableArray(DW.APP_LIST, arrayOf(appAssociation(config.packageName)))
    }

    private fun barcodePlugin(config: ProfileConfig): Bundle {
        val params = Bundle().apply {
            putString(DW.SCANNER_SELECTION, "auto")
            putString(DW.SCANNER_INPUT_ENABLED, "true")
            config.decoders?.forEach { (key, enabled) -> putString(key, enabled.toString()) }
            config.sameBarcodeTimeoutMs?.let { putString(DW.SAME_BARCODE_TIMEOUT, it.toString()) }
            config.hapticFeedback?.let { putString(DW.DECODE_HAPTIC_FEEDBACK, it.toString()) }
        }
        return plugin(DW.PLUGIN_BARCODE, params)
    }

    private fun intentPlugin(config: ProfileConfig): Bundle {
        val params = Bundle().apply {
            putString(DW.INTENT_OUTPUT_ENABLED, "true")
            putString(DW.INTENT_ACTION, config.scanAction)
            putString(DW.INTENT_CATEGORY, DW.CATEGORY_DEFAULT)
            putString(DW.INTENT_DELIVERY, DW.INTENT_DELIVERY_BROADCAST)
            config.signature?.let { signature ->
                val component = Bundle().apply {
                    putString(DW.PACKAGE_NAME, config.packageName)
                    putString(DW.SIGNATURE, signature)
                }
                putParcelableArrayList(DW.INTENT_COMPONENT_INFO, arrayListOf(component))
            }
        }
        return plugin(DW.PLUGIN_INTENT, params)
    }

    private fun keystrokePlugin(): Bundle {
        val params = Bundle().apply { putString(DW.KEYSTROKE_OUTPUT_ENABLED, "false") }
        return plugin(DW.PLUGIN_KEYSTROKE, params)
    }

    private fun plugin(name: String, params: Bundle): Bundle = Bundle().apply {
        putString(DW.PLUGIN_NAME, name)
        putString(DW.RESET_CONFIG, "true")
        putBundle(DW.PARAM_LIST, params)
    }

    private fun appAssociation(packageName: String): Bundle = Bundle().apply {
        putString(DW.PACKAGE_NAME, packageName)
        putStringArray(DW.ACTIVITY_LIST, arrayOf("*"))
    }
}
