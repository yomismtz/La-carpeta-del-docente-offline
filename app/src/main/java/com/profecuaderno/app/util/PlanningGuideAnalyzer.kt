package com.profecuaderno.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.profecuaderno.app.data.AcademicPeriod
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import java.util.zip.ZipInputStream

private data class DateHit(val date: LocalDate, val start: Int, val end: Int)

data class PlanningSuggestion(
    val date: String,
    val title: String,
    val type: String,
    val source: String
)

object PlanningGuideAnalyzer {

    fun analyze(context: Context, uri: Uri, period: AcademicPeriod): List<PlanningSuggestion> {
        val displayName = displayName(context, uri)
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()

        var text = when {
            extension == "pdf" || mime == "application/pdf" -> extractPdfText(context, uri)
            extension == "docx" || mime.contains("wordprocessingml") -> extractDocxText(context, uri)
            extension in setOf("txt", "csv") || mime.startsWith("text/") -> extractPlainText(context, uri)
            extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") || mime.startsWith("image/") -> ocrImage(context, uri)
            extension == "doc" || mime == "application/msword" -> extractLegacyDocBestEffort(context, uri)
            else -> extractPlainText(context, uri)
        }

        var suggestions = suggestionsFromText(text, period)

        // Algunos PDF exportados desde Word contienen el texto como trazos/imágenes o con una
        // codificación que PDFBox no puede reconstruir. Si no encontramos fechas, hacemos OCR.
        if (suggestions.isEmpty() && (extension == "pdf" || mime == "application/pdf")) {
            val ocrText = ocrPdf(context, uri)
            if (ocrText.isNotBlank()) {
                text = listOf(text, ocrText).filter { it.isNotBlank() }.joinToString("\n")
                suggestions = suggestionsFromText(text, period)
            }
        }
        return suggestions
    }

    private fun suggestionsFromText(text: String, period: AcademicPeriod): List<PlanningSuggestion> {
        if (text.isBlank()) return emptyList()
        val compact = text.replace(Regex("\\s+"), " ").trim()
        if (compact.isBlank()) return emptyList()

        val periodStart = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
        val periodEnd = runCatching { LocalDate.parse(period.endDate) }.getOrNull()
        val inferredYear = periodStart?.year ?: LocalDate.now().year

        return findDates(compact, inferredYear, periodStart, periodEnd)
            .map { hit ->
                val from = (hit.start - 90).coerceAtLeast(0)
                val to = (hit.end + 150).coerceAtMost(compact.length)
                val contextText = compact.substring(from, to).trim(' ', '.', ',', ';', ':', '-', '–', '—')
                val type = classify(contextText)
                PlanningSuggestion(
                    date = hit.date.toString(),
                    title = buildTitle(contextText.take(140), type),
                    type = type,
                    source = contextText.take(240)
                )
            }
            .distinctBy { "${it.date}|${it.title.lowercase(Locale.getDefault())}" }
            .sortedBy { it.date }
            .take(120)
    }

