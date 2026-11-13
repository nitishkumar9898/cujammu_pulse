package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.cujammu.pulse.ui.theme.CUJ_Gold
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary
import com.cujammu.pulse.ui.theme.ErrorRed
import com.cujammu.pulse.ui.theme.SuccessGreen

data class VenueUiModel(
    val id: Long,
    val name: String,
    val type: String,
    val capacity: Int,
    val currentStatus: String, // AVAILABLE, BOOKED, PENDING
    val bookedFor: String? = null,
    val bookedDepartment: String? = null,
    val timeSlot: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueCalendarScreen(
    onBack: () -> Unit
) {
    var venueList by remember {
        mutableStateOf(
            listOf(
                VenueUiModel(1, "Brig. Rajendra Singh Main Auditorium", "AUDITORIUM", 800, "BOOKED", "National AI & Cybersecurity Summit", "CSIT", "10:00 AM - 02:00 PM"),
                VenueUiModel(2, "Mini Auditorium & Multi-Purpose Hall", "AUDITORIUM", 300, "AVAILABLE"),
                VenueUiModel(3, "Main Cricket & Athletics Ground", "SPORTS_GROUND", 2000, "BOOKED", "Inter-Department Cricket Trophy", "Sports Dept", "01:30 PM - 05:30 PM"),
                VenueUiModel(4, "Indoor Badminton & TT Courts", "SPORTS_GROUND", 250, "AVAILABLE"),
                VenueUiModel(5, "School of Business Seminar Hall", "SEMINAR_HALL", 150, "PENDING", "Supply Chain Workshop", "Marketing Dept", "03:00 PM - 05:00 PM"),
                VenueUiModel(6, "CSIT Advanced Computing Lab & Hall", "SEMINAR_HALL", 120, "AVAILABLE")
            )
        )
    }

    var selectedFilter by remember { mutableStateOf("ALL") }
    var bookingVenue by remember { mutableStateOf<VenueUiModel?>(null) }
    var viewingScheduleVenue by remember { mutableStateOf<VenueUiModel?>(null) }
    var bookingConfirmedMessage by remember { mutableStateOf<String?>(null) }

    // Booking Dialog form fields
    var eventTitleInput by remember { mutableStateOf("") }
    var departmentInput by remember { mutableStateOf("CSIT") }
    var selectedSlot by remember { mutableStateOf("02:00 PM - 05:00 PM") }
    var needAudioSystem by remember { mutableStateOf(true) }
    var needProjector by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Venues & Sports Grounds", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(SuccessGreen, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Available", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(ErrorRed, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Booked (Conflict)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(CUJ_Gold, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pending Review", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Venue List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(venueList) { venue ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(venue.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                val badgeColor = when (venue.currentStatus) {
                                    "AVAILABLE" -> SuccessGreen
                                    "BOOKED" -> ErrorRed
                                    else -> CUJ_Gold
                                }
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = venue.currentStatus,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp), tint = CUJ_NavyPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Capacity: ${venue.capacity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp), tint = CUJ_NavyPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(venue.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (venue.currentStatus == "BOOKED" && venue.bookedFor != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ErrorRed.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Occupied by: ${venue.bookedFor}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ErrorRed)
                                        Text("Department: ${venue.bookedDepartment} | Slot: ${venue.timeSlot}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (venue.currentStatus == "AVAILABLE") {
                                    Button(
                                        onClick = {
                                            bookingVenue = venue
                                            eventTitleInput = "Department Seminar / Cultural Activity"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Book Venue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewingScheduleVenue = venue },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("View Schedule", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Booking Dialog
    bookingVenue?.let { venue ->
        AlertDialog(
            onDismissRequest = { bookingVenue = null },
            title = {
                Column {
                    Text("Book ${venue.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Capacity: ${venue.capacity} seats | ${venue.type}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = eventTitleInput,
                        onValueChange = { eventTitleInput = it },
                        label = { Text("Event / Activity Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = departmentInput,
                        onValueChange = { departmentInput = it },
                        label = { Text("Organizing Department *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Time Slot:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("09:00 - 12:00", "01:00 - 04:00", "04:30 - 07:30").forEach { slot ->
                            FilterChip(
                                selected = (selectedSlot == slot),
                                onClick = { selectedSlot = slot },
                                label = { Text(slot, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = needAudioSystem, onCheckedChange = { needAudioSystem = it })
                        Text("PA System & Wireless Mics", fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = needProjector, onCheckedChange = { needProjector = it })
                        Text("Projector & Screen setup", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Update state to reflect pending approval
                        venueList = venueList.map {
                            if (it.id == venue.id) {
                                it.copy(
                                    currentStatus = "PENDING",
                                    bookedFor = eventTitleInput.ifBlank { "Department Activity" },
                                    bookedDepartment = departmentInput,
                                    timeSlot = selectedSlot
                                )
                            } else it
                        }
                        bookingVenue = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Confirm Booking Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingVenue = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Interactive View Schedule Dialog
    viewingScheduleVenue?.let { venue ->
        AlertDialog(
            onDismissRequest = { viewingScheduleVenue = null },
            title = {
                Column {
                    Text("Daily Schedule", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(venue.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("08:30 AM - 10:30 AM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("COMPLETED", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text("Campus Yoga & Morning Meditation Session", fontSize = 12.sp)
                        }
                    }

                    Surface(
                        color = ErrorRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(venue.timeSlot ?: "11:00 AM - 03:00 PM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ErrorRed)
                                Text("OCCUPIED", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text(venue.bookedFor ?: "National AI & Cybersecurity Summit 2026", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("By: ${venue.bookedDepartment ?: "CSIT Dept"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        color = SuccessGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("04:00 PM - 07:30 PM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                Text("OPEN / VACANT", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text("Available for departmental cultural practice and club activities.", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewingScheduleVenue = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }
}
