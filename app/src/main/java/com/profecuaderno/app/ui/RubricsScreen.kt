package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*
import kotlin.math.abs

private data class RubricPreset(val name: String, val criteria: List<Pair<String, Double>>)
private data class WeightedPreset(val name: String, val items: List<Pair<String, Double>>)

private val rubricPresets = listOf(
    RubricPreset("Investigación", listOf("Título y planteamiento" to 10.0, "Objetivos" to 10.0, "Marco teórico" to 15.0, "Metodología" to 15.0, "Resultados" to 15.0, "Discusión" to 15.0, "Conclusiones" to 10.0, "Ortografía, redacción y referencias" to 10.0)),
    RubricPreset("Exposición", listOf("Dominio del tema" to 25.0, "Organización" to 15.0, "Claridad" to 15.0, "Material visual" to 10.0, "Expresión oral" to 10.0, "Uso del tiempo" to 10.0, "Respuesta a preguntas" to 15.0)),
    RubricPreset("Laboratorio", listOf("Preparación previa" to 15.0, "Procedimiento" to 20.0, "Manejo de materiales" to 15.0, "Orden y seguridad" to 15.0, "Resultados" to 20.0, "Reporte" to 10.0, "Responsabilidad" to 5.0)),
    RubricPreset("Trabajo escrito", listOf("Indicaciones" to 20.0, "Contenido" to 25.0, "Organización" to 15.0, "Calidad de información" to 15.0, "Presentación" to 10.0, "Ortografía y redacción" to 10.0, "Entrega en tiempo" to 5.0))
)

private val examPreset = WeightedPreset(
    "Ejemplo de exámenes",
    listOf("Examen 1: Oclusión" to 10.0, "Examen 2: Diagnóstico" to 30.0, "Examen final: Plan de tratamiento" to 60.0)
)
private val activityPreset = WeightedPreset(
    "Ejemplo de actividades",
    listOf("Actividad 1" to 20.0, "Actividad 2" to 20.0, "Actividad 3" to 20.0, "Actividad 4" to 20.0, "Actividad 5" to 20.0)
)

private enum class EvaluationChoice(val label: String) {
    ACTIVITIES("Promedio de actividades"),
    EXAMS("Promedio de exámenes"),
    RUBRIC("Rúbrica"),
    ATTENDANCE("Reporte de asistencia"),
    DIRECT("Calificación directa")
}

