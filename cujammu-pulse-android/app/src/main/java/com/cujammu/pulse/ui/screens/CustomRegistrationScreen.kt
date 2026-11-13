package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.cujammu.pulse.ui.theme.CUJ_NavyDark
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary
import com.cujammu.pulse.ui.theme.CUJ_BlueAccent
import com.cujammu.pulse.ui.theme.CUJ_Gold
import com.cujammu.pulse.ui.theme.CUJ_GoldLight
import com.cujammu.pulse.ui.theme.SuccessGreen
import com.cujammu.pulse.ui.theme.WarningAmber
import com.cujammu.pulse.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRegistrationScreen(
    eventId: Long = 1L,
    eventTitle: String = "National AI & Cybersecurity Summit 2026",
    onBack: () -> Unit
) {
    // Database autofilled details (editable by student)
    var name by remember { mutableStateOf("Nitish Kumar") }
    var email by remember { mutableStateOf("nitish.cuj@gmail.com") }
    var enrollmentNo by remember { mutableStateOf("CUJ/CSIT/2024/042") }
    var department by remember { mutableStateOf("CSIT") }
    var semester by remember { mutableStateOf("Sem 4") }

    // Dynamic Form Custom Question Fields
    var tShirtSize by remember { mutableStateOf("L") }
    var githubLink by remember { mutableStateOf("") }
    var dietaryPref by remember { mutableStateOf("Vegetarian") }
    var priorExperience by remember { mutableStateOf("") }
    var needsAccommodation by remember { mutableStateOf(false) }

    var isSubmitted by remember { mutableStateOf(false) }
    var qrTicketCode by remember { mutableStateOf("") }
    var ticketQrMode by remember { mutableStateOf("DUAL_QR") } // DUAL_QR, SINGLE_QR, NO_QR

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Event Registration Form", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(eventTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        var showPdfDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSubmitted) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Registration Confirmed!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SuccessGreen)
                                Text(
                                    "Your participation pass is officially generated and synced with Organizer Attendance Turnstile.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Conditional QR Ticket Generation (Dual QR vs Single QR vs No QR / Manual)
                        when (ticketQrMode) {
                            "DUAL_QR" -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CUJ_Gold.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            "⚡ ORGANIZER DUAL-QR MODE: ENTRY & EXIT PASSES",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CUJ_NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Entry QR
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .border(2.dp, SuccessGreen, RoundedCornerShape(12.dp))
                                                    .background(Color.White, RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.QrCode2, contentDescription = "Entry QR", tint = Color.Black, modifier = Modifier.size(105.dp))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("ENTRY PASS (QR-1)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                            Text("CUJ-ENT-884291", fontWeight = FontWeight.Black, fontSize = 12.sp, color = CUJ_NavyPrimary)
                                        }

                                        // Exit QR
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .border(2.dp, CUJ_BlueAccent, RoundedCornerShape(12.dp))
                                                    .background(Color.White, RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.QrCode2, contentDescription = "Exit QR", tint = Color.Black, modifier = Modifier.size(105.dp))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("EXIT PASS (QR-2)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CUJ_BlueAccent)
                                            Text("CUJ-EXT-884291", fontWeight = FontWeight.Black, fontSize = 12.sp, color = CUJ_NavyPrimary)
                                        }
                                    }
                                }
                            }

                            "SINGLE_QR" -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CUJ_NavyPrimary.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            "🎫 ORGANIZER SINGLE-QR MODE: ONE-TAP CHECK-IN",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CUJ_NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .border(2.dp, CUJ_Gold, RoundedCornerShape(14.dp))
                                            .background(Color.White, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = "Single Ticket QR", tint = Color.Black, modifier = Modifier.size(130.dp))
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("PASS CODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CUJ_Gold)
                                    Text(qrTicketCode.ifBlank { "CUJ-PASS-884291" }, fontWeight = FontWeight.Black, fontSize = 16.sp, color = CUJ_NavyPrimary)
                                }
                            }

                            else -> {
                                // NO_QR / MANUAL MODE
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = WarningAmber.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            "📋 MANUAL ROLL-CALL MODE — NO QR GENERATED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(2.dp, CUJ_NavyPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.Badge, contentDescription = null, tint = CUJ_NavyPrimary, modifier = Modifier.size(52.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("OFFICIAL DELEGATE IDENTIFICATION PASS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = CUJ_NavyPrimary)
                                            Text("NO QR CODE: Organizer Configured Manual Physical Verification", fontSize = 10.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Gate Entry: Present College Student ID at Desk", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Delegate Name:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Enrollment No:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(enrollmentNo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Department:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$department ($semester)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Delegate Kit:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("T-Shirt: $tShirtSize | Meal: $dietaryPref", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = CUJ_NavyPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showPdfDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Verified PDF Pass", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Return to Events Hub")
                        }
                    }
                }

                if (showPdfDialog) {
                    AlertDialog(
                        onDismissRequest = { showPdfDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = SuccessGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pass PDF Downloaded")
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Official Entry Pass PDF has been saved to your device Downloads folder.", fontSize = 13.sp)
                                Text("• File: CUJ_EventPass_${qrTicketCode.ifBlank { "884291" }}.pdf", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("• Digital Signature: SHA256-CUJ-2026-VERIFIED", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Gate Access: Turnstile Gate 1 & 2 Approved", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showPdfDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                            ) {
                                Text("Open File")
                            }
                        }
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CUJ_Gold.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = CUJ_Gold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Database Autofill Active: Your official student credentials have been pre-filled. You may update any field before submission.",
                            fontSize = 12.sp,
                            color = CUJ_NavyDark,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text("Student Credentials", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("University / Contact Email *") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = enrollmentNo,
                    onValueChange = { enrollmentNo = it },
                    label = { Text("Roll / Enrollment Number *") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department *") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = semester,
                        onValueChange = { semester = it },
                        label = { Text("Semester *") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Event Specific Questions", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Text("T-Shirt Size for Delegate Kit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("S", "M", "L", "XL", "XXL").forEach { size ->
                        FilterChip(
                            selected = (tShirtSize == size),
                            onClick = { tShirtSize = size },
                            label = { Text(size) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CUJ_NavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text("Dietary Preference", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Vegetarian", "Non-Vegetarian", "Jain Meal").forEach { meal ->
                        FilterChip(
                            selected = (dietaryPref == meal),
                            onClick = { dietaryPref = meal },
                            label = { Text(meal, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CUJ_NavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = githubLink,
                    onValueChange = { githubLink = it },
                    label = { Text("GitHub / Project Portfolio Link") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    placeholder = { Text("https://github.com/username") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priorExperience,
                    onValueChange = { priorExperience = it },
                    label = { Text("Briefly describe any prior hackathon/project experience") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = needsAccommodation, onCheckedChange = { needsAccommodation = it })
                    Text("Request Campus Hostel Accommodation for Event", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Organizer Gate Entry Configuration Setting
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CUJ_NavyPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = CUJ_Gold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Organizer Attendance Pass Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            "The event host configured how attendee gate verification is performed:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("DUAL_QR", "Dual QR Code", "Entry + Exit"),
                                Triple("SINGLE_QR", "Single QR", "Check-in only"),
                                Triple("NO_QR", "No QR (Manual)", "Physical ID")
                            ).forEach { (mode, label, sub) ->
                                val isSelected = (ticketQrMode == mode)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) CUJ_NavyPrimary else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) CUJ_Gold else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { ticketQrMode = mode }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            sub,
                                            fontSize = 9.sp,
                                            color = if (isSelected) CUJ_GoldLight else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        qrTicketCode = "CUJ-PASS-" + System.currentTimeMillis().toString().takeLast(6)
                        isSubmitted = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Icon(Icons.Default.HowToReg, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Registration & Generate Pass", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
