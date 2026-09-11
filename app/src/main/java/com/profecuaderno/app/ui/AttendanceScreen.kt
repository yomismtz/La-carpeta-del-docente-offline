package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.AttendanceHistoryStore
import com.profecuaderno.app.data.AttendancePolicyStore
import com.profecuaderno.app.data.AttendanceSession
import com.profecuaderno.app.data.AttendanceStatus
import com.profecuaderno.app.data.JustifiedEffect
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.TeacherDbHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class AttendanceView { MENU, TAKE, REVIEW }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AttendanceScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    var view by remember(period.id) { mutableStateOf(AttendanceView.MENU) }
    var showOptions by remember { mutableStateOf(false) }
    var historyStudent by remember { mutableStateOf<Student?>(null) }
    val policy = remember(refresh, period.id) { AttendancePolicyStore.policy(db, period.id) }

    when (view) {
        AttendanceView.MENU -> AttendanceMenu(
            policyText = "${policy.latePerAbsence} retardos = 1 falta · Justificada: ${policy.justifiedEffect.label}",
            onTake = { view = AttendanceView.TAKE },
            onReview = { view = AttendanceView.REVIEW },
            onOptions = { showOptions = true }
        )
        AttendanceView.TAKE -> AttendanceTakingContent(
            db = db,
            period = period,
            refresh = refresh,
            onChanged = onChanged,
            onBack = { view = AttendanceView.MENU },
            onOptions = { showOptions = true },
            onHistory = { historyStudent = it }
        )
        AttendanceView.REVIEW -> AttendanceReviewContent(
            db = db,
            period = period,
            refresh = refresh,
            onChanged = onChanged,
            onBack = { view = AttendanceView.MENU },
            onOptions = { showOptions = true },
            onHistory = { historyStudent = it }
        )
    }

    if (showOptions) {
        AttendancePolicySheet(
            initialLatePerAbsence = policy.latePerAbsence,
            initialJustifiedEffect = policy.justifiedEffect,
            onDismiss = { showOptions = false },
            onSave = { lateRule, effect ->
                AttendancePolicyStore.setJustifiedEffect(db, period.id, effect)
                AttendancePolicyStore.setLatePerAbsence(db, period.id, lateRule)
                showOptions = false
                onChanged()
            }
        )
    }

    historyStudent?.let { student ->
        AttendanceHistorySheet(db, period, student, refresh, onChanged) { historyStudent = null }
    }
}

