package com.profecuaderno.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class TeacherDbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE teacher(
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                birth_date TEXT NOT NULL DEFAULT '',
                degree TEXT NOT NULL DEFAULT '',
                institution TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE periods(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                start_date TEXT NOT NULL DEFAULT '',
                end_date TEXT NOT NULL DEFAULT '',
                active INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE students(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                student_code TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT '',
                birth_date TEXT NOT NULL DEFAULT '',
                group_name TEXT NOT NULL DEFAULT '',
                clinic TEXT NOT NULL DEFAULT '',
                team_name TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE attendance_sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                worked INTEGER NOT NULL DEFAULT 1,
                UNIQUE(period_id, date),
                FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE attendance_records(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                student_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                UNIQUE(session_id, student_id),
                FOREIGN KEY(session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE evaluation_categories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                weight REAL NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                mode TEXT NOT NULL DEFAULT 'DIRECT',
                FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE assessment_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE assessment_scores(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                item_id INTEGER NOT NULL,
                score REAL NOT NULL,
                UNIQUE(student_id, item_id),
                FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                FOREIGN KEY(item_id) REFERENCES assessment_items(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE rubric_criteria(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                weight REAL NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE grades(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                student_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                score REAL NOT NULL DEFAULT 0,
                UNIQUE(student_id, category_id),
                FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE,
                FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE rubric_marks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                criterion_id INTEGER NOT NULL,
                score REAL NOT NULL DEFAULT 0,
                UNIQUE(student_id, criterion_id),
                FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                FOREIGN KEY(criterion_id) REFERENCES rubric_criteria(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE events(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT 'IMPORTANTE',
                FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE evaluation_categories ADD COLUMN mode TEXT NOT NULL DEFAULT 'DIRECT'")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS assessment_items(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS assessment_scores(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    score REAL NOT NULL,
                    UNIQUE(student_id, item_id),
                    FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                    FOREIGN KEY(item_id) REFERENCES assessment_items(id) ON DELETE CASCADE
                )
            """.trimIndent())
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    fun exportBackup(uri: Uri): Boolean {
        return runCatching {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
            close()
            val dbFile = appContext.getDatabasePath(DB_NAME)
            appContext.contentResolver.openOutputStream(uri, "w")?.use { out ->
                dbFile.inputStream().use { input -> input.copyTo(out) }
            } ?: error("No se pudo abrir el archivo de destino.")
            readableDatabase
            true
        }.getOrElse {
            runCatching { readableDatabase }
            false
        }
    }

    fun importBackup(uri: Uri): Boolean {
        return runCatching {
            val temp = java.io.File(appContext.cacheDir, "profecuaderno_restore.db")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("No se pudo leer la copia de seguridad.")

            val testDb = SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                testDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='teacher'", null).use { cursor ->
                    if (!cursor.moveToFirst()) error("El archivo no es una copia válida de ProfeCuaderno.")
                }
                val requiredTables = listOf("periods", "students", "attendance_sessions", "evaluation_categories", "grades")
                requiredTables.forEach { table ->
                    testDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
                        if (!cursor.moveToFirst()) error("La copia está incompleta: falta $table.")
                    }
                }
            } finally {
                testDb.close()
            }

            close()
            val dbFile = appContext.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()
            java.io.File(dbFile.absolutePath + "-wal").delete()
            java.io.File(dbFile.absolutePath + "-shm").delete()
            temp.copyTo(dbFile, overwrite = true)
            temp.delete()
            readableDatabase
            true
        }.getOrElse {
            runCatching { readableDatabase }
            false
        }
    }

    fun getTeacher(): Teacher? {
        readableDatabase.query("teacher", null, "id=1", null, null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return Teacher(
                id = 1,
                name = c.getString(c.getColumnIndexOrThrow("name")),
                birthDate = c.getString(c.getColumnIndexOrThrow("birth_date")),
                degree = c.getString(c.getColumnIndexOrThrow("degree")),
                institution = c.getString(c.getColumnIndexOrThrow("institution")),
                email = c.getString(c.getColumnIndexOrThrow("email"))
            )
        }
    }

    fun saveTeacher(teacher: Teacher) {
        val values = ContentValues().apply {
            put("id", 1)
            put("name", teacher.name.trim())
            put("birth_date", teacher.birthDate.trim())
            put("degree", teacher.degree.trim())
            put("institution", teacher.institution.trim())
            put("email", teacher.email.trim())
        }
        writableDatabase.insertWithOnConflict("teacher", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPeriods(): List<AcademicPeriod> {
        val out = mutableListOf<AcademicPeriod>()
        readableDatabase.query("periods", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) out += c.toPeriod()
        }
        return out
    }

    fun getOpenGroups(): List<AcademicPeriod> = getPeriods().filter { !it.archived }

    fun getActivePeriod(): AcademicPeriod? {
        readableDatabase.query("periods", null, "active=1", null, null, null, "id DESC", "1").use { c ->
            return if (c.moveToFirst()) c.toPeriod() else null
        }
    }

    fun createPeriod(
        name: String,
        type: String,
        startDate: String,
        endDate: String,
        copyFromPeriodId: Long? = null,
        makeActive: Boolean = true
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (makeActive) {
                db.execSQL("UPDATE periods SET active=0")
            }
            val values = ContentValues().apply {
                put("name", name.trim())
                put("type", type.trim())
                put("start_date", startDate.trim())
                put("end_date", endDate.trim())
                put("active", if (makeActive) 1 else 0)
                put("archived", 0)
            }
            val newId = db.insertOrThrow("periods", null, values)
            if (copyFromPeriodId != null) copyEvaluationStructure(db, copyFromPeriodId, newId)
            db.setTransactionSuccessful()
            return newId
        } finally {
            db.endTransaction()
        }
    }

    fun closePeriod(periodId: Long) {
        writableDatabase.execSQL("UPDATE periods SET active=0, archived=1 WHERE id=?", arrayOf(periodId))
    }

    fun activatePeriod(periodId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE periods SET active=0")
            db.execSQL("UPDATE periods SET active=1, archived=0 WHERE id=?", arrayOf(periodId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun copyEvaluationStructure(db: SQLiteDatabase, fromPeriodId: Long, toPeriodId: Long) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS weighted_evaluation_methods(
                category_id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL,
                FOREIGN KEY(category_id) REFERENCES evaluation_categories(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS assessment_item_weights(
                item_id INTEGER PRIMARY KEY,
                weight REAL NOT NULL DEFAULT 0,
                FOREIGN KEY(item_id) REFERENCES assessment_items(id) ON DELETE CASCADE
            )
        """.trimIndent())

        val oldCategories = mutableListOf<EvaluationCategory>()
        db.query("evaluation_categories", null, "period_id=?", arrayOf(fromPeriodId.toString()), null, null, "position,id").use { c ->
            while (c.moveToNext()) oldCategories += c.toCategory()
        }
        oldCategories.forEach { old ->
            val catValues = ContentValues().apply {
                put("period_id", toPeriodId)
                put("name", old.name)
                put("weight", old.weight)
                put("position", old.position)
                put("mode", old.mode)
            }
            val newCategoryId = db.insertOrThrow("evaluation_categories", null, catValues)

            db.query("weighted_evaluation_methods", arrayOf("kind"), "category_id=?", arrayOf(old.id.toString()), null, null, null).use { method ->
                if (method.moveToFirst()) {
                    db.insertOrThrow("weighted_evaluation_methods", null, ContentValues().apply {
                        put("category_id", newCategoryId)
                        put("kind", method.getString(0))
                    })
                }
            }

            db.query("assessment_items", null, "category_id=?", arrayOf(old.id.toString()), null, null, "position,id").use { ac ->
                while (ac.moveToNext()) {
                    val oldItemId = ac.getLong(ac.getColumnIndexOrThrow("id"))
                    val values = ContentValues().apply {
                        put("category_id", newCategoryId)
                        put("name", ac.getString(ac.getColumnIndexOrThrow("name")))
                        put("position", ac.getInt(ac.getColumnIndexOrThrow("position")))
                    }
                    val newItemId = db.insertOrThrow("assessment_items", null, values)
                    db.query("assessment_item_weights", arrayOf("weight"), "item_id=?", arrayOf(oldItemId.toString()), null, null, null).use { weightCursor ->
                        if (weightCursor.moveToFirst()) {
                            db.insertOrThrow("assessment_item_weights", null, ContentValues().apply {
                                put("item_id", newItemId)
                                put("weight", weightCursor.getDouble(0))
                            })
                        }
                    }
                }
            }
            db.query("rubric_criteria", null, "category_id=?", arrayOf(old.id.toString()), null, null, "position,id").use { rc ->
                while (rc.moveToNext()) {
                    val values = ContentValues().apply {
                        put("category_id", newCategoryId)
                        put("name", rc.getString(rc.getColumnIndexOrThrow("name")))
                        put("weight", rc.getDouble(rc.getColumnIndexOrThrow("weight")))
                        put("position", rc.getInt(rc.getColumnIndexOrThrow("position")))
                    }
                    db.insertOrThrow("rubric_criteria", null, values)
                }
            }
        }
    }

    fun getStudents(periodId: Long): List<Student> {
        val out = mutableListOf<Student>()
        readableDatabase.query("students", null, "period_id=?", arrayOf(periodId.toString()), null, null, "name COLLATE NOCASE").use { c ->
            while (c.moveToNext()) out += c.toStudent()
        }
        return out
    }

    fun saveStudent(student: Student): Long {
        val values = ContentValues().apply {
            put("period_id", student.periodId)
            put("name", student.name.trim())
            put("student_code", student.studentCode.trim())
            put("email", student.email.trim())
            put("phone", student.phone.trim())
            put("birth_date", student.birthDate.trim())
            put("group_name", student.groupName.trim())
            put("clinic", student.clinic.trim())
            put("team_name", student.teamName.trim())
            put("notes", student.notes.trim())
        }
        return if (student.id == 0L) {
            writableDatabase.insertOrThrow("students", null, values)
        } else {
            writableDatabase.update("students", values, "id=?", arrayOf(student.id.toString()))
            student.id
        }
    }

    fun deleteStudent(studentId: Long) {
        writableDatabase.delete("students", "id=?", arrayOf(studentId.toString()))
    }

    fun getAttendanceSession(periodId: Long, date: String): AttendanceSession? {
        readableDatabase.query("attendance_sessions", null, "period_id=? AND date=?", arrayOf(periodId.toString(), date), null, null, null).use { c ->
            return if (c.moveToFirst()) c.toAttendanceSession() else null
        }
    }

    fun createOrUpdateAttendanceSession(periodId: Long, date: String, title: String, worked: Boolean): Long {
        val existing = getAttendanceSession(periodId, date)
        val values = ContentValues().apply {
            put("period_id", periodId)
            put("date", date)
            put("title", title)
            put("worked", if (worked) 1 else 0)
        }
        return if (existing == null) {
            writableDatabase.insertOrThrow("attendance_sessions", null, values)
        } else {
            writableDatabase.update("attendance_sessions", values, "id=?", arrayOf(existing.id.toString()))
            existing.id
        }
    }

    fun listAttendanceSessions(periodId: Long): List<AttendanceSession> {
        val out = mutableListOf<AttendanceSession>()
        readableDatabase.query("attendance_sessions", null, "period_id=?", arrayOf(periodId.toString()), null, null, "date DESC").use { c ->
            while (c.moveToNext()) out += c.toAttendanceSession()
        }
        return out
    }

    fun getAttendanceStatus(sessionId: Long, studentId: Long): AttendanceStatus? {
        readableDatabase.query("attendance_records", arrayOf("status"), "session_id=? AND student_id=?", arrayOf(sessionId.toString(), studentId.toString()), null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return runCatching { AttendanceStatus.valueOf(c.getString(0)) }.getOrNull()
        }
    }

    fun setAttendanceStatus(sessionId: Long, studentId: Long, status: AttendanceStatus) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("student_id", studentId)
            put("status", status.name)
        }
        writableDatabase.insertWithOnConflict("attendance_records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun attendanceCounts(periodId: Long, studentId: Long): Map<AttendanceStatus, Int> {
        val counts = AttendanceStatus.entries.associateWith { 0 }.toMutableMap()
        val sql = """
            SELECT ar.status, COUNT(*)
            FROM attendance_records ar
            JOIN attendance_sessions s ON s.id = ar.session_id
            WHERE s.period_id=? AND s.worked=1 AND ar.student_id=?
            GROUP BY ar.status
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(periodId.toString(), studentId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching { AttendanceStatus.valueOf(cursor.getString(0)) }.getOrNull()
                if (status != null) counts[status] = cursor.getInt(1)
            }
        }
        return counts
    }

    fun hasAttendanceRecords(periodId: Long, studentId: Long): Boolean {
        val sql = """
            SELECT 1
            FROM attendance_records ar
            JOIN attendance_sessions s ON s.id=ar.session_id
            WHERE s.period_id=? AND s.worked=1 AND ar.student_id=?
            LIMIT 1
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(periodId.toString(), studentId.toString())).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun attendancePercentage(periodId: Long, studentId: Long): Double {
        val justifiedCounts = AttendancePolicyStore.justifiedCounts(this, periodId)
        val sql = """
            SELECT ar.status
            FROM attendance_records ar
            JOIN attendance_sessions s ON s.id=ar.session_id
            WHERE s.period_id=? AND s.worked=1 AND ar.student_id=?
        """.trimIndent()
        var denominator = 0
        var earned = 0.0
        readableDatabase.rawQuery(sql, arrayOf(periodId.toString(), studentId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val status = runCatching { AttendanceStatus.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
                if (status == AttendanceStatus.JUSTIFIED && !justifiedCounts) continue
                denominator++
                earned += status.factor
            }
        }
        return if (denominator == 0) 0.0 else earned / denominator * 100.0
    }

    fun getCategories(periodId: Long): List<EvaluationCategory> {
        val out = mutableListOf<EvaluationCategory>()
        readableDatabase.query("evaluation_categories", null, "period_id=?", arrayOf(periodId.toString()), null, null, "position,id").use { c ->
            while (c.moveToNext()) out += c.toCategory()
        }
        return out
    }

    fun saveCategory(category: EvaluationCategory): Long {
        val effectiveMode = if (isAttendanceName(category.name)) {
            EvaluationMode.ATTENDANCE.name
        } else {
            category.mode
        }
        val values = ContentValues().apply {
            put("period_id", category.periodId)
            put("name", category.name.trim())
            put("weight", category.weight)
            put("position", category.position)
            put("mode", effectiveMode)
        }
        return if (category.id == 0L) writableDatabase.insertOrThrow("evaluation_categories", null, values)
        else {
            writableDatabase.update("evaluation_categories", values, "id=?", arrayOf(category.id.toString()))
            category.id
        }
    }

    fun deleteCategory(categoryId: Long) {
        writableDatabase.delete("evaluation_categories", "id=?", arrayOf(categoryId.toString()))
    }

    fun categoryWeightTotal(periodId: Long): Double = getCategories(periodId).sumOf { it.weight }

    fun getAssessmentItems(categoryId: Long): List<AssessmentItem> {
        val out = mutableListOf<AssessmentItem>()
        readableDatabase.query("assessment_items", null, "category_id=?", arrayOf(categoryId.toString()), null, null, "position,id").use { c ->
            while (c.moveToNext()) out += AssessmentItem(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                categoryId = c.getLong(c.getColumnIndexOrThrow("category_id")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                position = c.getInt(c.getColumnIndexOrThrow("position"))
            )
        }
        return out
    }

    fun saveAssessmentItem(item: AssessmentItem): Long {
        val values = ContentValues().apply {
            put("category_id", item.categoryId)
            put("name", item.name.trim())
            put("position", item.position)
        }
        return if (item.id == 0L) writableDatabase.insertOrThrow("assessment_items", null, values)
        else {
            writableDatabase.update("assessment_items", values, "id=?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun deleteAssessmentItem(itemId: Long) {
        writableDatabase.delete("assessment_items", "id=?", arrayOf(itemId.toString()))
    }

    fun getAssessmentScore(studentId: Long, itemId: Long): Double? {
        readableDatabase.query("assessment_scores", arrayOf("score"), "student_id=? AND item_id=?", arrayOf(studentId.toString(), itemId.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.getDouble(0) else null
        }
    }

    fun setAssessmentScore(studentId: Long, itemId: Long, score: Double?) {
        if (score == null) {
            writableDatabase.delete("assessment_scores", "student_id=? AND item_id=?", arrayOf(studentId.toString(), itemId.toString()))
            return
        }
        val values = ContentValues().apply {
            put("student_id", studentId)
            put("item_id", itemId)
            put("score", score.coerceIn(0.0, 100.0))
        }
        writableDatabase.insertWithOnConflict("assessment_scores", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun calculateAndStoreAverageGrade(periodId: Long, studentId: Long, categoryId: Long): Double {
        val scores = getAssessmentItems(categoryId).mapNotNull { getAssessmentScore(studentId, it.id) }
        if (scores.isEmpty()) {
            writableDatabase.delete(
                "grades",
                "period_id=? AND student_id=? AND category_id=?",
                arrayOf(periodId.toString(), studentId.toString(), categoryId.toString())
            )
            return 0.0
        }
        val average = scores.average()
        setGrade(periodId, studentId, categoryId, average)
        return average
    }

    fun getRubricCriteria(categoryId: Long): List<RubricCriterion> {
        val out = mutableListOf<RubricCriterion>()
        readableDatabase.query("rubric_criteria", null, "category_id=?", arrayOf(categoryId.toString()), null, null, "position,id").use { c ->
            while (c.moveToNext()) out += RubricCriterion(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                categoryId = c.getLong(c.getColumnIndexOrThrow("category_id")),
                name = c.getString(c.getColumnIndexOrThrow("name")),
                weight = c.getDouble(c.getColumnIndexOrThrow("weight")),
                position = c.getInt(c.getColumnIndexOrThrow("position"))
            )
        }
        return out
    }

    fun saveRubricCriterion(criterion: RubricCriterion): Long {
        val values = ContentValues().apply {
            put("category_id", criterion.categoryId)
            put("name", criterion.name.trim())
            put("weight", criterion.weight)
            put("position", criterion.position)
        }
        return if (criterion.id == 0L) writableDatabase.insertOrThrow("rubric_criteria", null, values)
        else {
            writableDatabase.update("rubric_criteria", values, "id=?", arrayOf(criterion.id.toString()))
            criterion.id
        }
    }

    fun deleteRubricCriterion(criterionId: Long) {
        writableDatabase.delete("rubric_criteria", "id=?", arrayOf(criterionId.toString()))
    }

    fun rubricWeightTotal(categoryId: Long): Double = getRubricCriteria(categoryId).sumOf { it.weight }

    fun hasGradeRecord(studentId: Long, categoryId: Long): Boolean {
        readableDatabase.query(
            "grades",
            arrayOf("id"),
            "student_id=? AND category_id=?",
            arrayOf(studentId.toString(), categoryId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor -> return cursor.moveToFirst() }
    }

    fun getGrade(studentId: Long, categoryId: Long): Double {
        readableDatabase.query("grades", arrayOf("score"), "student_id=? AND category_id=?", arrayOf(studentId.toString(), categoryId.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.getDouble(0) else 0.0
        }
    }

    fun setGrade(periodId: Long, studentId: Long, categoryId: Long, score: Double) {
        val values = ContentValues().apply {
            put("period_id", periodId)
            put("student_id", studentId)
            put("category_id", categoryId)
            put("score", score.coerceIn(0.0, 100.0))
        }
        val updated = writableDatabase.update(
            "grades",
            values,
            "student_id=? AND category_id=?",
            arrayOf(studentId.toString(), categoryId.toString())
        )
        if (updated == 0) writableDatabase.insertOrThrow("grades", null, values)
    }

    fun getRubricMarkOrNull(studentId: Long, criterionId: Long): Double? {
        readableDatabase.query("rubric_marks", arrayOf("score"), "student_id=? AND criterion_id=?", arrayOf(studentId.toString(), criterionId.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.getDouble(0) else null
        }
    }

    fun getRubricMark(studentId: Long, criterionId: Long): Double =
        getRubricMarkOrNull(studentId, criterionId) ?: 0.0

    fun setRubricMark(studentId: Long, criterionId: Long, score: Double?) {
        if (score == null) {
            writableDatabase.delete("rubric_marks", "student_id=? AND criterion_id=?", arrayOf(studentId.toString(), criterionId.toString()))
            return
        }
        val values = ContentValues().apply {
            put("student_id", studentId)
            put("criterion_id", criterionId)
            put("score", score.coerceIn(0.0, 100.0))
        }
        writableDatabase.insertWithOnConflict("rubric_marks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun calculateAndStoreRubricGrade(periodId: Long, studentId: Long, categoryId: Long): Double {
        val criteria = getRubricCriteria(categoryId)
        if (criteria.isEmpty()) return getGrade(studentId, categoryId)
        val marks = criteria.mapNotNull { criterion ->
            getRubricMarkOrNull(studentId, criterion.id)?.let { score -> criterion to score }
        }
        if (marks.isEmpty()) {
            writableDatabase.delete(
                "grades",
                "period_id=? AND student_id=? AND category_id=?",
                arrayOf(periodId.toString(), studentId.toString(), categoryId.toString())
            )
            return 0.0
        }
        val registeredWeight = marks.sumOf { it.first.weight }
        val total = if (registeredWeight <= 0.0) 0.0
        else marks.sumOf { it.second * it.first.weight } / registeredWeight
        setGrade(periodId, studentId, categoryId, total)
        return total
    }

    fun effectiveEvaluationMode(category: EvaluationCategory): EvaluationMode {
        if (isAttendanceName(category.name)) return EvaluationMode.ATTENDANCE
        return runCatching { EvaluationMode.valueOf(category.mode) }.getOrDefault(EvaluationMode.DIRECT)
    }

    private fun isAttendanceName(name: String): Boolean {
        val normalized = name.trim().lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
        return normalized == "asistencia" || normalized == "asistencias"
    }

    fun categoryScoreOrNull(periodId: Long, studentId: Long, category: EvaluationCategory): Double? {
        return when (effectiveEvaluationMode(category)) {
            EvaluationMode.ATTENDANCE -> if (hasAttendanceRecords(periodId, studentId)) attendancePercentage(periodId, studentId) else null
            EvaluationMode.AVERAGE -> {
                val scores = getAssessmentItems(category.id).mapNotNull { getAssessmentScore(studentId, it.id) }
                scores.takeIf { it.isNotEmpty() }?.average()
            }
            else -> if (hasGradeRecord(studentId, category.id)) getGrade(studentId, category.id) else null
        }
    }

    fun categoryScore(periodId: Long, studentId: Long, category: EvaluationCategory): Double =
        categoryScoreOrNull(periodId, studentId, category) ?: 0.0

    fun finalPercentage(periodId: Long, studentId: Long): Double {
        var weightedPoints = 0.0
        var evaluatedWeight = 0.0
        getCategories(periodId).forEach { category ->
            val score = categoryScoreOrNull(periodId, studentId, category) ?: return@forEach
            if (category.weight <= 0.0) return@forEach
            weightedPoints += score * category.weight
            evaluatedWeight += category.weight
        }
        return if (evaluatedWeight <= 0.0) 0.0 else (weightedPoints / evaluatedWeight).coerceIn(0.0, 100.0)
    }

    fun getEvents(periodId: Long): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        readableDatabase.query("events", null, "period_id=?", arrayOf(periodId.toString()), null, null, "date ASC").use { c ->
            while (c.moveToNext()) out += CalendarEvent(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                periodId = c.getLong(c.getColumnIndexOrThrow("period_id")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                date = c.getString(c.getColumnIndexOrThrow("date")),
                notes = c.getString(c.getColumnIndexOrThrow("notes")),
                type = c.getString(c.getColumnIndexOrThrow("type"))
            )
        }
        return out
    }

    fun saveEvent(event: CalendarEvent): Long {
        val values = ContentValues().apply {
            put("period_id", event.periodId)
            put("title", event.title.trim())
            put("date", event.date.trim())
            put("notes", event.notes.trim())
            put("type", event.type)
        }
        return if (event.id == 0L) writableDatabase.insertOrThrow("events", null, values)
        else {
            writableDatabase.update("events", values, "id=?", arrayOf(event.id.toString()))
            event.id
        }
    }

    fun deleteEvent(eventId: Long) {
        writableDatabase.delete("events", "id=?", arrayOf(eventId.toString()))
    }

    fun eventsOn(date: String): List<Pair<AcademicPeriod, CalendarEvent>> {
        val out = mutableListOf<Pair<AcademicPeriod, CalendarEvent>>()
        getOpenGroups().forEach { group ->
            readableDatabase.query("events", null, "period_id=? AND date=?", arrayOf(group.id.toString(), date), null, null, "id").use { c ->
                while (c.moveToNext()) {
                    out += group to CalendarEvent(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        periodId = c.getLong(c.getColumnIndexOrThrow("period_id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        date = c.getString(c.getColumnIndexOrThrow("date")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes")),
                        type = c.getString(c.getColumnIndexOrThrow("type"))
                    )
                }
            }
        }
        return out
    }

    fun birthdaysOn(date: LocalDate): List<Pair<AcademicPeriod, Student>> {
        val out = mutableListOf<Pair<AcademicPeriod, Student>>()
        getOpenGroups().forEach { group ->
            val start = runCatching { LocalDate.parse(group.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(group.endDate) }.getOrNull()
            val inRange = (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end))
            if (inRange) {
                getStudents(group.id).forEach { student ->
                    val born = runCatching { LocalDate.parse(student.birthDate) }.getOrNull()
                    if (born != null && born.monthValue == date.monthValue && born.dayOfMonth == date.dayOfMonth) {
                        out += group to student
                    }
                }
            }
        }
        return out
    }

    fun birthdaysForPeriod(periodId: Long): List<CalendarEvent> {
        val group = getPeriods().firstOrNull { it.id == periodId }
        val start = runCatching { LocalDate.parse(group?.startDate) }.getOrNull()
        val end = runCatching { LocalDate.parse(group?.endDate) }.getOrNull()
        val startYear = start?.year ?: LocalDate.now().year
        val endYear = end?.year ?: startYear
        val out = mutableListOf<CalendarEvent>()

        getStudents(periodId).forEach { student ->
            val born = runCatching { LocalDate.parse(student.birthDate) }.getOrNull() ?: return@forEach
            for (year in startYear..endYear) {
                val birthday = runCatching { born.withYear(year) }.getOrNull() ?: continue
                val inRange = (start == null || !birthday.isBefore(start)) && (end == null || !birthday.isAfter(end))
                if (inRange) {
                    out += CalendarEvent(
                        id = -(student.id * 10000 + year),
                        periodId = periodId,
                        title = "🎂 ${student.name}",
                        date = birthday.toString(),
                        notes = "Cumpleaños",
                        type = "CUMPLEAÑOS"
                    )
                }
            }
        }
        return out
    }

    fun summaries(periodId: Long): List<StudentSummary> = getStudents(periodId).map { student ->
        StudentSummary(student, attendancePercentage(periodId, student.id), finalPercentage(periodId, student.id))
    }

    private fun android.database.Cursor.toPeriod() = AcademicPeriod(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        type = getString(getColumnIndexOrThrow("type")),
        startDate = getString(getColumnIndexOrThrow("start_date")),
        endDate = getString(getColumnIndexOrThrow("end_date")),
        active = getInt(getColumnIndexOrThrow("active")) == 1,
        archived = getInt(getColumnIndexOrThrow("archived")) == 1
    )

    private fun android.database.Cursor.toStudent() = Student(
        id = getLong(getColumnIndexOrThrow("id")),
        periodId = getLong(getColumnIndexOrThrow("period_id")),
        name = getString(getColumnIndexOrThrow("name")),
        studentCode = getString(getColumnIndexOrThrow("student_code")),
        email = getString(getColumnIndexOrThrow("email")),
        phone = getString(getColumnIndexOrThrow("phone")),
        birthDate = getString(getColumnIndexOrThrow("birth_date")),
        groupName = getString(getColumnIndexOrThrow("group_name")),
        clinic = getString(getColumnIndexOrThrow("clinic")),
        teamName = getString(getColumnIndexOrThrow("team_name")),
        notes = getString(getColumnIndexOrThrow("notes"))
    )

    private fun android.database.Cursor.toAttendanceSession() = AttendanceSession(
        id = getLong(getColumnIndexOrThrow("id")),
        periodId = getLong(getColumnIndexOrThrow("period_id")),
        date = getString(getColumnIndexOrThrow("date")),
        title = getString(getColumnIndexOrThrow("title")),
        worked = getInt(getColumnIndexOrThrow("worked")) == 1
    )

    private fun android.database.Cursor.toCategory() = EvaluationCategory(
        id = getLong(getColumnIndexOrThrow("id")),
        periodId = getLong(getColumnIndexOrThrow("period_id")),
        name = getString(getColumnIndexOrThrow("name")),
        weight = getDouble(getColumnIndexOrThrow("weight")),
        position = getInt(getColumnIndexOrThrow("position")),
        mode = getString(getColumnIndexOrThrow("mode"))
    )

    companion object {
        private const val DB_NAME = "profecuaderno.db"
        private const val DB_VERSION = 2
    }
}
