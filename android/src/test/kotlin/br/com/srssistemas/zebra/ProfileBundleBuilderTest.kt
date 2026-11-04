package br.com.srssistemas.zebra

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBundleBuilderTest {

    private val baseConfig = ProfileConfig(
        profileName = "TDRSeparação",
        packageName = "br.com.srssistemas.srs_separacao",
        scanAction = "br.com.srssistemas.srs_separacao.zebra.SCAN",
        signature = "A9993E364706816ABA3E25717850C26C9CD0D89D",
        decoders = null,
        sameBarcodeTimeoutMs = null,
        hapticFeedback = null,
    )

    private fun plugins(bundle: Bundle): Map<String, Bundle> {
        @Suppress("DEPRECATION")
        val list = bundle.getParcelableArrayList<Bundle>(DW.PLUGIN_CONFIG)!!
        return list.associateBy { it.getString(DW.PLUGIN_NAME)!! }
    }

    private fun params(bundle: Bundle, plugin: String): Bundle = plugins(bundle)[plugin]!!.getBundle(DW.PARAM_LIST)!!

    @Test
    fun `perfil e criado se nao existir, habilitado e associado so ao pacote do app`() {
        val bundle = ProfileBundleBuilder.build(baseConfig)

        assertEquals("TDRSeparação", bundle.getString(DW.PROFILE_NAME))
        assertEquals("true", bundle.getString(DW.PROFILE_ENABLED))
        assertEquals(DW.CONFIG_MODE_CREATE_IF_NOT_EXIST, bundle.getString(DW.CONFIG_MODE))

        @Suppress("DEPRECATION")
        val apps = bundle.getParcelableArray(DW.APP_LIST)!!.map { it as Bundle }
        assertEquals(1, apps.size)
        assertEquals(baseConfig.packageName, apps[0].getString(DW.PACKAGE_NAME))
        assertEquals(listOf("*"), apps[0].getStringArray(DW.ACTIVITY_LIST)!!.toList())
    }

    @Test
    fun `envia barcode, intent e keystroke num unico SET_CONFIG com reset`() {
        val plugins = plugins(ProfileBundleBuilder.build(baseConfig))
        assertEquals(setOf(DW.PLUGIN_BARCODE, DW.PLUGIN_INTENT, DW.PLUGIN_KEYSTROKE), plugins.keys)
        plugins.values.forEach { assertEquals("true", it.getString(DW.RESET_CONFIG)) }
    }

    @Test
    fun `intent usa a acao exclusiva do app com entrega por broadcast`() {
        val intent = params(ProfileBundleBuilder.build(baseConfig), DW.PLUGIN_INTENT)

        assertEquals("true", intent.getString(DW.INTENT_OUTPUT_ENABLED))
        assertEquals(baseConfig.scanAction, intent.getString(DW.INTENT_ACTION))
        assertNotEquals("br.com.srssistemas.SCAN", intent.getString(DW.INTENT_ACTION))
        assertEquals(DW.INTENT_DELIVERY_BROADCAST, intent.getString(DW.INTENT_DELIVERY))
        assertEquals(DW.CATEGORY_DEFAULT, intent.getString(DW.INTENT_CATEGORY))
    }

    @Test
    fun `com assinatura configura entrega explicita por pacote`() {
        val intent = params(ProfileBundleBuilder.build(baseConfig), DW.PLUGIN_INTENT)

        @Suppress("DEPRECATION")
        val components = intent.getParcelableArrayList<Bundle>(DW.INTENT_COMPONENT_INFO)!!
        assertEquals(1, components.size)
        assertEquals(baseConfig.packageName, components[0].getString(DW.PACKAGE_NAME))
        assertEquals(baseConfig.signature, components[0].getString(DW.SIGNATURE))
    }

    @Test
    fun `sem assinatura nao envia intent_component_info`() {
        val intent = params(ProfileBundleBuilder.build(baseConfig.copy(signature = null)), DW.PLUGIN_INTENT)
        assertFalse(intent.containsKey(DW.INTENT_COMPONENT_INFO))
    }

    @Test
    fun `keystroke e desabilitado`() {
        val keystroke = params(ProfileBundleBuilder.build(baseConfig), DW.PLUGIN_KEYSTROKE)
        assertEquals("false", keystroke.getString(DW.KEYSTROKE_OUTPUT_ENABLED))
    }

    @Test
    fun `barcode define scanner_selection e mantem padroes quando sem opcoes`() {
        val barcode = params(ProfileBundleBuilder.build(baseConfig), DW.PLUGIN_BARCODE)

        assertEquals("auto", barcode.getString(DW.SCANNER_SELECTION))
        assertEquals("true", barcode.getString(DW.SCANNER_INPUT_ENABLED))
        assertNull(barcode.getString("decoder_ean13"))
        assertNull(barcode.getString(DW.SAME_BARCODE_TIMEOUT))
        assertNull(barcode.getString(DW.DECODE_HAPTIC_FEEDBACK))
    }

    @Test
    fun `barcode aplica decoders, timeout de duplicata e vibracao`() {
        val config = baseConfig.copy(
            decoders = mapOf("decoder_ean13" to true, "decoder_qrcode" to false),
            sameBarcodeTimeoutMs = 800,
            hapticFeedback = true,
        )
        val barcode = params(ProfileBundleBuilder.build(config), DW.PLUGIN_BARCODE)

        assertEquals("true", barcode.getString("decoder_ean13"))
        assertEquals("false", barcode.getString("decoder_qrcode"))
        assertEquals("800", barcode.getString(DW.SAME_BARCODE_TIMEOUT))
        assertEquals("true", barcode.getString(DW.DECODE_HAPTIC_FEEDBACK))
    }

    @Test
    fun `fingerprint muda quando a configuracao muda`() {
        assertEquals(baseConfig.fingerprint(), baseConfig.copy().fingerprint())
        assertNotEquals(baseConfig.fingerprint(), baseConfig.copy(sameBarcodeTimeoutMs = 1).fingerprint())
        assertNotEquals(baseConfig.fingerprint(), baseConfig.copy(signature = null).fingerprint())
    }

    @Test
    fun `sha1Hex gera hexadecimal maiusculo sem separadores`() {
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", AppIdentity.sha1Hex("abc".toByteArray()))
    }
}
