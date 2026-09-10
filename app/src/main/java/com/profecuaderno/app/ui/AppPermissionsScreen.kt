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

@Composable
fun AppPermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val notificationsGranted = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    val contactsGranted = has(Manifest.permission.READ_CONTACTS)
    val filesGranted = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= 23 -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> true
    }

    fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            if (!contactsGranted) add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT < 30 && !filesGranted) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Permisos de la aplicación", style = MaterialTheme.typography.headlineSmall)
        Text("Concede solo los accesos que quieras usar. El acceso a archivos permitirá importar PDF, CSV, Word, Excel y TXT directamente desde el almacenamiento del teléfono.")

        PermissionCard("Notificaciones", notificationsGranted, Icons.Default.Notifications) {
            requestRuntimePermissions()
        }
        PermissionCard("Contactos", contactsGranted, Icons.Default.Contacts) {
            requestRuntimePermissions()
        }
        PermissionCard("Archivos del teléfono", filesGranted, Icons.Default.Folder) {
            if (Build.VERSION.SDK_INT >= 30) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
            } else {
                requestRuntimePermissions()
            }
        }

        Text(
            if (filesGranted) "Acceso a archivos: concedido ✓" else "Para usar el explorador interno de archivos, concede acceso a archivos del teléfono.",
            style = MaterialTheme.typography.bodySmall
        )

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
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (granted) "Concedido" else "No concedido", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick) { Text(if (granted) "Revisar" else "Permitir") }
        }
    }
}
