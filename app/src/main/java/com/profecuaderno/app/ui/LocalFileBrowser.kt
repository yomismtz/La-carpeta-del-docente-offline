package com.profecuaderno.app.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun LocalFileBrowserDialog(
    title: String,
    allowedExtensions: Set<String>,
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember { mutableStateOf(root) }

    val entries = remember(currentDir, allowedExtensions) {
        runCatching {
            currentDir.listFiles()?.filter { file ->
                file.isDirectory || allowedExtensions.isEmpty() || file.extension.lowercase() in allowedExtensions
            }?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 620.dp)) {
                Text(currentDir.absolutePath, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (currentDir.absolutePath != root.absolutePath) {
                    TextButton(onClick = { currentDir = currentDir.parentFile ?: root }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Carpeta anterior")
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(entries, key = { it.absolutePath }) { file ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (file.isDirectory) {
                                    currentDir = file
                                } else {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    onFileSelected(uri)
                                }
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(file.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
