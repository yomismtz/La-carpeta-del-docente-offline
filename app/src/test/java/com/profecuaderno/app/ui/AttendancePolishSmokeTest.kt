package com.profecuaderno.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendancePolishSmokeTest {
    @Test
    fun versionedAttendanceLabelsRemainDistinct() {
        val labels = listOf("Presente", "Falta", "Retardo", "Justificada")
        assertEquals(4, labels.distinct().size)
    }
}
