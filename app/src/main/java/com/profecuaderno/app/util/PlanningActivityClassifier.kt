package com.profecuaderno.app.util

import java.util.Locale

object PlanningActivityClassifier {
    fun classify(text: String): String {
        val s = normalize(text)
        fun hasAny(vararg terms: String) = terms.any { it in s }

        return when {
            hasAny("laboratorio", "lab ", "lab.", "sesion de lab", "practica de laboratorio", "practica laboratorio") -> "LABORATORIO"
            hasAny("examen final", "examen parcial", "examen ordinario", "examen extraordinario", "examen", "prueba escrita", "prueba oral", "quiz", "cuestionario evaluativo") -> "EXAMEN"
            hasAny("evaluacion modular", "evaluacion del proyecto", "evaluacion de proyecto", "evaluacion final del curso", "evaluacion del curso") -> "EVALUACION_MODULAR"
            hasAny("evaluacion parcial", "parcial", "evaluacion", "evaluar") -> "EVALUACION_PARCIAL"
            hasAny("practica clinica", "practica preclinica", "practica", "taller practico", "ejercicio practico") -> "PRACTICA"
            hasAny("exposicion modular", "presentacion de investigacion", "presentacion del proyecto", "exposicion de proyecto") -> "EXPOSICION_MODULAR"
            hasAny("exposicion", "presentacion oral", "presentacion") -> "EXPOSICION"
            hasAny("investigacion modular", "proyecto de investigacion", "trabajo de investigacion", "investigacion", "proyecto final") -> "INVESTIGACION_MODULAR"
            hasAny("maqueta", "modelo didactico", "prototipo") -> "MAQUETA"
            hasAny("entrega", "fecha limite", "fecha de entrega", "tarea", "evidencia", "portafolio") -> "ENTREGA"
            hasAny("docente invitado", "profesor invitado", "maestro invitado", "ponente", "conferencista", "especialista invitado") -> "DOCENTE_INVITADO"
            hasAny("visita", "recorrido") -> "VISITA"
            hasAny("salida", "excursion", "actividad externa", "practica de campo", "trabajo de campo") -> "SALIDA"
            else -> "TEMA_CLASE"
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
}
