package com.profecuaderno.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlin.math.abs

enum class WeightedEvaluationKind(val label: String) {
    ACTIVITIES("Promedio de actividades"),
    EXAMS("Promedio de exámenes")
}

object WeightedEvaluationStore {
    private const val TABLE_METHODS = "weighted_evaluation_methods"
    private const val TABLE_WEIGHTS = "assessment_item_weights"

    private fun ensureTables(db: TeacherDbHelper) {
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_METHODS(
                category_id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL,
                FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WEIGHTS(
                item_id INTEGER PRIMARY KEY,
                weight REAL NOT NULL DEFAULT 0,
                FOREIGN KEY(item_id) REFERENCES assessment_items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    fun kindFor(db: TeacherDbHelper, category: EvaluationCategory): WeightedEvaluationKind? {
        ensureTables(db)
        db.readableDatabase.query(
            TABLE_METHODS,
            arrayOf("kind"),
            "category_id=?",
            arrayOf(category.id.toString()),
            null,
            null,
            null
        ).use { c ->
            if (c.moveToFirst()) {
                return runCatching { WeightedEvaluationKind.valueOf(c.getString(0)) }.getOrNull()
            }
        }
        if (category.mode == EvaluationMode.AVERAGE.name) {
            return if (category.name.contains("examen", ignoreCase = true)) WeightedEvaluationKind.EXAMS else WeightedEvaluationKind.ACTIVITIES
        }
        return null
    }

    fun setKind(db: TeacherDbHelper, categoryId: Long, kind: WeightedEvaluationKind?) {
        ensureTables(db)
        if (kind == null) {
            db.writableDatabase.delete(TABLE_METHODS, "category_id=?", arrayOf(categoryId.toString()))
            return
        }
        val values = ContentValues().apply {
            put("category_id", categoryId)
            put("kind", kind.name)
        }
        db.writableDatabase.insertWithOnConflict(TABLE_METHODS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun migrateLegacyAverage(db: TeacherDbHelper, category: EvaluationCategory) {
        if (category.mode != EvaluationMode.AVERAGE.name) return
        val kind = kindFor(db, category) ?: WeightedEvaluationKind.ACTIVITIES
        setKind(db, category.id, kind)
        ensureEvenWeightsIfMissing(db, category.id)
        db.getStudents(category.periodId).forEach { student ->
            recalculateStudent(db, category.periodId, student.id, category.id)
        }
        db.saveCategory(category.copy(mode = EvaluationMode.DIRECT.name))
    }

    fun weightFor(db: TeacherDbHelper, itemId: Long): Double {
        ensureTables(db)
        db.readableDatabase.query(
            TABLE_WEIGHTS,
            arrayOf("weight"),
            "item_id=?",
            arrayOf(itemId.toString()),
            null,
            null,
            null
        ).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    fun setWeight(db: TeacherDbHelper, itemId: Long, weight: Double) {
        ensureTables(db)
        val values = ContentValues().apply {
            put("item_id", itemId)
            put("weight", weight.coerceIn(0.0, 100.0))
        }
        db.writableDatabase.insertWithOnConflict(TABLE_WEIGHTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun weightTotal(db: TeacherDbHelper, categoryId: Long): Double =
        db.getAssessmentItems(categoryId).sumOf { weightFor(db, it.id) }

    fun ensureEvenWeightsIfMissing(db: TeacherDbHelper, categoryId: Long) {
        val items = db.getAssessmentItems(categoryId)
        if (items.isEmpty()) return
        val existing = items.sumOf { weightFor(db, it.id) }
        if (existing > 0.001) return
        distributeEvenly(db, categoryId)
    }

    fun distributeEvenly(db: TeacherDbHelper, categoryId: Long) {
        val items = db.getAssessmentItems(categoryId)
        if (items.isEmpty()) return
        val base = 100.0 / items.size
        items.forEachIndexed { index, item ->
            val weight = if (index == items.lastIndex) 100.0 - base * (items.size - 1) else base
            setWeight(db, item.id, weight)
        }
    }

    fun weightedScoreOrNull(db: TeacherDbHelper, studentId: Long, categoryId: Long): Double? {
        val items = db.getAssessmentItems(categoryId)
        var weighted = 0.0
        var registeredWeight = 0.0
        items.forEach { item ->
            val score = db.getAssessmentScore(studentId, item.id) ?: return@forEach
            val weight = weightFor(db, item.id)
            if (weight > 0.0) {
                weighted += score * weight
                registeredWeight += weight
            }
        }
        return if (registeredWeight <= 0.0) null else (weighted / registeredWeight).coerceIn(0.0, 100.0)
    }

    fun weightedScore(db: TeacherDbHelper, studentId: Long, categoryId: Long): Double =
        weightedScoreOrNull(db, studentId, categoryId) ?: 0.0

    fun recalculateStudent(db: TeacherDbHelper, periodId: Long, studentId: Long, categoryId: Long): Double {
        val score = weightedScoreOrNull(db, studentId, categoryId)
        if (score == null) {
            db.writableDatabase.delete(
                "grades",
                "period_id=? AND student_id=? AND category_id=?",
                arrayOf(periodId.toString(), studentId.toString(), categoryId.toString())
            )
            return 0.0
        }
        db.setGrade(periodId, studentId, categoryId, score)
        return score
    }

    fun recalculateCategory(db: TeacherDbHelper, category: EvaluationCategory) {
        db.getStudents(category.periodId).forEach { student ->
            recalculateStudent(db, category.periodId, student.id, category.id)
        }
    }

    fun pendingCount(db: TeacherDbHelper, studentId: Long, categoryId: Long): Int =
        db.getAssessmentItems(categoryId).count { db.getAssessmentScore(studentId, it.id) == null }

    fun isConfigured(db: TeacherDbHelper, categoryId: Long): Boolean =
        abs(weightTotal(db, categoryId) - 100.0) < 0.001
}
