package com.smart.smsmanager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val historyId = intent.getStringExtra("history_id") ?: return
        when (intent.action) {
            ACTION_SMS_SENT -> {
                val status = if (resultCode == Activity.RESULT_OK) "SENT" else "FAILED"
                SmsStorage.updateHistoryStatus(context, historyId, status)
            }
            ACTION_SMS_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    SmsStorage.updateHistoryStatus(context, historyId, "DELIVERED")
                }
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.smart.smsmanager.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.smart.smsmanager.SMS_DELIVERED"
    }
}
