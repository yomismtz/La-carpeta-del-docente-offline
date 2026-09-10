package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

private val teamActivityTypes = listOf("Exposición", "Investigación", "Actividad", "Laboratorio / práctica", "Proyecto", "Debate", "Trabajo en clase", "Otro")
private enum class TeamSizing { MEMBERS_PER_TEAM, NUMBER_OF_TEAMS }
private enum class TeamMethod { RANDOM, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamFormationScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val students = remember(refresh, period.id) { db.getStudents(period.id).sortedBy { it.name.lowercase() } }
    var activityType by remember { mutableStateOf("Exposición") }
    var activityName by remember { mutableStateOf("") }
    var sizing by remember { mutableStateOf(TeamSizing.MEMBERS_PER_TEAM) }
    var amountText by remember { mutableStateOf("4") }
    var method by remember { mutableStateOf(TeamMethod.RANDOM) }
    var excludedIds by remember { mutableStateOf(emptySet<Long>()) }
    var manualAssignments by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var preview by remember { mutableStateOf<List<TeamGroup>>(emptyList()) }
    var historyTick by remember { mutableIntStateOf(0) }
    var gradingId by remember { mutableStateOf<String?>(null) }
    val history = remember(historyTick, period.id) { TeamFormationStore.load(context, period.id) }

    fun teamCount(activeCount: Int): Int {
        val amount = amountText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return if (sizing == TeamSizing.MEMBERS_PER_TEAM) ceil(activeCount.toDouble() / amount).toInt().coerceAtLeast(1)
        else amount.coerceAtMost(activeCount.coerceAtLeast(1))
    }

    fun buildTeams() {
        val active = students.filterNot { it.id in excludedIds }
        if (active.isEmpty()) { preview = emptyList(); return }
        val count = teamCount(active.size)
        val buckets = List(count) { mutableListOf<Long>() }
        if (method == TeamMethod.RANDOM) active.shuffled().forEachIndexed { index, student -> buckets[index % count].add(student.id) }
        else active.forEach { student -> manualAssignments[student.id]?.takeIf { it in 0 until count }?.let { buckets[it].add(student.id) } }
        preview = buckets.mapIndexed { index, ids -> TeamGroup("Equipo ${index + 1}", ids) }
    }

    fun saveFormation() {
        val teams = preview.filter { it.studentIds.isNotEmpty() }
        if (teams.isEmpty()) return
        TeamFormationStore.save(context, TeamFormation(periodId = period.id, activityType = activityType, activityName = activityName.trim(), teams = teams))
        historyTick++; preview = emptyList(); onChanged()
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Creación y evaluación de equipos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Forma equipos y consolida sus calificaciones en los rubros de evaluación. Todo permanece en este dispositivo.")
        }
        if (students.isEmpty()) item { ElevatedCard { Text("Primero agrega alumnos a este grupo.", modifier = Modifier.padding(18.dp)) } }
        else {
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = activityType, onValueChange = {}, readOnly = true, label = { Text("Tipo de actividad") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        teamActivityTypes.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { activityType = option; expanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = activityName, onValueChange = { activityName = it }, label = { Text("Tema o nombre") }, placeholder = { Text("Ej. Sistema Solar") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sizing == TeamSizing.MEMBERS_PER_TEAM, onClick = { sizing = TeamSizing.MEMBERS_PER_TEAM }, label = { Text("Integrantes/equipo") })
                    FilterChip(selected = sizing == TeamSizing.NUMBER_OF_TEAMS, onClick = { sizing = TeamSizing.NUMBER_OF_TEAMS }, label = { Text("N.º equipos") })
                }
                OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit).take(2) }, label = { Text(if (sizing == TeamSizing.MEMBERS_PER_TEAM) "Integrantes" else "Equipos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == TeamMethod.RANDOM, onClick = { method = TeamMethod.RANDOM }, label = { Text("🎲 Al azar") })
                    FilterChip(selected = method == TeamMethod.MANUAL, onClick = { method = TeamMethod.MANUAL }, label = { Text("✋ Manual") })
                }
            }
            item { Text("Alumnos incluidos", style = MaterialTheme.typography.titleMedium); Text("Desmarca ausentes.", style = MaterialTheme.typography.bodySmall) }
            items(students, key = { "include-${it.id}" }) { student ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(student.name, Modifier.weight(1f))
                    Checkbox(checked = student.id !in excludedIds, onCheckedChange = { include -> excludedIds = if (include) excludedIds - student.id else excludedIds + student.id })
                }
            }
            if (method == TeamMethod.MANUAL) {
                item {
                    val count = teamCount(students.count { it.id !in excludedIds })
                    Text("Asignación manual", fontWeight = FontWeight.Bold)
                    students.filterNot { it.id in excludedIds }.forEach { student ->
                        Text(student.name, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(count.coerceAtMost(5)) { idx -> FilterChip(selected = manualAssignments[student.id] == idx, onClick = { manualAssignments = manualAssignments + (student.id to idx) }, label = { Text("E${idx + 1}") }) }
                        }
                    }
                }
            }
            item { Button(onClick = ::buildTeams, modifier = Modifier.fillMaxWidth()) { Text(if (preview.isEmpty()) "Crear equipos" else "Volver a crear equipos") } }
            if (preview.isNotEmpty()) {
                items(preview, key = { "preview-${it.name}" }) { team ->
                    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        Text(team.name, fontWeight = FontWeight.Bold)
                        team.studentIds.forEach { id -> Text("• ${students.firstOrNull { it.id == id }?.name ?: "Alumno"}") }
                    } }
                }
                item { Button(onClick = ::saveFormation, modifier = Modifier.fillMaxWidth()) { Text("Guardar formación") } }
            }
        }
        item { HorizontalDivider(); Text("Actividades por equipos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item { Text("Todavía no hay formaciones guardadas.") }
        else items(history, key = { "history-${it.id}" }) { formation ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val label = if (formation.activityName.isBlank()) formation.activityType else "${formation.activityType}: ${formation.activityName}"
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(formation.createdAt)), style = MaterialTheme.typography.bodySmall)
                    formation.teams.forEach { team ->
                        val names = team.studentIds.mapNotNull { id -> students.firstOrNull { it.id == id }?.name }
                        Text("${team.name}: ${names.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { gradingId = if (gradingId == formation.id) null else formation.id }) { Text(if (gradingId == formation.id) "Cerrar evaluación" else "Calificar") }
                        TextButton(onClick = { TeamFormationStore.delete(context, formation.id); historyTick++; onChanged() }) { Text("Eliminar") }
                    }
                    if (gradingId == formation.id) TeamActivityGradingCard(db = db, period = period, formation = formation, onChanged = onChanged)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
