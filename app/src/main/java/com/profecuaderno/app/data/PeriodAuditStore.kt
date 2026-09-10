package com.profecuaderno.app.data

import kotlin.math.abs

data class PeriodAuditSummary(
    val studentCount: Int,
    val activityCount: Int,
    val examCount: Int,
    val attendanceWeight: Double,
    val evaluationWeightTotal: Double,
    val studentsWithPending: Int,
    val pendingCells: Int,
    val readyToClose: Boolean
)

object PeriodAuditStore {
    fun summary(db: TeacherDbHelper, periodId: Long): PeriodAuditSummary {
        val students = db.getStudents(periodId)
        val categories = db.getCategories(periodId)

        var activities = 0
        var exams = 0
        var attendanceWeight = 0.0
        var pendingCells = 0
        var studentsWithPending = 0

        categories.forEach { category ->
            when (WeightedEvaluationStore.kindFor(db, category)) {
                WeightedEvaluationKind.ACTIVITIES -> activities += db.getAssessmentItems(category.id).size
                WeightedEvaluationKind.EXAMS -> exams += db.getAssessmentItems(category.id).size
                null -> Unit
            }
            if (db.effectiveEvaluationMode(category) == EvaluationMode.ATTENDANCE) {
                attendanceWeight += category.weight
            }
        }

        students.forEach { student ->
            var studentPending = false
            categories.forEach { category ->
                val weighted = WeightedEvaluationStore.kindFor(db, category)
                val mode = db.effectiveEvaluationMode(category)
                when {
                    weighted != null -> {
                        db.getAssessmentItems(category.id).forEach { item ->
                            if (db.getAssessmentScore(student.id, item.id) == null) {
                                pendingCells++
                                studentPending = true
                            }
                        }
                    }
                    mode == EvaluationMode.RUBRIC -> {
                        val criteria = db.getRubricCriteria(category.id)
                        if (criteria.isEmpty()) {
                            pendingCells++
                            studentPending = true
                        } else {
                            criteria.forEach { criterion ->
                                if (db.getRubricMarkOrNull(student.id, criterion.id) == null) {
                                    pendingCells++
                                    studentPending = true
                                }
                            }
                        }
                    }
                    mode == EvaluationMode.ATTENDANCE -> Unit
                    !db.hasGradeRecord(student.id, category.id) -> {
                        pendingCells++
                        studentPending = true
                    }
                }
            }
            if (studentPending) studentsWithPending++
        }

        val total = categories.sumOf { it.weight }
        val configured = categories.all { category ->
            val weighted = WeightedEvaluationStore.kindFor(db, category)
            when {
                weighted != null -> WeightedEvaluationStore.isConfigured(db, category.id)
                db.effectiveEvaluationMode(category) == EvaluationMode.RUBRIC -> {
                    val criteria = db.getRubricCriteria(category.id)
                    criteria.isNotEmpty() && abs(criteria.sumOf { it.weight } - 100.0) < 0.001
                }
                else -> true
            }
        }

        return PeriodAuditSummary(
            studentCount = students.size,
            activityCount = activities,
            examCount = exams,
            attendanceWeight = attendanceWeight,
            evaluationWeightTotal = total,
            studentsWithPending = studentsWithPending,
            pendingCells = pendingCells,
            readyToClose = abs(total - 100.0) < 0.001 && configured
        )
    }
}
