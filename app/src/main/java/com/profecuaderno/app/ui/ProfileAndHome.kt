package com.profecuaderno.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.Teacher
import com.profecuaderno.app.data.TeacherDbHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Lavender = Color(0xFFEDE4FB)
private val Mint = Color(0xFFDDF7F4)
private val Blush = Color(0xFFFCE3EE)
private val Sky = Color(0xFFE5F1FF)
private val Cream = Color(0xFFFFF4D9)
private val SoftPurple = Color(0xFFD8C6F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherSetupScreen(onSave: (Teacher) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var degree by remember { mutableStateOf("") }
    var teachingLevel by remember { mutableStateOf(loadTeachingLevel(context)) }
    var institution by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(Modifier.widthIn(max = 620.dp), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Mint, shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(38.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("El Cuaderno del Maestro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Planea · Organiza · Asiste · Evalúa")
                        }
                    }
                    HorizontalDivider()
                    Text("Configura tu perfil", style = MaterialTheme.typography.titleMedium)
                    Text("Diseñado para docentes de cualquier nivel educativo.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre del docente *") }, modifier = Modifier.fillMaxWidth())
                    DegreeSelector(degree, { degree = it }, "Nivel de estudios *")
                    DatePickerField(birth, { birth = it }, "Fecha de nacimiento")
                    TeachingLevelSelector(teachingLevel, { teachingLevel = it })
                    OutlinedTextField(institution, { institution = it }, label = { Text("Nombre de la escuela") }, modifier = Modifier.fillMaxWidth())
                    TeacherPhotoPicker()
                    Button(
                        onClick = {
                            saveTeachingLevel(context, teachingLevel)
                            onSave(Teacher(name = name, birthDate = birth, degree = degree, institution = institution, email = ""))
                        },
                        enabled = name.isNotBlank() && degree.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Continuar") }
                    Text(
                        "Después podrás cambiar estos datos desde Mi perfil.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(teacher: Teacher, onSecurity: () -> Unit, onSave: (Teacher) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(teacher.name) }
    var birth by remember { mutableStateOf(teacher.birthDate) }
    var degree by remember { mutableStateOf(teacher.degree) }
    var teachingLevel by remember { mutableStateOf(loadTeachingLevel(context)) }
    var institution by remember { mutableStateOf(teacher.institution) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mi perfil docente", style = MaterialTheme.typography.titleLarge)
        Text("Estos datos se muestran en tu cuaderno y pueden editarse cuando lo necesites.")
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del docente") }, modifier = Modifier.fillMaxWidth())
        DegreeSelector(degree, { degree = it }, "Nivel de estudios")
        DatePickerField(birth, { birth = it }, "Fecha de nacimiento")
        TeachingLevelSelector(teachingLevel, { teachingLevel = it })
        OutlinedTextField(institution, { institution = it }, label = { Text("Nombre de la escuela") }, modifier = Modifier.fillMaxWidth())
        TeacherPhotoPicker()
        NotificationPermissionCard()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    saveTeachingLevel(context, teachingLevel)
                    onSave(teacher.copy(name = name, birthDate = birth, degree = degree, institution = institution))
                },
                enabled = name.isNotBlank() && degree.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Guardar cambios") }
            OutlinedButton(onClick = onSecurity, shape = RoundedCornerShape(16.dp)) { Text("Seguridad y respaldo") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DegreeSelector(value: String, onValueChange: (String) -> Unit, label: String) {
    val options = listOf("Secundaria", "Bachillerato / Preparatoria", "Carrera técnica", "Licenciatura", "Ingeniería", "Especialidad", "Maestría", "Doctorado", "Postdoctorado", "Otro")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false }) }
        }
    }
}

private data class FolderAction(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val action: () -> Unit)

