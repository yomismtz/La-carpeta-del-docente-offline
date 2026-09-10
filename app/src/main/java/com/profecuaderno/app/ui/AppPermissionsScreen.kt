package com.profecuaderno.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val refreshKey = refresh
    val notificationsGranted = remember(refreshKey) {
        Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    }
    val calendarGranted = remember(refreshKey) {
        has(Manifest.permission.READ_CALENDAR) && has(Manifest.permission.WRITE_CALENDAR)
    }

    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de notificaciones." }
        }
    }

    fun requestCalendar() {
        if (!calendarGranted) {
            runCatching {
                calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }.onFailure { infoMessage = "No se pudo abrir el permiso de calendario." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Permisos de la aplicación", style = MaterialTheme.typography.headlineSmall)
        Text("La app solicita notificaciones y calendario. Para archivos usa el selector seguro de Android, sin acceso general a todo el almacenamiento.")

        PermissionCard("Notificaciones", notificationsGranted, Icons.Default.Notifications) { requestNotification() }
        PermissionCard("Calendario", calendarGranted, Icons.Default.CalendarMonth) { requestCalendar() }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("Archivos", style = MaterialTheme.typography.titleMedium)
                    Text("Al importar o exportar, Android te permitirá elegir exactamente el archivo o ubicación que quieras usar.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        infoMessage?.let {
            AssistChip(onClick = { infoMessage = null }, label = { Text(it) })
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Continuar") }
        Spacer(Modifier.height(4.dp))
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