@Composable
fun RubricsScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    val categories = remember(refresh, period.id) { db.getCategories(period.id) }
    val total = categories.sumOf { it.weight }
    var showNewCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<EvaluationCategory?>(null) }
    var rubricCategory by remember { mutableStateOf<EvaluationCategory?>(null) }
    var weightedCategory by remember { mutableStateOf<EvaluationCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<EvaluationCategory?>(null) }

    LaunchedEffect(categories.map { it.id to it.mode }) {
        var migrated = false
        categories.filter { it.mode == EvaluationMode.AVERAGE.name }.forEach { category ->
            WeightedEvaluationStore.migrateLegacyAverage(db, category)
            migrated = true
        }
        if (migrated) onChanged()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Rubros de la calificación final", style = MaterialTheme.typography.titleMedium)
                Text("Combina exámenes, actividades, rúbricas, asistencia y calificaciones directas hasta completar 100%.")
                val ok = abs(total - 100.0) < 0.001
                Spacer(Modifier.height(6.dp))
                Text(
                    "Total actual: ${"%.1f".format(total)}%${if (ok) " ✓" else if (total < 100) " — faltan ${"%.1f".format(100-total)}%" else " — excede ${"%.1f".format(total-100)}%"}",
                    color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { showNewCategory = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Agregar rubro")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { category ->
                val weightedKind = WeightedEvaluationStore.kindFor(db, category)
                val mode = db.effectiveEvaluationMode(category)
                val criteria = remember(refresh, category.id) { db.getRubricCriteria(category.id) }
                val assessmentItems = remember(refresh, category.id) { db.getAssessmentItems(category.id) }
                val internalTotal = when {
                    weightedKind != null -> assessmentItems.sumOf { WeightedEvaluationStore.weightFor(db, it.id) }
                    mode == EvaluationMode.RUBRIC -> criteria.sumOf { it.weight }
                    else -> 0.0
                }

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleMedium)
                                Text("Vale ${"%.1f".format(category.weight)}% de la calificación final")
                                Text("Forma de evaluar: ${weightedKind?.label ?: mode.label}", style = MaterialTheme.typography.bodySmall)
                                when {
                                    weightedKind != null -> Text("${assessmentItems.size} elementos · pesos internos ${"%.1f".format(internalTotal)}%${if (abs(internalTotal-100.0)<0.001) " ✓" else " ⚠"}", style = MaterialTheme.typography.bodySmall)
                                    mode == EvaluationMode.RUBRIC -> Text("${criteria.size} criterios · ${"%.1f".format(internalTotal)}%${if (abs(internalTotal-100.0)<0.001) " ✓" else " ⚠"}", style = MaterialTheme.typography.bodySmall)
                                    mode == EvaluationMode.ATTENDANCE -> Text("Usa el porcentaje actual del reporte de asistencia.", style = MaterialTheme.typography.bodySmall)
                                    else -> Text("Se captura una sola calificación.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { editingCategory = category }) { Icon(Icons.Default.Edit, "Editar") }
                            IconButton(onClick = { deletingCategory = category }) { Icon(Icons.Default.Delete, "Eliminar") }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            weightedKind != null -> OutlinedButton(onClick = { weightedCategory = category }) {
                                Text(if (assessmentItems.isEmpty()) "Configurar ${if (weightedKind == WeightedEvaluationKind.EXAMS) "exámenes" else "actividades"}" else "Editar nombres y porcentajes")
                            }
                            mode == EvaluationMode.RUBRIC -> OutlinedButton(onClick = { rubricCategory = category }) {
                                Text(if (criteria.isEmpty()) "Configurar rúbrica" else "Editar rúbrica")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showNewCategory) {
        CategoryDialog(
            title = "Nuevo rubro",
            initial = EvaluationCategory(0, period.id, "", 0.0, categories.size, EvaluationMode.DIRECT.name),
            initialChoice = EvaluationChoice.ACTIVITIES,
            onDismiss = { showNewCategory = false },
            onSave = { category, choice ->
                val underlyingMode = when (choice) {
                    EvaluationChoice.RUBRIC -> EvaluationMode.RUBRIC
                    EvaluationChoice.ATTENDANCE -> EvaluationMode.ATTENDANCE
                    else -> EvaluationMode.DIRECT
                }
                val id = db.saveCategory(category.copy(mode = underlyingMode.name))
                val kind = when (choice) {
                    EvaluationChoice.ACTIVITIES -> WeightedEvaluationKind.ACTIVITIES
                    EvaluationChoice.EXAMS -> WeightedEvaluationKind.EXAMS
                    else -> null
                }
                WeightedEvaluationStore.setKind(db, id, kind)
                showNewCategory = false
                onChanged()
            }
        )
    }

    editingCategory?.let { category ->
        val kind = WeightedEvaluationStore.kindFor(db, category)
        val initialChoice = when {
            kind == WeightedEvaluationKind.ACTIVITIES -> EvaluationChoice.ACTIVITIES
            kind == WeightedEvaluationKind.EXAMS -> EvaluationChoice.EXAMS
            db.effectiveEvaluationMode(category) == EvaluationMode.RUBRIC -> EvaluationChoice.RUBRIC
            db.effectiveEvaluationMode(category) == EvaluationMode.ATTENDANCE -> EvaluationChoice.ATTENDANCE
            else -> EvaluationChoice.DIRECT
        }
        CategoryDialog(
            title = "Editar rubro",
            initial = category,
            initialChoice = initialChoice,
            onDismiss = { editingCategory = null },
            onSave = { updated, choice ->
                val mode = when (choice) {
                    EvaluationChoice.RUBRIC -> EvaluationMode.RUBRIC
                    EvaluationChoice.ATTENDANCE -> EvaluationMode.ATTENDANCE
                    else -> EvaluationMode.DIRECT
                }
                db.saveCategory(updated.copy(mode = mode.name))
                val weighted = when (choice) {
                    EvaluationChoice.ACTIVITIES -> WeightedEvaluationKind.ACTIVITIES
                    EvaluationChoice.EXAMS -> WeightedEvaluationKind.EXAMS
                    else -> null
                }
                WeightedEvaluationStore.setKind(db, updated.id, weighted)
                if (weighted != null) WeightedEvaluationStore.recalculateCategory(db, updated)
                editingCategory = null
                onChanged()
            }
        )
    }

    weightedCategory?.let { category ->
        val kind = WeightedEvaluationStore.kindFor(db, category) ?: WeightedEvaluationKind.ACTIVITIES
        WeightedItemsDialog(db, category, kind, refresh, onDismiss = { weightedCategory = null }, onChanged = onChanged)
    }

    rubricCategory?.let { category ->
        RubricDialog(db, category, refresh, onDismiss = { rubricCategory = null }, onChanged = onChanged)
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Eliminar rubro") },
            text = { Text("¿Enviar '${category.name}' a Papelera? Sus actividades, exámenes, rúbrica y calificaciones se conservarán para poder restaurarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    // No borres la configuración ponderada aquí: deleteCategory es interceptado
                    // por la Papelera y el rubro todavía debe poder restaurarse íntegramente.
                    db.deleteCategory(category.id)
                    deletingCategory = null
                    onChanged()
                }) { Text("Enviar a Papelera") }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    title: String,
    initial: EvaluationCategory,
    initialChoice: EvaluationChoice,
    onDismiss: () -> Unit,
    onSave: (EvaluationCategory, EvaluationChoice) -> Unit
) {
    val suggestions = listOf("Exámenes", "Actividades", "Investigación", "Exposición", "Laboratorio", "Tareas", "Asistencia")
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var weightText by remember(initial.id) { mutableStateOf(if (initial.weight == 0.0) "" else initial.weight.toString()) }
    var choice by remember(initial.id) { mutableStateOf(initialChoice) }
    var expanded by remember { mutableStateOf(false) }
    val weight = weightText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ideas de rubros", style = MaterialTheme.typography.labelLarge)
                suggestions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { suggestion ->
                            AssistChip(onClick = {
                                name = suggestion
                                choice = when (suggestion) {
                                    "Exámenes" -> EvaluationChoice.EXAMS
                                    "Actividades", "Tareas" -> EvaluationChoice.ACTIVITIES
                                    "Asistencia" -> EvaluationChoice.ATTENDANCE
                                    else -> EvaluationChoice.RUBRIC
                                }
                            }, label = { Text(suggestion) })
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del rubro") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(weightText, { weightText = it }, label = { Text("Porcentaje de la calificación final") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = choice.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("¿Cómo se va a evaluar?") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        EvaluationChoice.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { choice = option; expanded = false }) }
                    }
                }
                Text(
                    when (choice) {
                        EvaluationChoice.ACTIVITIES -> "Cada actividad puede tener nombre y porcentaje propio. Ejemplo: Actividad 1 20%, Actividad 2 30%, Proyecto 50%."
                        EvaluationChoice.EXAMS -> "Cada examen puede tener nombre y porcentaje propio. Ejemplo: Examen 1: Oclusión 10%, Examen 2: Diagnóstico 30%, Examen final 60%."
                        EvaluationChoice.RUBRIC -> "Puedes usar una rúbrica sugerida o crear tu propia rúbrica con criterios y porcentajes."
                        EvaluationChoice.ATTENDANCE -> "Usa automáticamente el porcentaje del reporte de asistencia del alumno."
                        EvaluationChoice.DIRECT -> "Captura directamente una calificación de 0 a 100."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && weight != null && weight in 0.0..100.0, onClick = { onSave(initial.copy(name = name, weight = weight!!), choice) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun WeightedItemsDialog(
    db: TeacherDbHelper,
    category: EvaluationCategory,
    kind: WeightedEvaluationKind,
    refresh: Int,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val items = remember(refresh, category.id) { db.getAssessmentItems(category.id) }
    val total = items.sumOf { WeightedEvaluationStore.weightFor(db, it.id) }
    var editing by remember { mutableStateOf<AssessmentItem?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AssessmentItem?>(null) }
    var applyPreset by remember { mutableStateOf(false) }
    val label = if (kind == WeightedEvaluationKind.EXAMS) "exámenes" else "actividades"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${category.name} · $label") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                Text("Pon nombre y porcentaje a cada ${if (kind == WeightedEvaluationKind.EXAMS) "examen" else "actividad"}. Los porcentajes internos deben sumar 100%.")
                Text("Total interno: ${"%.1f".format(total)}%${if (abs(total-100.0)<0.001) " ✓" else " ⚠"}", color = if (abs(total-100.0)<0.001 || items.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    OutlinedButton(onClick = { applyPreset = true }, modifier = Modifier.fillMaxWidth()) { Text(if (kind == WeightedEvaluationKind.EXAMS) "Usar ejemplo de exámenes" else "Usar ejemplo de actividades") }
                    Spacer(Modifier.height(6.dp))
                }
                if (items.size > 1) {
                    TextButton(onClick = {
                        WeightedEvaluationStore.distributeEvenly(db, category.id)
                        WeightedEvaluationStore.recalculateCategory(db, category)
                        onChanged()
                    }) { Text("Repartir 100% por igual") }
                }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.id }) { item ->
                        val weight = WeightedEvaluationStore.weightFor(db, item.id)
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.name); Text("${"%.1f".format(weight)}%", style = MaterialTheme.typography.bodySmall) }
                                IconButton(onClick = { editing = item }) { Icon(Icons.Default.Edit, "Editar") }
                                IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, "Eliminar") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showNew = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar ${if (kind == WeightedEvaluationKind.EXAMS) "examen" else "actividad"}") }
            }
        },
        confirmButton = { TextButton(enabled = items.isEmpty() || abs(total - 100.0) < 0.001, onClick = onDismiss) { Text("Listo") } }
    )

    val preset = if (kind == WeightedEvaluationKind.EXAMS) examPreset else activityPreset
    if (applyPreset) {
        AlertDialog(
            onDismissRequest = { applyPreset = false }, title = { Text(preset.name) },
            text = { Column { preset.items.forEach { Text("${it.first} — ${"%.0f".format(it.second)}%") } } },
            confirmButton = { TextButton(onClick = {
                preset.items.forEachIndexed { index, pair ->
                    val id = db.saveAssessmentItem(AssessmentItem(0, category.id, pair.first, index))
                    WeightedEvaluationStore.setWeight(db, id, pair.second)
                }
                WeightedEvaluationStore.recalculateCategory(db, category); applyPreset = false; onChanged()
            }) { Text("Usar ejemplo") } },
            dismissButton = { TextButton(onClick = { applyPreset = false }) { Text("Cancelar") } }
        )
    }

    if (showNew) WeightedItemEditor(
        title = if (kind == WeightedEvaluationKind.EXAMS) "Nuevo examen" else "Nueva actividad", initialName = "", initialWeight = 0.0,
        placeholder = if (kind == WeightedEvaluationKind.EXAMS) "Ej. Examen 1: Oclusión" else "Ej. Actividad 1: Mapa conceptual",
        onDismiss = { showNew = false }, onSave = { name, weight ->
            val id = db.saveAssessmentItem(AssessmentItem(0, category.id, name, items.size)); WeightedEvaluationStore.setWeight(db, id, weight)
            WeightedEvaluationStore.recalculateCategory(db, category); showNew = false; onChanged()
        }
    )

    editing?.let { item -> WeightedItemEditor(
        title = "Editar", initialName = item.name, initialWeight = WeightedEvaluationStore.weightFor(db, item.id), placeholder = "Nombre",
        onDismiss = { editing = null }, onSave = { name, weight ->
            db.saveAssessmentItem(item.copy(name = name)); WeightedEvaluationStore.setWeight(db, item.id, weight)
            WeightedEvaluationStore.recalculateCategory(db, category); editing = null; onChanged()
        }
    ) }

    deleting?.let { item -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Eliminar") }, text = { Text("¿Eliminar '${item.name}' y sus calificaciones?") },
        confirmButton = { TextButton(onClick = { db.deleteAssessmentItem(item.id); WeightedEvaluationStore.recalculateCategory(db, category); deleting = null; onChanged() }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
    ) }
}

@Composable
private fun WeightedItemEditor(title: String, initialName: String, initialWeight: Double, placeholder: String, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var weightText by remember(initialWeight) { mutableStateOf(if (initialWeight == 0.0) "" else initialWeight.toString()) }
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weightText, { weightText = it }, label = { Text("Porcentaje dentro del rubro") }, placeholder = { Text("Ej. 10") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && weight != null && weight in 0.0..100.0, onClick = { onSave(name, weight!!) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RubricDialog(db: TeacherDbHelper, category: EvaluationCategory, refresh: Int, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val criteria = remember(refresh, category.id) { db.getRubricCriteria(category.id) }
    val total = criteria.sumOf { it.weight }
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RubricCriterion?>(null) }
    var deleting by remember { mutableStateOf<RubricCriterion?>(null) }
    var selectedPreset by remember { mutableStateOf<RubricPreset?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Rúbrica · ${category.name}") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Text("Los criterios internos deben sumar 100%.")
            Text("Total: ${"%.1f".format(total)}%", color = if (abs(total-100.0)<0.001 || criteria.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            if (criteria.isEmpty()) {
                Text("Usar una rúbrica", style = MaterialTheme.typography.titleSmall)
                rubricPresets.forEach { preset -> OutlinedButton(onClick = { selectedPreset = preset }, modifier = Modifier.fillMaxWidth()) { Text("Usar esta rúbrica: ${preset.name}") }; Spacer(Modifier.height(4.dp)) }
                HorizontalDivider(); Spacer(Modifier.height(8.dp)); Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear mi propia rúbrica") }; Spacer(Modifier.height(8.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(criteria, key = { it.id }) { criterion -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${criterion.name} · ${"%.1f".format(criterion.weight)}%", modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = criterion }) { Icon(Icons.Default.Edit, "Editar") }
                        IconButton(onClick = { deleting = criterion }) { Icon(Icons.Default.Delete, "Eliminar") }
                    }
                } }
            }
            if (criteria.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Button(onClick = { showNew = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar criterio") } }
        } },
        confirmButton = { TextButton(enabled = criteria.isEmpty() || abs(total-100.0)<0.001, onClick = onDismiss) { Text("Listo") } }
    )

    selectedPreset?.let { preset -> AlertDialog(
        onDismissRequest = { selectedPreset = null }, title = { Text(preset.name) },
        text = { Column { preset.criteria.forEach { Text("${it.first} — ${"%.0f".format(it.second)}%") } } },
        confirmButton = { TextButton(onClick = { preset.criteria.forEachIndexed { index, pair -> db.saveRubricCriterion(RubricCriterion(0, category.id, pair.first, pair.second, index)) }; selectedPreset = null; onChanged() }) { Text("Usar esta rúbrica") } },
        dismissButton = { TextButton(onClick = { selectedPreset = null }) { Text("Cancelar") } }
    ) }

    if (showNew) CriterionDialog(RubricCriterion(0, category.id, "", 0.0, criteria.size), { showNew = false }) { db.saveRubricCriterion(it); showNew = false; onChanged() }
    editing?.let { criterion -> CriterionDialog(criterion, { editing = null }) { db.saveRubricCriterion(it); editing = null; onChanged() } }
    deleting?.let { criterion -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Eliminar criterio") }, text = { Text("¿Eliminar '${criterion.name}'?") },
        confirmButton = { TextButton(onClick = { db.deleteRubricCriterion(criterion.id); deleting = null; onChanged() }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
    ) }
}

@Composable
private fun CriterionDialog(initial: RubricCriterion, onDismiss: () -> Unit, onSave: (RubricCriterion) -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var weightText by remember(initial.id) { mutableStateOf(if (initial.weight == 0.0) "" else initial.weight.toString()) }
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "Nuevo criterio" else "Editar criterio") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("¿Qué vas a evaluar?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weightText, { weightText = it }, label = { Text("Porcentaje dentro de la rúbrica") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && weight != null && weight in 0.0..100.0, onClick = { onSave(initial.copy(name = name, weight = weight!!)) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
