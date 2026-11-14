package br.com.srssistemas.zebra

import androidx.annotation.Keep

/*
 * Callbacks implementados no Dart (bindings JNIgen em lib/src/jni/zebra_bindings.dart).
 *
 * Todos os métodos são `void`: o Dart os implementa em modo listener, então o Kotlin
 * nunca espera o Dart responder (sem risco de deadlock com a main thread).
 * Cada chamada a [ZebraBridge] recebe um callback e o chama exatamente uma vez.
 * @Keep: o R8 não pode remover nem renomear o que o JNI procura pelo nome.
 */

/** Resultado de [ZebraBridge.configureProfile]. */
@Keep
interface ProfileCallback {
    fun onApplied(applied: Boolean, scanAction: String, secureDelivery: Boolean)

    fun onError(code: String, message: String)
}

/** Resultado de [ZebraBridge.getInfo]. Nunca falha por ausência do DataWedge. */
@Keep
interface InfoCallback {
    /**
     * @param dataWedgeVersion nulo quando o DataWedge não respondeu.
     * @param dataWedgeEnabled `1` habilitado, `0` desabilitado, `-1` sem resposta.
     */
    fun onInfo(
        isZebraDevice: Boolean,
        dataWedgeInstalled: Boolean,
        dataWedgeVersion: String?,
        dataWedgeEnabled: Int,
        scanAction: String,
    )
}

/** Resultado dos comandos de runtime (gatilho por software, suspender/retomar). */
@Keep
interface CommandCallback {
    fun onDone()

    fun onError(code: String, message: String)
}

/** Leituras do scanner, entregues só com a activity em primeiro plano. */
@Keep
interface ScanListener {
    /**
     * @param data conteúdo exatamente como enviado pelo DataWedge (nunca vazio).
     * @param timestampMs momento do recebimento (epoch, ms).
     */
    fun onScan(data: String, labelType: String?, source: String?, timestampMs: Long)
}
