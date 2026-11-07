package com.smart.smsmanager

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object SmsScheduler {
    private fun uniqueName(id: String) = "smart_sms_schedule_$id"

    fun nextDailyTime(timeOfDay: String, selectedDays: List<Int>, after: Long): Long {
        val pieces = timeOfDay.split(":")
        require(pieces.size == 2)
        val hour = pieces[0].toInt()
        val minute = pieces[1].toInt()
        require(hour in 0..23 && minute in 0..59)
        require(selectedDays.isNotEmpty())

        for (offset in 0..7) {
            val c = Calendar.getInstance().apply {
                timeInMillis = after
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.get(Calendar.DAY_OF_WEEK) in selectedDays && c.timeInMillis > after) {
                return c.timeInMillis
            }
        }
        throw IllegalArgumentException("Select at least one weekday.")
    }

    fun calculateNext(schedule: SmsSchedule, now: Long = System.currentTimeMillis()): Long =
        when (schedule.mode) {
            ScheduleMode.ONCE -> schedule.runAt
            ScheduleMode.INTERVAL -> now + TimeUnit.MINUTES.toMillis(schedule.intervalMinutes)
            ScheduleMode.DAILY -> nextDailyTime(schedule.timeOfDay, schedule.days, now)
        }

    fun enqueue(context: Context, schedule: SmsSchedule, append: Boolean = false) {
        val delay = (schedule.nextRunAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ScheduledSmsWorker.KEY_SCHEDULE_ID, schedule.id).build())
            .addTag(uniqueName(schedule.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(schedule.id),
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pause(context: Context, schedule: SmsSchedule) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(schedule.id))
        SmsStorage.upsertSchedule(context, schedule.copy(status = "PAUSED"))
    }

    fun resume(context: Context, schedule: SmsSchedule) {
        val resumed = schedule.copy(
            status = "ACTIVE",
            nextRunAt = calculateNext(schedule)
        )
        SmsStorage.upsertSchedule(context, resumed)
        enqueue(context, resumed)
    }

    fun stop(context: Context, schedule: SmsSchedule) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(schedule.id))
        SmsStorage.upsertSchedule(context, schedule.copy(status = "STOPPED"))
    }
}

class ScheduledSmsWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val id = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.failure()
        val schedule = SmsStorage.getSchedule(applicationContext, id) ?: return Result.success()
        if (schedule.status != "ACTIVE") return Result.success()

        val now = System.currentTimeMillis()
        if (schedule.nextRunAt > now + 2_000L) {
            SmsScheduler.enqueue(applicationContext, schedule)
            return Result.success()
        }

        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            SmsStorage.addHistory(
                applicationContext,
                SmsRecord(UUID.randomUUID().toString(), schedule.name, schedule.phone,
                    schedule.message, now, "FAILED - SEND_SMS permission missing")
            )
            if (schedule.mode != ScheduleMode.ONCE) enqueueNext(schedule)
            else SmsStorage.upsertSchedule(applicationContext, schedule.copy(status = "COMPLETED"))
            return Result.success()
        }

        val historyId = UUID.randomUUID().toString()
        SmsStorage.addHistory(
            applicationContext,
            SmsRecord(historyId, schedule.name, schedule.phone, schedule.message, now, "SUBMITTED")
        )

        try {
            @Suppress("DEPRECATION")
            val manager = SmsManager.getDefault()
            val parts = manager.divideMessage(schedule.message)
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            parts.forEachIndexed { index, _ ->
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val sentIntent = Intent(applicationContext, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra("history_id", historyId)
                }
                val deliveredIntent = Intent(applicationContext, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                    putExtra("history_id", historyId)
                }
                sent.add(PendingIntent.getBroadcast(
                    applicationContext, historyId.hashCode() + index, sentIntent, flags
                ))
                delivered.add(PendingIntent.getBroadcast(
                    applicationContext, historyId.hashCode() + 10_000 + index, deliveredIntent, flags
                ))
            }

            if (parts.size <= 1) {
                manager.sendTextMessage(schedule.phone, null, schedule.message, sent[0], delivered[0])
            } else {
                manager.sendMultipartTextMessage(schedule.phone, null, parts, sent, delivered)
            }
        } catch (e: Exception) {
            SmsStorage.updateHistoryStatus(applicationContext, historyId, "FAILED")
        }

        if (schedule.mode == ScheduleMode.ONCE) {
            SmsStorage.upsertSchedule(applicationContext, schedule.copy(status = "COMPLETED"))
        } else {
            enqueueNext(schedule)
        }
        return Result.success()
    }

    private fun enqueueNext(old: SmsSchedule) {
        try {
            val updated = old.copy(
                nextRunAt = SmsScheduler.calculateNext(old),
                status = "ACTIVE"
            )
            SmsStorage.upsertSchedule(applicationContext, updated)
            SmsScheduler.enqueue(applicationContext, updated, append = true)
        } catch (_: Exception) {
            SmsStorage.upsertSchedule(applicationContext, old.copy(status = "FAILED"))
        }
    }

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
    }
}
