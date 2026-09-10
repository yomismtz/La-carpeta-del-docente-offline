package com.profecuaderno.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class TrashEntry(
    val type: String,
    val entityId: Long,
    val label: String,
    val parentId: Long,
    val deletedAt: Long
)

object TrashStore {
    const val TYPE_GROUP = "GROUP"
    const val TYPE_STUDENT = "STUDENT"
    const val TYPE_EVENT = "EVENT"
    const val TYPE_CATEGORY = "CATEGORY"
    const val TYPE_ASSESSMENT_ITEM = "ASSESSMENT_ITEM"
    const val TYPE_RUBRIC_CRITERION = "RUBRIC_CRITERION"

    private const val TABLE = "trash_items"
    private const val CONTROL = "trash_control"
    private const val SETTINGS = "trash_settings"
    private const val INTERNAL_PERIOD = "__PAPELERA_INTERNA__"
    private const val INTERNAL_CATEGORY = "__PAPELERA_INTERNA__"
    const val RETENTION_DAYS = 30

    fun ensure(db: TeacherDbHelper) {
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                entity_type TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                parent_id INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER NOT NULL,
                PRIMARY KEY(entity_type, entity_id)
            )
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $CONTROL(
                id INTEGER PRIMARY KEY CHECK(id=1),
                bypass INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $SETTINGS(
                id INTEGER PRIMARY KEY CHECK(id=1),
                auto_expire INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        sqlDb.execSQL("INSERT OR IGNORE INTO $CONTROL(id,bypass) VALUES(1,0)")
        sqlDb.execSQL("INSERT OR IGNORE INTO $SETTINGS(id,auto_expire) VALUES(1,0)")
        ensureInternalContainers(sqlDb)
        installDeleteGuards(sqlDb)
    }

    fun autoExpirationEnabled(db: TeacherDbHelper): Boolean {
        ensure(db)
        db.readableDatabase.query(SETTINGS, arrayOf("auto_expire"), "id=1", null, null, null, null).use { c ->
            return c.moveToFirst() && c.getInt(0) == 1
        }
    }

    fun setAutoExpirationEnabled(db: TeacherDbHelper, enabled: Boolean) {
        ensure(db)
        db.writableDatabase.execSQL("UPDATE $SETTINGS SET auto_expire=? WHERE id=1", arrayOf(if (enabled) 1 else 0))
    }

    fun daysRemaining(entry: TrashEntry, now: Long = System.currentTimeMillis()): Int {
        val elapsedDays = ((now - entry.deletedAt).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
        return (RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
    }

    fun purgeExpired(db: TeacherDbHelper): Int {
        ensure(db)
        if (!autoExpirationEnabled(db)) return 0
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val expired = entries(db).filter { it.deletedAt <= cutoff }
        expired.forEach { deletePermanently(db, it) }
        return expired.size
    }

    fun emptyAll(db: TeacherDbHelper): Int {
        ensure(db)
        val all = entries(db)
        all.forEach { deletePermanently(db, it) }
        return all.size
    }

    private fun ensureInternalContainers(sqlDb: SQLiteDatabase) {
        var periodId: Long? = null
        sqlDb.query("periods", arrayOf("id"), "name=?", arrayOf(INTERNAL_PERIOD), null, null, null, "1").use { c ->
            if (c.moveToFirst()) periodId = c.getLong(0)
        }
        if (periodId == null) {
            val values = ContentValues().apply {
                put("name", INTERNAL_PERIOD)
                put("type", "Sistema")
                put("start_date", "")
                put("end_date", "")
                put("active", 0)
                put("archived", 1)
            }
            periodId = sqlDb.insertOrThrow("periods", null, values)
        }
        val pid = periodId!!
        sqlDb.query(
            "evaluation_categories",
            arrayOf("id"),
            "period_id=? AND name=?",
            arrayOf(pid.toString(), INTERNAL_CATEGORY),
            null,
            null,
            null,
            "1"
        ).use { c ->
            if (!c.moveToFirst()) {
                val values = ContentValues().apply {
                    put("period_id", pid)
                    put("name", INTERNAL_CATEGORY)
                    put("weight", 0.0)
                    put("position", 0)
                    put("mode", EvaluationMode.DIRECT.name)
                }
                sqlDb.insertOrThrow("evaluation_categories", null, values)
            }
        }
    }

    private fun installDeleteGuards(sqlDb: SQLiteDatabase) {
        val nowSql = "CAST(strftime('%s','now') AS INTEGER) * 1000"
        val trashPeriodSql = "(SELECT id FROM periods WHERE name='$INTERNAL_PERIOD' LIMIT 1)"
        val trashCategorySql = "(SELECT id FROM evaluation_categories WHERE period_id=$trashPeriodSql AND name='$INTERNAL_CATEGORY' LIMIT 1)"
        val enabled = "(SELECT bypass FROM $CONTROL WHERE id=1)=0"

        sqlDb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_trash_student_delete
            BEFORE DELETE ON students
            WHEN $enabled AND NOT EXISTS(SELECT 1 FROM $TABLE WHERE entity_type='$TYPE_STUDENT' AND entity_id=OLD.id)
            BEGIN
                INSERT OR REPLACE INTO $TABLE(entity_type,entity_id,label,parent_id,deleted_at)
                VALUES('$TYPE_STUDENT',OLD.id,OLD.name,OLD.period_id,$nowSql);
                UPDATE students SET period_id=$trashPeriodSql WHERE id=OLD.id;
                SELECT RAISE(IGNORE);
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_trash_event_delete
            BEFORE DELETE ON events
            WHEN $enabled AND NOT EXISTS(SELECT 1 FROM $TABLE WHERE entity_type='$TYPE_EVENT' AND entity_id=OLD.id)
            BEGIN
                INSERT OR REPLACE INTO $TABLE(entity_type,entity_id,label,parent_id,deleted_at)
                VALUES('$TYPE_EVENT',OLD.id,OLD.title,OLD.period_id,$nowSql);
                UPDATE events SET period_id=$trashPeriodSql WHERE id=OLD.id;
                SELECT RAISE(IGNORE);
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_trash_category_delete
            BEFORE DELETE ON evaluation_categories
            WHEN $enabled
              AND OLD.name<>'$INTERNAL_CATEGORY'
              AND NOT EXISTS(SELECT 1 FROM $TABLE WHERE entity_type='$TYPE_CATEGORY' AND entity_id=OLD.id)
            BEGIN
                INSERT OR REPLACE INTO $TABLE(entity_type,entity_id,label,parent_id,deleted_at)
                VALUES('$TYPE_CATEGORY',OLD.id,OLD.name,OLD.period_id,$nowSql);
                UPDATE evaluation_categories SET period_id=$trashPeriodSql WHERE id=OLD.id;
                SELECT RAISE(IGNORE);
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_trash_assessment_delete
            BEFORE DELETE ON assessment_items
            WHEN $enabled AND NOT EXISTS(SELECT 1 FROM $TABLE WHERE entity_type='$TYPE_ASSESSMENT_ITEM' AND entity_id=OLD.id)
            BEGIN
                INSERT OR REPLACE INTO $TABLE(entity_type,entity_id,label,parent_id,deleted_at)
                VALUES('$TYPE_ASSESSMENT_ITEM',OLD.id,OLD.name,OLD.category_id,$nowSql);
                UPDATE assessment_items SET category_id=$trashCategorySql WHERE id=OLD.id;
                SELECT RAISE(IGNORE);
            END
            """.trimIndent()
        )
        sqlDb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_trash_rubric_delete
            BEFORE DELETE ON rubric_criteria
            WHEN $enabled AND NOT EXISTS(SELECT 1 FROM $TABLE WHERE entity_type='$TYPE_RUBRIC_CRITERION' AND entity_id=OLD.id)
            BEGIN
                INSERT OR REPLACE INTO $TABLE(entity_type,entity_id,label,parent_id,deleted_at)
                VALUES('$TYPE_RUBRIC_CRITERION',OLD.id,OLD.name,OLD.category_id,$nowSql);
                UPDATE rubric_criteria SET category_id=$trashCategorySql WHERE id=OLD.id;
                SELECT RAISE(IGNORE);
            END
            """.trimIndent()
        )
    }

    private fun mark(db: TeacherDbHelper, type: String, id: Long, label: String, parentId: Long) {
        ensure(db)
        val values = ContentValues().apply {
            put("entity_type", type)
            put("entity_id", id)
            put("label", label)
            put("parent_id", parentId)
            put("deleted_at", System.currentTimeMillis())
        }
        db.writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun trashPeriodId(db: TeacherDbHelper): Long {
        ensure(db)
        db.readableDatabase.query("periods", arrayOf("id"), "name=?", arrayOf(INTERNAL_PERIOD), null, null, null, "1").use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        error("No se pudo inicializar la Papelera")
    }

    private fun trashCategoryId(db: TeacherDbHelper): Long {
        val periodId = trashPeriodId(db)
        db.readableDatabase.query(
            "evaluation_categories",
            arrayOf("id"),
            "period_id=? AND name=?",
            arrayOf(periodId.toString(), INTERNAL_CATEGORY),
            null,
            null,
            null,
            "1"
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        error("No se pudo inicializar la categoría interna de Papelera")
    }

    fun visiblePeriods(db: TeacherDbHelper): List<AcademicPeriod> {
        ensure(db)
        return db.getPeriods().filter { it.name != INTERNAL_PERIOD && !isTrashed(db, TYPE_GROUP, it.id) }
    }

    fun trashGroup(db: TeacherDbHelper, period: AcademicPeriod) {
        mark(db, TYPE_GROUP, period.id, period.name, 0)
        val sqlDb = db.writableDatabase
        val wasActive = period.active
        sqlDb.execSQL("UPDATE periods SET active=0, archived=1 WHERE id=?", arrayOf(period.id))
        if (wasActive) {
            sqlDb.rawQuery(
                "SELECT id FROM periods WHERE archived=0 AND name<>? AND id NOT IN (SELECT entity_id FROM $TABLE WHERE entity_type=?) ORDER BY id DESC LIMIT 1",
                arrayOf(INTERNAL_PERIOD, TYPE_GROUP)
            ).use { c ->
                if (c.moveToFirst()) sqlDb.execSQL("UPDATE periods SET active=1 WHERE id=?", arrayOf(c.getLong(0)))
            }
        }
    }

    fun trashStudent(db: TeacherDbHelper, student: Student) {
        mark(db, TYPE_STUDENT, student.id, student.name, student.periodId)
        db.writableDatabase.execSQL("UPDATE students SET period_id=? WHERE id=?", arrayOf(trashPeriodId(db), student.id))
    }

    fun trashEvent(db: TeacherDbHelper, event: CalendarEvent) {
        mark(db, TYPE_EVENT, event.id, event.title, event.periodId)
        db.writableDatabase.execSQL("UPDATE events SET period_id=? WHERE id=?", arrayOf(trashPeriodId(db), event.id))
    }

    fun trashCategory(db: TeacherDbHelper, category: EvaluationCategory) {
        mark(db, TYPE_CATEGORY, category.id, category.name, category.periodId)
        db.writableDatabase.execSQL("UPDATE evaluation_categories SET period_id=? WHERE id=?", arrayOf(trashPeriodId(db), category.id))
    }

    fun trashAssessmentItem(db: TeacherDbHelper, item: AssessmentItem) {
        mark(db, TYPE_ASSESSMENT_ITEM, item.id, item.name, item.categoryId)
        db.writableDatabase.execSQL("UPDATE assessment_items SET category_id=? WHERE id=?", arrayOf(trashCategoryId(db), item.id))
    }

    fun trashRubricCriterion(db: TeacherDbHelper, criterion: RubricCriterion) {
        mark(db, TYPE_RUBRIC_CRITERION, criterion.id, criterion.name, criterion.categoryId)
        db.writableDatabase.execSQL("UPDATE rubric_criteria SET category_id=? WHERE id=?", arrayOf(trashCategoryId(db), criterion.id))
    }

    fun isTrashed(db: TeacherDbHelper, type: String, entityId: Long): Boolean {
        ensure(db)
        db.readableDatabase.query(TABLE, arrayOf("entity_id"), "entity_type=? AND entity_id=?", arrayOf(type, entityId.toString()), null, null, null, "1").use {
            return it.moveToFirst()
        }
    }

    fun entries(db: TeacherDbHelper): List<TrashEntry> {
        ensure(db)
        val out = mutableListOf<TrashEntry>()
        db.readableDatabase.query(TABLE, null, null, null, null, null, "deleted_at DESC").use { c ->
            while (c.moveToNext()) out += TrashEntry(
                type = c.getString(c.getColumnIndexOrThrow("entity_type")),
                entityId = c.getLong(c.getColumnIndexOrThrow("entity_id")),
                label = c.getString(c.getColumnIndexOrThrow("label")),
                parentId = c.getLong(c.getColumnIndexOrThrow("parent_id")),
                deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at"))
            )
        }
        return out
    }

    fun restore(db: TeacherDbHelper, entry: TrashEntry) {
        ensure(db)
        val sqlDb = db.writableDatabase
        when (entry.type) {
            TYPE_GROUP -> sqlDb.execSQL("UPDATE periods SET archived=0 WHERE id=?", arrayOf(entry.entityId))
            TYPE_STUDENT -> sqlDb.execSQL("UPDATE students SET period_id=? WHERE id=?", arrayOf(entry.parentId, entry.entityId))
            TYPE_EVENT -> sqlDb.execSQL("UPDATE events SET period_id=? WHERE id=?", arrayOf(entry.parentId, entry.entityId))
            TYPE_CATEGORY -> sqlDb.execSQL("UPDATE evaluation_categories SET period_id=? WHERE id=?", arrayOf(entry.parentId, entry.entityId))
            TYPE_ASSESSMENT_ITEM -> sqlDb.execSQL("UPDATE assessment_items SET category_id=? WHERE id=?", arrayOf(entry.parentId, entry.entityId))
            TYPE_RUBRIC_CRITERION -> sqlDb.execSQL("UPDATE rubric_criteria SET category_id=? WHERE id=?", arrayOf(entry.parentId, entry.entityId))
        }
        sqlDb.delete(TABLE, "entity_type=? AND entity_id=?", arrayOf(entry.type, entry.entityId.toString()))
    }

    fun deletePermanently(db: TeacherDbHelper, entry: TrashEntry) {
        ensure(db)
        val sqlDb = db.writableDatabase
        sqlDb.beginTransaction()
        try {
            sqlDb.execSQL("UPDATE $CONTROL SET bypass=1 WHERE id=1")
            when (entry.type) {
                TYPE_GROUP -> sqlDb.delete("periods", "id=?", arrayOf(entry.entityId.toString()))
                TYPE_STUDENT -> sqlDb.delete("students", "id=?", arrayOf(entry.entityId.toString()))
                TYPE_EVENT -> sqlDb.delete("events", "id=?", arrayOf(entry.entityId.toString()))
                TYPE_CATEGORY -> sqlDb.delete("evaluation_categories", "id=?", arrayOf(entry.entityId.toString()))
                TYPE_ASSESSMENT_ITEM -> sqlDb.delete("assessment_items", "id=?", arrayOf(entry.entityId.toString()))
                TYPE_RUBRIC_CRITERION -> sqlDb.delete("rubric_criteria", "id=?", arrayOf(entry.entityId.toString()))
            }
            sqlDb.delete(TABLE, "entity_type=? AND entity_id=?", arrayOf(entry.type, entry.entityId.toString()))
            sqlDb.execSQL("UPDATE $CONTROL SET bypass=0 WHERE id=1")
            sqlDb.setTransactionSuccessful()
        } finally {
            runCatching { sqlDb.execSQL("UPDATE $CONTROL SET bypass=0 WHERE id=1") }
            sqlDb.endTransaction()
        }
    }

    fun typeLabel(type: String): String = when (type) {
        TYPE_GROUP -> "Grupo"
        TYPE_STUDENT -> "Estudiante"
        TYPE_EVENT -> "Fecha / evento"
        TYPE_CATEGORY -> "Rubro de evaluación"
        TYPE_ASSESSMENT_ITEM -> "Actividad / examen"
        TYPE_RUBRIC_CRITERION -> "Criterio de rúbrica"
        else -> "Elemento"
    }
}
