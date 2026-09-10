package com.profecuaderno.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings

object DocumentPickerCompat {
    fun openDocumentIntent(mimeTypes: Array<String>): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun getContentIntent(mimeTypes: Array<String>): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun createDocumentIntent(mimeType: String, suggestedName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

    fun chooserIntent(mimeTypes: Array<String>, title: String): Intent {
        val primary = openDocumentIntent(mimeTypes)
        val fallback = getContentIntent(mimeTypes)
        return Intent.createChooser(primary, title).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(fallback))
        }
    }

    /**
     * No bloqueamos el selector por resolveActivity() en Android moderno.
     * Algunos fabricantes devuelven null para ACTION_CHOOSER aunque el selector
     * del sistema sí exista. En esos equipos la comprobación anterior mostraba
     * incorrectamente "Selector de archivos no disponible".
     *
     * El lanzamiento real se protege con runCatching desde la pantalla.
     */
    fun canResolve(context: Context, intent: Intent): Boolean {
        if (intent.action == Intent.ACTION_CHOOSER ||
            intent.action == Intent.ACTION_OPEN_DOCUMENT ||
            intent.action == Intent.ACTION_GET_CONTENT ||
            intent.action == Intent.ACTION_CREATE_DOCUMENT
        ) return true

        return intent.resolveActivity(context.packageManager) != null
    }

    fun appSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
