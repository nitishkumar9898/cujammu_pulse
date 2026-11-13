package com.cujammu.pulse.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cujammu.pulse.R
import com.cujammu.pulse.ui.theme.*

data class QuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

data class SampleEvent(
    val id: Long,
    val title: String,
    val department: String,
    val date: String,
    val venue: String,
    val seatsLeft: Int,
    val totalSeats: Int,
    val category: String,
    val fee: String = "FREE"
)

data class CampusNotification(
    val id: Long,
    val title: String,
    val message: String,
    val timeAgo: String,
    val type: String, // SPORTS, GATEPASS, ACADEMIC, EVENT
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit
) {
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var selectedDept by remember { mutableStateOf("ALL") }

    val cujDepartments = remember {
        listOf("ALL", "CSIT", "CSE", "ECE", "MATH", "PHY", "CHEM", "HRM", "TTM", "SCM", "ECON", "PPPA", "SW", "MCNM", "ENG", "HINDI", "EVS", "ZOOL", "BOT", "MBIO", "NSS")
    }

    // Until the approved notifications API is connected, show an honest empty inbox.
    val notifications = remember { emptyList<CampusNotification>() }

    val quickActions = remember {
        listOf(
            QuickAction("Campus Feed", "University updates", Icons.Default.Public, CUJ_NavyCard, "pulse_wall"),
            QuickAction("Sports", "Fixtures and scorecards", Icons.Default.SportsCricket, CUJ_NavyCard, "live_sports"),
            QuickAction("Alumni Network", "Alumni directory", Icons.Default.School, CUJ_NavyCard, "alumni_network"),
            QuickAction("Event Forms", "Event registration", Icons.Default.DynamicForm, CUJ_GoldWarm, "custom_form"),
            QuickAction("Library", "Library services", Icons.Default.LocalLibrary, CUJ_NavyCard, "library"),
            QuickAction("Campus Venues", "Venue information", Icons.Default.Stadium, CUJ_NavyCard, "venues"),
            QuickAction("Academic Records", "Published scorecards", Icons.Default.Assessment, CUJ_NavyCard, "scorecards"),
            QuickAction("Community", "Campus groups", Icons.AutoMirrored.Filled.Chat, CUJ_NavyCard, "chat"),
            QuickAction("Hostel Services", "Gate pass requests", Icons.Default.NightShelter, CUJ_NavyCard, "hostel"),
            QuickAction("Attendance", "Event check-in", Icons.Default.QrCodeScanner, CUJ_NavyCard, "scanner"),
            QuickAction("Teams & Judging", "Event participation", Icons.Default.Groups, CUJ_NavyCard, "teams"),
            QuickAction("Lost & Found", "Report and claim items", Icons.Default.Search, CUJ_NavyCard, "lost_found"),
            QuickAction("Helpdesk", "Contact campus support", Icons.Default.SupportAgent, CUJ_NavyCard, "helpdesk"),
            QuickAction("Polls", "Campus polls", Icons.Default.HowToVote, CUJ_GoldWarm, "brackets_polls")
        )
    }

    // The native client has no approved events data adapter yet; never display fabricated listings.
    val sampleEvents = remember { emptyList<SampleEvent>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.cujammu_logo_circle),
                            contentDescription = "CUJammu Emblem",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, CUJ_Gold, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row {
                                Text("CUJAMMU", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                Text(" PULSE", fontWeight = FontWeight.Bold, color = CUJ_GoldWarm, fontSize = 16.sp)
                            }
                            Text("जम्मू केंद्रीय विश्वविद्यालय", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Central University of Jammu", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Notification Bell with Badge & Click Dialog
                    IconButton(onClick = { showNotificationSheet = true }) {
                        if (notifications.isNotEmpty()) {
                            BadgedBox(badge = { Badge(containerColor = CUJ_GoldWarm) { Text("${notifications.size}") } }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Campus notifications", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = "Campus notifications", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    // Profile Avatar with Click Dialog
                    IconButton(onClick = { showProfileDialog = true }) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Open account details", tint = CUJ_NavyPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Official campus welcome and service navigation.
            // University welcome panel
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CUJ_NavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .background(CUJ_NavyPrimary)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CUJ_Gold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "CENTRAL UNIVERSITY OF JAMMU",
                                    color = CUJ_Gold,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text("CAMPUS SERVICES", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Your campus, together",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Find university events, learning spaces and campus services from one clear starting point.",
                            color = Color(0xFFF1F5F9),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )

                    }
                }
            }

            // 3. CAMPUS CORE SERVICES (GRID OF QUICK ACTIONS)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Campus Core Services", fontWeight = FontWeight.Black, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("For students and staff", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in quickActions.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val action1 = quickActions[i]
                            Card(
                                onClick = { onNavigate(action1.route) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(action1.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(action1.icon, contentDescription = null, tint = action1.color, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(action1.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                        Text(
                                            action1.subtitle,
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), // High contrast!
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (i + 1 < quickActions.size) {
                                val action2 = quickActions[i + 1]
                                Card(
                                    onClick = { onNavigate(action2.route) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(action2.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(action2.icon, contentDescription = null, tint = action2.color, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(action2.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text(
                                                action2.subtitle,
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Filters are shown only when an approved event list is available.
            if (sampleEvents.isNotEmpty()) item {
                Text("Explore by 22 University Departments", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cujDepartments) { dept ->
                        val isSelected = (selectedDept == dept)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDept = dept },
                            label = { Text(dept, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CUJ_NavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 5. FEATURED & UPCOMING UNIVERSITY EVENTS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Upcoming Campus Events", fontWeight = FontWeight.Black, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { onNavigate("custom_form") }) {
                        Text("My registrations", color = CUJ_NavyPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            val filteredEvents = if (selectedDept == "ALL") sampleEvents else {
                sampleEvents.filter { it.department.contains(selectedDept, ignoreCase = true) }
            }

            if (filteredEvents.isEmpty()) item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No published events yet", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("University events will appear here once they are published.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            items(filteredEvents) { ev ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("custom_form") }
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CUJ_Gold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    ev.category.uppercase(),
                                    color = CUJ_GoldWarm,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                ev.department,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(ev.title, fontWeight = FontWeight.Black, fontSize = 17.sp, lineHeight = 22.sp)

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = CUJ_BlueAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(ev.date, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = ErrorRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(ev.venue, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Seat progress bar and WORKABLE Register Now Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${ev.seatsLeft} of ${ev.totalSeats} seats open",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (ev.totalSeats - ev.seatsLeft).toFloat() / ev.totalSeats.toFloat() },
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = SuccessGreen,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            Button(
                                onClick = { onNavigate("custom_form") }, // WORKABLE WIRED BUTTON!
                                colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Register Now", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = CUJ_Gold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // INTERACTIVE NOTIFICATIONS MODAL (WIRED TO BELL ICON)
    if (showNotificationSheet) {
        AlertDialog(
            onDismissRequest = { showNotificationSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = CUJ_Gold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Campus Notifications", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (notifications.isEmpty()) {
                        Text("No notifications are available on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    notifications.forEach { notif ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNotificationSheet = false
                                    onNavigate(notif.route)
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CUJ_NavyPrimary)
                                    Text(notif.timeAgo, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(notif.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showNotificationSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Do not show fabricated identity or student records in a local build.
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text("University account", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            },
            text = {
                Text("Profile details are shown after verified university sign-in is connected to this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Close") }
            }
        )
    }
}

