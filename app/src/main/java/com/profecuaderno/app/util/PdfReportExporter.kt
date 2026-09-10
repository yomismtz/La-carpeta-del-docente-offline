package com.profecuaderno.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.profecuaderno.app.data.*
import java.io.File
import java.text.DecimalFormat

object PdfReportExporter {

    fun shareGroup(context: Context, db: TeacherDbHelper, period: AcademicPeriod) {
        val file = File(reportDir(context), "reporte_grupo_${safe(period.name)}.pdf")
        val doc = PdfDocument()
        val rows = db.summaries(period.id)
        val categories = db.getCategories(period.id)
        val writer = PdfWriter(doc)

        writer.title("ProfeCuaderno · Reporte de grupo")
        writer.line("${period.name} · ${period.type}")
        writer.line("${period.startDate} → ${period.endDate}")
        writer.gap()
        writer.heading("Resumen")
        writer.line("Alumnos: ${rows.size}")
        writer.line("Rubros configurados: ${categories.size} · Total: ${"%.1f".format(categories.sumOf { it.weight })}%")
        writer.gap()

        rows.forEach { row ->
            writer.heading(row.student.name)
            writer.line("Asistencia: ${"%.1f".format(row.attendancePercent)}%")
            categories.forEach { cat ->
                writer.line("${cat.name}: ${"%.1f".format(db.categoryScore(period.id, row.student.id, cat))}/100 · peso ${"%.1f".format(cat.weight)}%")
            }
            writer.line("Final: ${"%.1f".format(row.finalPercent)}% · ${"%.2f".format(row.finalPercent / 10.0)}/10")
            writer.gap()
        }

        writer.finish(file)
        share(context, file)
    }

    fun shareStudent(context: Context, db: TeacherDbHelper, period: AcademicPeriod, student: Student) {
        val file = File(reportDir(context), "reporte_${safe(student.name)}.pdf")
        val doc = PdfDocument()
        val writer = PdfWriter(doc)
        val df = DecimalFormat("0.0")
        val counts = db.attendanceCounts(period.id, student.id)
        val categories = db.getCategories(period.id)

        writer.title("ProfeCuaderno · Reporte individual")
        writer.heading(student.name)
        if (student.studentCode.isNotBlank()) writer.line("Matrícula / ID: ${student.studentCode}")
        if (student.email.isNotBlank()) writer.line("Correo: ${student.email}")
        if (student.phone.isNotBlank()) writer.line("Teléfono: ${student.phone}")
        if (student.birthDate.isNotBlank()) writer.line("Fecha de nacimiento: ${student.birthDate}")
        if (student.teamName.isNotBlank()) writer.line("Equipo: ${student.teamName}")
        writer.line("Grupo: ${period.name}")
        writer.gap()

        writer.heading("Asistencia")
        writer.line("Porcentaje: ${df.format(db.attendancePercentage(period.id, student.id))}%")
        writer.line(
            "Presentes: ${counts[AttendanceStatus.PRESENT] ?: 0} · Faltas: ${counts[AttendanceStatus.ABSENT] ?: 0} · " +
                "Retardos: ${counts[AttendanceStatus.LATE] ?: 0} · Justificadas: ${counts[AttendanceStatus.JUSTIFIED] ?: 0}"
        )
        writer.gap()

        writer.heading("Evaluación")
        categories.forEach { cat ->
            val mode = runCatching { EvaluationMode.valueOf(cat.mode) }.getOrDefault(EvaluationMode.DIRECT)
            writer.line("${cat.name} · ${df.format(cat.weight)}% · ${mode.label}")
            when (mode) {
                EvaluationMode.AVERAGE -> {
                    db.getAssessmentItems(cat.id).forEach { item ->
                        val score = db.getAssessmentScore(student.id, item.id)
                        writer.line("   ${item.name}: ${score?.let { df.format(it) } ?: "Pendiente"}")
                    }
                }
                EvaluationMode.RUBRIC -> {
                    db.getRubricCriteria(cat.id).forEach { criterion ->
                        writer.line("   ${criterion.name} (${df.format(criterion.weight)}%): ${df.format(db.getRubricMark(student.id, criterion.id))}")
                    }
                }
                EvaluationMode.ATTENDANCE -> writer.line("   Resultado automático: ${df.format(db.attendancePercentage(period.id, student.id))}/100")
                EvaluationMode.DIRECT -> writer.line("   Resultado: ${df.format(db.getGrade(student.id, cat.id))}/100")
            }
            writer.line("   Promedio del rubro: ${df.format(db.categoryScore(period.id, student.id, cat))}/100")
            writer.gap(4f)
        }

        writer.heading("Calificación final")
        writer.line("${df.format(db.finalPercentage(period.id, student.id))}% · ${"%.2f".format(db.finalPercentage(period.id, student.id) / 10.0)}/10")

        writer.finish(file)
        share(context, file)
    }

    private fun reportDir(context: Context): File = File(context.cacheDir, "reports").apply { mkdirs() }
    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
    }

    private class PdfWriter(private val doc: PdfDocument) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        private val normal = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        private val pageWidth = 595
        private val pageHeight = 842
        private val left = 44f
        private val right = 44f
        private val bottom = 52f
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = 58f

        init { newPage() }

        fun title(text: String) {
            paint.typeface = bold
            paint.textSize = 18f
            drawWrapped(text, 24f)
            gap(6f)
        }

        fun heading(text: String) {
            paint.typeface = bold
            paint.textSize = 12f
            drawWrapped(text, 17f)
        }

        fun line(text: String) {
            paint.typeface = normal
            paint.textSize = 10f
            drawWrapped(text, 14f)
        }

        fun gap(amount: Float = 10f) {
            y += amount
            ensureSpace(16f)
        }

        private fun drawWrapped(text: String, lineHeight: Float) {
            val maxWidth = pageWidth - left - right
            val words = text.split(Regex("\\s+"))
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) > maxWidth && current.isNotBlank()) {
                    draw(current, lineHeight)
                    current = word
                } else current = candidate
            }
            if (current.isNotBlank()) draw(current, lineHeight)
        }

        private fun draw(text: String, lineHeight: Float) {
            ensureSpace(lineHeight)
            page!!.canvas.drawText(text, left, y, paint)
            y += lineHeight
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - bottom) newPage()
        }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = 54f
        }

        fun finish(file: File) {
            page?.let { doc.finishPage(it) }
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }
}
