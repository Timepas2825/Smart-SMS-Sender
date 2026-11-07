package com.smart.smsmanager

data class Contact(val name: String, val phone: String)
data class MessageTemplate(val title: String, val content: String)

data class SmsRecord(
    val id: String,
    val name: String,
    val phone: String,
    val message: String,
    val time: Long,
    val status: String
)

enum class ScheduleMode { ONCE, INTERVAL, DAILY }

data class SmsSchedule(
    val id: String,
    val name: String,
    val phone: String,
    val message: String,
    val mode: ScheduleMode,
    val runAt: Long = 0L,
    val intervalMinutes: Long = 0L,
    val timeOfDay: String = "",
    val days: List<Int> = emptyList(), // Calendar.SUNDAY=1 ... SATURDAY=7
    val nextRunAt: Long = 0L,
    val status: String = "ACTIVE"
)
