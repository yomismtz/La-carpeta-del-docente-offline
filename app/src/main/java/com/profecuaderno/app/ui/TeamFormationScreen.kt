package com.profecuaderno.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.TeamFormation
import com.profecuaderno.app.data.TeamFormationStore
import com.profecuaderno.app.data.TeamGroup
import com.profecuaderno.app.data.TeacherDbHelper
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

private val teamActivityTypes = listOf(
    "Exposición", "Investigación", "Actividad", "Laboratorio / práctica",
    "Proyecto", "Debate", "Trabajo en clase", "Otro"
)

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
    var preview by remember { mutableStateOf<List<TeamGroup>>(emptyList()) }
    var manualAssignments by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick, period.id) { TeamFormationStore.load(context, period.id) }

    fun teamCount(activeCount: Int): Int {
        val amount = amountText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return when (sizing) {
            TeamSizing.MEMBERS_PER_TEAM -> ceil(activeCount.toDouble() / amount).toInt().coerceAtLeast(1)
            TeamSizing.NUMBER_OF_TEAMS -> amount.coerceAtMost(activeCount.coerceAtLeast(1))
        }
    }

    fun buildRandom() {
        val active = students.filterNot { it.id in excludedIds }.shuffled()
        if (active.isEmpty()) {
            preview = emptyList()
            return
        }
        val count = teamCount(active.size)
        val buckets = List(count) { mutableListOf<Long>() }
        active.forEachIndexed { index, student -> buckets[index % count].add(student.id) }
        preview = buckets.mapIndexed { index, ids -> TeamGroup("Equipo ${index + 1}", ids) }
    }

    fun buildManual() {
        val active = students.filterNot { it.id in excludedIds }
        if (active.isEmpty()) {
            preview = emptyList()
            return
        }
        val count = teamCount(active.size)
        val buckets = List(count) { mutableListOf<Long>() }
        active.forEach { student ->
            manualAssignments[student.id]?.takeIf { it in 0 until count }?.let { buckets[it].add(student.id) }
        }
        preview = buckets.mapIndexed { index, ids -> TeamGroup("Equipo ${index + 1}", ids) }
    }

    fun saveFormation() {
        val teams = preview.filter { it.studentIds.isNotEmpty() }
        if (teams.isEmpty()) return
        TeamFormationStore.save(
            context,
            TeamFormation(
                periodId = period.id,
                activityType = activityType,
                activityName = activityName.trim(),
                teams = teams
            )
        )
        historyTick++
        onChanged()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Creación de equipos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Los alumnos se toman directamente del registro del grupo ${period.name}.")
        }

        if (students.isEmpty()) {
            item { ElevatedCard { Text("Primero agrega alumnos a este grupo.", modifier = Modifier.padding(18.dp)) } }
        } else {
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = activityType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de actividad") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        teamActivityTypes.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { activityType = option; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text(if (activityType == "Otro") "Nombre de la actividad" else "Tema o nombre (opcional)") },
                    placeholder = { Text("Ej. Sistema Solar") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("¿Cómo quieres dividir el grupo?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sizing == TeamSizing.MEMBERS_PER_TEAM,
                        onClick = { sizing = TeamSizing.MEMBERS_PER_TEAM; preview = emptyList() },
                        label = { Text("Integrantes por equipo") }
                    )
                    FilterChip(
                        selected = sizing == TeamSizing.NUMBER_OF_TEAMS,
                        onClick = { sizing = TeamSizing.NUMBER_OF_TEAMS; preview = emptyList() },
                        label = { Text("Número de equipos") }
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(2); preview = emptyList() },
                    label = { Text(if (sizing == TeamSizing.MEMBERS_PER_TEAM) "Número de integrantes" else "Número de equipos") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Método", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == TeamMethod.RANDOM, onClick = { method = TeamMethod.RANDOM; preview = emptyList() }, label = { Text("🎲 Al azar") })
                    FilterChip(selected = method == TeamMethod.MANUAL, onClick = { method = TeamMethod.MANUAL; preview = emptyList() }, label = { Text("✋ Manual") })
                }
            }

            item {
                Text("Alumnos incluidos", style = MaterialTheme.typography.titleMedium)
                Text("Desmarca a quien esté ausente para que no participe en la rifa.", style = MaterialTheme.typography.bodySmall)
            }
            items(students, key = { "include-${it.id}" }) { student ->
                val included = student.id !in excludedIds
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(student.name, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = included,
                        onCheckedChange = {
                            excludedIds = if (it) excludedIds - student.id else excludedIds + student.id
                            preview = emptyList()
                        }
                    )
                }
            }

            if (method == TeamMethod.MANUAL) {
                item {
                    val count = teamCount(students.count { it.id !in excludedIds })
                    Text("Asignación manual", style = MaterialTheme.typography.titleMedium)
                    Text("Selecciona un equipo para cada alumno incluido.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    students.filterNot { it.id in excludedIds }.forEach { student ->
                        Text(student.name, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(count) { teamIndex ->
                                FilterChip(
                                    selected = manualAssignments[student.id] == teamIndex,
                                    onClick = {
                                        manualAssignments = manualAssignments + (student.id to teamIndex)
                                        preview = emptyList()
                                    },
                                    label = { Text("Equipo ${teamIndex + 1}") }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            item {
                val activeCount = students.count { it.id !in excludedIds }
                Button(
                    onClick = { if (method == TeamMethod.RANDOM) buildRandom() else buildManual() },
                    enabled = activeCount > 0 && (amountText.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (method == TeamMethod.RANDOM && preview.isNotEmpty()) "🎲 Volver a sortear"
                        else if (method == TeamMethod.RANDOM) "🎲 Sortear equipos"
                        else "Crear vista previa"
                    )
                }
            }

            if (preview.isNotEmpty()) {
                item { Text("Vista previa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(preview, key = { "preview-${it.name}" }) { team ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            team.studentIds.forEach { id ->
                                Text("• ${students.firstOrNull { it.id == id }?.name ?: "Alumno"}")
                            }
                            if (team.studentIds.isEmpty()) Text("Sin alumnos", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Button(onClick = ::saveFormation, modifier = Modifier.fillMaxWidth()) {
                        Text("Guardar formación de equipos")
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("Historial de equipos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (history.isEmpty()) {
            item { Text("Todavía no hay formaciones guardadas.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(history, key = { "history-${it.id}" }) { formation ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            buildString {
                                append(formation.activityType)
                                if (formation.activityName.isNotBlank()) append(": ${formation.activityName}")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(formation.createdAt)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        formation.teams.forEach { team ->
                            val names = team.studentIds.mapNotNull { id -> students.firstOrNull { it.id == id }?.name }
                            Text("${team.name}: ${names.joinToString(", ").ifBlank { "Sin alumnos" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            TeamFormationStore.delete(context, formation.id)
                            historyTick++
                            onChanged()
                        }) { Text("Eliminar") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
