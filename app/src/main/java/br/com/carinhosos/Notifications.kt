package br.com.carinhosos

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val CHANNEL_ID = "medicamentos"
private const val SCHEDULE_PREFS = "carinhosos_notifications"
private const val SCHEDULED_IDS = "scheduled_ids"
private const val EXTRA_REQUEST_ID = "request_id"
private const val EXTRA_ELDER = "elder_name"
private const val EXTRA_MEDICATION = "medication_name"
private const val EXTRA_TIME = "medication_time"
private const val EXTRA_START_DATE = "medication_start_date"
private const val EXTRA_DAILY = "medication_daily"

object NotificationScheduler {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Horários de medicamentos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes para aplicação dos medicamentos dos residentes"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun sync(context: Context, elders: List<Elder>, enabled: Boolean = true) {
        ensureChannel(context)
        cancelPrevious(context)

        val scheduled = mutableSetOf<String>()
        if (!enabled) {
            context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(SCHEDULED_IDS, scheduled)
                .apply()
            return
        }
        elders.forEach { elder ->
            elder.medications.filter {
                it.notificationEnabled &&
                    (it.frequency == "Diário" || it.lastAppliedDate.isBlank())
            }.forEach { medication ->
                val requestId = "${elder.id}:${medication.id}".hashCode()
                scheduleReminder(
                    context = context,
                    requestId = requestId,
                    elderName = elder.name,
                    medicationName = medicationDisplay(medication.name, medication.dose),
                    time = medication.time,
                    startDate = medication.startDate,
                    daily = medication.frequency == "Diário",
                    appliedToday = medication.lastAppliedDate == todayKeyForNotification()
                )
                scheduled += requestId.toString()
            }
        }

        context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(SCHEDULED_IDS, scheduled)
            .apply()
    }

    internal fun scheduleReminder(
        context: Context,
        requestId: Int,
        elderName: String,
        medicationName: String,
        time: String,
        startDate: String,
        daily: Boolean,
        appliedToday: Boolean
    ) {
        val parts = time.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return

        val now = Calendar.getInstance()
        var trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        parseStartDate(startDate)?.let { start ->
            start.set(Calendar.HOUR_OF_DAY, hour)
            start.set(Calendar.MINUTE, minute)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            if (start.timeInMillis > trigger.timeInMillis) trigger = start
        }
        if (appliedToday && sameDay(trigger, now)) trigger.add(Calendar.DAY_OF_YEAR, 1)
        while (trigger.timeInMillis <= now.timeInMillis) trigger.add(Calendar.DAY_OF_YEAR, 1)

        val pendingIntent = reminderPendingIntent(
            context,
            requestId,
            elderName,
            medicationName,
            time,
            startDate,
            daily
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun cancelPrevious(context: Context) {
        val preferences = context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ids.forEach { storedId ->
            val requestId = storedId.toIntOrNull() ?: return@forEach
            val intent = Intent(context, MedicationAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun reminderPendingIntent(
        context: Context,
        requestId: Int,
        elderName: String,
        medicationName: String,
        time: String,
        startDate: String,
        daily: Boolean
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_ELDER, elderName)
            putExtra(EXTRA_MEDICATION, medicationName)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_START_DATE, startDate)
            putExtra(EXTRA_DAILY, daily)
        }
        return PendingIntent.getBroadcast(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, 0)
        val elderName = intent.getStringExtra(EXTRA_ELDER).orEmpty()
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val startDate = intent.getStringExtra(EXTRA_START_DATE).orEmpty()
        val daily = intent.getBooleanExtra(EXTRA_DAILY, true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationScheduler.ensureChannel(context)
            val openApp = PendingIntent.getActivity(
                context,
                requestId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Hora do medicamento")
                .setContentText("$elderName • $medicationName • $time")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "Aplicar $medicationName para $elderName às $time."
                    )
                )
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(requestId, notification)
        }

        if (daily) {
            NotificationScheduler.scheduleReminder(
                context = context,
                requestId = requestId,
                elderName = elderName,
                medicationName = medicationName,
                time = time,
                startDate = startDate,
                daily = true,
                appliedToday = false
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = LocalRepository(context)
            NotificationScheduler.sync(
                context,
                repository.loadElders(),
                repository.notificationsEnabled()
            )
        }
    }
}

private fun parseStartDate(value: String): Calendar? {
    val formats = listOf("dd/MM/yyyy", "yyyy-MM-dd")
    formats.forEach { pattern ->
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull()
        if (parsed != null) return Calendar.getInstance().apply { time = parsed }
    }
    return null
}

private fun sameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

private fun todayKeyForNotification(): String {
    val now = Calendar.getInstance()
    return "%04d-%02d-%02d".format(
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH)
    )
}
