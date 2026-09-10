package com.profecuaderno.app.ui

import android.app.Activity
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
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.security.AppSecurityManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SecurityBackupScreen(db: TeacherDbHelper, onRestored: () -> Unit) {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(AppSecurityManager.hasPin(context)) }
    var biometric by remember { mutableStateOf(AppSecurityManager.isBiometricEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showFileHelp by remember { mutableStateOf(false) }
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
        val intent = DocumentPickerCompat.createDocumentIntent(
            "application/octet-stream",
            "El_Cuaderno_del_Maestro_$stamp.pcbackup"
        )
        if (!DocumentPickerCompat.canResolve(context, intent)) {
            showFileHelp = true
            return
        }
        ExternalActivityGuard.active = true
        runCatching { backupLauncher.launch(intent) }
            .onFailure {
                ExternalActivityGuard.active = false
                showFileHelp = true
            }
    }

    fun launchRestore() {
        val intent = DocumentPickerCompat.chooserIntent(
            arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*"),
            "Seleccionar copia de seguridad"
        )
        if (!DocumentPickerCompat.canResolve(context, intent)) {
            showFileHelp = true
            return
        }
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
                    Text("Funciona sin internet", style = MaterialTheme.typography.titleMedium)
                    Text("Tus datos se guardan primero en este dispositivo. No necesitas Google Console para usar esta versión.", style = MaterialTheme.typography.bodySmall)
                    Text("Más adelante podremos añadir sincronización cuando esté disponible, sin quitar el funcionamiento offline.", style = MaterialTheme.typography.labelSmall)
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
                Text("La copia no incluye el PDF de la guía si ese archivo está guardado fuera de la app.", style = MaterialTheme.typography.bodySmall)
            }
        }

        message?.let {
            AssistChip(onClick = { message = null }, label = { Text(it) })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showFileHelp) {
        AlertDialog(
            onDismissRequest = { showFileHelp = false },
            title = { Text("Selector de archivos no disponible") },
            text = { Text("Android no encontró una aplicación capaz de abrir o guardar documentos. Habilita o instala un administrador de archivos y vuelve a intentarlo.") },
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
