package com.smart.smsmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Contact(
    val name: String,
    val phone: String
)

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

    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "SmartSMSData",
            android.content.Context.MODE_PRIVATE
        )
    }

    val contacts = remember {
        mutableStateListOf<Contact>().apply {
            try {
                val saved = preferences.getString("contacts", "[]")
                val array = JSONArray(saved)

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Contact(
                            item.getString("name"),
                            item.getString("phone")
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    var selectedTab by remember {
        mutableStateOf("Dashboard")
    }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }

    fun saveContacts() {
        val array = JSONArray()

        contacts.forEach {
            val item = JSONObject()
            item.put("name", it.name)
            item.put("phone", it.phone)
            array.put(item)
        }

        preferences.edit()
            .putString("contacts", array.toString())
            .apply()
    }

    MaterialTheme {

        Scaffold(
            topBar = {
                Surface(tonalElevation = 4.dp) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Text(
                    "Manage Application",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    "Dashboard",
                    "Contacts",
                    "Templates",
                    "Scheduler",
                    "History",
                    "Settings"
                ).forEach { tab ->

                    OutlinedButton(
                        onClick = {
                            selectedTab = tab
                            message = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tab)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (selectedTab) {

                    "Dashboard" -> {

                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        DashboardCard(
                            "Contacts",
                            contacts.size.toString(),
                            Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DashboardCard(
                            "Templates",
                            "0",
                            Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DashboardCard(
                            "Sent Today",
                            "0",
                            Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DashboardCard(
                            "Pending",
                            "0",
                            Modifier.fillMaxWidth()
                        )
                    }

                    "Contacts" -> {

                        Text(
                            "Contact Management",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Contact Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Number") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {

                                val cleanPhone = phone.filter {
                                    it.isDigit()
                                }

                                when {
                                    name.isBlank() ->
                                        message = "Please enter contact name"

                                    cleanPhone.length !in 10..15 ->
                                        message = "Enter a valid mobile number"

                                    contacts.anyIndexed { index, contact ->
                                        contact.phone == phone.trim() &&
                                            index != editingIndex
                                    } ->
                                        message = "This number already exists"

                                    else -> {
                                        val contact = Contact(
                                            name.trim(),
                                            phone.trim()
                                        )

                                        if (editingIndex == -1) {
                                            contacts.add(contact)
                                            message = "Contact saved successfully"
                                        } else {
                                            contacts[editingIndex] = contact
                                            message = "Contact updated successfully"
                                        }

                                        saveContacts()
                                        name = ""
                                        phone = ""
                                        editingIndex = -1
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (editingIndex == -1) {
                                    "SAVE CONTACT"
                                } else {
                                    "UPDATE CONTACT"
                                }
                            )
                        }

                        Text(
                            message,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "Saved Contacts (${contacts.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        contacts.forEachIndexed { index, contact ->

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        contact.name,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(contact.phone)

                                    Row(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                name = contact.name
                                                phone = contact.phone
                                                editingIndex = index
                                                message = "Editing contact"
                                            }
                                        ) {
                                            Text("EDIT")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                contacts.removeAt(index)
                                                saveContacts()

                                                if (editingIndex == index) {
                                                    name = ""
                                                    phone = ""
                                                    editingIndex = -1
                                                }

                                                message = "Contact deleted"
                                            }
                                        ) {
                                            Text("DELETE")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            "$selectedTab feature coming soon",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

fun <T> List<T>.anyIndexed(
    predicate: (Int, T) -> Boolean
): Boolean {
    return indices.any { predicate(it, this[it]) }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(105.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(title)
        }
    }
}
