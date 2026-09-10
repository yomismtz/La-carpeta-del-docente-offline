package com.profecuaderno.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.profecuaderno.app.MainActivity
import com.profecuaderno.app.R

object NotificationHelper {
    const val CHANNEL_BIRTHDAYS = "birthdays"
    const val CHANNEL_AGENDA = "agenda"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val birthdays = NotificationChannel(
                CHANNEL_BIRTHDAYS,
                "Cumpleaños",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de cumpleaños de alumnos"
            }
            val agenda = NotificationChannel(
                CHANNEL_AGENDA,
                "Agenda docente",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Recordatorios de clases, exámenes, prácticas, entregas, visitas y otras fechas"
            }
            manager.createNotificationChannels(listOf(birthdays, agenda))
        }
    }

    fun canNotify(context: Context): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postBirthday(context: Context, studentName: String, groupName: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_BIRTHDAYS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎂 Cumpleaños de alumno")
            .setContentText("$studentName · $groupName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Hoy es el cumpleaños de $studentName, del grupo $groupName."))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(("birthday-$studentName-$groupName").hashCode(), notification)
        } catch (_: SecurityException) {
            // El permiso puede cambiar entre la comprobación y la publicación.
        }
    }

    fun postAgenda(context: Context, title: String, groupName: String, typeLabel: String, notes: String) {
        if (!canNotify(context)) return
        val body = buildString {
            append(groupName)
            if (notes.isNotBlank()) append(" · ").append(notes)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENDA)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$typeLabel · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(("agenda-$groupName-$title").hashCode(), notification)
        } catch (_: SecurityException) {
            // El permiso puede cambiar entre la comprobación y la publicación.
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
