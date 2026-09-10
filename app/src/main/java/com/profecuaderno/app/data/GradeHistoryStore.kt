package com.profecuaderno.app.data

data class GradeHistoryEntry(
    val id: Long,
    val periodId: Long,
    val studentId: Long,
    val categoryId: Long,
    val studentName: String,
    val categoryName: String,
    val oldScore: Double?,
    val newScore: Double,
    val changedAt: Long,
    val action: String
)

object GradeHistoryStore {
    private const val TABLE = "grade_history"
    const val ACTION_CREATED = "CREATED"
    const val ACTION_MODIFIED = "MODIFIED"
    const val ACTION_RESTORED = "RESTORED"

    fun ensure(db: TeacherDbHelper) {
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                student_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                student_name TEXT NOT NULL DEFAULT '',
                category_name TEXT NOT NULL DEFAULT '',
                old_score REAL,
                new_score REAL NOT NULL,
                changed_at INTEGER NOT NULL,
                action TEXT NOT NULL DEFAULT '$ACTION_MODIFIED'
            )
            """.trimIndent()
        )
        runCatching { sqlDb.execSQL("ALTER TABLE $TABLE ADD COLUMN action TEXT NOT NULL DEFAULT '$ACTION_MODIFIED'") }

        sqlDb.execSQL("DROP TRIGGER IF EXISTS trg_grade_history_insert")
        sqlDb.execSQL("DROP TRIGGER IF EXISTS trg_grade_history_before_insert_new")
        sqlDb.execSQL("DROP TRIGGER IF EXISTS trg_grade_history_before_insert_replace")
        sqlDb.execSQL("DROP TRIGGER IF EXISTS trg_grade_history_update")

        // setGrade currently uses INSERT OR REPLACE. Logging BEFORE INSERT lets us
        // inspect the previous row before SQLite replaces it, so a real edit is
        // recorded as MODIFIED instead of incorrectly appearing as CREATED.
        sqlDb.execSQL(
            """
            CREATE TRIGGER trg_grade_history_before_insert_new
            BEFORE INSERT ON grades
            WHEN NOT EXISTS(
                SELECT 1 FROM grades
                WHERE student_id=NEW.student_id AND category_id=NEW.category_id
            )
            BEGIN
                INSERT INTO $TABLE(period_id, student_id, category_id, student_name, category_name, old_score, new_score, changed_at, action)
                VALUES(
                    NEW.period_id,
                    NEW.student_id,
                    NEW.category_id,
                    COALESCE((SELECT name FROM students WHERE id=NEW.student_id), ''),
                    COALESCE((SELECT name FROM evaluation_categories WHERE id=NEW.category_id), ''),
                    NULL,
                    NEW.score,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    '$ACTION_CREATED'
                );
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER trg_grade_history_before_insert_replace
            BEFORE INSERT ON grades
            WHEN EXISTS(
                SELECT 1 FROM grades
                WHERE student_id=NEW.student_id AND category_id=NEW.category_id
                  AND ABS(score - NEW.score) > 0.0001
            )
            BEGIN
                INSERT INTO $TABLE(period_id, student_id, category_id, student_name, category_name, old_score, new_score, changed_at, action)
                VALUES(
                    NEW.period_id,
                    NEW.student_id,
                    NEW.category_id,
                    COALESCE((SELECT name FROM students WHERE id=NEW.student_id), ''),
                    COALESCE((SELECT name FROM evaluation_categories WHERE id=NEW.category_id), ''),
                    (SELECT score FROM grades WHERE student_id=NEW.student_id AND category_id=NEW.category_id LIMIT 1),
                    NEW.score,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    '$ACTION_MODIFIED'
                );
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER trg_grade_history_update
            AFTER UPDATE OF score ON grades
            WHEN ABS(NEW.score - OLD.score) > 0.0001
            BEGIN
                INSERT INTO $TABLE(period_id, student_id, category_id, student_name, category_name, old_score, new_score, changed_at, action)
                VALUES(
                    NEW.period_id,
                    NEW.student_id,
                    NEW.category_id,
                    COALESCE((SELECT name FROM students WHERE id=NEW.student_id), ''),
                    COALESCE((SELECT name FROM evaluation_categories WHERE id=NEW.category_id), ''),
                    OLD.score,
                    NEW.score,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    '$ACTION_MODIFIED'
                );
            END
            """.trimIndent()
        )
    }

    fun entries(db: TeacherDbHelper, limit: Int = 500): List<GradeHistoryEntry> {
        ensure(db)
        val out = mutableListOf<GradeHistoryEntry>()
        db.readableDatabase.query(TABLE, null, null, null, null, null, "changed_at DESC, id DESC", limit.toString()).use { c ->
            while (c.moveToNext()) out += GradeHistoryEntry(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                periodId = c.getLong(c.getColumnIndexOrThrow("period_id")),
                studentId = c.getLong(c.getColumnIndexOrThrow("student_id")),
                categoryId = c.getLong(c.getColumnIndexOrThrow("category_id")),
                studentName = c.getString(c.getColumnIndexOrThrow("student_name")),
                categoryName = c.getString(c.getColumnIndexOrThrow("category_name")),
                oldScore = if (c.isNull(c.getColumnIndexOrThrow("old_score"))) null else c.getDouble(c.getColumnIndexOrThrow("old_score")),
                newScore = c.getDouble(c.getColumnIndexOrThrow("new_score")),
                changedAt = c.getLong(c.getColumnIndexOrThrow("changed_at")),
                action = c.getString(c.getColumnIndexOrThrow("action"))
            )
        }
        return out
    }

    fun restorePrevious(db: TeacherDbHelper, entry: GradeHistoryEntry): Boolean {
        val previous = entry.oldScore ?: return false
        ensure(db)
        db.setGrade(entry.periodId, entry.studentId, entry.categoryId, previous)
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            "UPDATE $TABLE SET action=? WHERE id=(SELECT id FROM $TABLE WHERE period_id=? AND student_id=? AND category_id=? ORDER BY id DESC LIMIT 1)",
            arrayOf(ACTION_RESTORED, entry.periodId, entry.studentId, entry.categoryId)
        )
        return true
    }

    fun actionLabel(action: String): String = when (action) {
        ACTION_CREATED -> "Registrada"
        ACTION_RESTORED -> "Restaurada desde historial"
        else -> "Modificada"
    }

    fun clear(db: TeacherDbHelper) {
        ensure(db)
        db.writableDatabase.delete(TABLE, null, null)
    }
}
