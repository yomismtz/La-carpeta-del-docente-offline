package com.profecuaderno.app.data

data class Teacher(
    val id: Long = 1,
    val name: String,
    val birthDate: String,
    val degree: String,
    val institution: String = "",
    val email: String = ""
)

data class AcademicPeriod(
    val id: Long,
    val name: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val active: Boolean,
    val archived: Boolean
)

data class Student(
    val id: Long = 0,
    val periodId: Long,
    val name: String,
    val studentCode: String = "",
    val email: String = "",
    val phone: String = "",
    val birthDate: String = "",
    val groupName: String = "",
    val clinic: String = "",
    val teamName: String = "",
    val notes: String = ""
)

enum class AttendanceStatus(val label: String, val factor: Double) {
    PRESENT("Asistencia", 1.0),
    ABSENT("Falta", 0.0),
    LATE("Retardo", 0.5),
    JUSTIFIED("Justificada", 1.0)
}

data class AttendanceSession(
    val id: Long,
    val periodId: Long,
    val date: String,
    val title: String,
    val worked: Boolean
)

enum class EvaluationMode(val label: String) {
    AVERAGE("Promedio de actividades / exámenes"),
    RUBRIC("Rúbrica"),
    ATTENDANCE("Reporte de asistencia"),
    DIRECT("Calificación directa")
}

data class EvaluationCategory(
    val id: Long,
    val periodId: Long,
    val name: String,
    val weight: Double,
    val position: Int,
    val mode: String = EvaluationMode.DIRECT.name
)

data class RubricCriterion(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val weight: Double,
    val position: Int
)

data class AssessmentItem(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val position: Int
)

data class CalendarEvent(
    val id: Long,
    val periodId: Long,
    val title: String,
    val date: String,
    val notes: String,
    val type: String
)

data class StudentSummary(
    val student: Student,
    val attendancePercent: Double,
    val finalPercent: Double
)
