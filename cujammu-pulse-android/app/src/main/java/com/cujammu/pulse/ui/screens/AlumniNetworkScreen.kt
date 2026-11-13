package com.cujammu.pulse.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

data class AlumniMember(
    val id: Long,
    val name: String,
    val department: String,
    val gradYear: Int,
    val degree: String,
    val company: String,
    val designation: String,
    val location: String,
    val willingToMentor: Boolean,
    val willingToRefer: Boolean,
    val linkedInUrl: String? = null
)

data class AlumniJob(
    val id: Long,
    val title: String,
    val company: String,
    val jobType: String,
    val location: String,
    val targetDept: String,
    val postedBy: String,
    val applyLink: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlumniNetworkScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Directory, 1 = Job Referrals, 2 = My Alumni Profile
    var selectedDept by remember { mutableStateOf("ALL") }

    val departments = remember {
        listOf("ALL", "CSIT", "CSE", "ECE", "MATH", "PHY", "CHEM", "HRM", "TTM", "ECON")
    }

    val alumniList = remember {
        listOf(
            AlumniMember(1, "Aman Sharma", "CSIT", 2023, "M.Tech CSIT", "Amazon AWS", "SDE II", "Bengaluru", true, true, "https://linkedin.com"),
            AlumniMember(2, "Mehak Gupta", "CSE", 2022, "B.Tech CSE", "Microsoft", "Cloud Solution Architect", "Hyderabad", true, true, "https://linkedin.com"),
            AlumniMember(3, "Rahul Verma", "ECE", 2021, "B.Tech ECE", "Qualcomm", "Silicon Design Engineer", "Noida", true, false, "https://linkedin.com"),
            AlumniMember(4, "Sunita Devi", "HRM", 2024, "MBA HRM", "Tata Consultancy Services", "Talent Acquisition Lead", "Gurugram", true, true, "https://linkedin.com"),
            AlumniMember(5, "Tariq Bhatt", "MATH", 2023, "Integrated M.Sc", "DRDO", "Scientist 'B'", "Chandigarh", false, false, "https://linkedin.com")
        )
    }

    val jobs = remember {
        listOf(
            AlumniJob(1, "Graduate Software Engineer", "Amazon AWS", "FULL_TIME", "Bengaluru (Hybrid)", "CSIT", "Aman Sharma (2023)", "https://amazon.jobs"),
            AlumniJob(2, "Summer Tech Intern 2026", "Microsoft", "INTERNSHIP", "Hyderabad", "CSE", "Mehak Gupta (2022)", "https://careers.microsoft.com"),
            AlumniJob(3, "HR Management Trainee", "TCS", "FULL_TIME", "Gurugram", "HRM", "Sunita Devi (2024)", "https://tcs.com/careers")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CUJ Alumni Network", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Department Groups & Mentorship Connect", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Directory", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Referrals & Jobs", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("My Profile", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Department filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(departments) { dept ->
                            FilterChip(
                                selected = (selectedDept == dept),
                                onClick = { selectedDept = dept },
                                label = { Text(if (dept == "ALL") "All Depts" else dept, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CUJ_NavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    val filteredAlumni = if (selectedDept == "ALL") alumniList else alumniList.filter { it.department == selectedDept }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredAlumni) { member ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF8E24AA)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                member.name.take(1),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("${member.designation} at ${member.company}", fontSize = 13.sp, color = CUJ_NavyPrimary, fontWeight = FontWeight.SemiBold)
                                            Text("Batch of ${member.gradYear} • ${member.department} (${member.degree})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("📍 ${member.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (member.willingToMentor) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SuccessGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text("🎓 Open to Mentor", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                        if (member.willingToRefer) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = CUJ_Gold.copy(alpha = 0.15f)
                                            ) {
                                                Text("💼 Referral Open", color = CUJ_Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(member.linkedInUrl ?: "https://linkedin.com"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Connect on LinkedIn", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Job referrals list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(jobs) { job ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(job.company, fontWeight = FontWeight.Bold, color = CUJ_NavyPrimary, fontSize = 14.sp)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = CUJ_Gold.copy(alpha = 0.2f)
                                        ) {
                                            Text(job.jobType, color = CUJ_Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(job.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("📍 ${job.location} • Target: ${job.targetDept} Students & Alumni", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Referral Posted by: ${job.postedBy}", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(job.applyLink))
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply with Alumni Referral", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Self Profile Editor
                    var company by remember { mutableStateOf("Google") }
                    var designation by remember { mutableStateOf("Software Engineer") }
                    var location by remember { mutableStateOf("Bengaluru, India") }
                    var linkedin by remember { mutableStateOf("https://linkedin.com/in/nitish") }
                    var willingMentor by remember { mutableStateOf(true) }
                    var willingRefer by remember { mutableStateOf(true) }
                    var savedMsg by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Update Your Alumni Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Graduated students are automatically enrolled to help current students with referrals and mentorship.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Current Company / Organization") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation / Role") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Work Location / City") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = linkedin, onValueChange = { linkedin = it }, label = { Text("LinkedIn Profile URL") }, modifier = Modifier.fillMaxWidth())

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = willingMentor, onCheckedChange = { willingMentor = it })
                            Text("Willing to Mentor CUJ Students", fontSize = 13.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = willingRefer, onCheckedChange = { willingRefer = it })
                            Text("Willing to Provide Company Job Referrals", fontSize = 13.sp)
                        }

                        if (savedMsg) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("✅ Alumni profile updated and verified in university directory!", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                            }
                        }

                        Button(
                            onClick = { savedMsg = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Alumni Credentials")
                        }
                    }
                }
            }
        }
    }
}
