package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cujammu.pulse.data.model.SupportTicketDto
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpdeskScreen(
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("EVENT_REGISTRATION") }

    val mockTickets = remember {
        mutableStateListOf(
            SupportTicketDto(
                1L, "CUJ-TKT-202609-1001", "Dual-QR Entry scanner timing issue",
                "Scanner took 5 seconds to record entry at Main Auditorium during prelims.",
                "TECH_SUPPORT", "HIGH", "OPEN", "Nitish Kumar", null, null, "2026-09-15 11:20", null
            ),
            SupportTicketDto(
                2L, "CUJ-TKT-202609-1002", "Hostel Night Curfew Gate-Pass Request",
                "Applied for weekend leave to Jammu city; awaiting warden sign-off.",
                "HOSTEL_LEAVE", "MEDIUM", "RESOLVED", "Nitish Kumar", "Warden Office",
                "Gate pass approved with QR code valid till 9:00 PM.", "2026-09-14 15:45", "2026-09-14 17:30"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("24/7 Helpdesk & Grievance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CUJ_NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.SupportAgent, contentDescription = null) },
                text = { Text("Raise Ticket") },
                containerColor = CUJ_NavyPrimary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("My Grievance & Support Tickets", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mockTickets) { ticket ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ticket.ticketNumber, fontWeight = FontWeight.Bold, color = CUJ_NavyPrimary, fontSize = 14.sp)
                                Surface(
                                    color = when (ticket.status) {
                                        "RESOLVED" -> Color(0xFFE8F5E9)
                                        "IN_PROGRESS" -> Color(0xFFE3F2FD)
                                        else -> Color(0xFFFFF3E0)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = ticket.status,
                                        color = when (ticket.status) {
                                            "RESOLVED" -> Color(0xFF2E7D32)
                                            "IN_PROGRESS" -> Color(0xFF1565C0)
                                            else -> Color(0xFFE65100)
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(ticket.subject, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ticket.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ticket.resolutionNotes?.let { notes ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFF1F8E9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Resolution: $notes", fontSize = 12.sp, color = Color(0xFF33691E), modifier = Modifier.padding(8.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Created: ${ticket.createdAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Raise New Support Ticket") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Subject") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Detailed Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (subject.isNotBlank() && description.isNotBlank()) {
                                mockTickets.add(
                                    0,
                                    SupportTicketDto(
                                        System.currentTimeMillis(),
                                        "CUJ-TKT-202609-${(1003..1999).random()}",
                                        subject,
                                        description,
                                        selectedCategory,
                                        "MEDIUM",
                                        "OPEN",
                                        "Nitish Kumar",
                                        null,
                                        null,
                                        "Just now",
                                        null
                                    )
                                )
                                subject = ""
                                description = ""
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                    ) {
                        Text("Submit Ticket")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
