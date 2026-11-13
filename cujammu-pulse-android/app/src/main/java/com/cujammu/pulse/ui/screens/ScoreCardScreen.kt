package com.cujammu.pulse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cujammu.pulse.ui.theme.CUJ_NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreCardScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("CoE Academic Score Cards", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scorecards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("No verified scorecards are loaded. Sign in to the university service to view records published for your own account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { }, enabled = false, colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary), modifier = Modifier.fillMaxWidth()) {
                            Text("Official PDF unavailable until authenticated document service is connected")
                        }
                    }
                }
            }
        }
    }
}
