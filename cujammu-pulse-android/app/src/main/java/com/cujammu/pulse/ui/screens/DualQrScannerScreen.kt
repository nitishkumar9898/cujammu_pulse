package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.cujammu.pulse.ui.theme.CUJ_NavyDark
import com.cujammu.pulse.ui.theme.ErrorRed
import com.cujammu.pulse.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualQrScannerScreen(
    scanMode: String = "ENTRY", // ENTRY or EXIT
    onBack: () -> Unit
) {
    var isTorchOn by remember { mutableStateOf(false) }
    var currentScanMode by remember { mutableStateOf(scanMode) }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentScanMode == "ENTRY") "Scan Entry QR-1" else "Scan Exit QR-2", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isTorchOn = !isTorchOn }) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) CUJ_Gold else Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CUJ_NavyDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .border(3.dp, if (currentScanMode == "ENTRY") CUJ_Gold else SuccessGreen, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Align QR Code inside box",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            // Bottom Controls Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Toggle
                Row(
                    modifier = Modifier
                        .background(CUJ_NavyDark.copy(alpha = 0.9f), RoundedCornerShape(30.dp))
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { currentScanMode = "ENTRY" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentScanMode == "ENTRY") CUJ_Gold else Color.Transparent
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text(
                            "Entry QR-1",
                            color = if (currentScanMode == "ENTRY") CUJ_NavyDark else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { currentScanMode = "EXIT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentScanMode == "EXIT") SuccessGreen else Color.Transparent
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text(
                            "Exit QR-2",
                            color = if (currentScanMode == "EXIT") Color.White else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manual Roll No entry
                OutlinedButton(
                    onClick = { showUnavailableDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Roll No Manually", fontSize = 13.sp)
                }
            }

            // The native camera/API workflow is intentionally fail-closed until
            // CameraX, owner-bound credentials and the confirmation endpoint
            // are wired together. Never display fabricated attendance results.
            if (showUnavailableDialog) {
                AlertDialog(
                    onDismissRequest = { showUnavailableDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = CUJ_Gold,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text("Scanner unavailable", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column {
                            Text("Camera and owner-confirmation service are not connected in this build.")
                            Text("No attendance was recorded. Use the verified shared workflow after the scanner integration is enabled.")
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showUnavailableDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyDark)
                        ) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}
