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
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ExternalActivityGuard.active = false
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // refresh se lee expresamente para reevaluar el estado al volver de Ajustes.
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
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    fun requestContacts() {
        if (!contactsGranted) permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    fun requestFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            ExternalActivityGuard.active = true
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .onFailure { ExternalActivityGuard.active = false }
            }
        } else if (!filesGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Permisos de la aplicación", style = MaterialTheme.typography.headlineSmall)
        Text("Puedes concederlos ahora o continuar. Para importar archivos sin depender del selector de Google/Android, concede acceso a los archivos del teléfono.")

        PermissionCard("Notificaciones", notificationsGranted, Icons.Default.Notifications) {
            requestNotification()
        }
        PermissionCard("Contactos", contactsGranted, Icons.Default.Contacts) {
            requestContacts()
        }
        PermissionCard("Archivos del teléfono", filesGranted, Icons.Default.Folder) {
            requestFiles()
        }

        Text(
            if (filesGranted) {
                "Acceso a archivos: concedido ✓. La app usará su explorador interno."
            } else {
                "Acceso a archivos: no concedido. La app intentará el selector estándar de Android como alternativa."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continuar")
        }
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
