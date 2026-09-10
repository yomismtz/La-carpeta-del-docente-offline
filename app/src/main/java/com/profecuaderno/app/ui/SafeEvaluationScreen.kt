package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.EvaluationSetupStore
import com.profecuaderno.app.data.TeacherDbHelper

@Composable
fun SafeEvaluationScreen(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit
) {
    var localTick by remember { mutableIntStateOf(0) }
    val tick = refresh + localTick
    val finalized = remember(tick, period.id) { EvaluationSetupStore.isFinalized(db, period.id) }
    val canFinalize = remember(tick, period.id) { EvaluationSetupStore.canFinalize(db, period.id) }

    if (finalized) {
        EvaluationScreen(db, period, tick, onChanged)
        return
    }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (canFinalize) Icons.Default.Verified else Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
                Text("Esquema de evaluación en borrador", style = MaterialTheme.typography.titleLarge)
                if (canFinalize) {
                    Text("Los rubros y porcentajes están completos. Puedes finalizar el esquema para comenzar a capturar calificaciones.")
                    Button(
                        onClick = {
                            if (EvaluationSetupStore.finalize(db, period.id)) {
                                localTick++
                                onChanged()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Finalizar esquema y comenzar a calificar") }
                } else {
                    Text("Puedes seguir guardando rubros como borrador, pero no se habilitará la captura hasta que:")
                    Text("• Los rubros finales sumen exactamente 100%.\n• Cada conjunto de actividades/exámenes sume 100%.\n• Cada rúbrica configurada sume 100%.")
                    Text("Regresa a Rubros y rúbricas para completar la configuración.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
