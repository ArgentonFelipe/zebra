package br.com.srssistemas.zebra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.UUID

/** Resultado de um comando enviado ao DataWedge. */
internal sealed class CommandResult {
    data class Success(val intent: Intent?) : CommandResult()
    data class Failure(val code: String, val message: String) : CommandResult()
}

/**
 * Envia comandos à API de intents do DataWedge, um por vez (recomendação da Zebra),
 * correlacionando as respostas de RESULT_ACTION pelo COMMAND_IDENTIFIER.
 *
 * - Comandos saem com `setPackage(com.symbol.datawedge)` e `sendOrderedBroadcast`
 *   (evita o atraso de ~500 ms do Android 14).
 * - Respostas de outros apps (outro COMMAND_IDENTIFIER) são ignoradas.
 * - Todo comando termina: sucesso, falha ou TIMEOUT — o callback é sempre chamado.
 * - Todos os métodos devem ser chamados na main thread.
 */
internal class DataWedgeCommandClient(
    private val context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private class Command(
        val id: String,
        val extraName: String,
        val value: Any,
        /** Para consultas (GET_*): extra de resposta que identifica o retorno. */
        val expectedResultExtra: String?,
        val timeoutMs: Long,
        val callback: (CommandResult) -> Unit,
    )

    private val queue = ArrayDeque<Command>()
    private var inFlight: Command? = null
    private var timeoutRunnable: Runnable? = null
    private var resultReceiver: BroadcastReceiver? = null

    /** Comando com SEND_RESULT=COMPLETE_RESULT (ex.: SET_CONFIG). */
    fun sendWithResult(extraName: String, value: Any, timeoutMs: Long, callback: (CommandResult) -> Unit) {
        enqueue(Command(newId(), extraName, value, null, timeoutMs, callback))
    }

    /** Consulta (GET_*) cuja resposta vem no extra [resultExtra]. */
    fun query(extraName: String, resultExtra: String, timeoutMs: Long, callback: (CommandResult) -> Unit) {
        enqueue(Command(newId(), extraName, "", resultExtra, timeoutMs, callback))
    }

    /** Comandos de runtime sem resposta (SOFT_SCAN_TRIGGER, SCANNER_INPUT_PLUGIN). */
    fun fire(extraName: String, value: String) {
        context.sendOrderedBroadcast(commandIntent(extraName, value), null)
    }

    fun dispose() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        val pending = listOfNotNull(inFlight) + queue
        inFlight = null
        queue.clear()
        unregisterResultReceiver()
        pending.forEach { it.callback(CommandResult.Failure(ErrorCodes.COMMAND_FAILED, "Plugin finalizado")) }
    }

    private fun enqueue(command: Command) {
        queue.add(command)
        if (inFlight == null) sendNext()
    }

    private fun sendNext() {
        val command = queue.poll()
        inFlight = command
        if (command == null) {
            unregisterResultReceiver()
            return
        }
        registerResultReceiver()

        val intent = commandIntent(command.extraName, command.value).apply {
            putExtra(DW.COMMAND_IDENTIFIER, command.id)
            if (command.expectedResultExtra == null) putExtra(DW.SEND_RESULT, DW.SEND_RESULT_COMPLETE)
        }
        val timeout = Runnable {
            if (inFlight === command) {
                finish(command, CommandResult.Failure(ErrorCodes.TIMEOUT, "DataWedge não respondeu ao comando ${command.extraName}"))
            }
        }
        timeoutRunnable = timeout
        handler.postDelayed(timeout, command.timeoutMs)
        context.sendOrderedBroadcast(intent, null)
    }

    private fun finish(command: Command, result: CommandResult) {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        inFlight = null
        try {
            command.callback(result)
        } finally {
            sendNext()
        }
    }

    /** Visível para testes. */
    internal fun onResult(intent: Intent) {
        val command = inFlight ?: return
        val id = intent.getStringExtra(DW.COMMAND_IDENTIFIER)
        val matches = when {
            id != null -> id == command.id
            // Algumas consultas não ecoam o identificador: casa pelo extra de retorno.
            command.expectedResultExtra != null -> intent.hasExtra(command.expectedResultExtra)
            else -> false
        }
        if (!matches) return
        finish(command, ResultParser.parse(intent, command.extraName))
    }

    private fun registerResultReceiver() {
        if (resultReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DW.RESULT_ACTION) onResult(intent)
            }
        }
        val filter = IntentFilter(DW.RESULT_ACTION).apply { addCategory(DW.CATEGORY_DEFAULT) }
        // EXPORTED: as respostas vêm do processo do DataWedge (outro app).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        resultReceiver = receiver
    }

    private fun unregisterResultReceiver() {
        val receiver = resultReceiver ?: return
        resultReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // já desregistrado
        }
    }

    private fun commandIntent(extraName: String, value: Any): Intent = Intent(DW.ACTION).apply {
        setPackage(DW.PACKAGE)
        when (value) {
            is Bundle -> putExtra(extraName, value)
            is String -> putExtra(extraName, value)
            is Boolean -> putExtra(extraName, value)
            is Array<*> -> putExtra(extraName, value.map { it.toString() }.toTypedArray())
            else -> putExtra(extraName, value.toString())
        }
    }

    private fun newId(): String = "${context.packageName}:${UUID.randomUUID()}"
}

/** Interpreta as respostas (RESULT / RESULT_INFO / RESULT_LIST) do DataWedge. */
internal object ResultParser {

    fun parse(intent: Intent, command: String): CommandResult {
        val failures = mutableListOf<String>()

        if (intent.getStringExtra(DW.RESULT) == DW.RESULT_FAILURE) {
            failures += describe(intent.getBundleExtra(DW.RESULT_INFO), label = command)
        }

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val list = intent.getSerializableExtra(DW.RESULT_LIST) as? ArrayList<Bundle>
        list?.forEach { item ->
            if (item.getString(DW.RESULT) == DW.RESULT_FAILURE) {
                val module = item.getString("MODULE") ?: item.getString(DW.PLUGIN_NAME) ?: command
                failures += describe(item.getBundle(DW.RESULT_INFO) ?: item, label = module)
            }
        }

        return if (failures.isEmpty()) {
            CommandResult.Success(intent)
        } else {
            val codes = failures.joinToString("; ")
            CommandResult.Failure(firstCode(intent, list) ?: ErrorCodes.COMMAND_FAILED, "DataWedge recusou $command: $codes")
        }
    }

    private fun firstCode(intent: Intent, list: List<Bundle>?): String? {
        intent.getBundleExtra(DW.RESULT_INFO)?.let { readCode(it) }?.let { return it }
        list?.forEach { item ->
            if (item.getString(DW.RESULT) == DW.RESULT_FAILURE) {
                readCode(item.getBundle(DW.RESULT_INFO) ?: item)?.let { return it }
            }
        }
        return null
    }

    private fun readCode(bundle: Bundle): String? {
        bundle.getString(DW.RESULT_CODE)?.let { return it }
        return bundle.getStringArray(DW.RESULT_CODE)?.firstOrNull()
    }

    private fun describe(bundle: Bundle?, label: String): String =
        "$label=${bundle?.let(::readCode) ?: "FAILURE"}"
}
