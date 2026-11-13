package com.cujammu.pulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cujammu.pulse.ui.theme.*

data class MatchItem(
    val id: Long,
    val sport: String,
    val tournament: String,
    val teamA: String,
    val teamB: String,
    var scoreA: String,
    var scoreB: String,
    val status: String, // LIVE, COMPLETED, SCHEDULED
    var summary: String,
    val venue: String,
    val commentaries: MutableList<CommentaryEntry> = mutableListOf()
)

data class CommentaryEntry(
    val mark: String, // "18.4" or "74'"
    val text: String,
    val eventType: String = "REGULAR", // SIX, FOUR, WICKET, GOAL, POINT, REGULAR
    val isHighlight: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScorecardScreen(
    onBack: () -> Unit
) {
    var selectedSport by remember { mutableStateOf("ALL") }
    var selectedMatchId by remember { mutableStateOf<Long?>(1L) }
    var showAddBallDialog by remember { mutableStateOf(false) }
    var selectedBallType by remember { mutableStateOf("FOUR") }
    var ballCommentaryInput by remember { mutableStateOf("") }
    var overMarkInput by remember { mutableStateOf("18.3") }

    val sports = remember {
        listOf("ALL", "CRICKET", "FOOTBALL", "VOLLEYBALL", "BASKETBALL", "BADMINTON", "KABADDI")
    }

    // Live pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val matches = remember {
        mutableStateListOf(
            MatchItem(
                id = 1,
                sport = "CRICKET",
                tournament = "CUJ Inter-Departmental Premier League 2026",
                teamA = "CSIT Thunderbolts",
                teamB = "CSE Warriors",
                scoreA = "164/5 (18.2 ov)",
                scoreB = "161/8 (20.0 ov)",
                status = "LIVE",
                summary = "CSIT need 1 run in 10 balls to win with 5 wickets in hand!",
                venue = "Main Cricket & Athletics Ground",
                commentaries = mutableListOf(
                    CommentaryEntry("18.2", "FOUR! Smashed straight down the ground past mid-off! Scores are level now!", "FOUR", true),
                    CommentaryEntry("18.1", "Good length delivery on off stump, defended softly to cover for a quick single.", "REGULAR"),
                    CommentaryEntry("17.6", "SIX! What a massive hit over deep square leg! Out of the stadium!", "SIX", true),
                    CommentaryEntry("17.5", "Yorker right in the blockhole, dug out towards mid-on for a dot.", "REGULAR"),
                    CommentaryEntry("17.4", "WICKET! Trapped LBW! The batsman was looking to flick across the line.", "WICKET", true)
                )
            ),
            MatchItem(
                id = 2,
                sport = "FOOTBALL",
                tournament = "Vice-Chancellor's Football Cup 2026",
                teamA = "ECE Strikers",
                teamB = "Math Dynamos",
                scoreA = "2",
                scoreB = "1",
                status = "LIVE",
                summary = "Second Half (82') - ECE Strikers leading by 1 goal",
                venue = "University Football Arena",
                commentaries = mutableListOf(
                    CommentaryEntry("80'", "Spectacular fingertip save by ECE goalkeeper on a curling free-kick!", "REGULAR", true),
                    CommentaryEntry("67'", "GOAL! ECE forward slots it into the bottom-right corner!", "GOAL", true),
                    CommentaryEntry("42'", "Yellow card shown to Math defender for a reckless tackle.", "REGULAR")
                )
            ),
            MatchItem(
                id = 3,
                sport = "VOLLEYBALL",
                tournament = "Inter-Hostel Championship 2026",
                teamA = "Hostel A (Chenab)",
                teamB = "Hostel B (Tawi)",
                scoreA = "Set 2 (21-19)",
                scoreB = "Set 1 (25-23)",
                status = "LIVE",
                summary = "Chenab leading Set 2 after dropping Set 1",
                venue = "Indoor Sports Complex Court 1",
                commentaries = mutableListOf(
                    CommentaryEntry("Point 21", "Thunderous spike down the line by Chenab captain!", "POINT", true),
                    CommentaryEntry("Point 20", "Service ace landing right on the baseline!", "POINT")
                )
            ),
            MatchItem(
                id = 4,
                sport = "BADMINTON",
                tournament = "CUJ Annual Badminton Open",
                teamA = "Vikram (HRM)",
                teamB = "Aryan (Physics)",
                scoreA = "21, 19, 15",
                scoreB = "18, 21, 10",
                status = "COMPLETED",
                summary = "Vikram won by 2-1 (21-18, 19-21, 15-10)",
                venue = "Indoor Badminton Court 2"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cricbuzz Live Sports", fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .scale(pulseScale)
                                    .background(LiveRed, CircleShape)
                            )
                        }
                        Text("Real-time ball-by-ball scores & commentary", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddBallDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Log Ball", tint = CUJ_Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sport Category Selector
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sports) { sp ->
                    val isSelected = (selectedSport == sp)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSport = sp },
                        label = {
                            Text(
                                when (sp) {
                                    "CRICKET" -> "🏏 Cricket"
                                    "FOOTBALL" -> "⚽ Football"
                                    "VOLLEYBALL" -> "🏐 Volleyball"
                                    "BASKETBALL" -> "🏀 Basketball"
                                    "BADMINTON" -> "🏸 Badminton"
                                    "KABADDI" -> "🤼 Kabaddi"
                                    else -> "All Sports"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CUJ_NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            val filteredMatches = if (selectedSport == "ALL") matches else matches.filter { it.sport == selectedSport }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredMatches, key = { it.id }) { match ->
                    val isExpanded = (selectedMatchId == match.id)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMatchId = if (isExpanded) null else match.id
                            }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Header: Tournament name + Live Pulse Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    match.tournament,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    modifier = Modifier.weight(1f)
                                )

                                if (match.status == "LIVE") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(LiveRed.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .scale(pulseScale)
                                                .clip(CircleShape)
                                                .background(LiveRed)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("LIVE", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            match.status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Teams and Scores
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(match.teamA, fontWeight = FontWeight.Black, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(match.teamB, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(match.scoreA, fontWeight = FontWeight.Black, fontSize = 17.sp, color = CUJ_BlueAccent)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(match.scoreB, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Live Summary strip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CUJ_Gold.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SportsCricket, contentDescription = null, tint = CUJ_GoldWarm, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        match.summary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Venue & Ball-by-ball Chip ribbon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = ErrorRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(match.venue, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("6", "4", "1", ".", "W").forEach { b ->
                                        Surface(
                                            shape = CircleShape,
                                            color = when (b) {
                                                "6" -> Color(0xFF6366F1)
                                                "4" -> Color(0xFF0EA5E9)
                                                "W" -> LiveRed
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(b, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Commentary Accordion
                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Ball-by-Ball Live Commentary", fontWeight = FontWeight.Black, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Button(
                                            onClick = { showAddBallDialog = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Log Ball", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    match.commentaries.forEach { c ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (c.eventType) {
                                                    "SIX" -> Color(0xFF6366F1)
                                                    "FOUR" -> Color(0xFF0EA5E9)
                                                    "WICKET", "GOAL" -> LiveRed
                                                    "POINT" -> SuccessGreen
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    c.mark,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                c.text,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                lineHeight = 17.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // WORKABLE "ADD BALL / UPDATE SCORE" DIALOG
    if (showAddBallDialog) {
        AlertDialog(
            onDismissRequest = { showAddBallDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SportsCricket, contentDescription = null, tint = CUJ_Gold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Live Ball / Score Event", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Outcome:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SIX", "FOUR", "WICKET", "DOT", "1 RUN").forEach { ev ->
                            FilterChip(
                                selected = (selectedBallType == ev),
                                onClick = { selectedBallType = ev },
                                label = { Text(ev, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = overMarkInput,
                        onValueChange = { overMarkInput = it },
                        label = { Text("Over Mark / Timeline (e.g. 18.3)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = ballCommentaryInput,
                        onValueChange = { ballCommentaryInput = it },
                        label = { Text("Ball Commentary Text") },
                        placeholder = { Text("e.g. Smashed over extra cover for a glorious boundary!") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeMatch = matches.firstOrNull { it.id == selectedMatchId } ?: matches[0]
                        val text = if (ballCommentaryInput.isNotBlank()) ballCommentaryInput else when (selectedBallType) {
                            "SIX" -> "SIX! Huge strike sailing over deep midwicket into the stands!"
                            "FOUR" -> "FOUR! Driven crisply through the covers for a boundary!"
                            "WICKET" -> "WICKET! Clean bowled! Stumps shattered by a searing yorker!"
                            else -> "Single taken towards long-off, rotates strike smoothly."
                        }

                        activeMatch.commentaries.add(0, CommentaryEntry(overMarkInput, text, selectedBallType, true))
                        if (selectedBallType == "FOUR") {
                            activeMatch.scoreA = "168/5 (18.3 ov)"
                            activeMatch.summary = "🎉 CSIT THUNDERBOLTS WON BY 5 WICKETS!"
                        } else if (selectedBallType == "SIX") {
                            activeMatch.scoreA = "170/5 (18.3 ov)"
                            activeMatch.summary = "🎉 CSIT THUNDERBOLTS WON BY 5 WICKETS!"
                        }

                        ballCommentaryInput = ""
                        showAddBallDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Publish to Live Feed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
