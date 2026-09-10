package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class IntroStep(
    val title: String,
    val text: String,
    val example: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onStart: () -> Unit
) {
    val steps = listOf(
        IntroStep(
            "1. Crea tu perfil y tus grupos",
            "Registra tus datos docentes y crea uno o varios grupos, materias, cursos, talleres o módulos con el periodo que utilices.",
            "Funciona para preescolar, primaria, secundaria, telesecundaria, bachillerato, escuelas técnicas, universidad, maestría y doctorado.",
            Icons.Default.Folder
        ),
        IntroStep(
            "2. Agrega estudiantes y pasa asistencia",
            "En cada grupo guarda estudiantes, matrícula o identificador, correo, teléfono y fecha de nacimiento. La asistencia se calcula sobre los días realmente trabajados.",
            "Ejemplo: presente, falta, retardo o justificada. Los cumpleaños aparecen automáticamente en el calendario.",
            Icons.Default.FactCheck
        ),
        IntroStep(
            "3. Organiza la evaluación hasta 100%",
            "Crea rubros como exámenes, actividades, prácticas, proyectos, tareas, asistencia, investigación, exposiciones o los que necesites. Tú decides el porcentaje de cada uno y el total debe ser 100%.",
            "Ejemplo: Exámenes 30% + Actividades 20% + Proyecto 30% + Asistencia 20% = 100%.",
            Icons.Default.Assessment
        ),
        IntroStep(
            "4. Elige cómo evaluar cada rubro",
            "Puedes usar promedio de actividades, promedio de exámenes, rúbrica, reporte de asistencia o una calificación directa.",
            "Si asignas porcentaje a Reporte de asistencia, la app toma automáticamente el porcentaje real calculado en la carpeta Asistencia y lo aplica a la calificación final.",
            Icons.Default.Checklist
        ),
        IntroStep(
            "5. Personaliza actividades, exámenes y rúbricas",
            "Pon nombre y porcentaje a cada actividad o examen. En rúbricas puedes usar una plantilla existente o crear tus propios criterios y porcentajes.",
            "Ejemplo: Examen 1: Oclusión 10% · Examen 2: Diagnóstico 30% · Examen final 60%.",
            Icons.Default.EditNote
        ),
        IntroStep(
            "6. Planea desde la guía y el calendario",
            "Guarda la guía o planeación del grupo y registra fechas de temas, prácticas, laboratorios, exámenes, evaluaciones, exposiciones, entregas, visitas y actividades externas.",
            "La misma fecha puede aparecer en el calendario general para mantener todo organizado.",
            Icons.Default.CalendarMonth
        ),
        IntroStep(
            "7. Activa recordatorios y revisa reportes",
            "Cuando quieras, activa las notificaciones para recibir avisos de cumpleaños y actividades programadas. En Reportes podrás revisar asistencia y calificación final.",
            "Los reportes se adaptan al grupo y al esquema de evaluación que hayas configurado.",
            Icons.Default.NotificationsActive
        )
    )

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Bienvenido a El Cuaderno del Maestro",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tu cuaderno docente digital para organizar grupos, estudiantes, asistencia, evaluación, planeación, calendario y reportes en cualquier nivel educativo.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("¿Para qué sirve?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("La idea es concentrar en una sola app lo que normalmente tienes repartido entre listas, hojas de cálculo, rúbricas, calendarios y documentos, sin obligarte a trabajar con un solo modelo escolar.")
                    }
                }
            }

            steps.forEach { step ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                step.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(step.text)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    step.example,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Importante", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Las notificaciones se solicitan después, cuando actives los recordatorios. Puedes usar El Cuaderno del Maestro aunque no les des permiso.")
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Comenzar a usar El Cuaderno del Maestro")
            }
        }
    }
}