@Composable
fun HomeScreen(teacher: Teacher, period: AcademicPeriod?, onPrograms: () -> Unit, onCalendar: () -> Unit, onProfile: () -> Unit, db: TeacherDbHelper, refresh: Int) {
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp < 360 -> 1
        configuration.screenWidthDp < 700 -> 2
        configuration.screenWidthDp < 1000 -> 3
        else -> 4
    }
    val todayDate = LocalDate.now()
    val today = todayDate.toString()
    val todaySession = remember(refresh, period?.id) { period?.let { db.getAttendanceSession(it.id, today) } }
    val todayEvents = remember(refresh) { db.eventsOn(today) }
    val todayBirthdays = remember(refresh) { db.birthdaysOn(todayDate) }
    val groupsToday = remember(refresh) { db.getOpenGroups().filter { group ->
        val start = runCatching { LocalDate.parse(group.startDate) }.getOrNull(); val end = runCatching { LocalDate.parse(group.endDate) }.getOrNull()
        (start == null || !todayDate.isBefore(start)) && (end == null || !todayDate.isAfter(end))
    } }
    val attendancePending = remember(refresh, groupsToday.map { it.id }, todayEvents) {
        val attendanceRelevantTypes = setOf("TEMA_CLASE", "PRACTICA", "LABORATORIO", "EXAMEN", "EVALUACION_PARCIAL", "EVALUACION_MODULAR", "EXPOSICION", "EXPOSICION_MODULAR", "INVESTIGACION_MODULAR")
        val groupsWithActivityToday = todayEvents.filter { (_, event) -> event.type in attendanceRelevantTypes }.map { (group, _) -> group.id }.toSet()
        groupsToday.filter { group -> group.id in groupsWithActivityToday && db.getAttendanceSession(group.id, today) == null }
    }
    val pendingEvaluations = remember(refresh) { db.getOpenGroups().sumOf { group ->
        val categories = db.getCategories(group.id).filter { category -> runCatching { com.profecuaderno.app.data.EvaluationMode.valueOf(category.mode) }.getOrDefault(com.profecuaderno.app.data.EvaluationMode.DIRECT) != com.profecuaderno.app.data.EvaluationMode.ATTENDANCE }
        val students = db.getStudents(group.id); students.sumOf { student -> categories.count { category -> !db.hasGradeRecord(student.id, category.id) } }
    } }
    val actions = listOf(
        FolderAction("Grupos", if (period == null) "Crea tu primer grupo" else "Grupo actual: ${period.name}", Icons.Default.Groups, Mint, onPrograms),
        FolderAction("Calendario", if (period == null) "Agenda y fechas importantes" else if (todaySession == null) "Fechas, entregas y cumpleaños" else "Asistencia de hoy registrada", Icons.Default.CalendarMonth, Lavender, onCalendar),
        FolderAction("Mi perfil", "Datos y configuración docente", Icons.Default.Person, Blush, onProfile)
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Mint, shape = RoundedCornerShape(22.dp)) { Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(42.dp)) }
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                    Text("¡Hola, Profe!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("El Cuaderno del Maestro", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Planea · Organiza · Asiste · Evalúa", style = MaterialTheme.typography.bodySmall)
                    if (teacher.institution.isNotBlank()) Text(teacher.institution, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(todayDate.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "MX"))).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (todayEvents.isEmpty() && todayBirthdays.isEmpty() && attendancePending.isEmpty() && pendingEvaluations == 0) Text("No hay actividades ni avisos pendientes para hoy.", style = MaterialTheme.typography.bodySmall) else {
                todayEvents.take(3).forEach { (group, event) -> Text("• ${eventTypeLabel(event.type)}: ${event.title} · ${group.name}", style = MaterialTheme.typography.bodySmall) }
                todayBirthdays.take(3).forEach { (group, student) -> Text("• 🎂 Cumpleaños de ${student.name} · ${group.name}", style = MaterialTheme.typography.bodySmall) }
                attendancePending.take(3).forEach { group -> Text("• Falta pasar asistencia · ${group.name}", style = MaterialTheme.typography.bodySmall) }
                if (pendingEvaluations > 0) Text("• $pendingEvaluations evaluaciones pendientes de captura", style = MaterialTheme.typography.bodySmall)
            }
        } }
        LazyVerticalGrid(columns = GridCells.Fixed(columns), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(actions) { item -> ElevatedCard(onClick = item.action, modifier = Modifier.height(138.dp), shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxSize().background(item.tint).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)); Column { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(item.subtitle, style = MaterialTheme.typography.bodySmall) }
            } } }
        }
    }
}

@Composable
fun ProgramHomeScreen(period: AcademicPeriod, onStudents: () -> Unit, onTeams: () -> Unit, onAttendance: () -> Unit, onEvaluation: () -> Unit, onRubrics: () -> Unit, onGuide: () -> Unit, onFiles: () -> Unit, onNotes: () -> Unit, onReports: () -> Unit) {
    val configuration = LocalConfiguration.current; val columns = when { configuration.screenWidthDp < 360 -> 1; configuration.screenWidthDp < 700 -> 2; configuration.screenWidthDp < 1000 -> 3; else -> 4 }
    val folders = listOf(
        FolderAction("Estudiantes", "Lista, datos y cumpleaños", Icons.Default.Groups, Mint, onStudents), FolderAction("Creación de equipos", "Rifa al azar o selección manual", Icons.Default.Groups, Sky, onTeams), FolderAction("Asistencia", "Pase de lista y porcentaje actual", Icons.Default.FactCheck, Lavender, onAttendance),
        FolderAction("Calificaciones", "Captura y cálculo automático", Icons.Default.Assessment, Blush, onEvaluation), FolderAction("Rúbricas", "Criterios, rubros y porcentajes", Icons.Default.Checklist, Cream, onRubrics),
        FolderAction("Planeación", "Guía, documentos y apoyo docente", Icons.Default.Assignment, Sky, onGuide), FolderAction("Archivos del grupo", "PDF, CSV, Excel, Word y TXT", Icons.Default.MenuBook, Mint, onFiles), FolderAction("Bloc de notas", "Teclado, dedo, stylus o pen", Icons.Default.Assignment, Cream, onNotes), FolderAction("Reportes", "Asistencia y calificación en PDF", Icons.Default.MenuBook, SoftPurple, onReports)
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(26.dp)) { Column(Modifier.padding(18.dp)) {
            Text(period.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Tu grupo en un solo lugar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text("${period.type} · ${period.startDate.ifBlank { "Sin fecha de inicio" }} → ${period.endDate.ifBlank { "Sin fecha de término" }}", style = MaterialTheme.typography.bodySmall)
        } }
        LazyVerticalGrid(columns = GridCells.Fixed(columns), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(folders) { item -> ElevatedCard(onClick = item.action, modifier = Modifier.height(150.dp), shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxSize().background(item.tint).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.Start) {
                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)); Column { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(item.subtitle, style = MaterialTheme.typography.bodySmall) }
            } } }
        }
        Text("Todo tu grupo en un solo lugar ♥", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
