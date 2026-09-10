package com.profecuaderno.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hace una copia privada y estable de un archivo elegido por el usuario.
 * Así la app no depende de que Android conserve permisos sobre una URI externa.
 */
object LocalImportStore {
    fun copyIntoApp(
        context: Context,
        source: Uri,
        folder: String,
        fallbackName: String = "documento"
    ): Uri? = runCatching {
        val displayName = context.contentResolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: fallbackName

        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._áéíóúÁÉÍÓÚñÑ -]"), "_")
            .take(120)
            .ifBlank { fallbackName }

        val targetDir = File(context.filesDir, "imports/$folder").apply { mkdirs() }
        var target = File(targetDir, safeName)
        if (target.exists()) {
            val base = target.nameWithoutExtension
            val ext = target.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            target = File(targetDir, "${base}_${System.currentTimeMillis()}$ext")
        }

        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()
}
