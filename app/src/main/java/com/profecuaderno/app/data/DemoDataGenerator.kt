package com.profecuaderno.app.data

import java.time.LocalDate

object DemoDataGenerator {
    fun create(db: TeacherDbHelper): Long {
        val today = LocalDate.now()
        val periodId = db.createPeriod(
            name = "Grupo DEMO · 2B",
            type = "Semestre",
            startDate = today.minusDays(35).toString(),
            endDate = today.plusDays(70).toString(),
            copyFromPeriodId = null,
            makeActive = true
        )

        val students = (1..25).map { index ->
            val id = db.saveStudent(
                Student(
                    periodId = periodId,
                    name = "Estudiante Demo %02d".format(index),
                    studentCode = "DEMO-%03d".format(index),
                    groupName = "2B",
                    notes = "Dato ficticio del modo demostración"
                )
            )
            db.getStudents(periodId).first { it.id == id }
        }

        val activityCategoryId = db.saveCategory(
            EvaluationCategory(0, periodId, "Actividades", 40.0, 0, EvaluationMode.DIRECT.name)
        )
        WeightedEvaluationStore.setKind(db, activityCategoryId, WeightedEvaluationKind.ACTIVITIES)
        val activityItems = listOf(
            "Actividad 1 · Diagnóstico" to 20.0,
            "Actividad 2 · Mapa conceptual" to 20.0,
            "Actividad 3 · Caso práctico" to 30.0,
            "Proyecto" to 30.0
        ).mapIndexed { index, pair ->
            val itemId = db.saveAssessmentItem(AssessmentItem(0, activityCategoryId, pair.first, index))
            WeightedEvaluationStore.setWeight(db, itemId, pair.second)
            itemId
        }

        val examCategoryId = db.saveCategory(
            EvaluationCategory(0, periodId, "Exámenes", 40.0, 1, EvaluationMode.DIRECT.name)
        )
        WeightedEvaluationStore.setKind(db, examCategoryId, WeightedEvaluationKind.EXAMS)
        val examItems = listOf("Examen parcial" to 40.0, "Examen final" to 60.0).mapIndexed { index, pair ->
            val itemId = db.saveAssessmentItem(AssessmentItem(0, examCategoryId, pair.first, index))
            WeightedEvaluationStore.setWeight(db, itemId, pair.second)
            itemId
        }

        db.saveCategory(
            EvaluationCategory(0, periodId, "Asistencia", 20.0, 2, EvaluationMode.ATTENDANCE.name)
        )

        students.forEachIndexed { studentIndex, student ->
            activityItems.forEachIndexed { itemIndex, itemId ->
                val leavePending = studentIndex in 0..2 && itemIndex == activityItems.lastIndex
                if (!leavePending) {
                    val score = (72 + ((studentIndex * 7 + itemIndex * 5) % 27)).toDouble()
                    db.setAssessmentScore(student.id, itemId, score)
                }
            }
            examItems.forEachIndexed { itemIndex, itemId ->
                val leavePending = studentIndex == 3 && itemIndex == examItems.lastIndex
                if (!leavePending) {
                    val score = (68 + ((studentIndex * 5 + itemIndex * 9) % 31)).toDouble()
                    db.setAssessmentScore(student.id, itemId, score)
                }
            }
            WeightedEvaluationStore.recalculateStudent(db, periodId, student.id, activityCategoryId)
            WeightedEvaluationStore.recalculateStudent(db, periodId, student.id, examCategoryId)
        }

        (0..7).forEach { offset ->
            val date = today.minusDays((offset * 4).toLong()).toString()
            val sessionId = db.createOrUpdateAttendanceSession(periodId, date, "Clase ${offset + 1}", true)
            students.forEachIndexed { index, student ->
                val status = when {
                    index == 4 && offset == 2 -> AttendanceStatus.JUSTIFIED
                    (index + offset) % 17 == 0 -> AttendanceStatus.ABSENT
                    (index + offset) % 11 == 0 -> AttendanceStatus.LATE
                    else -> AttendanceStatus.PRESENT
                }
                db.setAttendanceStatus(sessionId, student.id, status)
            }
        }

        db.saveEvent(CalendarEvent(0, periodId, "Entrega de proyecto DEMO", today.plusDays(7).toString(), "Evento ficticio para probar calendario", "ENTREGA"))
        db.saveEvent(CalendarEvent(0, periodId, "Examen final DEMO", today.plusDays(21).toString(), "Evento ficticio", "EXAMEN"))

        AttendancePolicyStore.setJustifiedCounts(db, periodId, true)
        EvaluationSetupStore.finalize(db, periodId)
        SyncMetadataStore.ensure(db)
        return periodId
    }
}
