 
package com.smart.smsmanager

import android.os.Bundle
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
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

data class Contact(val name: String, val phone: String)
data class MessageTemplate(val title: String, val content: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartSMSApp() }
    }
}

@Composable
fun SmartSMSApp() {

    val context = androidx.compose.ui.platform.LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("SmartSMSData", Context.MODE_PRIVATE)
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
                    add(MessageTemplate(item.getString("title"), item.getString("content")))
                }
            } catch (_: Exception) {}
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

    // Send the selected message using the phone's SMS service.
    fun sendSmsNow() {
        if (selectedContact !in contacts.indices) {
            notice = "Please select a contact."
            return
        }

        if (selectedTemplate !in templates.indices) {
            notice = "Please select a saved message."
            return
        }

        val phone = contacts[selectedContact].phone.trim()
        val message = templates[selectedTemplate].content.trim()

        if (phone.isBlank() || message.isBlank()) {
            notice = "Mobile number or message is empty."
            return
        }

        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()

            val parts = smsManager.divideMessage(message)

            if (parts.size <= 1) {
                smsManager.sendTextMessage(
                    phone,
                    null,
                    message,
                    null,
                    null
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    phone,
                    null,
                    parts,
                    null,
                    null
                )
            }

            notice = "SMS submitted to your phone's SMS service."
        } catch (e: Exception) {
            notice = "SMS sending failed: ${e.message ?: "Unknown error"}"
        }
    }

    // Ask for permission when the user taps SEND SMS.
    val smsPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                sendSmsNow()
            } else {
                notice = "SMS permission denied. Allow SMS permission in app settings."
            }
        }

    fun requestAndSendSms() {
        if (selectedContact !in contacts.indices) {
            notice = "Please select a contact."
            return
        }

        if (selectedTemplate !in templates.indices) {
            notice = "Please select a saved message."
            return
        }

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            sendSmsNow()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Dashboard", "Contacts", "Templates").forEach { tab ->
                        Button(
                            onClick = {
                                selectedTab = tab
                                notice = ""
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(tab)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Scheduler", "History", "Settings").forEach { tab ->
                        OutlinedButton(
                            onClick = {
                                selectedTab = tab
                                notice = ""
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
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

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardCard("Contacts", contacts.size.toString(), Modifier.weight(1f))
                            DashboardCard("Templates", templates.size.toString(), Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardCard("Sent Today", "0", Modifier.weight(1f))
                            DashboardCard("Pending", "0", Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "Automation Controls",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                notice = "Automatic SMS scheduling is not implemented yet."
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
                                notice = "Automation stopped."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("STOP")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Next Scheduled Message", fontWeight = FontWeight.Bold)
                        Text("No schedule configured")

                        HorizontalDivider(Modifier.padding(vertical = 20.dp))

                        Text(
                            "Prepare and Send SMS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        Text("Select Contact", fontWeight = FontWeight.Bold)

                        if (contacts.isEmpty()) {
                            Text("No contacts available. Add contacts first.")
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

                        Spacer(Modifier.height(12.dp))

                        Text("Select Saved Message", fontWeight = FontWeight.Bold)

                        if (templates.isEmpty()) {
                            Text("No saved messages. Add templates first.")
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

                        Spacer(Modifier.height(12.dp))

                        if (
                            selectedContact in contacts.indices &&
                            selectedTemplate in templates.indices
                        ) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Message Preview", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("To: ${contacts[selectedContact].name}")
                                    Text("Number: ${contacts[selectedContact].phone}")
                                    Spacer(Modifier.height(8.dp))
                                    Text(templates[selectedTemplate].content)
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { requestAndSendSms() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SEND SMS")
                            }
                        } else {
                            Text("Select a contact and saved message to preview and send.")
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            notice,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    "Contacts" -> {
                        Text(
                            "Contact Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(16.dp))

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

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (contactName.isBlank() || contactPhone.isBlank()) {
                                    notice = "Enter name and mobile number."
                                } else {
                                    val contact = Contact(
                                        contactName.trim(),
                                        contactPhone.trim()
                                    )

                                    if (editingContact == -1) {
                                        contacts.add(contact)
                                        notice = "Contact added successfully."
                                    } else {
                                        contacts[editingContact] = contact
                                        notice = "Contact updated successfully."
                                    }

                                    saveContacts()
                                    contactName = ""
                                    contactPhone = ""
                                    editingContact = -1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (editingContact == -1) "ADD CONTACT" else "UPDATE CONTACT")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(notice, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Text("Saved Contacts (${contacts.size})", fontWeight = FontWeight.Bold)

                        contacts.forEachIndexed { index, contact ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(contact.name, fontWeight = FontWeight.Bold)
                                    Text(contact.phone)

                                    Row {
                                        TextButton(
                                            onClick = {
                                                contactName = contact.name
                                                contactPhone = contact.phone
                                                editingContact = index
                                            }
                                        ) {
                                            Text("EDIT")
                                        }

                                        TextButton(
                                            onClick = {
                                                contacts.removeAt(index)
                                                saveContacts()
                                                if (selectedContact == index) selectedContact = -1
                                                else if (selectedContact > index) selectedContact--
                                                editingContact = -1
                                                contactName = ""
                                                contactPhone = ""
                                                notice = "Contact deleted."
                                            }
                                        ) {
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

                        Spacer(Modifier.height(16.dp))

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
                            label = { Text("Write Your Message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (templateTitle.isBlank() || templateContent.isBlank()) {
                                    notice = "Enter title and message."
                                } else {
                                    val template = MessageTemplate(
                                        templateTitle.trim(),
                                        templateContent.trim()
                                    )

                                    if (editingTemplate == -1) {
                                        templates.add(template)
                                        notice = "Message saved successfully."
                                    } else {
                                        templates[editingTemplate] = template
                                        notice = "Message updated successfully."
                                    }

                                    saveTemplates()
                                    templateTitle = ""
                                    templateContent = ""
                                    editingTemplate = -1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (editingTemplate == -1) "SAVE MESSAGE" else "UPDATE MESSAGE")
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(notice, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Text("Saved Messages (${templates.size})", fontWeight = FontWeight.Bold)

                        templates.forEachIndexed { index, template ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(template.title, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(template.content)

                                    Row {
                                        TextButton(
                                            onClick = {
                                                templateTitle = template.title
                                                templateContent = template.content
                                                editingTemplate = index
                                            }
                                        ) {
                                            Text("EDIT")
                                        }

                                        TextButton(
                                            onClick = {
                                                templates.removeAt(index)
                                                saveTemplates()
                                                if (selectedTemplate == index) selectedTemplate = -1
                                                else if (selectedTemplate > index) selectedTemplate--
                                                editingTemplate = -1
                                                templateTitle = ""
                                                templateContent = ""
                                                notice = "Message deleted."
                                            }
                                        ) {
                                            Text("DELETE")
                                        }
                                    }
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
                        Spacer(Modifier.height(16.dp))
                        Text("No schedules configured.")
                        Text("Automatic scheduling will be added in a later development stage.")
                    }

                    "History" -> {
                        Text(
                            "SMS History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No SMS history available yet.")
                        Text("Message history and delivery tracking are not implemented yet.")
                    }

                    "Settings" -> {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Smart SMS Manager")
                        Text("Version 1.0")
                        Spacer(Modifier.height(12.dp))
                        Text("Contacts saved: ${contacts.size}")
                        Text("Templates saved: ${templates.size}")
                        Text("SMS permission is requested when you tap SEND SMS.")
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
    Card(modifier = modifier.height(105.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(text = title)
        }
    }
}
