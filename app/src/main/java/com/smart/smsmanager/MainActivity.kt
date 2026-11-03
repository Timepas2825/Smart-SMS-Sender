package com.smart.smsmanager

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    listOf("Dashboard", "Contacts", "Templates").forEach {
                        Button(
                            onClick = {
                                selectedTab = it
                                notice = ""
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(it)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Scheduler", "History", "Settings").forEach {
                        OutlinedButton(
                            onClick = {
                                selectedTab = it
                                notice = ""
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(it)
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
                            onClick = { notice = "SMS automation is not enabled yet." },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("START") }

                        OutlinedButton(
                            onClick = { notice = "No active automation to pause." },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("PAUSE") }

                        OutlinedButton(
                            onClick = { notice = "No paused automation to resume." },
                            modifier =