@Composable
private fun AttendanceMenu(
    policyText: String,
    onTake: () -> Unit,
    onReview: () -> Unit,
    onOptions: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("¿Qué deseas hacer?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Elige una opción. Puedes regresar aquí en cualquier momento.", style = MaterialTheme.typography.bodyMedium)

        ElevatedCard(onClick = onTake, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pasar asistencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Crea o abre la sesión del día y registra presente, falta, retardo o justificada.")
            }
        }
        ElevatedCard(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Verificar asistencias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Consulta fechas anteriores, revisa el pase completo y corrige registros cuando sea necesario.")
            }
        }
        OutlinedCard(onClick = onOptions, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reglas de asistencia", fontWeight = FontWeight.SemiBold)
                }
                Text(policyText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceTakingContent(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onHistory: (Student) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var title by remember { mutableStateOf("Clase") }
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    val session = remember(refresh, period.id, date) { db.getAttendanceSession(period.id, date) }
    var worked by remember { mutableStateOf(true) }
    var showSessionEditor by remember { mutableStateOf(session == null) }

    LaunchedEffect(session?.id, session?.title, session?.worked) {
        if (session != null) {
            title = session.title
            worked = session.worked
            showSessionEditor = false
        } else {
            title = "Clase"
            worked = true
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Opciones")
            }
            OutlinedButton(onClick = onOptions, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reglas")
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(session?.title ?: title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                session == null -> "Aún no se ha creado el pase de lista."
                                !session.worked -> "Clase suspendida / no trabajada."
                                else -> "${students.size} estudiantes"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { showSessionEditor = !showSessionEditor }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.EditCalendar, contentDescription = "Editar fecha y sesión")
                    }
                }

                if (showSessionEditor) {
                    DatePickerField(date, { date = it }, "Fecha")
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Sesión") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = worked, onCheckedChange = { worked = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (worked) "Día trabajado" else "Clase suspendida", modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            db.createOrUpdateAttendanceSession(period.id, date, title, worked)
                            showSessionEditor = false
                            onChanged()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(if (session == null) "Crear pase de lista" else "Guardar cambios") }
                }
            }
        }

        when {
            session == null -> EmptyAttendanceMessage("Crea la sesión para comenzar a pasar lista.")
            !session.worked -> EmptyAttendanceMessage("Esta fecha está marcada como no trabajada y no afecta el porcentaje de asistencia.")
            students.isEmpty() -> EmptyAttendanceMessage("Agrega estudiantes primero.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(students, key = { it.id }) { student ->
                    StudentAttendanceCard(
                        db = db,
                        period = period,
                        session = session,
                        student = student,
                        refresh = refresh,
                        onChanged = onChanged,
                        onHistory = onHistory
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceReviewContent(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onHistory: (Student) -> Unit
) {
    val sessions = remember(refresh, period.id) { db.listAttendanceSessions(period.id) }
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    var selectedSessionId by remember(period.id) { mutableStateOf<Long?>(null) }
    val selected = sessions.firstOrNull { it.id == selectedSessionId }

    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { if (selected != null) selectedSessionId = null else onBack() },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (selected != null) "Fechas" else "Opciones")
            }
            OutlinedButton(onClick = onOptions, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reglas")
            }
        }

        if (selected == null) {
            Text("Asistencias registradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (sessions.isEmpty()) {
                EmptyAttendanceMessage("Todavía no hay pases de lista guardados para este grupo.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        ElevatedCard(onClick = { selectedSessionId = session.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(session.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(session.title.ifBlank { "Clase" })
                                Text(
                                    if (session.worked) "Abrir y verificar registros" else "Clase suspendida / no trabajada",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        } else {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(selected.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selected.title.ifBlank { "Clase" })
                    if (!selected.worked) Text("Esta sesión está marcada como no trabajada.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!selected.worked) {
                EmptyAttendanceMessage("No hay asistencia que verificar porque la sesión no cuenta como día trabajado.")
            } else if (students.isEmpty()) {
                EmptyAttendanceMessage("Este grupo no tiene estudiantes.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(students, key = { it.id }) { student ->
                        StudentAttendanceCard(
                            db = db,
                            period = period,
                            session = selected,
                            student = student,
                            refresh = refresh,
                            onChanged = onChanged,
                            onHistory = onHistory
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentAttendanceCard(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    session: AttendanceSession,
    student: Student,
    refresh: Int,
    onChanged: () -> Unit,
    onHistory: (Student) -> Unit
) {
    val current = remember(refresh, session.id, student.id) { db.getAttendanceStatus(session.id, student.id) }
    val base = AttendancePolicyStore.baseStatus(current)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                student.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4
            )
            Text(
                "Asistencia acumulada: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AttendanceChip("Presente", base == AttendanceStatus.PRESENT) {
                    AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, AttendanceStatus.PRESENT)
                    onChanged()
                }
                AttendanceChip("Falta", base == AttendanceStatus.ABSENT) {
                    AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, AttendanceStatus.ABSENT)
                    onChanged()
                }
                AttendanceChip("Retardo", base == AttendanceStatus.LATE) {
                    AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, AttendanceStatus.LATE)
                    onChanged()
                }
                AttendanceChip("Justificada", base == AttendanceStatus.JUSTIFIED) {
                    AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, AttendanceStatus.JUSTIFIED)
                    onChanged()
                }
                OutlinedButton(onClick = { onHistory(student) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Historial")
                }
            }
        }
    }
}

@Composable
private fun AttendanceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        modifier = Modifier.heightIn(min = 44.dp)
    )
}

