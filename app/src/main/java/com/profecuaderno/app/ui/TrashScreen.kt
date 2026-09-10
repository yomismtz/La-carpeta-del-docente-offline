package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashEntry
import com.profecuaderno.app.data.TrashStore
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashScreen(db: TeacherDbHelper, refresh: Int, onChanged: () -> Unit) {
    var localTick by remember { mutableIntStateOf(0) }
    val tick = refresh + localTick
    var autoExpire by remember(tick) { mutableStateOf(TrashStore.autoExpirationEnabled(db)) }
    var entries by remember(tick) { mutableStateOf(TrashStore.entries(db)) }
    var deleting by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        val purged = TrashStore.purgeExpired(db)
        if (purged > 0) {
            localTick++
            onChanged()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderDelete, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Carpeta Papelera", style = MaterialTheme.typography.titleLarge)
                        Text("Todo lo eliminado permanece recuperable hasta que lo borres definitivamente.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Eliminar automáticamente después de ${TrashStore.RETENTION_DAYS} días", style = MaterialTheme.typography.bodyMedium)
                        Text("Está desactivado por defecto.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = autoExpire,
                        onCheckedChange = {
                            autoExpire = it
                            TrashStore.setAutoExpirationEnabled(db, it)
                            if (it) {
                                TrashStore.purgeExpired(db)
                                localTick++
                                onChanged()
                            }
                        }
                    )
                }
                if (entries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { confirmEmpty = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Vaciar papelera")
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("La papelera está vacía.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { "${it.type}-${it.entityId}" }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label.ifBlank { TrashStore.typeLabel(entry.type) }, style = MaterialTheme.typography.titleSmall)
                                Text(TrashStore.typeLabel(entry.type), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Eliminado: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.deletedAt))}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (autoExpire) {
                                    Text(
                                        "Se eliminará definitivamente en ${TrashStore.daysRemaining(entry)} días",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    TrashStore.restore(db, entry)
                                    entries = TrashStore.entries(db)
                                    onChanged()
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restaurar ${entry.label}")
                            }
                            IconButton(
                                onClick = { deleting = entry },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar definitivamente ${entry.label}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar definitivamente") },
            text = { Text("¿Eliminar '${entry.label}' de forma permanente? Esta acción ya no se podrá deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.deletePermanently(db, entry)
                    deleting = null
                    entries = TrashStore.entries(db)
                    onChanged()
                }) { Text("Eliminar definitivamente", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Vaciar papelera") },
            text = { Text("Se eliminarán definitivamente ${entries.size} elementos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.emptyAll(db)
                    confirmEmpty = false
                    entries = emptyList()
                    onChanged()
                }) { Text("Vaciar definitivamente", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancelar") } }
        )
    }
}
