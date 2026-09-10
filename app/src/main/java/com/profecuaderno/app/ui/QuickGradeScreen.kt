package com.profecuaderno.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickGradeScreen(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit
) {
    val finalized = remember(refresh, period.id) { EvaluationSetupStore.isFinalized(db, period.id) }
    if (!finalized) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.fillMaxWidth().widthIn(max = 620.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Captura rápida bloqueada", style = MaterialTheme.typography.titleLarge)
                    Text("Primero finaliza el esquema de evaluación. Los porcentajes finales, las actividades/exámenes y las rúbricas deben sumar 100%.")
                    Text("Puedes seguir guardando la configuración como borrador desde Rubros y rúbricas.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    val categories = remember(refresh, period.id) {
        db.getCategories(period.id).filter { WeightedEvaluationStore.kindFor(db, it) != null }
    }
    var selectedCategoryId by remember(categories.map { it.id }) { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    val items = remember(refresh, selectedCategoryId) { selected?.let { db.getAssessmentItems(it.id) }.orEmpty() }
    var values by remember(refresh, selectedCategoryId, students.map { it.id }, items.map { it.id }) {
        mutableStateOf(
            students.associate { student ->
                student.id to items.associate { item ->
                    item.id to (db.getAssessmentScore(student.id, item.id)?.let { "%.1f".format(it) } ?: "")
                }
            }
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(vertical).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Captura rápida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Formato tipo libreta. Deja una celda vacía si todavía no se evalúa; escribe 0 si la calificación real es cero.")
            }
        }

        if (categories.isEmpty()) {
            Text("No hay rubros de actividades o exámenes disponibles para captura rápida.")
            return@Column
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selected?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Rubro") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            selectedCategoryId = category.id
                            expanded = false
                            message = null
                        }
                    )
                }
            }
        }

        if (selected != null) {
            val configured = WeightedEvaluationStore.isConfigured(db, selected.id)
            val total = items.sumOf { WeightedEvaluationStore.weightFor(db, it.id) }
            if (!configured || abs(total - 100.0) > 0.001) {
                AssistChip(onClick = {}, label = { Text("Este rubro está en borrador: sus porcentajes internos deben sumar 100% antes de capturar.") })
            }

            if (students.isEmpty()) {
                Text("No hay estudiantes en este grupo.")
            } else if (items.isEmpty()) {
                Text("No hay actividades o exámenes configurados en este rubro.")
            } else {
                Box(Modifier.fillMaxWidth().horizontalScroll(horizontal)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(tonalElevation = 1.dp, modifier = Modifier.width(190.dp).heightIn(min = 56.dp)) {
                                Box(Modifier.padding(10.dp), contentAlignment = Alignment.CenterStart) {
                                    Text("Estudiante", fontWeight = FontWeight.Bold)
                                }
                            }
                            items.forEach { item ->
                                Surface(tonalElevation = 1.dp, modifier = Modifier.width(108.dp).heightIn(min = 56.dp)) {
                                    Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                        Text("${item.name}\n${"%.0f".format(WeightedEvaluationStore.weightFor(db, item.id))}%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        students.forEach { student ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.width(190.dp).heightIn(min = 60.dp)) {
                                    Box(Modifier.padding(10.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(student.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                items.forEach { item ->
                                    val text = values[student.id]?.get(item.id).orEmpty()
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { raw ->
                                            if (raw.length <= 6) {
                                                values = values.toMutableMap().also { studentMap ->
                                                    val itemMap = studentMap[student.id].orEmpty().toMutableMap()
                                                    itemMap[item.id] = raw
                                                    studentMap[student.id] = itemMap
                                                }
                                            }
                                        },
                                        modifier = Modifier.width(108.dp).padding(horizontal = 3.dp),
                                        singleLine = true,
                                        placeholder = { Text("—") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    enabled = configured,
                    onClick = {
                        var invalid = 0
                        students.forEach { student ->
                            items.forEach { item ->
                                val raw = values[student.id]?.get(item.id).orEmpty().trim()
                                val parsed = raw.replace(',', '.').toDoubleOrNull()
                                when {
                                    raw.isBlank() -> db.setAssessmentScore(student.id, item.id, null)
                                    parsed != null && parsed in 0.0..100.0 -> db.setAssessmentScore(student.id, item.id, parsed)
                                    else -> invalid++
                                }
                            }
                            WeightedEvaluationStore.recalculateStudent(db, period.id, student.id, selected.id)
                        }
                        message = if (invalid == 0) "Calificaciones guardadas." else "Se guardaron las celdas válidas; $invalid celdas tienen valores fuera de 0 a 100."
                        onChanged()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Guardar captura") }
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
    }
}
