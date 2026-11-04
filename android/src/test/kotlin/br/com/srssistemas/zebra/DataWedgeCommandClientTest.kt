package br.com.srssistemas.zebra

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataWedgeCommandClientTest {

    private lateinit var app: Application
    private lateinit var client: DataWedgeCommandClient
    private val results = mutableListOf<CommandResult>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        client = DataWedgeCommandClient(app)
        results.clear()
    }

    private fun sentCommands(): List<Intent> =
        shadowOf(app).broadcastIntents.filter { it.action == DW.ACTION }

    private fun resultIntent(id: String?, result: String, code: String? = null) = Intent(DW.RESULT_ACTION).apply {
        id?.let { putExtra(DW.COMMAND_IDENTIFIER, it) }
        putExtra(DW.RESULT, result)
        code?.let { putExtra(DW.RESULT_INFO, Bundle().apply { putString(DW.RESULT_CODE, it) }) }
    }

    @Test
    fun `comando sai para o pacote do DataWedge com identificador e SEND_RESULT`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }

        val intent = sentCommands().single()
        assertEquals(DW.PACKAGE, intent.`package`)
        assertEquals(DW.SEND_RESULT_COMPLETE, intent.getStringExtra(DW.SEND_RESULT))
        assertTrue(intent.getStringExtra(DW.COMMAND_IDENTIFIER)!!.startsWith("${app.packageName}:"))
    }

    @Test
    fun `resposta com o identificador correto conclui com sucesso`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }
        val id = sentCommands().single().getStringExtra(DW.COMMAND_IDENTIFIER)

        client.onResult(resultIntent(id, DW.RESULT_SUCCESS))

        assertTrue(results.single() is CommandResult.Success)
    }

    @Test
    fun `resposta de outro app e ignorada`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }

        client.onResult(resultIntent("outro.app:123", DW.RESULT_SUCCESS))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `falha retorna o RESULT_CODE do DataWedge`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }
        val id = sentCommands().single().getStringExtra(DW.COMMAND_IDENTIFIER)

        client.onResult(resultIntent(id, DW.RESULT_FAILURE, "APP_ALREADY_ASSOCIATED"))

        val failure = results.single() as CommandResult.Failure
        assertEquals("APP_ALREADY_ASSOCIATED", failure.code)
    }

    @Test
    fun `falha em um modulo do RESULT_LIST e reportada`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }
        val id = sentCommands().single().getStringExtra(DW.COMMAND_IDENTIFIER)
        val list = arrayListOf(
            Bundle().apply { putString("MODULE", "BARCODE"); putString(DW.RESULT, DW.RESULT_SUCCESS) },
            Bundle().apply {
                putString("MODULE", "INTENT")
                putString(DW.RESULT, DW.RESULT_FAILURE)
                putString(DW.RESULT_CODE, "PARAMETER_INVALID")
            },
        )

        client.onResult(resultIntent(id, DW.RESULT_SUCCESS).apply { putExtra(DW.RESULT_LIST, list) })

        val failure = results.single() as CommandResult.Failure
        assertEquals("PARAMETER_INVALID", failure.code)
        assertTrue(failure.message.contains("INTENT=PARAMETER_INVALID"))
    }

    @Test
    fun `sem resposta conclui com TIMEOUT e libera o proximo comando`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }
        client.query(DW.GET_PROFILES_LIST, DW.RESULT_GET_PROFILES_LIST, 1_000) { results += it }
        assertEquals("comandos são enviados um por vez", 1, sentCommands().size)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100))

        assertEquals(ErrorCodes.TIMEOUT, (results.single() as CommandResult.Failure).code)
        assertEquals(2, sentCommands().size)
    }

    @Test
    fun `consulta sem identificador casa pelo extra de retorno`() {
        client.query(DW.GET_PROFILES_LIST, DW.RESULT_GET_PROFILES_LIST, 1_000) { results += it }

        client.onResult(Intent(DW.RESULT_ACTION).apply {
            putExtra(DW.RESULT_GET_PROFILES_LIST, arrayOf("Profile0 (default)", "TDRSeparação"))
        })

        val success = results.single() as CommandResult.Success
        assertEquals(
            listOf("Profile0 (default)", "TDRSeparação"),
            success.intent!!.getStringArrayExtra(DW.RESULT_GET_PROFILES_LIST)!!.toList(),
        )
    }

    @Test
    fun `dispose conclui comandos pendentes`() {
        client.sendWithResult(DW.SET_CONFIG, Bundle(), 1_000) { results += it }
        client.dispose()
        assertTrue(results.single() is CommandResult.Failure)
    }
}
