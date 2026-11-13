package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsAndJudgingScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var joinSuccessMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teams & Live Leaderboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CUJ_NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CUJ_NavyPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Team Roster & Join", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Live Leaderboard", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Leaderboard, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // Team Roster & Join Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Join a Team with Invite Code", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = inviteCodeInput,
                                        onValueChange = { inviteCodeInput = it.uppercase() },
                                        placeholder = { Text("e.g. CUJ-A9X4K2") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (inviteCodeInput.isNotBlank()) {
                                                joinSuccessMessage = "Team service is not connected in this build; no roster change was made."
                                                inviteCodeInput = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                                    ) {
                                        Text("Join")
                                    }
                                }
                                joinSuccessMessage?.let { msg ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(msg, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    item {
                        Text("Registered Teams for Event", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    item { Text("No verified team roster is available yet. Join and roster changes are disabled until the authenticated team API is connected.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else {
                // Live Leaderboard Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = CUJ_Gold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = CUJ_Gold, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Final Round Live Scores", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Official scores verified by university panel judges", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item { Text("No verified leaderboard is available yet. Scores will appear only after authenticated server publication and judge authorization.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
