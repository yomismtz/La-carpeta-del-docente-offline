package com.profecuaderno.app.ui

data class EventTypeOption(val code: String, val label: String)

val teacherEventTypes = listOf(
    EventTypeOption("TEMA_CLASE", "Tema / clase"),
    EventTypeOption("PRACTICA", "Práctica"),
    EventTypeOption("LABORATORIO", "Laboratorio"),
    EventTypeOption("EXAMEN", "Examen"),
    EventTypeOption("EVALUACION_PARCIAL", "Evaluación parcial"),
    EventTypeOption("EVALUACION_MODULAR", "Evaluación de proyecto / curso"),
    EventTypeOption("EXPOSICION", "Exposición"),
    EventTypeOption("EXPOSICION_MODULAR", "Exposición de proyecto / investigación"),
    EventTypeOption("INVESTIGACION_MODULAR", "Investigación / proyecto"),
    EventTypeOption("MAQUETA", "Proyecto / maqueta"),
    EventTypeOption("ENTREGA", "Entrega"),
    EventTypeOption("DOCENTE_INVITADO", "Docente / invitado especial"),
    EventTypeOption("VISITA", "Visita"),
    EventTypeOption("SALIDA", "Salida / actividad externa"),
    EventTypeOption("IMPORTANTE", "Fecha importante"),
    EventTypeOption("OTRO", "Otro")
)

fun eventTypeLabel(code: String): String =
    teacherEventTypes.firstOrNull { it.code == code }?.label ?: code.replace('_', ' ').lowercase()
