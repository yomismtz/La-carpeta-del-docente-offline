package com.profecuaderno.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

enum class JustifiedEffect(val label: String) {
    PRESENT("Cuenta como asistencia"),
    LATE("Cuenta como retardo"),
    ABSENT("Cuenta como falta"),
    EXCLUDED("No se contabiliza")
}

data class AttendancePolicy(
    val latePerAbsence: Int = 3,
    val justifiedEffect: JustifiedEffect = JustifiedEffect.PRESENT
)

object AttendancePolicyStore {
    private const val TABLE = "attendance_policy"

    private fun ensure(db: TeacherDbHelper) {
        val sqlDb = db.writableDatabase
        sqlDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE(
                period_id INTEGER PRIMARY KEY,
                justified_counts INTEGER NOT NULL DEFAULT 1,
                late_per_absence INTEGER NOT NULL DEFAULT 3,
                justified_effect TEXT NOT NULL DEFAULT 'PRESENT',
                policy_version INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        val hadLate = hasColumn(sqlDb, "late_per_absence")
        val hadEffect = hasColumn(sqlDb, "justified_effect")
        val hadVersion = hasColumn(sqlDb, "policy_version")
        if (!hadLate) sqlDb.execSQL("ALTER TABLE $TABLE ADD COLUMN late_per_absence INTEGER NOT NULL DEFAULT 3")
        if (!hadEffect) sqlDb.execSQL("ALTER TABLE $TABLE ADD COLUMN justified_effect TEXT NOT NULL DEFAULT 'PRESENT'")
        if (!hadVersion) sqlDb.execSQL("ALTER TABLE $TABLE ADD COLUMN policy_version INTEGER NOT NULL DEFAULT 0")
        if (!hadEffect) {
            sqlDb.execSQL("UPDATE $TABLE SET justified_effect='EXCLUDED' WHERE justified_counts=0")
        }
    }

    private fun hasColumn(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
            val index = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(index) == name) return true
        }
        return false
    }

    private fun ensureRow(db: TeacherDbHelper, periodId: Long) {
        ensure(db)
        db.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put("period_id", periodId)
                put("justified_counts", 1)
                put("late_per_absence", 3)
                put("justified_effect", JustifiedEffect.PRESENT.name)
                put("policy_version", 0)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun policy(db: TeacherDbHelper, periodId: Long): AttendancePolicy {
        ensureRow(db, periodId)
        val raw = readRaw(db, periodId)
        if (raw.third < 1) {
            recalculatePeriod(db, periodId, raw.first, raw.second)
            db.writableDatabase.execSQL("UPDATE $TABLE SET policy_version=1 WHERE period_id=?", arrayOf(periodId))
        }
        return AttendancePolicy(raw.first, raw.second)
    }

    private fun readRaw(db: TeacherDbHelper, periodId: Long): Triple<Int, JustifiedEffect, Int> {
        db.readableDatabase.query(
            TABLE,
            arrayOf("late_per_absence", "justified_effect", "policy_version"),
            "period_id=?",
            arrayOf(periodId.toString()),
            null,
            null,
            null,
            "1"
        ).use { c ->
            if (!c.moveToFirst()) return Triple(3, JustifiedEffect.PRESENT, 0)
            val late = c.getInt(0).coerceIn(2, 10)
            val effect = runCatching { JustifiedEffect.valueOf(c.getString(1)) }.getOrDefault(JustifiedEffect.PRESENT)
            return Triple(late, effect, c.getInt(2))
        }
    }

    fun setLatePerAbsence(db: TeacherDbHelper, periodId: Long, value: Int) {
        ensureRow(db, periodId)
        val late = value.coerceIn(2, 10)
        val effect = readRaw(db, periodId).second
        db.writableDatabase.execSQL(
            "UPDATE $TABLE SET late_per_absence=?, policy_version=1 WHERE period_id=?",
            arrayOf(late, periodId)
        )
        recalculatePeriod(db, periodId, late, effect)
    }

    fun setJustifiedEffect(db: TeacherDbHelper, periodId: Long, effect: JustifiedEffect) {
        ensureRow(db, periodId)
        val late = readRaw(db, periodId).first
        val justifiedCounts = if (effect == JustifiedEffect.EXCLUDED) 0 else 1
        db.writableDatabase.execSQL(
            "UPDATE $TABLE SET justified_effect=?, justified_counts=?, policy_version=1 WHERE period_id=?",
            arrayOf(effect.name, justifiedCounts, periodId)
        )
        recalculatePeriod(db, periodId, late, effect)
    }

    fun justifiedCounts(db: TeacherDbHelper, periodId: Long): Boolean =
        policy(db, periodId).justifiedEffect != JustifiedEffect.EXCLUDED

    fun setJustifiedCounts(db: TeacherDbHelper, periodId: Long, counts: Boolean) {
        setJustifiedEffect(db, periodId, if (counts) JustifiedEffect.PRESENT else JustifiedEffect.EXCLUDED)
    }

    fun baseStatus(status: AttendanceStatus?): AttendanceStatus? = when (status) {
        AttendanceStatus.LATE_PENALTY -> AttendanceStatus.LATE
        AttendanceStatus.JUSTIFIED_PRESENT,
        AttendanceStatus.JUSTIFIED_LATE,
        AttendanceStatus.JUSTIFIED_LATE_PENALTY,
        AttendanceStatus.JUSTIFIED_ABSENT -> AttendanceStatus.JUSTIFIED
        else -> status
    }

    fun setStatus(
        db: TeacherDbHelper,
        periodId: Long,
        sessionId: Long,
        studentId: Long,
        baseStatus: AttendanceStatus
    ) {
        val p = policy(db, periodId)
        val stored = when (baseStatus) {
            AttendanceStatus.JUSTIFIED -> mappedJustified(p.justifiedEffect)
            AttendanceStatus.LATE, AttendanceStatus.LATE_PENALTY -> AttendanceStatus.LATE
            AttendanceStatus.PRESENT -> AttendanceStatus.PRESENT
            AttendanceStatus.ABSENT -> AttendanceStatus.ABSENT
            AttendanceStatus.JUSTIFIED_PRESENT,
            AttendanceStatus.JUSTIFIED_LATE,
            AttendanceStatus.JUSTIFIED_LATE_PENALTY,
            AttendanceStatus.JUSTIFIED_ABSENT -> mappedJustified(p.justifiedEffect)
        }
        db.setAttendanceStatus(sessionId, studentId, stored)
        recalculateStudent(db, periodId, studentId, p.latePerAbsence)
    }

    fun aggregatedCounts(db: TeacherDbHelper, periodId: Long, studentId: Long): Map<AttendanceStatus, Int> {
        policy(db, periodId)
        val raw = db.attendanceCounts(periodId, studentId)
        return mapOf(
            AttendanceStatus.PRESENT to (raw[AttendanceStatus.PRESENT] ?: 0),
            AttendanceStatus.ABSENT to (raw[AttendanceStatus.ABSENT] ?: 0),
            AttendanceStatus.LATE to ((raw[AttendanceStatus.LATE] ?: 0) + (raw[AttendanceStatus.LATE_PENALTY] ?: 0)),
            AttendanceStatus.JUSTIFIED to (
                (raw[AttendanceStatus.JUSTIFIED] ?: 0) +
                    (raw[AttendanceStatus.JUSTIFIED_PRESENT] ?: 0) +
                    (raw[AttendanceStatus.JUSTIFIED_LATE] ?: 0) +
                    (raw[AttendanceStatus.JUSTIFIED_LATE_PENALTY] ?: 0) +
                    (raw[AttendanceStatus.JUSTIFIED_ABSENT] ?: 0)
                )
        )
    }

    private fun mappedJustified(effect: JustifiedEffect): AttendanceStatus = when (effect) {
        JustifiedEffect.PRESENT -> AttendanceStatus.JUSTIFIED_PRESENT
        JustifiedEffect.LATE -> AttendanceStatus.JUSTIFIED_LATE
        JustifiedEffect.ABSENT -> AttendanceStatus.JUSTIFIED_ABSENT
        JustifiedEffect.EXCLUDED -> AttendanceStatus.JUSTIFIED
    }

    private fun recalculatePeriod(
        db: TeacherDbHelper,
        periodId: Long,
        latePerAbsence: Int,
        effect: JustifiedEffect
    ) {
        val sqlDb = db.writableDatabase
        sqlDb.beginTransaction()
        try {
            rewriteJustifiedStatuses(sqlDb, periodId, effect)
            val studentIds = mutableListOf<Long>()
            sqlDb.rawQuery(
                "SELECT DISTINCT ar.student_id FROM attendance_records ar JOIN attendance_sessions s ON s.id=ar.session_id WHERE s.period_id=?",
                arrayOf(periodId.toString())
            ).use { c -> while (c.moveToNext()) studentIds += c.getLong(0) }
            studentIds.forEach { recalculateStudentInternal(sqlDb, periodId, it, latePerAbsence) }
            sqlDb.setTransactionSuccessful()
        } finally {
            sqlDb.endTransaction()
        }
    }

    private fun rewriteJustifiedStatuses(db: SQLiteDatabase, periodId: Long, effect: JustifiedEffect) {
        val target = mappedJustified(effect).name
        db.execSQL(
            """
            UPDATE attendance_records
            SET status=?
            WHERE session_id IN (SELECT id FROM attendance_sessions WHERE period_id=?)
              AND status IN ('JUSTIFIED','JUSTIFIED_PRESENT','JUSTIFIED_LATE','JUSTIFIED_LATE_PENALTY','JUSTIFIED_ABSENT')
            """.trimIndent(),
            arrayOf(target, periodId)
        )
    }

    private fun recalculateStudent(db: TeacherDbHelper, periodId: Long, studentId: Long, latePerAbsence: Int) {
        val sqlDb = db.writableDatabase
        sqlDb.beginTransaction()
        try {
            recalculateStudentInternal(sqlDb, periodId, studentId, latePerAbsence)
            sqlDb.setTransactionSuccessful()
        } finally {
            sqlDb.endTransaction()
        }
    }

    private fun recalculateStudentInternal(db: SQLiteDatabase, periodId: Long, studentId: Long, latePerAbsence: Int) {
        db.execSQL(
            """
            UPDATE attendance_records SET status='LATE'
            WHERE student_id=? AND status='LATE_PENALTY'
              AND session_id IN (SELECT id FROM attendance_sessions WHERE period_id=? AND worked=1)
            """.trimIndent(),
            arrayOf(studentId, periodId)
        )
        db.execSQL(
            """
            UPDATE attendance_records SET status='JUSTIFIED_LATE'
            WHERE student_id=? AND status='JUSTIFIED_LATE_PENALTY'
              AND session_id IN (SELECT id FROM attendance_sessions WHERE period_id=? AND worked=1)
            """.trimIndent(),
            arrayOf(studentId, periodId)
        )

        val lateRows = mutableListOf<Pair<Long, String>>()
        db.rawQuery(
            """
            SELECT ar.id, ar.status
            FROM attendance_records ar
            JOIN attendance_sessions s ON s.id=ar.session_id
            WHERE s.period_id=? AND s.worked=1 AND ar.student_id=?
              AND ar.status IN ('LATE','JUSTIFIED_LATE')
            ORDER BY s.date ASC, s.id ASC, ar.id ASC
            """.trimIndent(),
            arrayOf(periodId.toString(), studentId.toString())
        ).use { c -> while (c.moveToNext()) lateRows += c.getLong(0) to c.getString(1) }

        lateRows.forEachIndexed { index, row ->
            if ((index + 1) % latePerAbsence == 0) {
                val penalty = if (row.second == AttendanceStatus.JUSTIFIED_LATE.name) {
                    AttendanceStatus.JUSTIFIED_LATE_PENALTY.name
                } else AttendanceStatus.LATE_PENALTY.name
                db.execSQL("UPDATE attendance_records SET status=? WHERE id=?", arrayOf(penalty, row.first))
            }
        }
    }
}
