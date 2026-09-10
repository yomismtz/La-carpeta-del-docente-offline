package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    val categories = remember(refresh, period.id) { db.getCategories(period.id) }
    val totalWeight = categories.sumOf { it.weight }
    var selectedId by remember(categories.map { it.id }) { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    val selected = categories.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }
    var rubricStudent by remember { mutableStateOf<Student?>(null) }
    var weightedStudent by remember { mutableStateOf<Student?>(null) }
    var applyDirectToTeam by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (abs(totalWeight - 100.0) > 0.001) {
            AssistChip(onClick = {}, label = { Text("Los rubros suman ${"%.1f".format(totalWeight)}%. Deben sumar 100%.") })
        }
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Primero crea los rubros de evaluación.") }
            return
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selected?.let { "${it.name} · ${"%.1f".format(it.weight)}%" } ?: "",
                onValueChange = {}, readOnly = true, label = { Text("Rubro a calificar") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(text = { Text("${cat.name} · ${"%.1f".format(cat.weight)}%") }, onClick = { selectedId = cat.id; expanded = false })
                }
            }
        }

        selected?.let { category ->
            val weightedKind = WeightedEvaluationStore.kindFor(db, category)
            val mode = db.effectiveEvaluationMode(category)
            val criteria = remember(refresh, category.id) { db.getRubricCriteria(category.id) }
            val assessmentItems = remember(refresh, category.id) { db.getAssessmentItems(category.id) }
            val weightedConfigured = weightedKind != null && WeightedEvaluationStore.isConfigured(db, category.id)

            Text(
                when {
                    weightedKind == WeightedEvaluationKind.EXAMS -> if (weightedConfigured) "Califica cada examen; la app aplica el porcentaje asignado a cada uno." else "Configura primero los exámenes y asegúrate de que sus porcentajes sumen 100%."
                    weightedKind == WeightedEvaluationKind.ACTIVITIES -> if (weightedConfigured) "Califica cada actividad; la app aplica el porcentaje asignado a cada una." else "Configura primero las actividades y asegúrate de que sus porcentajes sumen 100%."
                    mode == EvaluationMode.RUBRIC -> if (criteria.isEmpty()) "Primero configura la rúbrica." else "Evalúa criterio por criterio; la rúbrica interna suma 100%."
                    mode == EvaluationMode.ATTENDANCE -> "Este rubro usa el porcentaje actual del reporte de asistencia y actualiza la calificación final."
                    else -> "Captura una calificación directa de 0 a 100."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (students.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Agrega alumnos al grupo para comenzar a evaluar.") }
                return@let
            }

            if (weightedKind == null && mode == EvaluationMode.DIRECT && students.any { it.teamName.isNotBlank() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyDirectToTeam, onCheckedChange = { applyDirectToTeam = it })
                    Text("Aplicar calificación directa a todo el equipo")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(students, key = { it.id }) { student ->
                    val categoryScore: Double? = remember(refresh, student.id, category.id, weightedKind, mode) {
                        if (weightedKind != null) WeightedEvaluationStore.weightedScoreOrNull(db, student.id, category.id)
                        else db.categoryScoreOrNull(period.id, student.id, category)
                    }
                    val contribution = (categoryScore ?: 0.0) * category.weight / 100.0
                    val finalScore = remember(refresh, student.id) { db.finalPercentage(period.id, student.id) }

                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(student.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (categoryScore == null) "${category.name}: Sin evaluar"
                                    else "${category.name}: ${"%.1f".format(categoryScore)}/100",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (weightedKind != null && assessmentItems.isNotEmpty()) {
                                    val scores = assessmentItems.map { db.getAssessmentScore(student.id, it.id) }
                                    Text("Registrados: ${scores.count { it != null }}/${assessmentItems.size} · 0: ${scores.count { it == 0.0 }}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (mode == EvaluationMode.ATTENDANCE && categoryScore != null) {
                                    Text("Aporta ${"%.2f".format(contribution)} de ${"%.1f".format(category.weight)} puntos posibles", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Calificación actual: ${"%.1f".format(finalScore)}%", style = MaterialTheme.typography.bodySmall)
                            }

                            when {
                                weightedKind != null -> Button(
                                    enabled = assessmentItems.isNotEmpty() && weightedConfigured,
                                    onClick = { weightedStudent = student }
                                ) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("Calificar") }
                                mode == EvaluationMode.RUBRIC -> Button(
                                    enabled = criteria.isNotEmpty() && abs(criteria.sumOf { it.weight } - 100.0) < 0.001,
                                    onClick = { rubricStudent = student }
                                ) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("Rúbrica") }
                                mode == EvaluationMode.ATTENDANCE -> AssistChip(onClick = {}, label = { Text("Reporte") })
                                else -> InlineGradeEditor(categoryScore) { value ->
                                    val targets = if (applyDirectToTeam && student.teamName.isNotBlank()) students.filter { it.teamName.equals(student.teamName, ignoreCase = true) } else listOf(student)
                                    targets.forEach { db.setGrade(period.id, it.id, category.id, value) }
                                    onChanged()
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    val category = selected
    rubricStudent?.let { student ->
        if (category != null) RubricGradingDialog(db, period, category, student, refresh, { rubricStudent = null }) { rubricStudent = null; onChanged() }
    }
    weightedStudent?.let { student ->
        if (category != null) WeightedGradingDialog(db, period, category, student, refresh, { weightedStudent = null }) { weightedStudent = null; onChanged() }
    }
}

@Composable
private fun InlineGradeEditor(initial: Double?, onSave: (Double) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial?.let { "%.1f".format(it) } ?: "") }
    val value = text.replace(',', '.').toDoubleOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("0-100") }, modifier = Modifier.width(110.dp), singleLine = true)
        Spacer(Modifier.width(6.dp))
        FilledTonalButton(enabled = value != null && value in 0.0..100.0, onClick = { onSave(value!!) }) { Text("✓") }
    }
}

@Composable
private fun WeightedGradingDialog(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    category: EvaluationCategory,
    student: Student,
    refresh: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val items = remember(refresh, category.id) { db.getAssessmentItems(category.id) }
    val initial = remember(items, student.id, refresh) { items.associate { it.id to (db.getAssessmentScore(student.id, it.id)?.let { score -> "%.1f".format(score) } ?: "") } }
    var values by remember(items, student.id, refresh) { mutableStateOf(initial) }
    var applyTeam by remember(student.id) { mutableStateOf(false) }
    val kind = WeightedEvaluationStore.kindFor(db, category)

    val preview = items.mapNotNull { item ->
        val score = values[item.id].orEmpty().replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val weight = WeightedEvaluationStore.weightFor(db, item.id)
        Triple(score, weight, item)
    }
    val weightRegistered = preview.sumOf { it.second }
    val calculated = if (weightRegistered <= 0.0) 0.0 else preview.sumOf { it.first * it.second } / weightRegistered

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.name} · ${student.name}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                Text("Escribe 0 si se perdió o no se entregó. Déjalo vacío si todavía no se evalúa.")
                if (student.teamName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyTeam, onCheckedChange = { applyTeam = it })
                        Text("Aplicar a todo el equipo ${student.teamName}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val weight = WeightedEvaluationStore.weightFor(db, item.id)
                        OutlinedTextField(
                            value = values[item.id] ?: "",
                            onValueChange = { raw -> values = values.toMutableMap().also { it[item.id] = raw } },
                            label = { Text("${item.name} · ${"%.1f".format(weight)}%") },
                            placeholder = { Text("0-100 o vacío") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Resultado ponderado actual: ${"%.1f".format(calculated)}/100", style = MaterialTheme.typography.titleMedium)
                Text("${if (kind == WeightedEvaluationKind.EXAMS) "Exámenes" else "Actividades"} registrados: ${preview.size}/${items.size}")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val targets = if (applyTeam && student.teamName.isNotBlank()) db.getStudents(period.id).filter { it.teamName.equals(student.teamName, ignoreCase = true) } else listOf(student)
                targets.forEach { target ->
                    items.forEach { item ->
                        val raw = values[item.id].orEmpty().trim()
                        val score = raw.replace(',', '.').toDoubleOrNull()
                        if (raw.isBlank()) db.setAssessmentScore(target.id, item.id, null)
                        else if (score != null && score in 0.0..100.0) db.setAssessmentScore(target.id, item.id, score)
                    }
                    WeightedEvaluationStore.recalculateStudent(db, period.id, target.id, category.id)
                }
                onSaved()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RubricGradingDialog(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    category: EvaluationCategory,
    student: Student,
    refresh: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val criteria = remember(refresh, category.id) { db.getRubricCriteria(category.id) }
    val initialMarks: Map<Long, Double?> = remember(criteria, student.id, refresh) {
        criteria.associate { criterion -> criterion.id to db.getRubricMarkOrNull(student.id, criterion.id) }
    }
    var marks: Map<Long, Double?> by remember(criteria, student.id, refresh) { mutableStateOf(initialMarks) }
    var applyTeam by remember(student.id) { mutableStateOf(false) }
    val registered = criteria.mapNotNull { criterion -> marks[criterion.id]?.let { score -> criterion to score } }
    val registeredWeight = registered.sumOf { entry -> entry.first.weight }
    val calculated = if (registeredWeight <= 0.0) 0.0 else registered.sumOf { entry -> entry.second * entry.first.weight } / registeredWeight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.name} · ${student.name}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                Text("Escribe 0 si corresponde. Déjalo vacío si todavía no se evalúa.")
                if (student.teamName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyTeam, onCheckedChange = { applyTeam = it })
                        Text("Aplicar esta rúbrica a todo el equipo ${student.teamName}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(criteria, key = { it.id }) { criterion ->
                        var text by remember(criterion.id, marks[criterion.id]) {
                            mutableStateOf(marks[criterion.id]?.let { score -> "%.1f".format(score) } ?: "")
                        }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text("${criterion.name} · ${"%.1f".format(criterion.weight)}%")
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { raw ->
                                        text = raw
                                        val trimmed = raw.trim()
                                        val parsed = trimmed.replace(',', '.').toDoubleOrNull()
                                        when {
                                            trimmed.isBlank() -> marks = marks.toMutableMap().also { map -> map[criterion.id] = null }
                                            parsed != null && parsed in 0.0..100.0 -> marks = marks.toMutableMap().also { map -> map[criterion.id] = parsed }
                                        }
                                    },
                                    label = { Text("Calificación 0-100") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Resultado actual: ${"%.1f".format(calculated)}/100", style = MaterialTheme.typography.titleMedium)
                Text("Criterios evaluados: ${registered.size}/${criteria.size}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val targets = if (applyTeam && student.teamName.isNotBlank()) db.getStudents(period.id).filter { it.teamName.equals(student.teamName, ignoreCase = true) } else listOf(student)
                targets.forEach { target ->
                    criteria.forEach { criterion -> db.setRubricMark(target.id, criterion.id, marks[criterion.id]) }
                    db.calculateAndStoreRubricGrade(period.id, target.id, category.id)
                }
                onSaved()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
