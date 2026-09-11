package com.profecuaderno.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.AttendanceHistoryStore
import com.profecuaderno.app.data.AttendancePolicyStore
import com.profecuaderno.app.data.AttendanceSession
import com.profecuaderno.app.data.AttendanceStatus
import com.profecuaderno.app.data.JustifiedEffect
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.TeacherDbHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class PolishedAttendanceView { MENU, TAKE, REVIEW }

internal data class SessionAttendanceStats(
    val present: Int,
    val absent: Int,
    val late: Int,
    val justified: Int,
    val pending: Int
) {
    val registered: Int get() = present + absent + late + justified
    val total: Int get() = registered + pending
}

internal fun sessionAttendanceStats(
    db: TeacherDbHelper,
    sessionId: Long,
    students: List<Student>
): SessionAttendanceStats {
    var present = 0
    var absent = 0
    var late = 0
    var justified = 0
    var pending = 0
    students.forEach { student ->
        when (AttendancePolicyStore.baseStatus(db.getAttendanceStatus(sessionId, student.id))) {
            AttendanceStatus.PRESENT -> present++
            AttendanceStatus.ABSENT -> absent++
            AttendanceStatus.LATE -> late++
            AttendanceStatus.JUSTIFIED -> justified++
            else -> pending++
        }
    }
    return SessionAttendanceStats(present, absent, late, justified, pending)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PolishedAttendanceScreen(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit
) {
    var view by remember(period.id) { mutableStateOf(PolishedAttendanceView.MENU) }
    var showPolicy by remember { mutableStateOf(false) }
    var historyStudent by remember { mutableStateOf<Student?>(null) }
    val policy = remember(refresh, period.id) { AttendancePolicyStore.policy(db, period.id) }

    when (view) {
        PolishedAttendanceView.MENU -> PolishedAttendanceMenu(
            policyText = "${policy.latePerAbsence} retardos = 1 falta · Justificada: ${policy.justifiedEffect.label}",
            onTake = { view = PolishedAttendanceView.TAKE },
            onReview = { view = PolishedAttendanceView.REVIEW },
            onPolicy = { showPolicy = true }
        )
        PolishedAttendanceView.TAKE -> PolishedAttendanceTake(
            db = db,
            period = period,
            refresh = refresh,
            onChanged = onChanged,
            onBack = { view = PolishedAttendanceView.MENU },
            onPolicy = { showPolicy = true },
            onHistory = { historyStudent = it }
        )
        PolishedAttendanceView.REVIEW -> PolishedAttendanceReview(
            db = db,
            period = period,
            refresh = refresh,
            onChanged = onChanged,
            onBack = { view = PolishedAttendanceView.MENU },
            onPolicy = { showPolicy = true },
            onHistory = { historyStudent = it }
        )
    }

    if (showPolicy) {
        PolishedAttendancePolicySheet(
            initialLatePerAbsence = policy.latePerAbsence,
            initialJustifiedEffect = policy.justifiedEffect,
            onDismiss = { showPolicy = false },
            onSave = { lateRule, effect ->
                AttendancePolicyStore.setJustifiedEffect(db, period.id, effect)
                AttendancePolicyStore.setLatePerAbsence(db, period.id, lateRule)
                showPolicy = false
                onChanged()
            }
        )
    }

    historyStudent?.let { student ->
        PolishedAttendanceHistorySheet(
            db = db,
            period = period,
            student = student,
            refresh = refresh,
            onChanged = onChanged,
            onDismiss = { historyStudent = null }
        )
    }
}

@Composable
private fun PolishedAttendanceMenu(
    policyText: String,
    onTake: () -> Unit,
    onReview: () -> Unit,
    onPolicy: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Text("Asistencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Registra la clase actual o revisa y corrige sesiones anteriores.")
        }
        item {
            ElevatedCard(onClick = onTake, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Pasar asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Completa pendientes como presentes y modifica únicamente las incidencias.")
                }
            }
        }
        item {
            ElevatedCard(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Verificar asistencias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Consulta cada fecha, detecta registros pendientes y corrige cualquier estado.")
                }
            }
        }
        item {
            OutlinedCard(onClick = onPolicy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reglas de asistencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(policyText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolishedAttendanceTake(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onPolicy: () -> Unit,
    onHistory: (Student) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var title by remember { mutableStateOf("Clase") }
    var worked by remember { mutableStateOf(true) }
    var showSessionEditor by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    val session = remember(refresh, period.id, date) { db.getAttendanceSession(period.id, date) }

    LaunchedEffect(session?.id, session?.title, session?.worked) {
        if (session != null) {
            title = session.title.ifBlank { "Clase" }
            worked = session.worked
        } else {
            title = "Clase"
            worked = true
            showSessionEditor = true
        }
    }

    val filteredStudents = remember(students, query) {
        if (query.isBlank()) students else students.filter {
            it.name.contains(query.trim(), ignoreCase = true) || it.studentCode.contains(query.trim(), ignoreCase = true)
        }
    }

    val stats = remember(refresh, session?.id, students) {
        session?.let { sessionAttendanceStats(db, it.id, students) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Opciones")
            }
            OutlinedButton(onClick = onPolicy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Reglas")
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(session?.title?.ifBlank { "Clase" } ?: title)
                        if (session != null && !session.worked) Text("Clase suspendida / no trabajada", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(
                        onClick = { showSessionEditor = !showSessionEditor },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) { Icon(Icons.Default.EditCalendar, contentDescription = "Editar sesión") }
                }

                if (showSessionEditor) {
                    DatePickerField(date, { date = it }, "Fecha")
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Sesión") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = worked, onCheckedChange = { worked = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (worked) "Día trabajado" else "Clase suspendida / no trabajada", modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            db.createOrUpdateAttendanceSession(period.id, date, title.trim().ifBlank { "Clase" }, worked)
                            showSessionEditor = false
                            onChanged()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) { Text(if (session == null) "Crear pase de lista" else "Guardar cambios") }
                }
            }
        }

        if (session != null && session.worked && students.isNotEmpty()) {
            stats?.let { AttendanceSummaryCard(it) }
            if (stats != null && stats.pending > 0) {
                FilledTonalButton(
                    onClick = {
                        students.forEach { student ->
                            if (db.getAttendanceStatus(session.id, student.id) == null) {
                                AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, AttendanceStatus.PRESENT)
                            }
                        }
                        onChanged()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Completar ${stats.pending} pendientes como presentes")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar alumno") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        when {
            session == null -> AttendanceEmptyState("Crea el pase de lista para comenzar.")
            !session.worked -> AttendanceEmptyState("Esta sesión no cuenta como día trabajado y no modifica los porcentajes.")
            students.isEmpty() -> AttendanceEmptyState("Este grupo todavía no tiene alumnos.")
            filteredStudents.isEmpty() -> AttendanceEmptyState("No hay alumnos que coincidan con la búsqueda.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(filteredStudents, key = { it.id }) { student ->
                    PolishedStudentAttendanceCard(
                        db = db,
                        period = period,
                        session = session,
                        student = student,
                        refresh = refresh,
                        onChanged = onChanged,
                        onHistory = onHistory
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolishedAttendanceReview(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onPolicy: () -> Unit,
    onHistory: (Student) -> Unit
) {
    val sessions = remember(refresh, period.id) { db.listAttendanceSessions(period.id) }
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    var selectedId by remember(period.id) { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val filteredStudents = remember(students, query) {
        if (query.isBlank()) students else students.filter {
            it.name.contains(query.trim(), ignoreCase = true) || it.studentCode.contains(query.trim(), ignoreCase = true)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { if (selected != null) { selectedId = null; query = "" } else onBack() },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (selected == null) "Opciones" else "Fechas")
            }
            OutlinedButton(onClick = onPolicy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Reglas")
            }
        }

        if (selected == null) {
            Text("Asistencias anteriores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Abre una fecha para revisar quién falta por registrar o corregir cualquier estado.", style = MaterialTheme.typography.bodySmall)
            if (sessions.isEmpty()) {
                AttendanceEmptyState("Todavía no hay pases de lista guardados.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val stats = remember(refresh, session.id, students) { sessionAttendanceStats(db, session.id, students) }
                        ElevatedCard(onClick = { selectedId = session.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(session.title.ifBlank { "Clase" })
                                if (session.worked) {
                                    AttendanceStatsText(stats)
                                    if (stats.pending > 0) {
                                        Text("⚠ ${stats.pending} alumno(s) sin registrar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    }
                                } else Text("Clase suspendida / no trabajada", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selected.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selected.title.ifBlank { "Clase" })
                    if (selected.worked) {
                        val stats = remember(refresh, selected.id, students) { sessionAttendanceStats(db, selected.id, students) }
                        AttendanceStatsText(stats)
                    }
                }
            }

            if (!selected.worked) {
                AttendanceEmptyState("Esta fecha está marcada como no trabajada.")
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar alumno") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        PolishedStudentAttendanceCard(
                            db = db,
                            period = period,
                            session = selected,
                            student = student,
                            refresh = refresh,
                            onChanged = onChanged,
                            onHistory = onHistory
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceSummaryCard(stats: SessionAttendanceStats) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Resumen del pase", fontWeight = FontWeight.SemiBold)
            AttendanceStatsText(stats)
            if (stats.pending == 0 && stats.total > 0) {
                Text("✓ Pase completo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AttendanceStatsText(stats: SessionAttendanceStats) {
    Text(
        "Presentes ${stats.present} · Faltas ${stats.absent} · Retardos ${stats.late} · Justificadas ${stats.justified} · Pendientes ${stats.pending}",
        style = MaterialTheme.typography.bodySmall
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolishedStudentAttendanceCard(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    session: AttendanceSession,
    student: Student,
    refresh: Int,
    onChanged: () -> Unit,
    onHistory: (Student) -> Unit
) {
    val current = remember(refresh, session.id, student.id) { db.getAttendanceStatus(session.id, student.id) }
    val base = AttendancePolicyStore.baseStatus(current)

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(student.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 6)
            if (student.studentCode.isNotBlank()) Text("Matrícula / ID: ${student.studentCode}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (base == null) "Sin registrar" else polishedAttendanceStatusText(current),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (base == null) FontWeight.Bold else FontWeight.Normal,
                color = if (base == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            PolishedAttendanceStatusSelector(
                selected = base,
                onSelect = { status ->
                    AttendancePolicyStore.setStatus(db, period.id, session.id, student.id, status)
                    onChanged()
                }
            )
            OutlinedButton(onClick = { onHistory(student) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Ver historial completo")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PolishedAttendanceStatusSelector(
    selected: AttendanceStatus?,
    onSelect: (AttendanceStatus) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf(
            AttendanceStatus.PRESENT to "Presente",
            AttendanceStatus.ABSENT to "Falta",
            AttendanceStatus.LATE to "Retardo",
            AttendanceStatus.JUSTIFIED to "Justificada"
        ).forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(label, fontWeight = if (selected == status) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier.heightIn(min = 44.dp)
            )
        }
    }
}

@Composable
private fun AttendanceEmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolishedAttendancePolicySheet(
    initialLatePerAbsence: Int,
    initialJustifiedEffect: JustifiedEffect,
    onDismiss: () -> Unit,
    onSave: (Int, JustifiedEffect) -> Unit
) {
    var lateRule by remember { mutableIntStateOf(initialLatePerAbsence) }
    var effect by remember { mutableStateOf(initialJustifiedEffect) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Reglas de asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Cambiar una regla recalcula el grupo sin borrar ninguna fecha del historial.")
            Text("Retardos equivalentes a una falta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$lateRule retardos = 1 falta", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = lateRule.toFloat(),
                onValueChange = { lateRule = it.roundToInt().coerceIn(2, 10) },
                valueRange = 2f..10f,
                steps = 7
            )
            Text("Los retardos siguen apareciendo como retardos; la equivalencia solo modifica el cálculo de asistencia.", style = MaterialTheme.typography.bodySmall)
            Text("¿Cómo cuenta una justificada?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            JustifiedEffect.entries.forEach { option ->
                FilterChip(
                    selected = effect == option,
                    onClick = { effect = option },
                    label = { Text(option.label) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                )
            }
            Button(onClick = { onSave(lateRule, effect) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Guardar reglas") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancelar") }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolishedAttendanceHistorySheet(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    student: Student,
    refresh: Int,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var localRefresh by remember { mutableIntStateOf(0) }
    val tick = refresh + localRefresh
    val entries = remember(tick, period.id, student.id) { AttendanceHistoryStore.entriesForStudent(db, period.id, student.id) }
    val counts = remember(tick, period.id, student.id) { AttendancePolicyStore.aggregatedCounts(db, period.id, student.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text("Historial de asistencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(student.name, style = MaterialTheme.typography.titleMedium, maxLines = 6)
                Spacer(Modifier.height(6.dp))
                Text("Asistencia actual: ${"%.1f".format(db.attendancePercentage(period.id, student.id))}%", fontWeight = FontWeight.SemiBold)
                Text(
                    "Faltas ${counts[AttendanceStatus.ABSENT] ?: 0} · Retardos ${counts[AttendanceStatus.LATE] ?: 0} · Justificadas ${counts[AttendanceStatus.JUSTIFIED] ?: 0}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (entries.isEmpty()) {
                item { Text("Este alumno todavía no tiene registros de asistencia.", modifier = Modifier.padding(vertical = 20.dp)) }
            } else {
                items(entries, key = { it.sessionId }) { entry ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(entry.title.ifBlank { "Clase" }, style = MaterialTheme.typography.bodySmall)
                            Text(polishedAttendanceStatusText(entry.status), fontWeight = FontWeight.SemiBold)
                            PolishedAttendanceStatusSelector(
                                selected = AttendancePolicyStore.baseStatus(entry.status),
                                onSelect = { status ->
                                    AttendancePolicyStore.setStatus(db, period.id, entry.sessionId, student.id, status)
                                    localRefresh++
                                    onChanged()
                                }
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cerrar") }
            }
        }
    }
}

internal fun polishedAttendanceStatusText(status: AttendanceStatus?): String = when (status) {
    null -> "Sin registrar"
    AttendanceStatus.PRESENT -> "✓ Presente"
    AttendanceStatus.ABSENT -> "✕ Falta"
    AttendanceStatus.LATE -> "◷ Retardo"
    AttendanceStatus.LATE_PENALTY -> "◷ Retardo · completa equivalencia a falta"
    AttendanceStatus.JUSTIFIED -> "J Justificada · no contabilizada"
    AttendanceStatus.JUSTIFIED_PRESENT -> "J Justificada · cuenta como asistencia"
    AttendanceStatus.JUSTIFIED_LATE -> "J Justificada · cuenta como retardo"
    AttendanceStatus.JUSTIFIED_LATE_PENALTY -> "J Justificada · cuenta como retardo y completa equivalencia a falta"
    AttendanceStatus.JUSTIFIED_ABSENT -> "J Justificada · cuenta como falta"
}
