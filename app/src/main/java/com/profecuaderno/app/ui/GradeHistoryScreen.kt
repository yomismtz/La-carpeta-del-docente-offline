package com.profecuaderno.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.GradeHistoryEntry
import com.profecuaderno.app.data.GradeHistoryStore
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore
import java.text.DateFormat
import java.util.Date

private enum class HistoryDateFilter(val label: String, val days: Int?) {
    ALL("Todas", null),
    TODAY("Hoy", 1),
    WEEK("7 días", 7),
    MONTH("30 días", 30)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeHistoryScreen(db: TeacherDbHelper, refresh: Int, onChanged: () -> Unit) {
    val entries = remember(refresh) { GradeHistoryStore.entries(db) }
    val groups = remember(refresh) { TrashStore.visiblePeriods(db) }
    var restoring by remember { mutableStateOf<GradeHistoryEntry?>(null) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var studentFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(HistoryDateFilter.ALL) }
    var groupExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val categoryNames = remember(entries) { entries.map { it.categoryName }.filter { it.isNotBlank() }.distinct().sorted() }
    val now = System.currentTimeMillis()
    val filtered = entries.filter { entry ->
        val dateOk = dateFilter.days?.let { days -> entry.changedAt >= now - days * 24L * 60L * 60L * 1000L } ?: true
        val groupOk = selectedGroupId == null || entry.periodId == selectedGroupId
        val studentOk = studentFilter.isBlank() || entry.studentName.contains(studentFilter, ignoreCase = true)
        val categoryOk = categoryFilter.isBlank() || entry.categoryName.equals(categoryFilter, ignoreCase = true)
        dateOk && groupOk && studentOk && categoryOk
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Filtros del historial", style = MaterialTheme.typography.titleMedium)
                }
                ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = !groupExpanded }) {
                    OutlinedTextField(
                        value = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Todos los grupos",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Grupo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                        DropdownMenuItem(text = { Text("Todos los grupos") }, onClick = { selectedGroupId = null; groupExpanded = false })
                        groups.forEach { group ->
                            DropdownMenuItem(text = { Text(group.name) }, onClick = { selectedGroupId = group.id; groupExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = studentFilter,
                    onValueChange = { studentFilter = it },
                    label = { Text("Estudiante") },
                    placeholder = { Text("Escribe un nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                    OutlinedTextField(
                        value = categoryFilter.ifBlank { "Todos los rubros" },
                        onValueChange = {}, readOnly = true,
                        label = { Text("Rubro") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        DropdownMenuItem(text = { Text("Todos los rubros") }, onClick = { categoryFilter = ""; categoryExpanded = false })
                        categoryNames.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { categoryFilter = name; categoryExpanded = false })
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryDateFilter.entries.forEach { option ->
                        FilterChip(
                            selected = dateFilter == option,
                            onClick = { dateFilter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        }

        Text("${filtered.size} cambios", style = MaterialTheme.typography.labelLarge)

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay cambios que coincidan con los filtros.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.studentName.ifBlank { "Estudiante" }, style = MaterialTheme.typography.titleSmall)
                                Text(entry.categoryName.ifBlank { "Rubro" }, style = MaterialTheme.typography.bodySmall)
                                val before = entry.oldScore?.let { "%.1f".format(it) } ?: "Sin registro"
                                Text("$before → ${"%.1f".format(entry.newScore)}", color = MaterialTheme.colorScheme.primary)
                                Text(GradeHistoryStore.actionLabel(entry.action), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.changedAt)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (entry.oldScore != null) {
                                IconButton(onClick = { restoring = entry }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restaurar calificación anterior")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    restoring?.let { entry ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Restaurar calificación") },
            text = { Text("¿Volver de ${"%.1f".format(entry.newScore)} a ${"%.1f".format(entry.oldScore)} para ${entry.studentName}?") },
            confirmButton = {
                TextButton(onClick = {
                    GradeHistoryStore.restorePrevious(db, entry)
                    restoring = null
                    onChanged()
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("Cancelar") } }
        )
    }
}
