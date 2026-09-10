package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.CalendarEvent
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore
import java.time.LocalDate
import kotlinx.coroutines.launch

private data class CalendarEntry(val group: AcademicPeriod, val event: CalendarEvent)

@Composable
fun CalendarScreen(db: TeacherDbHelper, refresh: Int, onChanged: () -> Unit) {
    val groups = remember(refresh) { db.getOpenGroups().filter { !TrashStore.isTrashed(db, TrashStore.TYPE_GROUP, it.id) } }
    val entries = remember(refresh, groups.map { it.id }) {
        groups.flatMap { group ->
            val regular = db.getEvents(group.id)
            val birthdays = db.birthdaysForPeriod(group.id)
            (regular + birthdays).map { CalendarEntry(group, it) }
        }.sortedWith(compareBy<CalendarEntry> { it.event.date }.thenBy { it.event.title })
    }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CalendarEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NotificationPermissionCard()
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Calendario docente", style = MaterialTheme.typography.titleMedium)
                        Text("Reúne fechas de todos tus grupos. Los cumpleaños se agregan automáticamente desde la fecha de nacimiento de cada alumno.")
                    }
                }
                if (groups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Crea un grupo para comenzar a usar el calendario.") }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entries.isEmpty()) item { Text("No hay fechas registradas.") }
                        items(entries, key = { "${it.group.id}-${it.event.type}-${it.event.id}-${it.event.date}" }) { entry ->
                            val event = entry.event
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(event.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text(event.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${entry.group.name} · ${eventTypeLabel(event.type)}", style = MaterialTheme.typography.bodySmall)
                                        if (event.notes.isNotBlank() && event.notes != "Cumpleaños") Text(event.notes, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (event.id > 0) IconButton(onClick = { deleting = entry }) { Icon(Icons.Default.Delete, "Eliminar") }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
            if (groups.isNotEmpty()) {
                FloatingActionButton(onClick = { showNew = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, "Agregar fecha") }
            }
        }
    }

    if (showNew) {
        EventDialog(
            groups = groups,
            initialGroupId = db.getActivePeriod()?.id ?: groups.first().id,
            onDismiss = { showNew = false },
            onSave = { db.saveEvent(it); showNew = false; onChanged() }
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Enviar fecha a Papelera") },
            text = { Text("¿Enviar '${entry.event.title}' de ${entry.group.name} a Papelera?") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.trashEvent(db, entry.event)
                    deleting = null
                    onChanged()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar("Fecha enviada a Papelera", "Deshacer", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) {
                            TrashStore.entries(db).firstOrNull { it.type == TrashStore.TYPE_EVENT && it.entityId == entry.event.id }?.let { TrashStore.restore(db, it) }
                            onChanged()
                        }
                    }
                }) { Text("Enviar a Papelera", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    groups: List<AcademicPeriod>,
    initialGroupId: Long,
    initialType: String = "IMPORTANTE",
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var groupId by remember { mutableStateOf(initialGroupId) }
    var groupExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.firstOrNull { it.id == groupId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva fecha") },
        text = {
            Column(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = !groupExpanded }) {
                    OutlinedTextField(value = selectedGroup?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Grupo") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                        groups.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { groupId = group.id; groupExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(value = eventTypeLabel(type), onValueChange = {}, readOnly = true, label = { Text("Tipo de actividad") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        teacherEventTypes.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { type = option.code; typeExpanded = false }) }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Actividad / título") }, placeholder = { Text("Ej. Examen 2, Práctica 4, tema de crecimiento") }, modifier = Modifier.fillMaxWidth())
                DatePickerField(date, { date = it }, "Fecha")
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, placeholder = { Text("Indicaciones, invitado, lugar, entrega, etc.") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && groupId > 0, onClick = { onSave(CalendarEvent(0, groupId, title, date, notes, type)) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
