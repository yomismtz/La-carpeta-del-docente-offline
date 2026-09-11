package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.CalendarEvent
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore
import com.profecuaderno.app.notifications.ReminderScheduler
import com.profecuaderno.app.util.SystemCalendarSync
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private data class CalendarEntry(val group: AcademicPeriod, val event: CalendarEvent)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(db: TeacherDbHelper, refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val groups = remember(refresh) { db.getOpenGroups().filter { !TrashStore.isTrashed(db, TrashStore.TYPE_GROUP, it.id) } }
    val entries = remember(refresh, groups.map { it.id }) {
        groups.flatMap { group ->
            val regular = db.getEvents(group.id)
            val birthdays = db.birthdaysForPeriod(group.id)
            (regular + birthdays).map { CalendarEntry(group, it) }
        }.sortedWith(compareBy<CalendarEntry> { it.event.date }.thenBy { it.event.title })
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CalendarEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val eventsByDate = remember(entries) {
        entries.groupBy { runCatching { LocalDate.parse(it.event.date) }.getOrNull() }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
    }
    val selectedEntries = eventsByDate[selectedDate].orEmpty()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item { NotificationPermissionCard() }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    month = month.minusMonths(1)
                                    selectedDate = month.atDay(1)
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior") }
                            Text(
                                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "MX"))).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            IconButton(
                                onClick = {
                                    month = month.plusMonths(1)
                                    selectedDate = month.atDay(1)
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) { Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente") }
                        }
                        MonthGrid(
                            month = month,
                            selectedDate = selectedDate,
                            eventsByDate = eventsByDate,
                            onSelect = { selectedDate = it }
                        )
                    }
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedEntries.isEmpty()) {
                            Text("No hay actividades registradas para este día.")
                        } else {
                            selectedEntries.forEach { entry ->
                                CalendarEventRow(entry) { if (entry.event.id > 0) deleting = entry }
                            }
                        }
                        if (groups.isNotEmpty()) {
                            Button(
                                onClick = { showNew = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Agregar actividad a este día")
                            }
                        }
                    }
                }
            }

            if (groups.isEmpty()) {
                item { Text("Crea un grupo para comenzar a usar el calendario.", modifier = Modifier.padding(20.dp)) }
            } else {
                item {
                    Text(
                        "Los días con actividades muestran un símbolo. Toca cualquier día para ver su contenido.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    if (showNew) {
        EventDialog(
            groups = groups,
            initialGroupId = db.getActivePeriod()?.id ?: groups.first().id,
            initialDate = selectedDate.toString(),
            onDismiss = { showNew = false },
            onSave = {
                db.saveEvent(it)
                val groupName = groups.firstOrNull { group -> group.id == it.periodId }?.name ?: "Grupo"
                SystemCalendarSync.addEvent(context, it.date, it.title, groupName, eventTypeLabel(it.type), it.notes)
                ReminderScheduler.ensureDaily(context)
                showNew = false
                onChanged()
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEntry>>,
    onSelect: (LocalDate) -> Unit
) {
    val headers = listOf("L", "M", "X", "J", "V", "S", "D")
    Row(Modifier.fillMaxWidth()) {
        headers.forEach { label ->
            Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    val offset = month.atDay(1).dayOfWeek.value - 1
    val totalSlots = offset + month.lengthOfMonth()
    val weeks = (totalSlots + 6) / 7
    repeat(weeks) { week ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { dayOfWeek ->
                val slot = week * 7 + dayOfWeek
                val day = slot - offset + 1
                if (day !in 1..month.lengthOfMonth()) {
                    Spacer(Modifier.weight(1f).heightIn(min = 72.dp))
                } else {
                    val date = month.atDay(day)
                    val events = eventsByDate[date].orEmpty()
                    val selected = date == selectedDate
                    Surface(
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f).padding(1.dp).heightIn(min = 72.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(day.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            if (events.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    events.take(2).forEach { Text(eventIcon(it.event.type), style = MaterialTheme.typography.labelMedium) }
                                }
                                if (events.size > 2) Text("+${events.size - 2}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(entry: CalendarEntry, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Text(eventIcon(entry.event.type), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 4)
                Text("${entry.group.name} · ${eventTypeLabel(entry.event.type)}", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                if (entry.event.notes.isNotBlank() && entry.event.notes != "Cumpleaños") {
                    Text(entry.event.notes, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                }
            }
            if (entry.event.id > 0) {
                IconButton(onClick = onDelete, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

private fun eventIcon(type: String): String = when (type) {
    "TEMA_CLASE" -> "✏️"
    "PRACTICA", "MAQUETA" -> "🛠️"
    "LABORATORIO" -> "🥼"
    "EXAMEN", "EVALUACION_PARCIAL", "EVALUACION_MODULAR" -> "📝"
    "EXPOSICION", "EXPOSICION_MODULAR" -> "🗣️"
    "INVESTIGACION_MODULAR" -> "📚"
    "ENTREGA" -> "📦"
    "DOCENTE_INVITADO" -> "🍎"
    "VISITA", "SALIDA" -> "🚌"
    "CUMPLEAÑOS" -> "🎂"
    else -> "📌"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    groups: List<AcademicPeriod>,
    initialGroupId: Long,
    initialType: String = "IMPORTANTE",
    initialTitle: String = "",
    initialDate: String = LocalDate.now().toString(),
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var date by remember { mutableStateOf(initialDate) }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var groupId by remember { mutableStateOf(initialGroupId) }
    var groupExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.firstOrNull { it.id == groupId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nueva actividad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = !groupExpanded }) {
                OutlinedTextField(
                    value = selectedGroup?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Grupo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    groups.forEach { group ->
                        DropdownMenuItem(text = { Text(group.name, maxLines = 3) }, onClick = { groupId = group.id; groupExpanded = false })
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                OutlinedTextField(
                    value = "${eventIcon(type)} ${eventTypeLabel(type)}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de actividad") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    teacherEventTypes.forEach { option ->
                        DropdownMenuItem(text = { Text("${eventIcon(option.code)} ${option.label}", maxLines = 3) }, onClick = { type = option.code; typeExpanded = false })
                    }
                }
            }
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Actividad / título") },
                placeholder = { Text("Ej. Examen 2, práctica, reunión") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
            DatePickerField(date, { date = it }, "Fecha")
            OutlinedTextField(
                notes,
                { notes = it },
                label = { Text("Notas") },
                placeholder = { Text("Indicaciones, invitado, lugar, entrega, etc.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )
            Button(
                enabled = title.isNotBlank() && groupId > 0,
                onClick = { onSave(CalendarEvent(0, groupId, title, date, notes, type)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Guardar") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancelar") }
            Spacer(Modifier.height(18.dp))
        }
    }
}
