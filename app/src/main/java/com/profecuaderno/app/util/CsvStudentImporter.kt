package com.profecuaderno.app.util

import android.content.Context
import android.net.Uri
import com.profecuaderno.app.data.Student

data class CsvImportResult(
    val students: List<Student>,
    val skipped: Int,
    val error: String? = null
)

object CsvStudentImporter {
    fun read(context: Context, uri: Uri, periodId: Long): CsvImportResult {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return CsvImportResult(emptyList(), 0, "No se pudo leer el archivo.")
            parse(text, periodId)
        }.getOrElse { CsvImportResult(emptyList(), 0, it.message ?: "No se pudo importar el archivo.") }
    }

    fun parse(text: String, periodId: Long): CsvImportResult {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvImportResult(emptyList(), 0, "El archivo está vacío.")

        val rows = lines.map { parseCsvLine(it) }
        val header = rows.first().map { normalize(it) }
        val hasHeader = header.any { it in knownHeaders }

        val indexes = if (hasHeader) {
            mapOf(
                "name" to findIndex(header, "nombre", "alumno", "estudiante", "name"),
                "code" to findIndex(header, "matricula", "matrícula", "id", "studentid", "codigo", "código"),
                "email" to findIndex(header, "correo", "email", "e-mail"),
                "phone" to findIndex(header, "telefono", "teléfono", "celular", "phone"),
                "birth" to findIndex(header, "fechadenacimiento", "nacimiento", "cumpleanos", "cumpleaños", "birthdate"),
                "group" to findIndex(header, "grupo", "group"),
                "clinic" to findIndex(header, "clinica", "clínica", "seccion", "sección", "salon", "salón"),
                "team" to findIndex(header, "equipo", "team"),
                "notes" to findIndex(header, "notas", "observaciones", "notes")
            )
        } else {
            mapOf("name" to 0, "code" to 1, "email" to 2, "phone" to 3, "birth" to 4, "group" to 5, "clinic" to 6, "team" to 7, "notes" to 8)
        }

        val dataRows = if (hasHeader) rows.drop(1) else rows
        var skipped = 0
        val students = dataRows.mapNotNull { row ->
            fun value(key: String): String {
                val i = indexes[key] ?: -1
                return if (i >= 0 && i < row.size) row[i].trim() else ""
            }
            val name = value("name")
            if (name.isBlank()) {
                skipped++
                null
            } else {
                Student(
                    periodId = periodId,
                    name = name,
                    studentCode = value("code"),
                    email = value("email"),
                    phone = value("phone"),
                    birthDate = normalizeDate(value("birth")),
                    groupName = value("group"),
                    clinic = value("clinic"),
                    teamName = value("team"),
                    notes = value("notes")
                )
            }
        }
        return CsvImportResult(students, skipped)
    }

    private fun normalizeDate(raw: String): String {
        val value = raw.trim()
        val iso = Regex("""\d{4}-\d{2}-\d{2}""")
        if (iso.matches(value)) return value
        val dmy = Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{4})""").matchEntire(value)
        if (dmy != null) {
            val (d, m, y) = dmy.destructured
            return "%04d-%02d-%02d".format(y.toInt(), m.toInt(), d.toInt())
        }
        return value
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace(Regex("""[^a-z0-9]"""), "")

    private fun findIndex(header: List<String>, vararg names: String): Int {
        val normalized = names.map { normalize(it) }
        return header.indexOfFirst { it in normalized }
    }

    private val knownHeaders = setOf(
        "nombre","alumno","estudiante","name","matricula","id","correo","email","telefono","celular",
        "fechadenacimiento","nacimiento","grupo","clinica","seccion","salon","equipo","notas","observaciones"
    )

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                ch == ';' && !inQuotes && !line.contains(',') -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
