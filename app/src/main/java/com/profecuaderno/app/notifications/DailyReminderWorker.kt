package com.profecuaderno.app.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.ui.eventTypeLabel
import java.time.LocalDate

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        NotificationHelper.createChannels(applicationContext)
        if (!NotificationHelper.canNotify(applicationContext)) return Result.success()

        val db = TeacherDbHelper(applicationContext)
        val today = LocalDate.now()

        db.birthdaysOn(today).forEach { (group, student) ->
            NotificationHelper.postBirthday(
                applicationContext,
                student.name,
                group.name
            )
        }

        db.eventsOn(today.toString()).forEach { (group, event) ->
            NotificationHelper.postAgenda(
                applicationContext,
                event.title,
                group.name,
                eventTypeLabel(event.type),
                event.notes
            )
        }

        return Result.success()
    }
}
