package com.profecuaderno.app.data

object SyncMetadataStore {
    private const val TABLE = "sync_metadata"

    private data class EntityTable(val type: String, val table: String)

    private val entities = listOf(
        EntityTable("TEACHER", "teacher"),
        EntityTable("PERIOD", "periods"),
        EntityTable("STUDENT", "students"),
        EntityTable("ATTENDANCE_SESSION", "attendance_sessions"),
        EntityTable("ATTENDANCE_RECORD", "attendance_records"),
        EntityTable("EVALUATION_CATEGORY", "evaluation_categories"),
        EntityTable("ASSESSMENT_ITEM", "assessment_items"),
        EntityTable("ASSESSMENT_SCORE", "assessment_scores"),
        EntityTable("RUBRIC_CRITERION", "rubric_criteria"),
        EntityTable("GRADE", "grades"),
        EntityTable("RUBRIC_MARK", "rubric_marks"),
        EntityTable("EVENT", "events")
    )

    fun ensure(db: TeacherDbHelper) {
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                entity_type TEXT NOT NULL,
                local_id INTEGER NOT NULL,
                sync_id TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                PRIMARY KEY(entity_type, local_id)
            )
            """.trimIndent()
        )

        entities.forEach { entity ->
            sqlDb.execSQL(
                """
                INSERT OR IGNORE INTO $TABLE(entity_type, local_id, sync_id, created_at, updated_at, sync_state)
                SELECT '${entity.type}', id, lower(hex(randomblob(16))),
                       CAST(strftime('%s','now') AS INTEGER) * 1000,
                       CAST(strftime('%s','now') AS INTEGER) * 1000,
                       'LOCAL_ONLY'
                FROM ${entity.table}
                """.trimIndent()
            )

            sqlDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_sync_${entity.table}_insert
                AFTER INSERT ON ${entity.table}
                BEGIN
                    INSERT OR IGNORE INTO $TABLE(entity_type, local_id, sync_id, created_at, updated_at, sync_state)
                    VALUES(
                        '${entity.type}', NEW.id, lower(hex(randomblob(16))),
                        CAST(strftime('%s','now') AS INTEGER) * 1000,
                        CAST(strftime('%s','now') AS INTEGER) * 1000,
                        'LOCAL_ONLY'
                    );
                END
                """.trimIndent()
            )

            sqlDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_sync_${entity.table}_update
                AFTER UPDATE ON ${entity.table}
                BEGIN
                    UPDATE $TABLE
                    SET updated_at = CAST(strftime('%s','now') AS INTEGER) * 1000,
                        sync_state = CASE WHEN sync_state='SYNCED' THEN 'DIRTY' ELSE sync_state END
                    WHERE entity_type='${entity.type}' AND local_id=NEW.id;
                END
                """.trimIndent()
            )

            sqlDb.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_sync_${entity.table}_delete
                BEFORE DELETE ON ${entity.table}
                BEGIN
                    UPDATE $TABLE
                    SET updated_at = CAST(strftime('%s','now') AS INTEGER) * 1000,
                        sync_state = 'DELETED'
                    WHERE entity_type='${entity.type}' AND local_id=OLD.id;
                END
                """.trimIndent()
            )
        }
    }

    fun stableId(db: TeacherDbHelper, entityType: String, localId: Long): String? {
        ensure(db)
        db.readableDatabase.query(
            TABLE,
            arrayOf("sync_id"),
            "entity_type=? AND local_id=?",
            arrayOf(entityType, localId.toString()),
            null,
            null,
            null,
            "1"
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }
}
