
package com.smart.smsmanager

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

private const val PREFS = "SmartSMSData"
private const val HISTORY_KEY = "sms_history"
private const val ACTION_SENT = "com.smart.smsmanager.SMS_SENT"
private const val ACTION_DELIVERED = "com.smart.smsmanager.SMS_DELIVERED"

private fun historyFrom(context: Context): MutableList<SmsRecord> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val result = mutableListOf<SmsRecord>()

    try {
        val array = JSONArray(prefs.getString(HISTORY_KEY, "[]"))
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            result.add(
                SmsRecord(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("phone"),
                    o.getString("message"),
                    o.getLong("time"),
                    o.getString("status")
                )
            )
        }
    } catch (_: Exception) {}

    return result
}

private fun saveHistory(context: Context, records: List<SmsRecord>) {
    val array = JSONArray()
    records.forEach { r ->
        array.put(JSONObject().apply {
            put("id", r.id)
            put("name", r.name)
            put("phone", r.phone)
            put("message", r.message)
            put("time", r.time)
            put("status", r.status)
        })
    }

    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(HISTORY_KEY, array.toString())
        .apply()
}

private fun updateHistoryStatus(
    context: Context,
    id: String,
    status: String
) {
    val records = historyFrom(context)
    val index = records.indexOfFirst { it.id == id }

    if (index >= 0) {
        records[index] = records[index].copy(status = status)
        saveHistory(context, records)
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("history_id") ?: return

        when (intent.action) {
            ACTION_SENT -> {
                val status = if (resultCode == Activity.RESULT_OK) {
                    "SENT"
                } else {
                    "FAILED"
                }
                updateHistoryStatus(context, id, status)
            }

            ACTION_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    updateHistoryStatus(context, id, "DELIVERED")
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartSMSApp()
        }
    }
}