@Composable
private fun EmptyAttendanceMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendancePolicySheet(
    initialLatePerAbsence: Int,
    initialJustifiedEffect: JustifiedEffect,
    onDismiss: () -> Unit,
    onSave: (Int, JustifiedEffect) -> Unit
) {
    var lateRule by remember { mutableIntStateOf(initialLatePerAbsence) }
    var effect by remember { mutableStateOf(initialJustifiedEffect) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Reglas de asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Estas reglas se aplican al grupo actual y recalculan los porcentajes conservando el historial.")

            Text("Retardos equivalentes a una falta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$lateRule retardos = 1 falta", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = lateRule.toFloat(),
                onValueChange = { lateRule = it.roundToInt().coerceIn(2, 10) },
                valueRange = 2f..10f,
                steps = 7
            )
            Text("Los retardos se conservan como retardos en el historial. Cada bloque de $lateRule reduce la asistencia como una falta.", style = MaterialTheme.typography.bodySmall)

            Text("¿Cómo cuenta una falta justificada?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            JustifiedEffect.entries.forEach { option ->
                FilterChip(
                    selected = effect == option,
                    onClick = { effect = option },
                    label = { Text(option.label) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                )
            }

            Button(
                onClick = { onSave(lateRule, effect) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Guardar reglas") }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Cancelar") }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceHistorySheet(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    student: Student,
    refresh: Int,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var localRefresh by remember { mutableIntStateOf(0) }
    val tick = refresh + localRefresh
    val incidents = remember(tick, period.id, student.id) {
        AttendanceHistoryStore.incidentsForStudent(db, period.id, student.id)
    }
    val counts = remember(tick, period.id, student.id) {
        AttendancePolicyStore.aggregatedCounts(db, period.id, student.id)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Historial de asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(student.name, style = MaterialTheme.typography.titleMedium)
            Text("Asistencia actual: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%", fontWeight = FontWeight.SemiBold)
            Text(
                "Faltas: ${counts[AttendanceStatus.ABSENT] ?: 0} · Justificadas: ${counts[AttendanceStatus.JUSTIFIED] ?: 0} · Retardos: ${counts[AttendanceStatus.LATE] ?: 0}",
                style = MaterialTheme.typography.bodySmall
            )

            if (incidents.isEmpty()) {
                Text("Este estudiante no tiene incidencias registradas.", modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(incidents, key = { "${it.sessionId}-${it.status.name}" }) { entry ->
                        val base = AttendancePolicyStore.baseStatus(entry.status)
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(entry.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(entry.title.ifBlank { "Clase" }, style = MaterialTheme.typography.bodySmall)
                                Text(shortStatus(entry.status), style = MaterialTheme.typography.bodyMedium)
                                if (base == AttendanceStatus.ABSENT) {
                                    Button(
                                        onClick = {
                                            AttendancePolicyStore.setStatus(db, period.id, entry.sessionId, student.id, AttendanceStatus.JUSTIFIED)
                                            localRefresh++
                                            onChanged()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) { Text("Cambiar falta a Justificada") }
                                } else if (base == AttendanceStatus.JUSTIFIED) {
                                    OutlinedButton(
                                        onClick = {
                                            AttendancePolicyStore.setStatus(db, period.id, entry.sessionId, student.id, AttendanceStatus.ABSENT)
                                            localRefresh++
                                            onChanged()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Volver a marcar como Falta")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cerrar") }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun shortStatus(status: AttendanceStatus): String = when (AttendancePolicyStore.baseStatus(status)) {
    AttendanceStatus.PRESENT -> "✓ Presente"
    AttendanceStatus.ABSENT -> "✕ Falta"
    AttendanceStatus.LATE -> if (status == AttendanceStatus.LATE_PENALTY) "◷ Retardo · completa equivalencia a falta" else "◷ Retardo"
    AttendanceStatus.JUSTIFIED -> when (status) {
        AttendanceStatus.JUSTIFIED_ABSENT -> "J Justificada · cuenta como falta"
        AttendanceStatus.JUSTIFIED_LATE, AttendanceStatus.JUSTIFIED_LATE_PENALTY -> "J Justificada · cuenta como retardo"
        AttendanceStatus.JUSTIFIED_PRESENT -> "J Justificada · cuenta como asistencia"
        else -> "J Justificada"
    }
    else -> status.label
}
