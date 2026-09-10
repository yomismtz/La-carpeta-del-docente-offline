package com.profecuaderno.app.data

import android.content.ContentValues

object EvaluationSetupStore {
    private const val TABLE = "evaluation_setup_state"

    private fun ensure(db: TeacherDbHelper) {
        db.writableDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                period_id INTEGER PRIMARY KEY,
                signature TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun signature(db: TeacherDbHelper, periodId: Long): String {
        return buildString {
            db.getCategories(periodId).forEach { category ->
                append(category.id).append('|')
                append(category.name).append('|')
                append("%.4f".format(java.util.Locale.US, category.weight)).append('|')
                append(category.mode).append('|')
                append(WeightedEvaluationStore.kindFor(db, category)?.name ?: "-").append('|')
                db.getAssessmentItems(category.id).forEach { item ->
                    append("A:").append(item.id).append(':').append(item.name).append(':')
                    append("%.4f".format(java.util.Locale.US, WeightedEvaluationStore.weightFor(db, item.id))).append('|')
                }
                db.getRubricCriteria(category.id).forEach { criterion ->
                    append("R:").append(criterion.id).append(':').append(criterion.name).append(':')
                    append("%.4f".format(java.util.Locale.US, criterion.weight)).append('|')
                }
                append(';')
            }
        }
    }

    fun canFinalize(db: TeacherDbHelper, periodId: Long): Boolean {
        val categories = db.getCategories(periodId)
        if (categories.isEmpty()) return false
        if (kotlin.math.abs(categories.sumOf { it.weight } - 100.0) > 0.001) return false
        return categories.all { category ->
            val weighted = WeightedEvaluationStore.kindFor(db, category)
            when {
                weighted != null -> WeightedEvaluationStore.isConfigured(db, category.id)
                db.effectiveEvaluationMode(category) == EvaluationMode.RUBRIC -> {
                    val criteria = db.getRubricCriteria(category.id)
                    criteria.isNotEmpty() && kotlin.math.abs(criteria.sumOf { it.weight } - 100.0) < 0.001
                }
                else -> true
            }
        }
    }

    fun finalize(db: TeacherDbHelper, periodId: Long): Boolean {
        if (!canFinalize(db, periodId)) return false
        ensure(db)
        val values = ContentValues().apply {
            put("period_id", periodId)
            put("signature", signature(db, periodId))
        }
        db.writableDatabase.insertWithOnConflict(TABLE, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun isFinalized(db: TeacherDbHelper, periodId: Long): Boolean {
        ensure(db)
        val current = signature(db, periodId)
        db.readableDatabase.query(TABLE, arrayOf("signature"), "period_id=?", arrayOf(periodId.toString()), null, null, null, "1").use { c ->
            return c.moveToFirst() && c.getString(0) == current && canFinalize(db, periodId)
        }
    }
}
