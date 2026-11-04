package br.com.srssistemas.zebra

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanReceiverTest {

    private val ownAction = "br.com.srssistemas.srs_confere_entrada_v2.zebra.SCAN"

    private fun scanIntent(action: String, data: String?) = Intent(action).apply {
        putExtra(DW.DATA_STRING, data)
        putExtra(DW.LABEL_TYPE, "LABEL-TYPE-EAN13")
        putExtra(DW.SOURCE, "scanner")
    }

    @Test
    fun `converte leitura da acao do proprio app`() {
        val map = ScanReceiver.toScanMap(scanIntent(ownAction, "7891234567895"), ownAction)!!
        assertEquals("7891234567895", map["data"])
        assertEquals("LABEL-TYPE-EAN13", map["labelType"])
        assertEquals("scanner", map["source"])
    }

    @Test
    fun `ignora leitura de outro app ou da acao compartilhada antiga`() {
        assertNull(ScanReceiver.toScanMap(scanIntent("br.com.srssistemas.srs_separacao.zebra.SCAN", "1"), ownAction))
        assertNull(ScanReceiver.toScanMap(scanIntent("br.com.srssistemas.SCAN", "1"), ownAction))
    }

    @Test
    fun `ignora leitura vazia ou nula`() {
        assertNull(ScanReceiver.toScanMap(scanIntent(ownAction, ""), ownAction))
        assertNull(ScanReceiver.toScanMap(scanIntent(ownAction, null), ownAction))
    }

    @Test
    fun `receiver so repassa leituras validas`() {
        val received = mutableListOf<Map<String, Any?>>()
        val receiver = ScanReceiver(ownAction) { received += it }

        receiver.onReceive(null, scanIntent("br.com.srssistemas.SCAN", "outro app"))
        receiver.onReceive(null, scanIntent(ownAction, "meu"))

        assertEquals(listOf("meu"), received.map { it["data"] })
    }
}
