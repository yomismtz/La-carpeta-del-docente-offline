package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.AttendanceHistoryStore
import com.profecuaderno.app.data.AttendancePolicyStore
import com.profecuaderno.app.data.AttendanceStatus
import com.profecuaderno.app.data.EvaluationMode
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.StudentAttendanceHistoryEntry
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.util.PdfReportExporter
import com.profecuaderno.app.util.ReportExporter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PolishedReportsScreen(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit
) {
    val rows = remember(refresh, period.id) { db.summaries(period.id) }
    val context = LocalContext.current
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var query by remember { mutableStateOf("") }
    val filteredRows = remember(rows, query) {
        if (query.isBlank()) rows else rows.filter {
            it.student.name.contains(query.trim(), ignoreCase = true) ||
                it.student.studentCode.contains(query.trim(), ignoreCase = true)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Concentrado del grupo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Toca a un alumno para ver sus fechas de faltas, retardos y justificadas y corregirlas desde aquí.")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = rows.isNotEmpty(),
                            onClick = { ReportExporter.shareCsv(context, period, rows) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("CSV")
                        }
                        Button(
                            enabled = rows.isNotEmpty(),
                            onClick = { PdfReportExporter.shareGroup(context, db, period) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("PDF del grupo")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar alumno") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (filteredRows.isEmpty()) {
            item {
                Text(
                    if (rows.isEmpty()) "Aún no hay alumnos en este grupo." else "No hay alumnos que coincidan con la búsqueda.",
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            items(filteredRows, key = { it.student.id }) { row ->
                ElevatedCard(onClick = { selectedStudent = row.student }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.student.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 6)
                        if (row.student.studentCode.isNotBlank()) Text("Matrícula / ID: ${row.student.studentCode}", style = MaterialTheme.typography.bodySmall)
                        Text("Asistencia: ${"%.1f".format(row.attendancePercent)}%")
                        Text("Calificación final: ${"%.1f".format(row.finalPercent)}% · ${"%.2f".format(row.finalPercent / 10.0)}/10")
                    }
                }
            }
        }
    }

    selectedStudent?.let { student ->
        PolishedStudentReportSheet(
            db = db,
            period = period,
            student = student,
            refresh = refresh,
            onChanged = onChanged,
            onDismiss = { selectedStudent = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolishedStudentReportSheet(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    student: Student,
    refresh: Int,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    val tick = refresh + localRefresh
    val counts = remember(tick, student.id) { AttendancePolicyStore.aggregatedCounts(db, period.id, student.id) }
    val incidents = remember(tick, student.id) { AttendanceHistoryStore.incidentsForStudent(db, period.id, student.id) }
    val categories = remember(tick, student.id) { db.getCategories(period.id) }
    val absences = incidents.filter { AttendancePolicyStore.baseStatus(it.status) == AttendanceStatus.ABSENT }
    val lates = incidents.filter { AttendancePolicyStore.baseStatus(it.status) == AttendanceStatus.LATE }
    val justified = incidents.filter { AttendancePolicyStore.baseStatus(it.status) == AttendanceStatus.JUSTIFIED }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Text(student.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 6)
                if (student.studentCode.isNotBlank()) Text("Matrícula / ID: ${student.studentCode}")
                if (student.teamName.isNotBlank()) Text("Equipo: ${student.teamName}")
                if (student.email.isNotBlank()) Text(student.email, style = MaterialTheme.typography.bodySmall)
                if (student.phone.isNotBlank()) Text(student.phone, style = MaterialTheme.typography.bodySmall)
                if (student.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Observaciones", fontWeight = FontWeight.SemiBold)
                    Text(student.notes, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asistencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Porcentaje actual: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%")
                        Text("Presentes: ${counts[AttendanceStatus.PRESENT] ?: 0} · Faltas: ${counts[AttendanceStatus.ABSENT] ?: 0}")
                        Text("Retardos: ${counts[AttendanceStatus.LATE] ?: 0} · Justificadas: ${counts[AttendanceStatus.JUSTIFIED] ?: 0}")
                        Text("Puedes corregir cualquier incidencia aquí; el apartado Asistencia se actualizará con el mismo registro.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                IncidentSectionTitle("Faltas", absences.size)
                if (absences.isEmpty()) Text("Sin faltas registradas.", style = MaterialTheme.typography.bodySmall)
            }
            items(absences, key = { "A-${it.sessionId}" }) { entry ->
                EditableReportIncident(
                    db = db,
                    period = period,
                    student = student,
                    entry = entry,
                    onChanged = {
                        localRefresh++
                        onChanged()
                    }
                )
            }

            item {
                IncidentSectionTitle("Retardos", lates.size)
                if (lates.isEmpty()) Text("Sin retardos registrados.", style = MaterialTheme.typography.bodySmall)
            }
            items(lates, key = { "L-${it.sessionId}" }) { entry ->
                EditableReportIncident(
                    db = db,
                    period = period,
                    student = student,
                    entry = entry,
                    onChanged = {
                        localRefresh++
                        onChanged()
                    }
                )
            }

            item {
                IncidentSectionTitle("Justificadas", justified.size)
                if (justified.isEmpty()) Text("Sin justificadas registradas.", style = MaterialTheme.typography.bodySmall)
            }
            items(justified, key = { "J-${it.sessionId}" }) { entry ->
                EditableReportIncident(
                    db = db,
                    period = period,
                    student = student,
                    entry = entry,
                    onChanged = {
                        localRefresh++
                        onChanged()
                    }
                )
            }

            item {
                Text("Evaluación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(categories, key = { it.id }) { category ->
                val score = db.categoryScoreOrNull(period.id, student.id, category)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (score == null) "${category.name}: Sin evaluar" else "${category.name}: ${"%.1f".format(score)}/100",
                            fontWeight = FontWeight.SemiBold
                        )
                        when (runCatching { EvaluationMode.valueOf(category.mode) }.getOrDefault(EvaluationMode.DIRECT)) {
                            EvaluationMode.AVERAGE -> {
                                val activityItems = db.getAssessmentItems(category.id)
                                val done = activityItems.count { db.getAssessmentScore(student.id, it.id) != null }
                                val zero = activityItems.count { db.getAssessmentScore(student.id, it.id) == 0.0 }
                                Text("Actividades registradas: $done/${activityItems.size} · con 0: $zero", style = MaterialTheme.typography.bodySmall)
                            }
                            EvaluationMode.RUBRIC -> Text("Rúbrica: ${db.getRubricCriteria(category.id).size} criterios", style = MaterialTheme.typography.bodySmall)
                            EvaluationMode.ATTENDANCE -> Text("Se calcula automáticamente con el historial de asistencia.", style = MaterialTheme.typography.bodySmall)
                            EvaluationMode.DIRECT -> Unit
                        }
                    }
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Calificación final", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val final = db.finalPercentage(period.id, student.id)
                        Text("${"%.1f".format(final)}% · ${"%.2f".format(final / 10.0)}/10")
                    }
                }
            }

            item {
                Button(
                    onClick = { PdfReportExporter.shareStudent(context, db, period, student) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Generar PDF del alumno")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun IncidentSectionTitle(title: String, count: Int) {
    Text("$title ($count)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EditableReportIncident(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    student: Student,
    entry: StudentAttendanceHistoryEntry,
    onChanged: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(entry.title.ifBlank { "Clase" }, style = MaterialTheme.typography.bodySmall)
            Text(polishedAttendanceStatusText(entry.status), fontWeight = FontWeight.SemiBold)
            Text("Corregir registro", style = MaterialTheme.typography.labelMedium)
            PolishedAttendanceStatusSelector(
                selected = AttendancePolicyStore.baseStatus(entry.status),
                onSelect = { status ->
                    AttendancePolicyStore.setStatus(db, period.id, entry.sessionId, student.id, status)
                    onChanged()
                }
            )
        }
    }
}
