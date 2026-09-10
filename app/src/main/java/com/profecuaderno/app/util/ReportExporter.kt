package com.profecuaderno.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.StudentSummary
import java.io.File
import java.text.DecimalFormat

object ReportExporter {
    fun shareCsv(context: Context, period: AcademicPeriod, rows: List<StudentSummary>) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = period.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "reporte_${safeName}.csv")
        val df = DecimalFormat("0.00")
        file.bufferedWriter().use { w ->
            w.appendLine("Alumno,Matrícula,Correo,Teléfono,Grupo/Clínica,Asistencia %,Calificación final %,Escala 0-10")
            rows.forEach { row ->
                val s = row.student
                w.appendLine(
                    listOf(
                        s.name,
                        s.studentCode,
                        s.email,
                        s.phone,
                        listOf(s.groupName, s.clinic).filter { it.isNotBlank() }.joinToString(" / "),
                        df.format(row.attendancePercent),
                        df.format(row.finalPercent),
                        df.format(row.finalPercent / 10.0)
                    ).joinToString(",") { csv(it) }
                )
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
