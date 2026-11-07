package com.smart.smsmanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object SmsStorage {
    private const val PREFS = "SmartSMSData"
    private const val CONTACTS = "contacts"
    private const val TEMPLATES = "templates"
    private const val HISTORY = "sms_history"
    private const val SCHEDULES = "sms_schedules"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getContacts(context: Context): MutableList<Contact> {
        val result = mutableListOf<Contact>()
        try {
            val a = JSONArray(prefs(context).getString(CONTACTS, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                result.add(Contact(o.getString("name"), o.getString("phone")))
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveContacts(context: Context, items: List<Contact>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        prefs(context).edit().putString(CONTACTS, a.toString()).apply()
    }

    fun getTemplates(context: Context): MutableList<MessageTemplate> {
        val result = mutableListOf<MessageTemplate>()
        try {
            val a = JSONArray(prefs(context).getString(TEMPLATES, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                result.add(MessageTemplate(o.getString("title"), o.getString("content")))
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveTemplates(context: Context, items: List<MessageTemplate>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("title", it.title).put("content", it.content)) }
        prefs(context).edit().putString(TEMPLATES, a.toString()).apply()
    }

    fun getHistory(context: Context): MutableList<SmsRecord> {
        val result = mutableListOf<SmsRecord>()
        try {
            val a = JSONArray(prefs(context).getString(HISTORY, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                result.add(
                    SmsRecord(
                        o.getString("id"), o.getString("name"), o.getString("phone"),
                        o.getString("message"), o.getLong("time"), o.getString("status")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveHistory(context: Context, items: List<SmsRecord>) {
        val a = JSONArray()
        items.forEach { r ->
            a.put(JSONObject().apply {
                put("id", r.id); put("name", r.name); put("phone", r.phone)
                put("message", r.message); put("time", r.time); put("status", r.status)
            })
        }
        prefs(context).edit().putString(HISTORY, a.toString()).apply()
    }

    fun addHistory(context: Context, record: SmsRecord) {
        val all = getHistory(context)
        all.add(0, record)
        saveHistory(context, all)
    }

    fun updateHistoryStatus(context: Context, id: String, status: String) {
        val all = getHistory(context)
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            all[index] = all[index].copy(status = status)
            saveHistory(context, all)
        }
    }

    fun getSchedules(context: Context): MutableList<SmsSchedule> {
        val result = mutableListOf<SmsSchedule>()
        try {
            val a = JSONArray(prefs(context).getString(SCHEDULES, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val daysJson = o.optJSONArray("days") ?: JSONArray()
                val days = (0 until daysJson.length()).map { daysJson.getInt(it) }
                result.add(
                    SmsSchedule(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        phone = o.getString("phone"),
                        message = o.getString("message"),
                        mode = ScheduleMode.valueOf(o.getString("mode")),
                        runAt = o.optLong("runAt", 0L),
                        intervalMinutes = o.optLong("intervalMinutes", 0L),
                        timeOfDay = o.optString("timeOfDay", ""),
                        days = days,
                        nextRunAt = o.optLong("nextRunAt", 0L),
                        status = o.optString("status", "ACTIVE")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveSchedules(context: Context, items: List<SmsSchedule>) {
        val a = JSONArray()
        items.forEach { s ->
            val days = JSONArray()
            s.days.forEach { days.put(it) }
            a.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("phone", s.phone)
                put("message", s.message); put("mode", s.mode.name)
                put("runAt", s.runAt); put("intervalMinutes", s.intervalMinutes)
                put("timeOfDay", s.timeOfDay); put("days", days)
                put("nextRunAt", s.nextRunAt); put("status", s.status)
            })
        }
        prefs(context).edit().putString(SCHEDULES, a.toString()).apply()
    }

    fun getSchedule(context: Context, id: String): SmsSchedule? =
        getSchedules(context).firstOrNull { it.id == id }

    fun upsertSchedule(context: Context, item: SmsSchedule) {
        val all = getSchedules(context)
        val index = all.indexOfFirst { it.id == item.id }
        if (index >= 0) all[index] = item else all.add(0, item)
        saveSchedules(context, all)
    }

    fun removeSchedule(context: Context, id: String) {
        saveSchedules(context, getSchedules(context).filterNot { it.id == id })
    }
}
