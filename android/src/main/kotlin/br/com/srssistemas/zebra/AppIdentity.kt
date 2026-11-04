package br.com.srssistemas.zebra

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Identidade do app hospedeiro e utilitários de detecção do DataWedge. */
internal object AppIdentity {

    /** Ação exclusiva deste app: nenhum outro app recebe as leituras. */
    fun scanAction(context: Context): String = "${context.packageName}.zebra.SCAN"

    fun isDataWedgeInstalled(context: Context): Boolean = try {
        packageInfo(context, DW.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * SHA-1 da assinatura do app em hexadecimal maiúsculo, sem separadores — formato
     * esperado por `intent_component_info.SIGNATURE` (exemplo oficial "Get SHA1 of application signature").
     */
    fun signatureSha1(context: Context): String? = try {
        signatures(context).firstOrNull()?.let { sha1Hex(it.toByteArray()) }
    } catch (_: Exception) {
        null
    }

    internal fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signatures(context: Context): Array<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageInfo(context, context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            // apkContentsSigners = certificado(s) atual(is) do APK (o que o DataWedge compara).
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            packageInfo(context, context.packageName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context, packageName: String, flags: Int) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageInfo(packageName, flags)
        }
}
