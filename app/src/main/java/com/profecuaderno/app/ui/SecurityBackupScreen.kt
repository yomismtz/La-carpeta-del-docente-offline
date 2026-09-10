package com.profecuaderno.app.ui

import android.app.Activity
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.security.AppSecurityManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SecurityBackupScreen(db: TeacherDbHelper, onRestored: () -> Unit) {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(AppSecurityManager.hasPin(context)) }
    var biometric by remember { mutableStateOf(AppSecurityManager.isBiometricEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showFileHelp by remember { mutableStateOf(false) }
    var showRestoreBrowser by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ExternalActivityGuard.active = false
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            message = if (uri == null) {
                "No se recibió un archivo de destino."
            } else if (db.exportBackup(uri)) {
                "Copia de seguridad guardada."
            } else {
                "No se pudo crear la copia de seguridad."
            }
        } else {
            message = "Guardado cancelado."
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ExternalActivityGuard.active = false
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            val ok = uri != null && db.importBackup(uri)
            message = if (ok) "Copia restaurada correctamente." else "No se pudo restaurar esa copia."
            if (ok) onRestored()
        } else {
            message = "Restauración cancelada."
        }
    }

    fun launchBackup() {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))

        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "La Carpeta del Docente Offline"
            ).apply { mkdirs() }
            val file = File(dir, "La_Carpeta_del_Docente_$stamp.pcbackup")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            message = if (db.exportBackup(uri)) {
                "Copia guardada en Descargas/La Carpeta del Docente Offline."
            } else {
                "No se pudo crear la copia de seguridad."
            }
            return
        }

        val intent = DocumentPickerCompat.createDocumentIntent(
            "application/octet-stream",
            "La_Carpeta_del_Docente_$stamp.pcbackup"
        )
        ExternalActivityGuard.active = true
        runCatching { backupLauncher.launch(intent) }
            .onFailure {
                ExternalActivityGuard.active = false
                showFileHelp = true
            }
    }

    fun launchRestore() {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            showRestoreBrowser = true
            return
        }

        val intent = DocumentPickerCompat.chooserIntent(
            arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*"),
            "Seleccionar copia de seguridad"
        )
        ExternalActivityGuard.active = true
        runCatching { restoreLauncher.launch(intent) }
            .onFailure {
                ExternalActivityGuard.active = false
                showFileHelp = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { ExternalActivityGuard.active = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Seguridad", style = MaterialTheme.typography.titleLarge)
                Text("Protege el acceso a los datos docentes del dispositivo.")
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("100 % local y sin internet", style = MaterialTheme.typography.titleMedium)
                    Text("Esta versión no comparte ni sincroniza datos. La información permanece en este dispositivo y en las copias que tú exportes.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("PIN de acceso", style = MaterialTheme.typography.titleMedium)
                }
                Text(if (hasPin) "PIN activado." else "Sin PIN configurado.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showPinDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (hasPin) "Cambiar PIN" else "Crear PIN")
                    }
                    if (hasPin) {
                        OutlinedButton(
                            onClick = {
                                AppSecurityManager.clearPin(context)
                                hasPin = false
                                biometric = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Desactivar") }
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Biometría", style = MaterialTheme.typography.titleMedium)
                    Text("Permite desbloquear con huella, rostro o credencial del dispositivo.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = biometric,
                    enabled = hasPin,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            AppSecurityManager.setBiometricEnabled(context, false)
                            biometric = false
                        } else {
                            val manager = BiometricManager.from(context)
                            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            val available = manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
                            if (available) {
                                AppSecurityManager.setBiometricEnabled(context, true)
                                biometric = true
                            } else {
                                message = "Este dispositivo no tiene biometría o credencial compatible configurada."
                            }
                        }
                    }
                )
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copia de seguridad", style = MaterialTheme.typography.titleMedium)
                Text("Guarda una copia manual de estudiantes, asistencias, evaluaciones, rúbricas, grupos y calendario. Consérvala en un lugar seguro.")
                Button(
                    onClick = { launchBackup() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar copia")
                }
                OutlinedButton(
                    onClick = { launchRestore() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restaurar copia")
                }
                Text("Con acceso a archivos, las copias se guardan directamente en Descargas y pueden restaurarse con el explorador interno.", style = MaterialTheme.typography.bodySmall)
            }
        }

        message?.let {
            AssistChip(onClick = { message = null }, label = { Text(it) })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showRestoreBrowser) {
        LocalFileBrowserDialog(
            title = "Restaurar copia local",
            allowedExtensions = setOf("pcbackup", "db", "sqlite", "sqlite3"),
            onDismiss = { showRestoreBrowser = false },
            onFileSelected = { uri ->
                showRestoreBrowser = false
                val ok = db.importBackup(uri)
                message = if (ok) "Copia restaurada correctamente." else "No se pudo restaurar esa copia."
                if (ok) onRestored()
            }
        )
    }

    if (showFileHelp) {
        AlertDialog(
            onDismissRequest = { showFileHelp = false },
            title = { Text("Acceso a archivos") },
            text = { Text("El selector de Android no respondió. Concede 'Acceso a todos los archivos' desde la pantalla de permisos de la app y podrás usar el explorador interno.") },
            confirmButton = {
                TextButton(onClick = {
                    showFileHelp = false
                    ExternalActivityGuard.active = true
                    runCatching { context.startActivity(DocumentPickerCompat.appSettingsIntent(context)) }
                        .onFailure {
                            ExternalActivityGuard.active = false
                            message = "No se pudo abrir la configuración del sistema."
                        }
                }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir configuración")
                }
            },
            dismissButton = { TextButton(onClick = { showFileHelp = false }) { Text("Cerrar") } }
        )
    }

    if (showPinDialog) {
        PinSetupDialog(
            hasExistingPin = hasPin,
            onDismiss = { showPinDialog = false },
            onSaved = {
                hasPin = true
                showPinDialog = false
                message = "PIN guardado."
            }
        )
    }
}

@Composable
private fun PinSetupDialog(
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "Cambiar PIN" else "Crear PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasExistingPin) {
                    OutlinedTextField(
                        current,
                        { current = it.filter(Char::isDigit).take(8) },
                        label = { Text("PIN actual") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    first,
                    { first = it.filter(Char::isDigit).take(8) },
                    label = { Text("Nuevo PIN de 4 a 8 dígitos") },
                    singleLine = true
                )
                OutlinedTextField(
                    second,
                    { second = it.filter(Char::isDigit).take(8) },
                    label = { Text("Confirmar PIN") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = first.length in 4..8 && second.length in 4..8,
                onClick = {
                    when {
                        hasExistingPin && !AppSecurityManager.verifyPin(context, current) ->
                            error = "El PIN actual no es correcto."
                        first != second -> error = "Los PIN no coinciden."
                        else -> {
                            AppSecurityManager.setPin(context, first)
                            onSaved()
                        }
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
