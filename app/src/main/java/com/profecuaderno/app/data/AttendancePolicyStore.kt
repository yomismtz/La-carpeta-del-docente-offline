package com.profecuaderno.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

object AttendancePolicyStore {
    private const val TABLE = "attendance_policy"

    private fun ensure(db: TeacherDbHelper) {
        db.writableDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                period_id INTEGER PRIMARY KEY,
                justified_counts INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    fun justifiedCounts(db: TeacherDbHelper, periodId: Long): Boolean {
        ensure(db)
        db.readableDatabase.query(
            TABLE,
            arrayOf("justified_counts"),
            "period_id=?",
            arrayOf(periodId.toString()),
            null,
            null,
            null,
            "1"
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) == 1 else true
        }
    }

    fun setJustifiedCounts(db: TeacherDbHelper, periodId: Long, counts: Boolean) {
        ensure(db)
        val values = ContentValues().apply {
            put("period_id", periodId)
            put("justified_counts", if (counts) 1 else 0)
        }
        db.writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
