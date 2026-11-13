package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.cujammu.pulse.data.model.LostAndFoundItemDto
import com.cujammu.pulse.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostAndFoundScreen(
    onBack: () -> Unit
) {
    val categories = listOf("ALL", "ELECTRONICS", "ID_CARDS_DOCS", "BAGS_WALLETS", "KEYS", "OTHER")
    var selectedCat by remember { mutableStateOf("ALL") }

    val mockItems = remember {
        mutableStateListOf(
            LostAndFoundItemDto(
                1L, "HP Blue Wireless Mouse", "ELECTRONICS",
                "Found on 3rd floor CSIT computer lab bench 4", "CSIT Block Room 302",
                "Amit Sharma", "+91 9876543210", "REPORTED", null, null, "2026-09-15 14:30",
                imageUrls = listOf("photo_mouse_lab.jpg")
            ),
            LostAndFoundItemDto(
                2L, "CUJ Student Identity Card", "ID_CARDS_DOCS",
                "Enrollment No: CUJ2024CS042 found near sports complex canteen", "Sports Ground Pavilion",
                "Rajesh Kumar", "+91 9419123456", "REPORTED", null, null, "2026-09-15 16:15",
                imageUrls = listOf("cuj_id_card_front.jpg", "cuj_id_card_back.jpg")
            ),
            LostAndFoundItemDto(
                3L, "Hero Cycle Key with Red Tag", "KEYS",
                "Found dropped near Shyama Prasad Mukherjee Hostel Cycle Stand", "Boys Hostel Stand",
                "Security Guard", "+91 9123456780", "CLAIMED", "Vikram Singh", null, "2026-09-14 09:00",
                imageUrls = listOf("cycle_key_tag.jpg")
            )
        )
    }

    var showReportDialog by remember { mutableStateOf(false) }
    var claimingItem by remember { mutableStateOf<LostAndFoundItemDto?>(null) }
    var showSourcePickerModal by remember { mutableStateOf(false) }
    var photoValidationError by remember { mutableStateOf<String?>(null) }
    var previewPhoto by remember { mutableStateOf<String?>(null) }
    val attachedPhotos = remember { mutableStateListOf<String>() }

    // Report Item Dialog States
    var reportTitle by remember { mutableStateOf("") }
    var reportCategory by remember { mutableStateOf("ELECTRONICS") }
    var reportDescription by remember { mutableStateOf("") }
    var reportLocation by remember { mutableStateOf("") }
    var reporterName by remember { mutableStateOf("Nitish Kumar") }
    var reporterPhone by remember { mutableStateOf("+91 9876543210") }

    // Claim Dialog States
    var claimantNameInput by remember { mutableStateOf("") }
    var claimantIdInput by remember { mutableStateOf("") }
    var ownershipProof by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Campus Lost & Found", fontWeight = FontWeight.Bold) },
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
                onClick = { showReportDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Report Found Item", fontWeight = FontWeight.Bold) },
                containerColor = CUJ_NavyPrimary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = (selectedCat == cat),
                        onClick = { selectedCat = cat },
                        label = { Text(cat.replace("_", " ")) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mockItems.filter { selectedCat == "ALL" || it.category == selectedCat }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Surface(
                                    color = if (item.status == "CLAIMED") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = item.status,
                                        color = if (item.status == "CLAIMED") Color(0xFF2E7D32) else Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(item.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = CUJ_NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(item.locationFound, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // Visual Item Recognition Photos (Min 1, Max 2)
                            val photos = if (item.imageUrls.isNotEmpty()) item.imageUrls else listOfNotNull(item.imageUrl)
                            if (photos.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    photos.forEachIndexed { idx, photo ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CUJ_NavyPrimary.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .width(130.dp)
                                                .height(80.dp)
                                                .clickable { previewPhoto = photo }
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Image,
                                                        contentDescription = null,
                                                        tint = CUJ_NavyPrimary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = "Photo ${idx + 1} (Tap to view)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = CUJ_NavyPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Reported by: ${item.reportedByName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                    if (item.claimantName != null) {
                                        Text("Claimed by: ${item.claimantName}", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (item.status != "CLAIMED") {
                                    Button(
                                        onClick = {
                                            claimantNameInput = "Nitish Kumar"
                                            claimantIdInput = "CUJ/CSIT/2024/042"
                                            claimingItem = item
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Claim Item", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Report Item Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Found Campus Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reportTitle,
                        onValueChange = { reportTitle = it },
                        label = { Text("Item Name / Title *") },
                        placeholder = { Text("e.g. Scientific Calculator Casio fx-991") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("ELECTRONICS", "ID_CARDS_DOCS", "BAGS_WALLETS", "KEYS", "OTHER")) { cat ->
                            FilterChip(
                                selected = (reportCategory == cat),
                                onClick = { reportCategory = cat },
                                label = { Text(cat.replace("_", " "), fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reportLocation,
                        onValueChange = { reportLocation = it },
                        label = { Text("Location Found *") },
                        placeholder = { Text("e.g. Library 2nd floor reading hall") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Description & Distinguishing Features") },
                        placeholder = { Text("Color, brand, tags, condition") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    // Photo Upload Section (Min 1, Max 2)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Item Photos (Min 1, Max 2) *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${attachedPhotos.size}/2 Attached",
                                fontSize = 11.sp,
                                color = if (attachedPhotos.isEmpty()) ErrorRed else SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (attachedPhotos.size < 2) {
                            OutlinedButton(
                                onClick = { showSourcePickerModal = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Image (Camera / Gallery)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (attachedPhotos.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                attachedPhotos.forEachIndexed { index, photo ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = CUJ_NavyPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Photo ${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { attachedPhotos.removeAt(index) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        photoValidationError?.let {
                            Text(it, color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reportTitle.isBlank()) {
                            photoValidationError = "Please enter item title."
                        } else if (attachedPhotos.isEmpty()) {
                            photoValidationError = "Please upload at least 1 photo (maximum 2) so the item can be visually identified."
                        } else {
                            mockItems.add(
                                0,
                                LostAndFoundItemDto(
                                    id = System.currentTimeMillis(),
                                    title = reportTitle,
                                    category = reportCategory,
                                    description = reportDescription.ifBlank { "Found on campus grounds" },
                                    locationFound = reportLocation.ifBlank { "Campus Premises" },
                                    reportedByName = reporterName,
                                    reportedByPhone = reporterPhone,
                                    status = "REPORTED",
                                    claimantName = null,
                                    imageUrl = attachedPhotos.firstOrNull(),
                                    reportedAt = "Just now",
                                    imageUrls = attachedPhotos.toList()
                                )
                            )
                            reportTitle = ""
                            reportDescription = ""
                            reportLocation = ""
                            attachedPhotos.clear()
                            photoValidationError = null
                            showReportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Submit Report with Photos")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportDialog = false
                    photoValidationError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Photo Source Picker Dialog (Camera vs Gallery)
    if (showSourcePickerModal) {
        AlertDialog(
            onDismissRequest = { showSourcePickerModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = CUJ_Gold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Item Photo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose how you want to upload the photo (Min 1, Max 2):", fontSize = 12.sp)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (attachedPhotos.size < 2) {
                                    attachedPhotos.add("camera_capture_${System.currentTimeMillis()}.jpg")
                                    photoValidationError = null
                                }
                                showSourcePickerModal = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = CUJ_NavyPrimary, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Direct Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Take live photo of the found item now", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (attachedPhotos.size < 2) {
                                    attachedPhotos.add("gallery_image_${System.currentTimeMillis()}.png")
                                    photoValidationError = null
                                }
                                showSourcePickerModal = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CUJ_NavyPrimary, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Device Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Select photo from gallery or album", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourcePickerModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Photo Preview Dialog
    previewPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = CUJ_Gold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Found Item Photo Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = CUJ_Gold,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                    Text(
                        "File: $photo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "High-resolution item image stored in secure university campus asset repository.",
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewPhoto = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Close Preview")
                }
            }
        )
    }

    // Claim Verification Dialog
    claimingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { claimingItem = null },
            title = {
                Column {
                    Text("Claim Item Verification", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Item: ${item.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = claimantNameInput,
                        onValueChange = { claimantNameInput = it },
                        label = { Text("Full Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = claimantIdInput,
                        onValueChange = { claimantIdInput = it },
                        label = { Text("CUJ Enrollment / Roll Number *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = ownershipProof,
                        onValueChange = { ownershipProof = it },
                        label = { Text("Proof of Ownership / Details *") },
                        placeholder = { Text("Serial number, lock pattern, distinctive scratches, stickers etc.") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Surface(
                        color = CUJ_NavyPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Note: False claims are strictly punishable under CUJammu Campus Disciplinary Code.",
                            fontSize = 11.sp,
                            color = CUJ_NavyPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val index = mockItems.indexOfFirst { it.id == item.id }
                        if (index >= 0) {
                            mockItems[index] = mockItems[index].copy(
                                status = "CLAIMED",
                                claimantName = claimantNameInput.ifBlank { "Nitish Kumar" }
                            )
                        }
                        claimingItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Confirm Claim & Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { claimingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
