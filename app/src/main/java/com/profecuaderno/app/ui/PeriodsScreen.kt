package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.*
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun PeriodsScreen(
    db: TeacherDbHelper,
    refresh: Int,
    onChanged: () -> Unit,
    onOpen: (AcademicPeriod) -> Unit
) {
    val periods = remember(refresh) { TrashStore.visiblePeriods(db) }
    val active = periods.firstOrNull { it.active }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AcademicPeriod?>(null) }
    var auditing by remember { mutableStateOf<AcademicPeriod?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mis grupos", style = MaterialTheme.typography.titleMedium)
                            Text("Abre un grupo para trabajar.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (periods.isEmpty()) {
                        item {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp)) {
                                    Text("Aún no tienes grupos.")
                                    Text("Pulsa + para crear tu primer grupo.")
                                }
                            }
                        }
                    }
                    items(periods, key = { it.id }) { period ->
                        ElevatedCard(onClick = { onOpen(period) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (period.active) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(period.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${period.type} · ${period.startDate.ifBlank { "Sin inicio" }} → ${period.endDate.ifBlank { "Sin término" }}", style = MaterialTheme.typography.bodySmall)
                                        Text(if (period.archived) "Periodo cerrado · toca para reabrir" else "Toca para abrir", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { deleting = period }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar ${period.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (!period.archived) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { auditing = period },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.FactCheck, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Revisar antes de cerrar")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
            FloatingActionButton(onClick = { showNew = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Icon(Icons.Default.Add, "Nuevo grupo")
            }
        }
    }

    if (showNew) NewProgramDialog(periods = periods, active = active, onDismiss = { showNew = false }) { name, type, start, end, copyFrom ->
        db.createPeriod(name, type, start, end, copyFrom, true)
        showNew = false
        onChanged()
        db.getActivePeriod()?.let(onOpen)
    }


    auditing?.let { period ->
        val summary = remember(refresh, period.id) { PeriodAuditStore.summary(db, period.id) }
        AlertDialog(
            onDismissRequest = { auditing = null },
            title = { Text("Resumen antes de cerrar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(period.name, style = MaterialTheme.typography.titleSmall)
                    Text("${summary.studentCount} estudiantes")
                    Text("${summary.activityCount} actividades · ${summary.examCount} exámenes")
                    Text("Asistencia: ${"%.1f".format(summary.attendanceWeight)}% de la calificación final")
                    Text("Total del esquema: ${"%.1f".format(summary.evaluationWeightTotal)}%")
                    Text("${summary.studentsWithPending} estudiantes con pendientes · ${summary.pendingCells} capturas pendientes")
                    if (!summary.readyToClose) {
                        Text("El esquema todavía no está completo al 100%. Corrígelo antes de cerrar el periodo.", color = MaterialTheme.colorScheme.error)
                    } else if (summary.studentsWithPending > 0) {
                        Text("El esquema es válido, pero todavía existen evaluaciones pendientes. Puedes cerrar si así lo decides.")
                    } else {
                        Text("✓ El periodo está listo para cerrarse.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = summary.readyToClose,
                    onClick = {
                        db.closePeriod(period.id)
                        auditing = null
                        onChanged()
                    }
                ) { Text(if (summary.studentsWithPending > 0) "Cerrar con pendientes" else "Cerrar periodo") }
            },
            dismissButton = { TextButton(onClick = { auditing = null }) { Text("Seguir trabajando") } }
        )
    }

    deleting?.let { period ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Enviar grupo a Papelera") },
            text = { Text("¿Enviar '${period.name}' a Papelera? Sus estudiantes, asistencias, calificaciones, rúbricas, planeación y eventos se conservarán para poder restaurarlos.") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.trashGroup(db, period)
                    deleting = null
                    onChanged()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "${period.name} enviado a Papelera",
                            actionLabel = "Deshacer",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            TrashStore.entries(db).firstOrNull { it.type == TrashStore.TYPE_GROUP && it.entityId == period.id }?.let { TrashStore.restore(db, it) }
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
private fun NewProgramDialog(
    periods: List<AcademicPeriod>,
    active: AcademicPeriod?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Trimestre") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var end by remember { mutableStateOf("") }
    var copyStructure by remember { mutableStateOf(active != null) }
    var copyFromId by remember { mutableStateOf(active?.id) }
    var typeExpanded by remember { mutableStateOf(false) }
    var copyExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del grupo / materia / curso") }, placeholder = { Text("Ej. Matemáticas 2B / Biología / Taller de diseño") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(type, {}, readOnly = true, label = { Text("Tipo de periodo") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("Bimestre", "Trimestre", "Cuatrimestre", "Semestre", "Anual", "Curso corto", "Otro").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { type = option; typeExpanded = false }) }
                    }
                }
                DatePickerField(start, { start = it }, "Fecha de inicio")
                DatePickerField(end, { end = it }, "Fecha de término")
                if (periods.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(copyStructure, { copyStructure = it }); Text("Usar plantilla de otro grupo") }
                    if (copyStructure) {
                        ExposedDropdownMenuBox(expanded = copyExpanded, onExpandedChange = { copyExpanded = !copyExpanded }) {
                            val source = periods.firstOrNull { it.id == copyFromId }
                            OutlinedTextField(value = source?.name ?: "Selecciona un grupo", onValueChange = {}, readOnly = true, label = { Text("Copiar rubros, rúbricas y actividades de") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(copyExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                            ExposedDropdownMenu(expanded = copyExpanded, onDismissRequest = { copyExpanded = false }) {
                                periods.forEach { sourcePeriod -> DropdownMenuItem(text = { Text(sourcePeriod.name) }, onClick = { copyFromId = sourcePeriod.id; copyExpanded = false }) }
                            }
                        }
                        Text("Solo se copiará la estructura de evaluación. No se copian estudiantes, asistencias ni calificaciones.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, type, start, end, if (copyStructure) copyFromId else null) }) { Text("Crear grupo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
