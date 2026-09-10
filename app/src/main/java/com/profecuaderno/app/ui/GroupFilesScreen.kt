package com.profecuaderno.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod

@Composable
fun GroupFilesScreen(period: AcademicPeriod) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("group_documents", 0) }
    val key = "documents_${period.id}"
    var documents by remember { mutableStateOf(prefs.getStringSet(key, emptySet()).orEmpty().toList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showLocalBrowser by remember { mutableStateOf(false) }

    fun save(values: List<String>) {
        documents = values.distinct()
        prefs.edit().putStringSet(key, documents.toSet()).apply()
    }

    fun importDocument(uri: Uri?) {
        if (uri == null) {
            message = "No se seleccionó ningún archivo."
            return
        }
        val readable = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read() } != null
        }.getOrDefault(false)
        if (!readable) {
            message = "No se pudo leer el archivo seleccionado."
            return
        }
        val localUri = LocalImportStore.copyIntoApp(context, uri, "group_${period.id}", "documento")
        if (localUri != null) {
            save(documents + localUri.toString())
            message = "Documento agregado y guardado dentro de la app."
        } else {
            message = "No se pudo guardar una copia local del documento."
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        importDocument(uri)
    }

    if (showLocalBrowser) {
        LocalFileBrowserDialog(
            title = "Agregar documento al grupo",
            allowedExtensions = setOf("pdf", "csv", "xls", "xlsx", "doc", "docx", "txt"),
            onDismiss = { showLocalBrowser = false },
            onFileSelected = { uri ->
                showLocalBrowser = false
                importDocument(uri)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Archivos del grupo", style = MaterialTheme.typography.headlineSmall)
            Text("Guarda planeaciones, listas, rúbricas, exámenes y material de apoyo asociado a ${period.name}. Todo permanece en este dispositivo.")
        }
        item {
            Button(
                onClick = {
                    message = null
                    if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                        showLocalBrowser = true
                    } else {
                        runCatching { picker.launch(DocumentImportPolicy.mimeTypes) }
                            .onFailure {
                                message = "No se pudo abrir el selector. Concede acceso a archivos para usar el explorador interno."
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar documento")
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (documents.isEmpty()) {
            item { Text("Todavía no hay documentos guardados para este grupo.") }
        } else {
            items(documents, key = { it }) { raw ->
                val uri = Uri.parse(raw)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Documento", maxLines = 2)
                            Text(context.contentResolver.getType(uri) ?: "archivo", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { message = "No hay una aplicación compatible para abrir este documento." }
                        }) { Icon(Icons.Default.FolderOpen, contentDescription = "Abrir") }
                        IconButton(onClick = { save(documents - raw) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
