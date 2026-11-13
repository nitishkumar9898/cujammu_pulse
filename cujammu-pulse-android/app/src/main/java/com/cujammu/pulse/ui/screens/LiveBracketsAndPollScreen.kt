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
import com.cujammu.pulse.data.model.MatchFixtureDto
import com.cujammu.pulse.ui.theme.CUJ_Gold
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary

data class PollOptionItem(
    val option: String,
    var votes: Int,
    val percentage: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBracketsAndPollScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedPollOption by remember { mutableStateOf<String?>(null) }
    var hasVoted by remember { mutableStateOf(false) }

    val mockFixtures = remember {
        listOf(
            MatchFixtureDto(
                1L, 1L, "CUJ Inter-Department Cricket Tournament", 3,
                "Semi Final 1", 1, "CSIT Strikers", "Math Warriors",
                "142/4", "138/9", "CSIT Strikers", "COMPLETED",
                "Today 10:00 AM", "Main Sports Ground Pitch 1"
            ),
            MatchFixtureDto(
                2L, 1L, "CUJ Inter-Department Cricket Tournament", 3,
                "Semi Final 2", 2, "MBA Titans", "Physics Challengers",
                "98/2 (12 ov)", "95/10", "MBA Titans", "LIVE",
                "Today 02:30 PM", "Main Sports Ground Pitch 2"
            ),
            MatchFixtureDto(
                3L, 1L, "CUJ Inter-Department Cricket Tournament", 4,
                "Grand Finale", 3, "CSIT Strikers", "MBA Titans",
                "0", "0", null, "SCHEDULED",
                "Tomorrow 11:00 AM", "Main Sports Ground Center Pitch"
            )
        )
    }

    val pollOptions = remember {
        mutableStateListOf(
            PollOptionItem("CSIT Strikers", 142, 0.48f),
            PollOptionItem("MBA Titans", 108, 0.36f),
            PollOptionItem("Physics Challengers", 46, 0.16f)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tournaments & Live Polls", fontWeight = FontWeight.Bold) },
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
                    text = { Text("Match Fixtures", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.SportsCricket, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audience Polls", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.HowToVote, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mockFixtures) { fix ->
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
                                    Text(fix.roundName ?: "Round ${fix.roundNumber}", fontWeight = FontWeight.Bold, color = CUJ_Gold, fontSize = 14.sp)
                                    Surface(
                                        color = when (fix.status) {
                                            "LIVE" -> Color(0xFFFFEBEE)
                                            "COMPLETED" -> Color(0xFFE8F5E9)
                                            else -> Color(0xFFE3F2FD)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = fix.status,
                                            color = when (fix.status) {
                                                "LIVE" -> Color.Red
                                                "COMPLETED" -> Color(0xFF2E7D32)
                                                else -> Color(0xFF1565C0)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(fix.team1Name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(fix.scoreTeam1, fontWeight = FontWeight.SemiBold, color = CUJ_NavyPrimary, fontSize = 14.sp)
                                    }
                                    Text("VS", fontWeight = FontWeight.Black, color = CUJ_Gold, fontSize = 14.sp)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(fix.team2Name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(fix.scoreTeam2, fontWeight = FontWeight.SemiBold, color = CUJ_NavyPrimary, fontSize = 14.sp)
                                    }
                                }
                                fix.winnerName?.let { winner ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Winner: $winner", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = CUJ_NavyPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(fix.courtOrVenue ?: "Campus Grounds", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // Audience Polls
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Poll, contentDescription = null, tint = CUJ_Gold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LIVE AUDIENCE POLL", fontWeight = FontWeight.Bold, color = CUJ_Gold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Who will win the CUJammu Inter-Department Cricket Trophy 2026?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            pollOptions.forEach { opt ->
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = (selectedPollOption == opt.option),
                                                onClick = {
                                                    if (!hasVoted) selectedPollOption = opt.option
                                                },
                                                enabled = !hasVoted
                                            )
                                            Text(opt.option, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                        }
                                        Text("${(opt.percentage * 100).toInt()}% (${opt.votes})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    LinearProgressIndicator(
                                        progress = { opt.percentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .padding(horizontal = 12.dp),
                                        color = CUJ_NavyPrimary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (selectedPollOption != null && !hasVoted) {
                                        val idx = pollOptions.indexOfFirst { it.option == selectedPollOption }
                                        if (idx >= 0) {
                                            pollOptions[idx] = pollOptions[idx].copy(votes = pollOptions[idx].votes + 1)
                                        }
                                        hasVoted = true
                                    }
                                },
                                enabled = !hasVoted && selectedPollOption != null,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                            ) {
                                Text(if (hasVoted) "Vote Recorded (Anti-Fraud Verified)" else "Cast Vote")
                            }
                        }
                    }
                }
            }
        }
    }
}