@Composable
fun SmartSMSApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val contacts = remember {
        mutableStateListOf<Contact>().apply {
            try {
                val array = JSONArray(prefs.getString("contacts", "[]"))
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(Contact(item.getString("name"), item.getString("phone")))
                }
            } catch (_: Exception) {}
        }
    }

    val templates = remember {
        mutableStateListOf<MessageTemplate>().apply {
            try {
                val array = JSONArray(prefs.getString("templates", "[]"))
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        MessageTemplate(
                            item.getString("title"),
                            item.getString("content")
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    val history = remember {
        mutableStateListOf<SmsRecord>().apply {
            addAll(historyFrom(context))
        }
    }

    var selectedTab by remember { mutableStateOf("Dashboard") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var editingContact by remember { mutableIntStateOf(-1) }

    var templateTitle by remember { mutableStateOf("") }
    var templateContent by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableIntStateOf(-1) }

    var selectedContact by remember { mutableIntStateOf(-1) }
    var selectedTemplate by remember { mutableIntStateOf(-1) }

    var notice by remember { mutableStateOf("") }

    fun refreshHistory() {
        history.clear()
        history.addAll(historyFrom(context))
    }

    fun saveContacts() {
        val array = JSONArray()
        contacts.forEach {
            array.put(JSONObject().apply {
                put("name", it.name)
                put("phone", it.phone)
            })
        }
        prefs.edit().putString("contacts", array.toString()).apply()
    }

    fun saveTemplates() {
        val array = JSONArray()
        templates.forEach {
            array.put(JSONObject().apply {
                put("title", it.title)
                put("content", it.content)
            })
        }
        prefs.edit().putString("templates", array.toString()).apply()
    }

    fun sendSmsNow() {
        if (selectedContact !in contacts.indices) {
            notice = "Please select a contact."
            return
        }

        if (selectedTemplate !in templates.indices) {
            notice = "Please select a saved message."
            return
        }

        val contact = contacts[selectedContact]
        val message = templates[selectedTemplate].content.trim()
        val phone = contact.phone.trim()

        if (phone.isBlank() || message.isBlank()) {
            notice = "Mobile number or message is empty."
            return
        }

        val id = UUID.randomUUID().toString()

        val record = SmsRecord(
            id = id,
            name = contact.name,
            phone = phone,
            message = message,
            time = System.currentTimeMillis(),
            status = "SUBMITTED"
        )

        val updated = historyFrom(context).toMutableList()
        updated.add(0, record)
        saveHistory(context, updated)
        refreshHistory()

        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()

            val parts = smsManager.divideMessage(message)

            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            parts.forEachIndexed { index, _ ->
                val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = ACTION_SENT
                    putExtra("history_id", id)
                }

                val deliveryIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = ACTION_DELIVERED
                    putExtra("history_id", id)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE

                sentIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        (id.hashCode() * 31) + index,
                        sentIntent,
                        flags
                    )
                )

                deliveryIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        (id.hashCode() * 31) + 10000 + index,
                        deliveryIntent,
                        flags
                    )
                )
            }

            if (parts.size <= 1) {
                smsManager.sendTextMessage(
                    phone,
                    null,
                    message,
                    sentIntents[0],
                    deliveryIntents[0]
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    phone,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }

            notice = "SMS submitted. Waiting for sending status."
        } catch (e: Exception) {
            updateHistoryStatus(context, id, "FAILED")
            refreshHistory()
            notice = "SMS sending failed: ${e.message ?: "Unknown error"}"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                sendSmsNow()
            } else {
                notice = "SMS permission denied."
            }
        }

    fun requestAndSendSms() {
        if (selectedContact !in contacts.indices ||
            selectedTemplate !in templates.indices
        ) {
            notice = "Select a contact and message first."
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            sendSmsNow()
        } else {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    val todayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    val sentToday = history.count {
        it.time >= todayStart &&
            (it.status == "SENT" || it.status == "DELIVERED")
    }

    val failedCount = history.count { it.status == "FAILED" }
    val pendingCount = history.count {
        it.status == "SUBMITTED" || it.status == "SENT"
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 4.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Smart SMS Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Your personal SMS assistant")
                    }
                }
            }
        ) { padding ->

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf("Dashboard", "Contacts", "Templates").forEach { tab ->
                        Button(
                            onClick = {
                                selectedTab = tab
                                notice = ""
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(3.dp)
                        ) {
                            Text(tab)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf("Scheduler", "History", "Settings").forEach { tab ->
                        OutlinedButton(
                            onClick = {
                                selectedTab = tab
                                notice = ""
                                if (tab == "History") refreshHistory()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(3.dp)
                        ) {
                            Text(tab)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                when (selectedTab) {

                    "Dashboard" -> {
                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardCard(
                                "Contacts",
                                contacts.size.toString(),
                                Modifier.weight(1f)
                            )
                            DashboardCard(
                                "Templates",
                                templates.size.toString(),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardCard(
                                "Sent Today",
                                sentToday.toString(),
                                Modifier.weight(1f)
                            )
                            DashboardCard(
                                "Pending",
                                pendingCount.toString(),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardCard(
                                "Failed",
                                failedCount.toString(),
                                Modifier.weight(1f)
                            )
                            DashboardCard(
                                "Total History",
                                history.size.toString(),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            "Automation Controls",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                notice = "Automatic scheduling is not implemented yet."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("START")
                        }

                        OutlinedButton(
                            onClick = {
                                notice = "No active automation to pause."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PAUSE")
                        }

                        OutlinedButton(
                            onClick = {
                                notice = "No paused automation to resume."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("RESUME")
                        }

                        OutlinedButton(
                            onClick = {
                                notice = "No active automation to stop."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("STOP")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Next Scheduled Message", fontWeight = FontWeight.Bold)
                        Text("No schedule configured")

                        HorizontalDivider(Modifier.padding(vertical = 18.dp))

                        Text(
                            "Prepare and Send SMS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))
                        Text("Select Contact", fontWeight = FontWeight.Bold)

                        if (contacts.isEmpty()) {
                            Text("Add contacts first.")
                        }

                        contacts.forEachIndexed { index, contact ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedContact == index,
                                    onClick = { selectedContact = index }
                                )
                                Column {
                                    Text(contact.name)
                                    Text(
                                        contact.phone,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Select Saved Message", fontWeight = FontWeight.Bold)

                        if (templates.isEmpty()) {
                            Text("Add saved messages first.")
                        }

                        templates.forEachIndexed { index, template ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedTemplate == index,
                                    onClick = { selectedTemplate = index }
                                )
                                Text(template.title)
                            }
                        }

                        if (selectedContact in contacts.indices &&
                            selectedTemplate in templates.indices
                        ) {
                            Spacer(Modifier.height(10.dp))

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Message Preview", fontWeight = FontWeight.Bold)
                                    Text("To: ${contacts[selectedContact].name}")
                                    Text("Number: ${contacts[selectedContact].phone}")
                                    Spacer(Modifier.height(8.dp))
                                    Text(templates[selectedTemplate].content)
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = { requestAndSendSms() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SEND SMS")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(notice, color = MaterialTheme.colorScheme.primary)
                    }

                    "Contacts" -> {
                        Text(
                            "Contact Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = { Text("Contact Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            label = { Text("Mobile Number") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (contactName.isBlank() || contactPhone.isBlank()) {
                                    notice = "Enter name and mobile number."
                                } else {
                                    val item = Contact(
                                        contactName.trim(),
                                        contactPhone.trim()
                                    )

                                    if (editingContact == -1) {
                                        contacts.add(item)
                                        notice = "Contact added."
                                    } else if (editingContact in contacts.indices) {
                                        contacts[editingContact] = item
                                        notice = "Contact updated."
                                    }

                                    saveContacts()
                                    contactName = ""
                                    contactPhone = ""
                                    editingContact = -1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (editingContact == -1) "ADD CONTACT"
                                else "UPDATE CONTACT"
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(notice, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(Modifier.padding(vertical = 14.dp))
                        Text("Saved Contacts (${contacts.size})", fontWeight = FontWeight.Bold)

                        contacts.forEachIndexed { index, contact ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(contact.name, fontWeight = FontWeight.Bold)
                                    Text(contact.phone)

                                    Row {
                                        TextButton(onClick = {
                                            contactName = contact.name
                                            contactPhone = contact.phone
                                            editingContact = index
                                        }) {
                                            Text("EDIT")
                                        }

                                        TextButton(onClick = {
                                            contacts.removeAt(index)
                                            saveContacts()

                                            if (selectedContact == index) {
                                                selectedContact = -1
                                            } else if (selectedContact > index) {
                                                selectedContact--
                                            }

                                            editingContact = -1
                                            contactName = ""
                                            contactPhone = ""
                                            notice = "Contact deleted."
                                        }) {
                                            Text("DELETE")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Templates" -> {
                        Text(
                            "Message Templates",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = templateTitle,
                            onValueChange = { templateTitle = it },
                            label = { Text("Message Title") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = templateContent,
                            onValueChange = { templateContent = it },
                            label = { Text("Message Content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (templateTitle.isBlank() ||
                                    templateContent.isBlank()
                                ) {
                                    notice = "Enter title and message."
                                } else {
                                    val item = MessageTemplate(
                                        templateTitle.trim(),
                                        templateContent.trim()
                                    )

                                    if (editingTemplate == -1) {
                                        templates.add(item)
                                        notice = "Template added."
                                    } else if (editingTemplate in templates.indices) {
                                        templates[editingTemplate] = item
                                        notice = "Template updated."
                                    }

                                    saveTemplates()
                                    templateTitle = ""
                                    templateContent = ""
                                    editingTemplate = -1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (editingTemplate == -1) "ADD TEMPLATE"
                                else "UPDATE TEMPLATE"
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(notice, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(Modifier.padding(vertical = 14.dp))
                        Text("Saved Messages (${templates.size})", fontWeight = FontWeight.Bold)

                        templates.forEachIndexed { index, template ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(template.title, fontWeight = FontWeight.Bold)
                                    Text(template.content)

                                    Row {
                                        TextButton(onClick = {
                                            templateTitle = template.title
                                            templateContent = template.content
                                            editingTemplate = index
                                        }) {
                                            Text("EDIT")
                                        }

                                        TextButton(onClick = {
                                            templates.removeAt(index)
                                            saveTemplates()

                                            if (selectedTemplate == index) {
                                                selectedTemplate = -1
                                            } else if (selectedTemplate > index) {
                                                selectedTemplate--
                                            }

                                            editingTemplate = -1
                                            templateTitle = ""
                                            templateContent = ""
                                            notice = "Template deleted."
                                        }) {
                                            Text("DELETE")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "History" -> {
                        Text(
                            "SMS History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = { refreshHistory() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("REFRESH HISTORY")
                        }

                        Text("Total records: ${history.size}")

                        if (history.isEmpty()) {
                            Text("No SMS history available.")
                        }

                        history.sortedByDescending { it.time }.forEach { record ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(record.name, fontWeight = FontWeight.Bold)
                                    Text(record.phone)
                                    Spacer(Modifier.height(5.dp))
                                    Text(record.message)
                                    Spacer(Modifier.height(5.dp))

                                    Text(
                                        SimpleDateFormat(
                                            "dd MMM yyyy, hh:mm a",
                                            Locale.getDefault()
                                        ).format(Date(record.time)),
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Text(
                                        "Status: ${record.status}",
                                        fontWeight = FontWeight.Bold,
                                        color = when (record.status) {
                                            "DELIVERED" -> MaterialTheme.colorScheme.primary
                                            "FAILED" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.secondary
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "Scheduler" -> {
                        Text(
                            "SMS Scheduler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Scheduling is the next development stage.")
                        Text("Date, time, repeat days and sending intervals are not active yet.")
                    }

                    "Settings" -> {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("App: Smart SMS Manager")
                        Text("Version: 1.0")
                        Text("SMS permission is required to send messages.")
                        Text("SMS charges may apply according to your mobile plan.")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
