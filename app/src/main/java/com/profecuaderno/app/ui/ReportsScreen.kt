package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.AttendancePolicyStore
import com.profecuaderno.app.data.AttendanceStatus
import com.profecuaderno.app.data.EvaluationMode
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.util.ReportExporter
import com.profecuaderno.app.util.PdfReportExporter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int) {
    val rows = remember(refresh, period.id) { db.summaries(period.id) }
    val context = LocalContext.current
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Concentrado del grupo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Asistencia sobre días trabajados y calificación final ponderada.")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = rows.isNotEmpty(),
                            onClick = { ReportExporter.shareCsv(context, period, rows) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, null)
                            Spacer(Modifier.width(5.dp))
                            Text("CSV")
                        }
                        Button(
                            enabled = rows.isNotEmpty(),
                            onClick = { PdfReportExporter.shareGroup(context, db, period) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(5.dp))
                            Text("PDF del grupo")
                        }
                    }
                }
            }
        }

        items(rows, key = { it.student.id }) { row ->
            ElevatedCard(onClick = { selectedStudent = row.student }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.student.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 5)
                    Text("Asistencia: ${"%.1f".format(row.attendancePercent)}%")
                    Text("Calificación final: ${"%.1f".format(row.finalPercent)}% · ${"%.2f".format(row.finalPercent / 10.0)}/10")
                }
            }
        }
    }

    selectedStudent?.let { student ->
        val counts = remember(refresh, student.id) { AttendancePolicyStore.aggregatedCounts(db, period.id, student.id) }
        val categories = remember(refresh, student.id) { db.getCategories(period.id) }
        ModalBottomSheet(onDismissRequest = { selectedStudent = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(student.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 5)
                    Spacer(Modifier.height(8.dp))
                    Text("Asistencia: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%")
                    Text("Presentes: ${counts[AttendanceStatus.PRESENT] ?: 0} · Faltas: ${counts[AttendanceStatus.ABSENT] ?: 0}")
                    Text("Retardos: ${counts[AttendanceStatus.LATE] ?: 0} · Justificadas: ${counts[AttendanceStatus.JUSTIFIED] ?: 0}")
                    Spacer(Modifier.height(10.dp))
                    Text("Evaluación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(categories, key = { it.id }) { category ->
                    val score = db.categoryScore(period.id, student.id, category)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${category.name}: ${"%.1f".format(score)}/100", fontWeight = FontWeight.SemiBold)
                            when (runCatching { EvaluationMode.valueOf(category.mode) }.getOrDefault(EvaluationMode.DIRECT)) {
                                EvaluationMode.AVERAGE -> {
                                    val activityItems = db.getAssessmentItems(category.id)
                                    val done = activityItems.count { db.getAssessmentScore(student.id, it.id) != null }
                                    val missed = activityItems.count { db.getAssessmentScore(student.id, it.id) == 0.0 }
                                    Text("Actividades: $done/${activityItems.size} · perdidas/no entregadas: $missed", style = MaterialTheme.typography.bodySmall)
                                }
                                EvaluationMode.RUBRIC -> Text("Rúbrica: ${db.getRubricCriteria(category.id).size} criterios", style = MaterialTheme.typography.bodySmall)
                                else -> Unit
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Calificación final: ${"%.1f".format(db.finalPercentage(period.id, student.id))}% · ${"%.2f".format(db.finalPercentage(period.id, student.id) / 10.0)}/10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { PdfReportExporter.shareStudent(context, db, period, student) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Generar PDF del alumno")
                    }
                    OutlinedButton(
                        onClick = { selectedStudent = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Cerrar") }
                }
            }
        }
    }
}
