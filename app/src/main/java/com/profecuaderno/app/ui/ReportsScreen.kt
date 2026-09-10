package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.util.ReportExporter
import com.profecuaderno.app.util.PdfReportExporter
import com.profecuaderno.app.data.Student

@Composable
fun ReportsScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int) {
    val rows = remember(refresh, period.id) { db.summaries(period.id) }
    val context = LocalContext.current
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Concentrado del grupo", style = MaterialTheme.typography.titleMedium)
                    Text("Asistencia sobre días trabajados y calificación final ponderada.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = rows.isNotEmpty(), onClick = { ReportExporter.shareCsv(context, period, rows) }) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("CSV")
                    }
                    Button(enabled = rows.isNotEmpty(), onClick = { PdfReportExporter.shareGroup(context, db, period) }) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(4.dp))
                        Text("PDF grupo")
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.student.id }) { row ->
                ElevatedCard(
                    onClick = { selectedStudent = row.student },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.student.name, style = MaterialTheme.typography.titleSmall)
                        Text("Asistencia: ${"%.1f".format(row.attendancePercent)}%")
                        Text("Calificación final: ${"%.1f".format(row.finalPercent)}%  ·  ${"%.2f".format(row.finalPercent / 10.0)}/10")
                    }
                }
            }
        }
    }
    selectedStudent?.let { student ->
        val counts = remember(refresh, student.id) { db.attendanceCounts(period.id, student.id) }
        val categories = remember(refresh, student.id) { db.getCategories(period.id) }
        AlertDialog(
            onDismissRequest = { selectedStudent = null },
            title = { Text(student.name) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text("Asistencia: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%")
                        Text("Presentes: ${counts[com.profecuaderno.app.data.AttendanceStatus.PRESENT] ?: 0} · Faltas: ${counts[com.profecuaderno.app.data.AttendanceStatus.ABSENT] ?: 0}")
                        Text("Retardos: ${counts[com.profecuaderno.app.data.AttendanceStatus.LATE] ?: 0} · Justificadas: ${counts[com.profecuaderno.app.data.AttendanceStatus.JUSTIFIED] ?: 0}")
                        Spacer(Modifier.height(8.dp))
                        Text("Evaluación", style = MaterialTheme.typography.titleSmall)
                    }
                    items(categories, key = { it.id }) { category ->
                        val score = db.categoryScore(period.id, student.id, category)
                        Column {
                            Text("${category.name}: ${"%.1f".format(score)}/100")
                            when (runCatching { com.profecuaderno.app.data.EvaluationMode.valueOf(category.mode) }.getOrDefault(com.profecuaderno.app.data.EvaluationMode.DIRECT)) {
                                com.profecuaderno.app.data.EvaluationMode.AVERAGE -> {
                                    val activityItems = db.getAssessmentItems(category.id)
                                    val done = activityItems.count { db.getAssessmentScore(student.id, it.id) != null }
                                    val missed = activityItems.count { db.getAssessmentScore(student.id, it.id) == 0.0 }
                                    Text("Actividades: $done/${activityItems.size} · perdidas/no entregadas: $missed", style = MaterialTheme.typography.bodySmall)
                                }
                                com.profecuaderno.app.data.EvaluationMode.RUBRIC -> {
                                    Text("Rúbrica: ${db.getRubricCriteria(category.id).size} criterios", style = MaterialTheme.typography.bodySmall)
                                }
                                else -> {}
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Calificación final: ${"%.1f".format(db.finalPercentage(period.id, student.id))}% · ${"%.2f".format(db.finalPercentage(period.id, student.id) / 10.0)}/10",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { PdfReportExporter.shareStudent(context, db, period, student) }) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(4.dp))
                    Text("PDF alumno")
                }
            },
            dismissButton = { TextButton(onClick = { selectedStudent = null }) { Text("Cerrar") } }
        )
    }
}
