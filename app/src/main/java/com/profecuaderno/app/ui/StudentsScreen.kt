package com.profecuaderno.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.Student
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.data.TrashStore
import com.profecuaderno.app.util.CsvStudentImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudentsScreen(db: TeacherDbHelper, period: AcademicPeriod, refresh: Int, onChanged: () -> Unit) {
    val students = remember(refresh, period.id) { db.getStudents(period.id) }
    var editing by remember { mutableStateOf<Student?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Student?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showFileHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun importUri(uri: android.net.Uri?) {
        ExternalActivityGuard.active = false
        if (uri == null) {
            importMessage = "No se seleccionó ningún archivo."
            return
        }
        importing = true
        importMessage = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val result = CsvStudentImporter.read(context, uri, period.id)
                    if (result.error != null) result.error
                    else if (result.students.isEmpty()) "No encontré alumnos válidos en el archivo. Revisa que tenga una columna de nombre."
                    else {
                        var saved = 0
                        var saveErrors = 0
                        result.students.forEach { student ->
                            runCatching { db.saveStudent(student) }
                                .onSuccess { saved++ }
                                .onFailure { saveErrors++ }
                        }
                        buildString {
                            append("Importados: $saved")
                            if (result.skipped > 0) append(" · omitidos: ${result.skipped}")
                            if (saveErrors > 0) append(" · errores al guardar: $saveErrors")
                        }
                    }
                }.getOrElse { error ->
                    "No se pudo importar el CSV: ${error.message ?: "error de lectura del archivo"}."
                }
            }
            importMessage = outcome
            importing = false
            onChanged()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        ExternalActivityGuard.active = false
        importUri(uri)
    }

    fun openCsvPicker() {
        importMessage = null
        val mimeTypes = arrayOf("text/csv", "text/plain", "application/vnd.ms-excel", "application/csv", "text/comma-separated-values", "application/octet-stream")
        ExternalActivityGuard.active = true
        runCatching { pickerLauncher.launch(mimeTypes) }
            .onFailure {
                ExternalActivityGuard.active = false
                importMessage = "No pude abrir el selector de CSV. Revisa que Archivos/Files esté habilitado."
                showFileHelp = true
            }
    }

    DisposableEffect(Unit) { onDispose { ExternalActivityGuard.active = false } }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openCsvPicker() }, enabled = !importing) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (importing) "Importando…" else "Importar CSV")
                }
                FilledTonalButton(onClick = { showNew = true }, enabled = !importing) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nuevo alumno")
                }
            }
            importMessage?.let {
                AssistChip(onClick = { importMessage = null }, label = { Text(it) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Box(Modifier.fillMaxSize()) {
                if (students.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aún no hay alumnos en este periodo.") }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(students, key = { it.id }) { student ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(student.name, style = MaterialTheme.typography.titleMedium)
                                        val second = listOf(student.groupName, student.clinic, student.teamName).filter { it.isNotBlank() }.joinToString(" • ")
                                        if (second.isNotBlank()) Text(second, style = MaterialTheme.typography.bodySmall)
                                        if (student.email.isNotBlank()) Text(student.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { editing = student }) { Icon(Icons.Default.Edit, "Editar") }
                                    IconButton(onClick = { deleting = student }) { Icon(Icons.Default.Delete, "Eliminar") }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }
    }

    if (showFileHelp) {
        AlertDialog(
            onDismissRequest = { showFileHelp = false },
            title = { Text("Selector de archivos no disponible") },
            text = { Text("Android no encontró una aplicación capaz de seleccionar documentos. Habilita o instala un administrador de archivos y vuelve a intentar. También puedes abrir la configuración de esta app desde aquí.") },
            confirmButton = {
                TextButton(onClick = {
                    showFileHelp = false
                    ExternalActivityGuard.active = true
                    runCatching { context.startActivity(DocumentPickerCompat.appSettingsIntent(context)) }
                        .onFailure { ExternalActivityGuard.active = false }
                }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir configuración")
                }
            },
            dismissButton = { TextButton(onClick = { showFileHelp = false }) { Text("Cerrar") } }
        )
    }

    if (showNew) StudentDialog(
        title = "Nuevo alumno",
        initial = Student(periodId = period.id, name = ""),
        onDismiss = { showNew = false },
        onSave = { db.saveStudent(it); showNew = false; onChanged() }
    )

    editing?.let { student ->
        StudentDialog(
            title = "Editar alumno",
            initial = student,
            onDismiss = { editing = null },
            onSave = { db.saveStudent(it); editing = null; onChanged() }
        )
    }

    deleting?.let { student ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Enviar estudiante a Papelera") },
            text = { Text("¿Enviar a ${student.name} a Papelera? Sus asistencias y calificaciones se conservarán para poder restaurarlas.") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.trashStudent(db, student)
                    deleting = null
                    onChanged()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar("${student.name} enviado a Papelera", "Deshacer", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) {
                            TrashStore.entries(db).firstOrNull { it.type == TrashStore.TYPE_STUDENT && it.entityId == student.id }?.let { TrashStore.restore(db, it) }
                            onChanged()
                        }
                    }
                }) { Text("Enviar a Papelera", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StudentDialog(title: String, initial: Student, onDismiss: () -> Unit, onSave: (Student) -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var code by remember(initial.id) { mutableStateOf(initial.studentCode) }
    var email by remember(initial.id) { mutableStateOf(initial.email) }
    var phone by remember(initial.id) { mutableStateOf(initial.phone) }
    var birth by remember(initial.id) { mutableStateOf(initial.birthDate) }
    var group by remember(initial.id) { mutableStateOf(initial.groupName) }
    var clinic by remember(initial.id) { mutableStateOf(initial.clinic) }
    var team by remember(initial.id) { mutableStateOf(initial.teamName) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("Matrícula / ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
                DatePickerField(birth, { birth = it }, "Fecha de nacimiento")
                OutlinedTextField(group, { group = it }, label = { Text("Grupo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clinic, { clinic = it }, label = { Text("Sección / salón / laboratorio / clínica") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(team, { team = it }, label = { Text("Equipo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name, studentCode = code, email = email, phone = phone, birthDate = birth, groupName = group, clinic = clinic, teamName = team, notes = notes)) }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
