package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TesterUpdateNotice(version: String = "1.9.1") {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tester_update_notice", 0) }
    var visible by remember(version) { mutableStateOf(prefs.getString("seen_version", "") != version) }

    if (!visible) return

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Novedades para probar · $version") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Esta actualización conserva los datos existentes.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("• Asistencia: Pasar asistencia y Verificar asistencias separados.")
                Text("• Contadores de presentes, faltas, retardos, justificadas y pendientes.")
                Text("• Completar solo pendientes como presentes, sin sobrescribir incidencias.")
                Text("• Historial por fecha editable desde Asistencia y desde Reportes.")
                Text("• Reporte individual con días exactos de falta, retardo y justificada.")
                Text("• Reglas configurables de retardos y justificadas.")
                Text("• Calendario mensual con símbolos por actividad.")
                Text("• Mejor adaptación a tamaños de letra grandes.")
                Text("• La apariencia elegida se conserva al volver a abrir la app.")
                Spacer(Modifier.height(8.dp))
                Text("Si notas un texto cortado o una pantalla incómoda, repórtalo indicando el modelo del teléfono y el tamaño de letra configurado.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putString("seen_version", version).apply()
                visible = false
            }) { Text("Entendido") }
        }
    )
}
