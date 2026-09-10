package com.profecuaderno.app.util

import android.content.Context
import android.net.Uri
import com.profecuaderno.app.data.AcademicPeriod
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class PlanningSuggestion(
    val date: String,
    val title: String,
    val type: String,
    val source: String
)

object PlanningGuideAnalyzer {

    fun analyze(context: Context, uri: Uri, period: AcademicPeriod): List<PlanningSuggestion> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        }.orEmpty()
        if (text.isBlank()) return emptyList()

        val periodStart = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
        val periodEnd = runCatching { LocalDate.parse(period.endDate) }.getOrNull()
        val inferredYear = periodStart?.year ?: LocalDate.now().year

        return text.lineSequence()
            .map { it.trim() }
            .filter { it.length >= 5 }
            .mapNotNull { line ->
                val date = extractDate(line, inferredYear, periodStart, periodEnd) ?: return@mapNotNull null
                val type = classify(line)
                val cleaned = line
                    .replace(Regex("""\s+"""), " ")
                    .take(140)
                PlanningSuggestion(
                    date = date.toString(),
                    title = buildTitle(cleaned, type),
                    type = type,
                    source = line.take(220)
                )
            }
            .distinctBy { "${it.date}|${it.title.lowercase(Locale.getDefault())}" }
            .sortedBy { it.date }
            .take(80)
            .toList()
    }

    private fun buildTitle(line: String, type: String): String {
        val prefix = when (type) {
            "EXAMEN" -> "Examen"
            "PRACTICA" -> "Práctica"
            "LABORATORIO" -> "Laboratorio"
            "EVALUACION_PARCIAL" -> "Evaluación"
            "EVALUACION_MODULAR" -> "Evaluación de proyecto / curso"
            "EXPOSICION" -> "Exposición"
            "EXPOSICION_MODULAR" -> "Exposición de proyecto / investigación"
            "INVESTIGACION_MODULAR" -> "Investigación / proyecto"
            "MAQUETA" -> "Maqueta"
            "ENTREGA" -> "Entrega"
            "DOCENTE_INVITADO" -> "Docente invitado"
            "VISITA" -> "Visita"
            "SALIDA" -> "Salida"
            else -> "Actividad"
        }
        return if (line.length <= 95) line else "$prefix · ${line.take(90)}"
    }

    private fun classify(line: String): String {
        val s = normalize(line)
        return when {
            "exposicion" in s && ("modular" in s || "investigacion" in s) -> "EXPOSICION_MODULAR"
            "evaluacion" in s && "modular" in s -> "EVALUACION_MODULAR"
            "investigacion" in s && "modular" in s -> "INVESTIGACION_MODULAR"
            "examen" in s -> "EXAMEN"
            "practica" in s -> "PRACTICA"
            "laboratorio" in s -> "LABORATORIO"
            "evaluacion" in s || "parcial" in s -> "EVALUACION_PARCIAL"
            "exposicion" in s -> "EXPOSICION"
            "maqueta" in s -> "MAQUETA"
            "entrega" in s -> "ENTREGA"
            "invitad" in s || "profesor" in s || "maestro" in s || "ponente" in s || "especialista" in s -> "DOCENTE_INVITADO"
            "visita" in s -> "VISITA"
            "salida" in s || "excursion" in s -> "SALIDA"
            else -> "TEMA_CLASE"
        }
    }

    private fun extractDate(
        line: String,
        inferredYear: Int,
        start: LocalDate?,
        end: LocalDate?
    ): LocalDate? {
        val numericFull = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b""").find(line)
        if (numericFull != null) {
            val (d, m, y) = numericFull.destructured
            return validDate(y.toInt(), m.toInt(), d.toInt())
        }

        val numericShort = Regex("""\b(\d{1,2})[/-](\d{1,2})\b""").find(line)
        if (numericShort != null) {
            val (d, m) = numericShort.destructured
            return fitYear(d.toInt(), m.toInt(), inferredYear, start, end)
        }

        val monthNames = mapOf(
            "enero" to Month.JANUARY, "febrero" to Month.FEBRUARY, "marzo" to Month.MARCH,
            "abril" to Month.APRIL, "mayo" to Month.MAY, "junio" to Month.JUNE,
            "julio" to Month.JULY, "agosto" to Month.AUGUST, "septiembre" to Month.SEPTEMBER,
            "setiembre" to Month.SEPTEMBER, "octubre" to Month.OCTOBER, "noviembre" to Month.NOVEMBER,
            "diciembre" to Month.DECEMBER
        )
        val normalized = normalize(line)
        monthNames.forEach { (name, month) ->
            val match = Regex("""\b(\d{1,2})\s+(?:de\s+)?$name(?:\s+(?:de\s+)?(\d{4}))?\b""").find(normalized)
            if (match != null) {
                val day = match.groupValues[1].toInt()
                val year = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toInt() ?: inferredYear
                return if (match.groupValues.getOrNull(2).isNullOrBlank()) {
                    fitYear(day, month.value, year, start, end)
                } else validDate(year, month.value, day)
            }
        }
        return null
    }

    private fun fitYear(day: Int, month: Int, year: Int, start: LocalDate?, end: LocalDate?): LocalDate? {
        val candidates = listOf(year - 1, year, year + 1)
            .mapNotNull { validDate(it, month, day) }
        return candidates.firstOrNull { candidate ->
            (start == null || !candidate.isBefore(start)) && (end == null || !candidate.isAfter(end))
        } ?: validDate(year, month, day)
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
}
