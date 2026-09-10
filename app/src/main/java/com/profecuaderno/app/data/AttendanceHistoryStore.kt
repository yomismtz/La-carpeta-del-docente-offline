package com.profecuaderno.app.data

data class StudentAttendanceHistoryEntry(
    val sessionId: Long,
    val date: String,
    val title: String,
    val status: AttendanceStatus
)

object AttendanceHistoryStore {
    fun entriesForStudent(
        db: TeacherDbHelper,
        periodId: Long,
        studentId: Long
    ): List<StudentAttendanceHistoryEntry> {
        val out = mutableListOf<StudentAttendanceHistoryEntry>()
        val sql = """
            SELECT s.id, s.date, s.title, ar.status
            FROM attendance_sessions s
            JOIN attendance_records ar ON ar.session_id = s.id
            WHERE s.period_id=?
              AND s.worked=1
              AND ar.student_id=?
            ORDER BY s.date DESC, s.id DESC
        """.trimIndent()

        db.readableDatabase.rawQuery(
            sql,
            arrayOf(periodId.toString(), studentId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching {
                    AttendanceStatus.valueOf(cursor.getString(3))
                }.getOrNull() ?: continue
                out += StudentAttendanceHistoryEntry(
                    sessionId = cursor.getLong(0),
                    date = cursor.getString(1),
                    title = cursor.getString(2),
                    status = status
                )
            }
        }
        return out
    }

    fun incidentsForStudent(
        db: TeacherDbHelper,
        periodId: Long,
        studentId: Long
    ): List<StudentAttendanceHistoryEntry> =
        entriesForStudent(db, periodId, studentId).filter { it.status != AttendanceStatus.PRESENT }
}
