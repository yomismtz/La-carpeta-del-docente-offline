package com.profecuaderno.app.ui

import android.content.Intent
import android.net.Uri
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

    fun save(values: List<String>) {
        documents = values.distinct()
        prefs.edit().putStringSet(key, documents.toSet()).apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            message = "No se seleccionó ningún archivo."
        } else {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val readable = runCatching { context.contentResolver.openInputStream(uri)?.use { it.read() } != null }.getOrDefault(false)
            if (readable) {
                save(documents + uri.toString())
                message = "Documento agregado al grupo."
            } else {
                message = "No se pudo leer el archivo seleccionado."
            }
        }
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
            Button(onClick = { picker.launch(DocumentImportPolicy.mimeTypes) }, modifier = Modifier.fillMaxWidth()) {
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
                            runCatching { context.startActivity(intent) }.onFailure { message = "No hay una aplicación compatible para abrir este documento." }
                        }) { Icon(Icons.Default.FolderOpen, contentDescription = "Abrir") }
                        IconButton(onClick = { save(documents - raw) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
