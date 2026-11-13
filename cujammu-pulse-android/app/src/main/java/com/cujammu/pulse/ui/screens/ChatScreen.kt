package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.cujammu.pulse.ui.theme.CUJ_Gold
import com.cujammu.pulse.ui.theme.CUJ_NavyDark
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary

data class UiMessage(
    val id: Long,
    val senderName: String,
    val role: String,
    val content: String,
    val time: String,
    val isMe: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showAttachmentToast by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            UiMessage(1, "Prof. Head of Dept", "FACULTY", "Welcome to CSIT Department Official Group! Annual Sports Meet and Tech Summit registrations are now open in the app.", "10:14 AM", false),
            UiMessage(2, "Aarav Sharma", "STUDENT", "Sir, will attendance for the AI Summit be tracked using Dual-QR at entry and exit?", "10:18 AM", false),
            UiMessage(3, "System Admin", "ADMIN", "Yes! Both Entry QR-1 and Exit QR-2 are mandatory to earn your verified certificate.", "10:20 AM", false),
            UiMessage(4, "Rahul Kumar", "STUDENT", "Thank you, Sir! Just registered.", "10:25 AM", true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CSIT Department Students", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("142 members • Active Community", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Night Silence / Slow Mode Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CUJ_Gold.copy(alpha = 0.15f))
                    .padding(vertical = 6.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Nightlight, contentDescription = null, modifier = Modifier.size(14.dp), tint = CUJ_NavyPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Night-Silence active 10 PM - 7 AM • Slow Mode: 1 msg/min", fontSize = 11.sp, color = CUJ_NavyPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // Message List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                    ) {
                        if (!msg.isMe) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(msg.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CUJ_NavyPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (msg.role == "ADMIN" || msg.role == "FACULTY") CUJ_Gold.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(msg.role, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (msg.role == "ADMIN" || msg.role == "FACULTY") CUJ_NavyDark else Color.DarkGray)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Card(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (msg.isMe) 14.dp else 2.dp,
                                bottomEnd = if (msg.isMe) 2.dp else 14.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isMe) CUJ_NavyPrimary else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.content,
                                    fontSize = 13.sp,
                                    color = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.time,
                                    fontSize = 9.sp,
                                    color = if (msg.isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentDialog = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = CUJ_NavyPrimary)
                    }

                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message #CSIT-Students...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 100.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                messages.add(
                                    UiMessage(
                                        id = System.currentTimeMillis(),
                                        senderName = "Rahul Kumar",
                                        role = "STUDENT",
                                        content = messageText,
                                        time = "Just now",
                                        isMe = true
                                    )
                                )
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .background(CUJ_NavyPrimary, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Attachment Dialog
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Share in Department Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListItem(
                        headlineContent = { Text("Campus Notice / Circular (PDF)", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CUJ_NavyPrimary) },
                        modifier = Modifier.clickable {
                            messages.add(UiMessage(System.currentTimeMillis(), "Rahul Kumar", "STUDENT", "📄 Shared Document: CUJ_Sports_Schedule_2026.pdf", "Just now", true))
                            showAttachmentDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Campus Event Photo / Banner", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = CUJ_Gold) },
                        modifier = Modifier.clickable {
                            messages.add(UiMessage(System.currentTimeMillis(), "Rahul Kumar", "STUDENT", "📸 Shared Image: Main_Auditorium_Setup.jpg", "Just now", true))
                            showAttachmentDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Project GitHub Repository Link", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = CUJ_NavyPrimary) },
                        modifier = Modifier.clickable {
                            messages.add(UiMessage(System.currentTimeMillis(), "Rahul Kumar", "STUDENT", "🔗 Shared Repository: https://github.com/nitishkumar9898/cuj-pulse", "Just now", true))
                            showAttachmentDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Options Dialog
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Group Settings & Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Channel: #CSIT-Department-Official", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Active Members: 142 enrolled students & faculty members", fontSize = 12.sp)
                    Text("• Department: Computer Science & Information Technology", fontSize = 12.sp)
                    Text("• Encryption: End-to-End Encrypted Campus Node", fontSize = 12.sp, color = com.cujammu.pulse.ui.theme.SuccessGreen, fontWeight = FontWeight.SemiBold)
                    Text("• Slow Mode: 1 message per minute to prevent spam", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOptionsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }
}
