package com.profecuaderno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.GradeHistoryStore
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore

private enum class Screen(val title: String) {
    HOME("Inicio"),
    PROGRAMS("Grupos"),
    PROGRAM_HOME("Grupo"),
    STUDENTS("Alumnos"),
    TEAMS("Creación de equipos"),
    ATTENDANCE("Asistencia"),
    EVALUATION("Evaluación"),
    QUICK_GRADE("Captura rápida"),
    RUBRICS("Rubros y rúbricas"),
    GUIDE("Guía / planeación"),
    GROUP_FILES("Archivos del grupo"),
    NOTES("Bloc de notas"),
    CALENDAR("Calendario"),
    REPORTS("Reportes"),
    PROFILE("Mi perfil docente"),
    APPEARANCE("Cambiar apariencia"),
    HELP("Ayuda"),
    SECURITY("Seguridad y respaldo"),
    TRASH("Papelera"),
    GRADE_HISTORY("Historial de calificaciones"),
    SEARCH("Buscar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfeCuadernoApp(
    db: TeacherDbHelper,
    onDataChanged: () -> Unit,
    globalRefresh: Int,
    currentTheme: AgendaThemeStyle,
    currentDarkMode: Boolean,
    currentFontScale: Float,
    currentFontStyle: AppFontStyle,
    onAppearanceChanged: (AgendaThemeStyle, Boolean, Float, AppFontStyle) -> Unit
) {
    remember(db) {
        TrashStore.ensure(db)
        GradeHistoryStore.ensure(db)
        true
    }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var localRefresh by remember { mutableIntStateOf(0) }
    val tick = globalRefresh + localRefresh
    val teacher = remember(tick) { db.getTeacher()!! }
    val period = remember(tick) { db.getActivePeriod() }

    val refreshAll = {
        localRefresh++
        onDataChanged()
    }

    fun goBack() {
        screen = when (screen) {
            Screen.PROGRAM_HOME -> Screen.PROGRAMS
            Screen.STUDENTS, Screen.TEAMS, Screen.ATTENDANCE, Screen.EVALUATION,
            Screen.RUBRICS, Screen.GUIDE, Screen.GROUP_FILES, Screen.NOTES, Screen.REPORTS -> Screen.PROGRAM_HOME
            Screen.QUICK_GRADE -> Screen.EVALUATION
            Screen.PROGRAMS, Screen.CALENDAR, Screen.PROFILE, Screen.HELP,
            Screen.TRASH, Screen.GRADE_HISTORY, Screen.SEARCH -> Screen.HOME
            Screen.APPEARANCE, Screen.SECURITY -> Screen.PROFILE
            Screen.HOME -> Screen.HOME
        }
    }

    fun openSearchResult(result: UniversalSearchResult) {
        if (result.groupId > 0L) {
            db.activatePeriod(result.groupId)
            refreshAll()
        }
        screen = when (result.type) {
            "GROUP" -> Screen.PROGRAM_HOME
            "STUDENT" -> Screen.STUDENTS
            "EVENT" -> Screen.CALENDAR
            "CATEGORY", "ASSESSMENT", "RUBRIC" -> Screen.RUBRICS
            else -> Screen.HOME
        }
    }

    BackHandler(enabled = screen != Screen.HOME) { goBack() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screen.title)
                        if (
                            screen != Screen.HOME && screen != Screen.PROFILE && screen != Screen.PROGRAMS &&
                            screen != Screen.APPEARANCE && screen != Screen.TRASH && screen != Screen.GRADE_HISTORY &&
                            screen != Screen.SEARCH && period != null
                        ) {
                            Text(period.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = { goBack() }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                        }
                    }
                },
                actions = {
                    if (screen == Screen.EVALUATION) {
                        IconButton(onClick = { screen = Screen.QUICK_GRADE }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Default.TableChart, contentDescription = "Captura rápida de calificaciones")
                        }
                    }
                    if (screen == Screen.PROFILE) {
                        IconButton(onClick = { screen = Screen.APPEARANCE }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = "Personalización")
                        }
                    }
                    if (screen != Screen.HELP) {
                        IconButton(onClick = { screen = Screen.HELP }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Ayuda")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    teacher = teacher,
                    period = period,
                    db = db,
                    refresh = tick,
                    onPrograms = { screen = Screen.PROGRAMS },
                    onCalendar = { screen = Screen.CALENDAR },
                    onProfile = { screen = Screen.PROFILE }
                )
                Screen.PROGRAMS -> PeriodsScreen(
                    db = db,
                    refresh = tick,
                    onChanged = refreshAll,
                    onOpen = {
                        db.activatePeriod(it.id)
                        refreshAll()
                        screen = Screen.PROGRAM_HOME
                    }
                )
                Screen.PROGRAM_HOME -> RequirePeriod(period) {
                    ProgramHomeScreen(
                        period = period!!,
                        onStudents = { screen = Screen.STUDENTS },
                        onTeams = { screen = Screen.TEAMS },
                        onAttendance = { screen = Screen.ATTENDANCE },
                        onEvaluation = { screen = Screen.EVALUATION },
                        onRubrics = { screen = Screen.RUBRICS },
                        onGuide = { screen = Screen.GUIDE },
                        onFiles = { screen = Screen.GROUP_FILES },
                        onNotes = { screen = Screen.NOTES },
                        onReports = { screen = Screen.REPORTS }
                    )
                }
                Screen.STUDENTS -> RequirePeriod(period) { StudentsScreen(db, period!!, tick, refreshAll) }
                Screen.TEAMS -> RequirePeriod(period) { TeamFormationScreen(db, period!!, tick, refreshAll) }
                Screen.ATTENDANCE -> RequirePeriod(period) { PolishedAttendanceScreen(db, period!!, tick, refreshAll) }
                Screen.EVALUATION -> RequirePeriod(period) { SafeEvaluationScreen(db, period!!, tick, refreshAll) }
                Screen.QUICK_GRADE -> RequirePeriod(period) { QuickGradeScreen(db, period!!, tick, refreshAll) }
                Screen.RUBRICS -> RequirePeriod(period) { RubricsScreen(db, period!!, tick, refreshAll) }
                Screen.GUIDE -> RequirePeriod(period) { GuideScreen(db, period!!, tick, refreshAll) }
                Screen.GROUP_FILES -> RequirePeriod(period) { GroupFilesScreen(period!!) }
                Screen.NOTES -> NotesScreen()
                Screen.CALENDAR -> CalendarScreen(db, tick, refreshAll)
                Screen.REPORTS -> RequirePeriod(period) { PolishedReportsScreen(db, period!!, tick, refreshAll) }
                Screen.PROFILE -> ProfileScreen(
                    teacher = teacher,
                    onSecurity = { screen = Screen.SECURITY },
                    onSave = {
                        db.saveTeacher(it)
                        refreshAll()
                        screen = Screen.HOME
                    }
                )
                Screen.APPEARANCE -> ThemeSelectionScreen(
                    initial = currentTheme,
                    initialDarkMode = currentDarkMode,
                    initialFontScale = currentFontScale,
                    initialFontStyle = currentFontStyle,
                    onSelected = { style, dark, scale, font ->
                        onAppearanceChanged(style, dark, scale, font)
                        screen = Screen.PROFILE
                    },
                    onCancel = { screen = Screen.PROFILE }
                )
                Screen.HELP -> HelpScreen()
                Screen.SECURITY -> SecurityBackupScreen(db) { refreshAll() }
                Screen.TRASH -> TrashScreen(db, tick, refreshAll)
                Screen.GRADE_HISTORY -> GradeHistoryScreen(db, tick, refreshAll)
                Screen.SEARCH -> UniversalSearchScreen(db, tick, ::openSearchResult)
            }
        }
    }
}

@Composable
private fun RequirePeriod(period: AcademicPeriod?, content: @Composable () -> Unit) {
    if (period == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ElevatedCard {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Primero crea un grupo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Después podrás agregar alumnos, asistencia, evaluación, rúbricas y demás carpetas.")
                }
            }
        }
    } else content()
}
