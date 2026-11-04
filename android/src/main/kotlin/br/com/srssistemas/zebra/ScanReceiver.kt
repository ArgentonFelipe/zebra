package br.com.srssistemas.zebra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recebe as leituras do DataWedge apenas para a ação exclusiva deste app
 * e as converte no mapa consumido pelo Dart (`ZebraScan.fromMap`).
 */
internal class ScanReceiver(
    private val scanAction: String,
    private val onScan: (Map<String, Any?>) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val scan = intent?.let { toScanMap(it, scanAction) } ?: return
        onScan(scan)
    }

    companion object {
        /** Retorna null para intents de outra ação ou leituras vazias. */
        fun toScanMap(intent: Intent, scanAction: String): Map<String, Any?>? {
            if (intent.action != scanAction) return null
            val data = intent.getStringExtra(DW.DATA_STRING)
            if (data.isNullOrEmpty()) return null
            return mapOf(
                "data" to data,
                "labelType" to intent.getStringExtra(DW.LABEL_TYPE),
                "source" to intent.getStringExtra(DW.SOURCE),
                "decodeMode" to intent.getStringExtra(DW.DECODED_MODE),
                "timestamp" to System.currentTimeMillis(),
            )
        }
    }
}
