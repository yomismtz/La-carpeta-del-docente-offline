package com.profecuaderno.app.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneOffset

object SystemCalendarSync {
    private const val APP_MARKER = "La Carpeta del Docente Offline"

    fun addEvent(
        context: Context,
        dateText: String,
        title: String,
        groupName: String,
        typeLabel: String,
        notes: String = ""
    ): Boolean {
        if (!hasCalendarPermission(context)) return false
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return false
        val calendarId = firstWritableCalendarId(context) ?: return false
        val start = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val finalTitle = "$typeLabel · $title"

        if (alreadyExists(context, calendarId, finalTitle, start)) return true

        val description = buildString {
            append(APP_MARKER).append(" · ").append(groupName)
            if (notes.isNotBlank()) append("\n").append(notes)
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, finalTitle)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    private fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun firstWritableCalendarId(context: Context): Long? = runCatching {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val visibleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
            while (cursor.moveToNext()) {
                val visible = cursor.getInt(visibleIndex) == 1
                val writable = cursor.getInt(accessIndex) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (visible && writable) return@use cursor.getLong(idIndex)
            }
            null
        }
    }.getOrNull()

    private fun alreadyExists(context: Context, calendarId: Long, title: String, start: Long): Boolean = runCatching {
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.TITLE}=? AND ${CalendarContract.Events.DTSTART}=?",
            arrayOf(calendarId.toString(), title, start.toString()),
            null
        )?.use { it.moveToFirst() } == true
    }.getOrDefault(false)
}