    private fun findDates(text: String, inferredYear: Int, start: LocalDate?, end: LocalDate?): List<DateHit> {
        val normalized = normalize(text)
        val hits = mutableListOf<DateHit>()

        fun add(match: MatchResult, date: LocalDate?) {
            if (date != null) hits += DateHit(date, match.range.first, match.range.last + 1)
        }

        Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4})\b""").findAll(normalized).forEach { m ->
            add(m, validDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b""").findAll(normalized).forEach { m ->
            add(m, validDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
        }
        Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2})\b""").findAll(normalized).forEach { m ->
            val yy = m.groupValues[3].toInt()
            val year = if (yy >= 70) 1900 + yy else 2000 + yy
            add(m, validDate(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        Regex("""\b(\d{1,2})[./-](\d{1,2})\b""").findAll(normalized).forEach { m ->
            // Evita volver a interpretar como fecha corta una parte de una fecha con año.
            val after = normalized.drop(m.range.last + 1).take(5)
            if (!after.matches(Regex("""^[./-]\d{2,4}.*"""))) {
                add(m, fitYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), inferredYear, start, end))
            }
        }

        val monthPattern = "enero|ene|febrero|feb|marzo|mar|abril|abr|mayo|may|junio|jun|julio|jul|agosto|ago|septiembre|setiembre|sep|sept|octubre|oct|noviembre|nov|diciembre|dic"
        Regex("""\b(\d{1,2})\s*(?:de\s+)?($monthPattern)(?:\s*(?:de|del)?\s*(\d{4}))?\b""").findAll(normalized).forEach { m ->
            val month = monthNumber(m.groupValues[2]) ?: return@forEach
            val yearText = m.groupValues.getOrNull(3).orEmpty()
            val date = if (yearText.isBlank()) fitYear(m.groupValues[1].toInt(), month, inferredYear, start, end)
            else validDate(yearText.toInt(), month, m.groupValues[1].toInt())
            add(m, date)
        }
        Regex("""\b($monthPattern)\s+(\d{1,2})(?:\s*(?:de|del|,)?\s*(\d{4}))?\b""").findAll(normalized).forEach { m ->
            val month = monthNumber(m.groupValues[1]) ?: return@forEach
            val yearText = m.groupValues.getOrNull(3).orEmpty()
            val date = if (yearText.isBlank()) fitYear(m.groupValues[2].toInt(), month, inferredYear, start, end)
            else validDate(yearText.toInt(), month, m.groupValues[2].toInt())
            add(m, date)
        }

        return hits
            .filter { hit -> (start == null || !hit.date.isBefore(start)) && (end == null || !hit.date.isAfter(end)) }
            .distinctBy { "${it.date}|${it.start}" }
            .sortedWith(compareBy<DateHit> { it.date }.thenBy { it.start })
    }

    private fun extractPdfText(context: Context, uri: Uri): String = runCatching {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        }.orEmpty()
    }.getOrDefault("")

    private fun extractPlainText(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    }.getOrDefault("")

    private fun extractDocxText(context: Context, uri: Uri): String = runCatching {
        val output = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml" || entry.name.startsWith("word/header") || entry.name.startsWith("word/footer")) {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        output.append(xmlToText(xml)).append('\n')
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        output.toString()
    }.getOrDefault("")

    private fun xmlToText(xml: String): String = xml
        .replace(Regex("""</w:p>"""), "\n")
        .replace(Regex("""<w:tab[^>]*/>"""), "\t")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    private fun extractLegacyDocBestEffort(context: Context, uri: Uri): String = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ""
        val raw = bytes.toString(Charset.forName("windows-1252"))
        raw.replace(Regex("""[^\p{L}\p{N}\s./,:;()\-áéíóúÁÉÍÓÚñÑ]+"""), " ")
            .replace(Regex("\\s+"), " ")
    }.getOrDefault("")

    private fun ocrImage(context: Context, uri: Uri): String = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }.getOrDefault("")

    private fun ocrPdf(context: Context, uri: Uri): String = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching ""
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val output = StringBuilder()
                    val pages = minOf(renderer.pageCount, 40)
                    for (index in 0 until pages) {
                        renderer.openPage(index).use { page ->
                            val scale = 2
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).coerceAtLeast(1),
                                (page.height * scale).coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                output.append(result.text).append('\n')
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    output.toString()
                } finally {
                    recognizer.close()
                }
            }
        }
    }.getOrDefault("")

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

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
        val clean = line.replace(Regex("\\s+"), " ").trim()
        return when {
            clean.isBlank() -> prefix
            clean.length <= 105 -> clean
            else -> "$prefix · ${clean.take(100)}"
        }
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

    private fun monthNumber(value: String): Int? = when (value.lowercase(Locale.ROOT)) {
        "enero", "ene" -> Month.JANUARY.value
        "febrero", "feb" -> Month.FEBRUARY.value
        "marzo", "mar" -> Month.MARCH.value
        "abril", "abr" -> Month.APRIL.value
        "mayo", "may" -> Month.MAY.value
        "junio", "jun" -> Month.JUNE.value
        "julio", "jul" -> Month.JULY.value
        "agosto", "ago" -> Month.AUGUST.value
        "septiembre", "setiembre", "sep", "sept" -> Month.SEPTEMBER.value
        "octubre", "oct" -> Month.OCTOBER.value
        "noviembre", "nov" -> Month.NOVEMBER.value
        "diciembre", "dic" -> Month.DECEMBER.value
        else -> null
    }

    private fun fitYear(day: Int, month: Int, year: Int, start: LocalDate?, end: LocalDate?): LocalDate? {
        val candidates = listOf(year - 1, year, year + 1).mapNotNull { validDate(it, month, day) }
        return candidates.firstOrNull { candidate ->
            (start == null || !candidate.isBefore(start)) && (end == null || !candidate.isAfter(end))
        } ?: validDate(year, month, day)
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace("ü", "u")
}
