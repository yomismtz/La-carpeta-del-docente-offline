package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamActivityGradingCard(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    formation: TeamFormation,
    onChanged: () -> Unit
) {
    val students = remember(period.id) { db.getStudents(period.id) }
    val categories = remember(period.id) { db.getCategories(period.id).filter { it.mode != EvaluationMode.ATTENDANCE.name } }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var categoryMenu by remember { mutableStateOf(false) }
    var teamScores by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var individualScores by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var useIndividualAdjustments by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Calificar actividad por equipos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("La calificación puede ser general por equipo y ajustarse de forma individual. Todo permanece en este dispositivo.", style = MaterialTheme.typography.bodySmall)

            if (categories.isEmpty()) {
                Text("Crea primero un rubro de evaluación (por ejemplo: Exposiciones o Proyectos).")
                return@Column
            }

            val selected = categories.firstOrNull { it.id == selectedCategoryId }
            ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = !categoryMenu }) {
                OutlinedTextField(
                    value = selected?.name.orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("Rubro al que se enviará") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text("${cat.name} · ${cat.weight}%") }, onClick = { selectedCategoryId = cat.id; categoryMenu = false }) }
                }
            }

            formation.teams.forEachIndexed { index, team ->
                Text(team.name, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = teamScores[index].orEmpty(),
                    onValueChange = { v -> teamScores = teamScores + (index to v.filter { it.isDigit() || it == '.' }.take(5)) },
                    label = { Text("Calificación del equipo (0–100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (useIndividualAdjustments) {
                    team.studentIds.forEach { studentId ->
                        val name = students.firstOrNull { it.id == studentId }?.name ?: "Alumno"
                        OutlinedTextField(
                            value = individualScores[studentId].orEmpty(),
                            onValueChange = { v -> individualScores = individualScores + (studentId to v.filter { it.isDigit() || it == '.' }.take(5)) },
                            label = { Text("$name · ajuste individual (opcional)") },
                            placeholder = { Text("Vacío = usar nota del equipo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                HorizontalDivider()
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Permitir ajustes individuales")
                Switch(checked = useIndividualAdjustments, onCheckedChange = { useIndividualAdjustments = it })
            }

            Button(
                onClick = {
                    val categoryId = selectedCategoryId ?: return@Button
                    val label = buildString {
                        append(formation.activityType)
                        if (formation.activityName.isNotBlank()) append(": ${formation.activityName}")
                    }
                    val existing = db.getAssessmentItems(categoryId).firstOrNull { it.name.equals(label, ignoreCase = true) }
                    val itemId = existing?.id ?: db.saveAssessmentItem(
                        AssessmentItem(0L, categoryId, label, db.getAssessmentItems(categoryId).size)
                    )
                    formation.teams.forEachIndexed { teamIndex, team ->
                        val teamScore = teamScores[teamIndex]?.toDoubleOrNull()?.coerceIn(0.0, 100.0)
                        team.studentIds.forEach { studentId ->
                            val individual = individualScores[studentId]?.toDoubleOrNull()?.coerceIn(0.0, 100.0)
                            val score = individual ?: teamScore
                            if (score != null) {
                                db.setAssessmentScore(studentId, itemId, score)
                                db.calculateAndStoreAverageGrade(period.id, studentId, categoryId)
                            }
                        }
                    }
                    message = "Actividad consolidada en ${selected?.name ?: "el rubro"}. Si vuelves a guardarla, se actualiza la misma actividad."
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar y consolidar calificaciones") }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
