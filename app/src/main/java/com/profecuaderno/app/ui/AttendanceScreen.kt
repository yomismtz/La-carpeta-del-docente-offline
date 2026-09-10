package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.AttendanceHistoryStore
import com.profecuaderno.app.data.AttendancePolicyStore
import com.profecuaderno.app.data.AttendanceStatus
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.TeacherDbHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AttendanceScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var title by remember { mutableStateOf("Clase") }
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    val session = remember(refresh, period.id, date) { db.getAttendanceSession(period.id, date) }
    var worked by remember { mutableStateOf(true) }
    var historyStudent by remember { mutableStateOf<Student?>(null) }
    var showSessionEditor by remember { mutableStateOf(session == null) }
    var showOptions by remember { mutableStateOf(false) }
    var justifiedCounts by remember(refresh, period.id) {
        mutableStateOf(AttendancePolicyStore.justifiedCounts(db, period.id))
    }

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

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$date · ${session?.title ?: title}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                session == null -> "Aún no se ha creado el pase de lista"
                                !session.worked -> "Clase suspendida / no trabajada"
                                else -> "${students.size} estudiantes · pase de lista"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { showSessionEditor = !showSessionEditor }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.EditCalendar, contentDescription = "Editar fecha y sesión")
                    }
                    IconButton(onClick = { showOptions = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Opciones de asistencia")
                    }
                }

                if (showSessionEditor) {
                    DatePickerField(date, { date = it }, "Fecha")
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Sesión") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = worked, onCheckedChange = { worked = it })
                        Spacer(Modifier.width(6.dp))
                        Text(if (worked) "Día trabajado" else "Clase suspendida", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                db.createOrUpdateAttendanceSession(period.id, date, title, worked)
                                showSessionEditor = false
                                onChanged()
                            },
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text(if (session == null) "Crear" else "Guardar") }
                    }
                }
            }
        }

        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Crea la sesión para comenzar a pasar lista.")
            }
        } else if (!session.worked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Esta fecha no afecta el porcentaje de asistencia.")
            }
        } else if (students.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Agrega estudiantes primero.") }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(students, key = { it.id }) { student ->
                    val current = remember(refresh, session.id, student.id) { db.getAttendanceStatus(session.id, student.id) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).widthIn(min = 72.dp)) {
                                Text(student.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${"%.0f".format(db.attendancePercentage(period.id, student.id))}%", style = MaterialTheme.typography.labelSmall)
                            }
                            CompactAttendanceChip("P", "Presente", current == AttendanceStatus.PRESENT) {
                                db.setAttendanceStatus(session.id, student.id, AttendanceStatus.PRESENT); onChanged()
                            }
                            CompactAttendanceChip("F", "Falta", current == AttendanceStatus.ABSENT) {
                                db.setAttendanceStatus(session.id, student.id, AttendanceStatus.ABSENT); onChanged()
                            }
                            CompactAttendanceChip("R", "Retardo", current == AttendanceStatus.LATE) {
                                db.setAttendanceStatus(session.id, student.id, AttendanceStatus.LATE); onChanged()
                            }
                            CompactAttendanceChip("J", "Justificada", current == AttendanceStatus.JUSTIFIED) {
                                db.setAttendanceStatus(session.id, student.id, AttendanceStatus.JUSTIFIED); onChanged()
                            }
                            IconButton(onClick = { historyStudent = student }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.History, contentDescription = "Historial de asistencia de ${student.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text("Opciones de asistencia") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Faltas justificadas", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (justifiedCounts) "Cuentan como asistencia completa." else "Se excluyen del porcentaje del estudiante.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = justifiedCounts,
                        onCheckedChange = { value ->
                            justifiedCounts = value
                            AttendancePolicyStore.setJustifiedCounts(db, period.id, value)
                            onChanged()
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showOptions = false }) { Text("Cerrar") } }
        )
    }

    historyStudent?.let { student ->
        AttendanceHistoryDialog(db, period, student, refresh, onChanged) { historyStudent = null }
    }
}

@Composable
private fun CompactAttendanceChip(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        modifier = Modifier.padding(horizontal = 1.dp).height(38.dp)
    )
}

@Composable
private fun AttendanceHistoryDialog(
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
    val absences = incidents.count { it.status == AttendanceStatus.ABSENT }
    val justified = incidents.count { it.status == AttendanceStatus.JUSTIFIED }
    val late = incidents.count { it.status == AttendanceStatus.LATE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Historial de asistencia")
                Text(student.name, style = MaterialTheme.typography.bodyMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Asistencia actual: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%", fontWeight = FontWeight.SemiBold)
                Text("Faltas: $absences · Justificadas: $justified · Retardos: $late", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Revisa qué días faltó. Cuando entregue un justificante, cambia esa fecha a Justificada; el porcentaje se recalcula de inmediato.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (incidents.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Este estudiante no tiene incidencias registradas.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(incidents, key = { "${it.sessionId}-${it.status.name}" }) { entry ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.date, style = MaterialTheme.typography.titleSmall)
                                            Text(entry.title.ifBlank { "Clase" }, style = MaterialTheme.typography.bodySmall)
                                        }
                                        AssistChip(onClick = {}, label = { Text(shortStatus(entry.status)) })
                                    }
                                    when (entry.status) {
                                        AttendanceStatus.ABSENT -> Button(
                                            onClick = {
                                                db.setAttendanceStatus(entry.sessionId, student.id, AttendanceStatus.JUSTIFIED)
                                                localRefresh++
                                                onChanged()
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        ) { Text("Cambiar falta a Justificada") }
                                        AttendanceStatus.JUSTIFIED -> OutlinedButton(
                                            onClick = {
                                                db.setAttendanceStatus(entry.sessionId, student.id, AttendanceStatus.ABSENT)
                                                localRefresh++
                                                onChanged()
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Volver a marcar como Falta")
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private fun shortStatus(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.PRESENT -> "✓ Presente"
    AttendanceStatus.ABSENT -> "✕ Falta"
    AttendanceStatus.LATE -> "◷ Retardo"
    AttendanceStatus.JUSTIFIED -> "J Justificada"
}
