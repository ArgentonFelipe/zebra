package br.com.srssistemas.zebra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZebraBridgeTest {

    private class Errors : ProfileCallback, CommandCallback {
        val codes = mutableListOf<String>()
        override fun onApplied(applied: Boolean, scanAction: String, secureDelivery: Boolean) {
            codes += "applied"
        }
        override fun onDone() {
            codes += "done"
        }
        override fun onError(code: String, message: String) {
            codes += code
        }
    }

    @Test
    fun `sem plugin anexado os comandos respondem DATAWEDGE_NOT_AVAILABLE uma unica vez`() {
        val errors = Errors()

        ZebraBridge.configureProfile("Perfil", null, null, -1, -1, true, false, errors)
        ZebraBridge.softTrigger("start", errors)
        ZebraBridge.suspendScanner(errors)
        ZebraBridge.resumeScanner(errors)

        assertEquals(List(4) { ErrorCodes.DATAWEDGE_NOT_AVAILABLE }, errors.codes)
    }

    @Test
    fun `sem plugin anexado getInfo responde sem DataWedge em vez de falhar`() {
        var installed: Boolean? = null
        var enabled: Int? = null
        var version: String? = "x"
        ZebraBridge.getInfo(object : InfoCallback {
            override fun onInfo(
                isZebraDevice: Boolean,
                dataWedgeInstalled: Boolean,
                dataWedgeVersion: String?,
                dataWedgeEnabled: Int,
                scanAction: String,
            ) {
                installed = dataWedgeInstalled
                enabled = dataWedgeEnabled
                version = dataWedgeVersion
            }
        })

        assertFalse(installed!!)
        assertEquals(-1, enabled)
        assertNull(version)
    }

    @Test
    fun `setScanListener guarda e limpa o ouvinte`() {
        val listener = object : ScanListener {
            override fun onScan(data: String, labelType: String?, source: String?, timestampMs: Long) = Unit
        }

        ZebraBridge.setScanListener(listener)
        assertSame(listener, ZebraRegistry.scanListener)

        ZebraBridge.setScanListener(null)
        assertNull(ZebraRegistry.scanListener)
    }
}
