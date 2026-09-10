package com.profecuaderno.app.ui

import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.app.Activity
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.profecuaderno.app.data.AcademicPeriod
import com.profecuaderno.app.data.CalendarEvent
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.notifications.ReminderScheduler
import com.profecuaderno.app.util.PlanningActivityClassifier
import com.profecuaderno.app.util.PlanningGuideAnalyzer
import com.profecuaderno.app.util.PlanningSuggestion
import com.profecuaderno.app.util.SystemCalendarSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GuideScreen(
    db: TeacherDbHelper,
    period: AcademicPeriod,
    refresh: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("program_documents", 0) }
    val key = "guide_uri_${period.id}"
    var uriString by remember { mutableStateOf(prefs.getString(key, null)) }
    val events = remember(refresh, period.id) { db.getEvents(period.id) }
    var eventTypeToCreate by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<PlanningSuggestion>>(emptyList()) }
    var selectedSuggestions by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var analysisMessage by remember { mutableStateOf<String?>(null) }
    var pickerMessage by remember { mutableStateOf<String?>(null) }
    var showLocalBrowser by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun handleDocument(uri: Uri?) {
        ExternalActivityGuard.active = false
        if (uri == null) {
            pickerMessage = "No se seleccionó ningún documento."
            return
        }

        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val supportedByMime = mime != null && DocumentImportPolicy.mimeTypes.contains(mime)
        if (!DocumentImportPolicy.isSupported(displayName) && !supportedByMime) {
            pickerMessage = "Formato no compatible. Elige PDF, Word, TXT o una imagen JPG, PNG, WEBP, HEIC/HEIF."
            return
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val readable = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read() } != null
        }.getOrDefault(false)

        if (readable) {
            prefs.edit().putString(key, uri.toString()).apply()
            uriString = uri.toString()
            pickerMessage = "Documento seleccionado correctamente."
        } else {
            pickerMessage = "No pude leer ese archivo. Elige un PDF, CSV, Excel, Word o TXT almacenado en el dispositivo o en un proveedor compatible."
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ExternalActivityGuard.active = false
        if (result.resultCode == Activity.RESULT_OK) handleDocument(result.data?.data)
        else if (result.resultCode != Activity.RESULT_CANCELED) pickerMessage = "No se pudo recibir el archivo seleccionado."
    }

    fun openPicker() {
        pickerMessage = null
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            showLocalBrowser = true
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, DocumentImportPolicy.mimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        ExternalActivityGuard.active = true
        runCatching { pickerLauncher.launch(intent) }
            .onFailure {
                ExternalActivityGuard.active = false
                pickerMessage = "No se pudo abrir el selector de documentos de Android."
            }
    }

    DisposableEffect(Unit) { onDispose { ExternalActivityGuard.active = false } }

    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Guía / planeación", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Puedes importar una planeación en PDF, Word, TXT o imagen. La app detectará fechas, usará OCR cuando haga falta y clasificará automáticamente exámenes, laboratorios, prácticas, evaluaciones, exposiciones, entregas, proyectos, visitas y otras actividades.")
                }
            }
        }

        item {
            if (uriString == null) {
                Button(onClick = { openPicker() }) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar planeación o guía")
                }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Documento guardado ✓", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val uri = Uri.parse(uriString)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ExternalActivityGuard.active = true
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        ExternalActivityGuard.active = false
                                        pickerMessage = "No encontré una aplicación compatible para abrir este documento."
                                    }
                            }) {
                                Icon(Icons.Default.FolderOpen, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Visualizar")
                            }
                            OutlinedButton(onClick = { openPicker() }) { Text("Cambiar documento") }
                        }
                        Button(
                            enabled = !analyzing,
                            onClick = {
                                val currentUri = uriString ?: return@Button
                                analyzing = true
                                analysisMessage = null
                                scope.launch {
                                    val found = withContext(Dispatchers.IO) {
                                        runCatching { PlanningGuideAnalyzer.analyze(context, Uri.parse(currentUri), period) }
                                            .getOrElse { emptyList() }
                                            .map { suggestion ->
                                                suggestion.copy(type = PlanningActivityClassifier.classify("${suggestion.title} ${suggestion.source}"))
                                            }
                                    }
                                    suggestions = found
                                    selectedSuggestions = found.indices.toSet()
                                    analyzing = false
                                    analysisMessage = if (found.isEmpty()) {
                                        "No encontré fechas reconocibles. La app revisó el texto disponible y, para PDF o imágenes, también intentó reconocimiento OCR. Revisa que las fechas sean visibles y tengan un formato como 10/09/2026, 10 de septiembre de 2026 o 10 sep."
                                    } else "Encontré ${found.size} posibles fechas y las clasifiqué por tipo. Revisa cuáles quieres agregar."
                                }
                            }
                        ) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (analyzing) "Analizando…" else "Analizar guía")
                        }
                        analysisMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            pickerMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fechas de la planeación", style = MaterialTheme.typography.titleMedium)
                    Text("Selecciona el tipo de actividad y agrega su fecha. Al guardarla aparecerá en el calendario de la app y, si autorizaste Calendario, también se añadirá al calendario de Android.")
                    teacherEventTypes.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { option ->
                                OutlinedButton(onClick = { eventTypeToCreate = option.code }, modifier = Modifier.weight(1f)) {
                                    Text(option.label)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item { Text("Fechas registradas para ${period.name}", style = MaterialTheme.typography.titleMedium) }
            items(events, key = { it.id }) { event ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(event.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            Text(eventTypeLabel(event.type), style = MaterialTheme.typography.bodySmall)
                            if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { deleting = event }) { Icon(Icons.Default.Delete, "Eliminar") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }

    if (showLocalBrowser) {
        LocalFileBrowserDialog(
            title = "Seleccionar documento",
            allowedExtensions = setOf("pdf", "doc", "docx", "txt", "jpg", "jpeg", "png", "webp", "heic", "heif"),
            onDismiss = { showLocalBrowser = false },
            onFileSelected = { uri ->
                showLocalBrowser = false
                handleDocument(uri)
            }
        )
    }

    eventTypeToCreate?.let { selectedType ->
        EventDialog(
            groups = listOf(period),
            initialGroupId = period.id,
            initialType = selectedType,
            onDismiss = { eventTypeToCreate = null },
            onSave = {
                db.saveEvent(it)
                SystemCalendarSync.addEvent(context, it.date, it.title, period.name, eventTypeLabel(it.type), it.notes)
                ReminderScheduler.ensureDaily(context)
                eventTypeToCreate = null
                onChanged()
            }
        )
    }

    if (suggestions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { suggestions = emptyList(); selectedSuggestions = emptySet() },
            title = { Text("Fechas detectadas y clasificadas") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                    Text("Confirma antes de agregar. La app no guarda ninguna fecha automáticamente.")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions.indices.toList()) { index ->
                            val suggestion = suggestions[index]
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                    Checkbox(
                                        checked = index in selectedSuggestions,
                                        onCheckedChange = { checked -> selectedSuggestions = if (checked) selectedSuggestions + index else selectedSuggestions - index }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text("${suggestion.date} · ${eventTypeLabel(suggestion.type)}", style = MaterialTheme.typography.labelLarge)
                                        Text(suggestion.title, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedSuggestions.isNotEmpty(),
                    onClick = {
                        selectedSuggestions.sorted().forEach { index ->
                            val suggestion = suggestions[index]
                            val event = CalendarEvent(0, period.id, suggestion.title, suggestion.date, "Detectado desde la guía / planeación", suggestion.type)
                            db.saveEvent(event)
                            SystemCalendarSync.addEvent(context, event.date, event.title, period.name, eventTypeLabel(event.type), event.notes)
                        }
                        ReminderScheduler.ensureDaily(context)
                        analysisMessage = "Se agregaron ${selectedSuggestions.size} fechas al calendario y quedaron enlazadas con los recordatorios."
                        suggestions = emptyList()
                        selectedSuggestions = emptySet()
                        onChanged()
                    }
                ) { Text("Agregar seleccionadas") }
            },
            dismissButton = { TextButton(onClick = { suggestions = emptyList(); selectedSuggestions = emptySet() }) { Text("Cancelar") } }
        )
    }

    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar fecha") },
            text = { Text("¿Eliminar '${event.title}' de la planeación?") },
            confirmButton = {
                TextButton(onClick = {
                    db.deleteEvent(event.id)
                    deleting = null
                    onChanged()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}
