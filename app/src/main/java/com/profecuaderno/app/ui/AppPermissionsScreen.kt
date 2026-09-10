package com.profecuaderno.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun AppPermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val legacyFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }
    val fileSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ExternalActivityGuard.active = false
        refresh++
        infoMessage = if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            "Acceso a archivos concedido."
        } else {
            "El acceso a archivos no fue concedido. Puedes continuar y autorizarlo después."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ExternalActivityGuard.active = false
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ExternalActivityGuard.active = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val refreshKey = refresh
    val notificationsGranted = remember(refreshKey) {
        Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    }
    val contactsGranted = remember(refreshKey) { has(Manifest.permission.READ_CONTACTS) }
    val filesGranted = remember(refreshKey) {
        when {
            Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= 23 -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> true
        }
    }

    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de notificaciones." }
        }
    }

    fun requestContacts() {
        if (!contactsGranted) {
            runCatching { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de contactos." }
        }
    }

    fun requestFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            infoMessage = "Android abrirá la pantalla del sistema para autorizar archivos. Activa el permiso y usa Atrás para regresar a la app."
            val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            ExternalActivityGuard.active = true
            runCatching { fileSettingsLauncher.launch(appIntent) }
                .onFailure {
                    val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    runCatching { fileSettingsLauncher.launch(generalIntent) }
                        .onFailure {
                            ExternalActivityGuard.active = false
                            infoMessage = "No se pudo abrir la configuración de acceso a archivos."
                        }
                }
        } else if (!filesGranted) {
            runCatching { legacyFilesLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de archivos." }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Permisos de la aplicación", style = MaterialTheme.typography.headlineSmall)
        Text("Puedes concederlos uno por uno. La app no debe cerrarse al autorizar notificaciones o contactos. Para archivos, Android abre una pantalla de Ajustes y después debes regresar con Atrás.")

        PermissionCard("Notificaciones", notificationsGranted, Icons.Default.Notifications) { requestNotification() }
        PermissionCard("Contactos", contactsGranted, Icons.Default.Contacts) { requestContacts() }
        PermissionCard("Archivos del teléfono", filesGranted, Icons.Default.Folder) { requestFiles() }

        Text(
            if (filesGranted) "Acceso a archivos: concedido ✓. La app usará su explorador interno."
            else "Acceso a archivos: no concedido. Puedes continuar y autorizarlo más tarde.",
            style = MaterialTheme.typography.bodySmall
        )

        infoMessage?.let {
            AssistChip(onClick = { infoMessage = null }, label = { Text(it) })
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (granted) "Concedido" else "No concedido", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick, enabled = !granted) {
                Text(if (granted) "Listo" else "Permitir")
            }
        }
    }
}
