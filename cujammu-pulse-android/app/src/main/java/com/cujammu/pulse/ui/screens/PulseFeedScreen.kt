package com.cujammu.pulse.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.cujammu.pulse.ui.theme.LiveRed

data class SocialPost(
    val id: Long,
    val authorName: String,
    val authorDept: String,
    val authorRole: String,
    val timeAgo: String,
    val content: String,
    val mediaType: String = "NONE", // NONE, IMAGE, PDF, BANNER, VICTORY_SHOWCASE
    val isWinner: Boolean = false,
    val winnerName: String? = null,
    val eventTitle: String? = null,
    val prize: String? = null,
    var likes: Int,
    var isLiked: Boolean = false,
    var commentsCount: Int = 0,
    val comments: MutableList<String> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseFeedScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var postInputText by remember { mutableStateOf("") }
    var postCategoryInput by remember { mutableStateOf("GENERAL") }
    var isWinnerPost by remember { mutableStateOf(false) }
    var winnerNameInput by remember { mutableStateOf("") }
    var eventTitleInput by remember { mutableStateOf("") }
    var prizeInputText by remember { mutableStateOf("") }

    val categories = remember {
        listOf("ALL", "VICTORY_BANNER", "GENERAL", "EVENT_UPDATE", "ALUMNI_CORNER", "CLUB_NEWS")
    }

    val posts = remember {
        mutableStateListOf(
            SocialPost(
                id = 1,
                authorName = "Sports Directorate CUJ",
                authorDept = "Sports Council",
                authorRole = "ORGANIZER",
                timeAgo = "15m ago",
                content = "🎉 CHAMPIONS DECLARED! CSIT Thunderbolts lifted the Inter-Departmental Cricket Trophy 2026 after a thrilling final against CSE Warriors!",
                mediaType = "VICTORY_SHOWCASE",
                isWinner = true,
                winnerName = "CSIT Thunderbolts",
                eventTitle = "Inter-Department Cricket Tournament 2026",
                prize = "Gold Trophy & ₹15,000 Cash Prize",
                likes = 142,
                isLiked = true,
                commentsCount = 28,
                comments = mutableListOf("Well played CSIT!", "Congratulations boys!", "Best cricket final of CUJ!")
            ),
            SocialPost(
                id = 2,
                authorName = "Priya Sharma",
                authorDept = "M.Tech CSE (Alumni 2024)",
                authorRole = "ALUMNI",
                timeAgo = "1h ago",
                content = "Excited to share that our team at Google is looking for Software Engineer Interns from Central University of Jammu! Check the Alumni portal for referral links.",
                mediaType = "BANNER",
                isWinner = false,
                likes = 89,
                isLiked = false,
                commentsCount = 14,
                comments = mutableListOf("Shared my resume ma'am!", "Thanks for supporting juniors!")
            ),
            SocialPost(
                id = 3,
                authorName = "Rohit Verma",
                authorDept = "B.Tech ECE (Sem 6)",
                authorRole = "STUDENT",
                timeAgo = "3h ago",
                content = "Robotics Club RoboWars arena is almost set up at Mini Auditorium! All participating teams report by 9:30 AM tomorrow for technical inspection.",
                mediaType = "IMAGE",
                isWinner = false,
                likes = 56,
                isLiked = false,
                commentsCount = 8
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pulse Social Wall", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Campus Media, Victories & Alumni Hub", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "New Post", tint = CUJ_Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CUJ_NavyPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Create Post")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = (selectedCategory == cat)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                when (cat) {
                                    "VICTORY_BANNER" -> "🏆 Victories"
                                    "ALUMNI_CORNER" -> "🎓 Alumni"
                                    "EVENT_UPDATE" -> "📢 Events"
                                    "CLUB_NEWS" -> "🎭 Clubs"
                                    else -> cat
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (cat == "VICTORY_BANNER") CUJ_Gold else CUJ_NavyPrimary,
                            selectedLabelColor = if (cat == "VICTORY_BANNER") Color.Black else Color.White
                        )
                    )
                }
            }

            // Posts Stream
            val filteredPosts = if (selectedCategory == "ALL") posts else {
                posts.filter {
                    if (selectedCategory == "VICTORY_BANNER") it.isWinner
                    else it.mediaType == selectedCategory || it.content.contains(selectedCategory, ignoreCase = true)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    SocialPostCard(
                        post = post,
                        onLikeToggle = {
                            if (post.isLiked) {
                                post.likes--
                                post.isLiked = false
                            } else {
                                post.likes++
                                post.isLiked = true
                            }
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${post.authorName} on CUJAMMU-PULSE:\n\n${post.content}\n\nVia CUJAMMU-PULSE Official App")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Pulse Post"))
                        }
                    )
                }
            }
        }
    }

    // Create Post Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Publish to Pulse Wall", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = postInputText,
                        onValueChange = { postInputText = it },
                        label = { Text("What's happening on campus?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isWinnerPost, onCheckedChange = { isWinnerPost = it })
                        Text("Winner / Victory Announcement Banner", fontSize = 13.sp)
                    }

                    if (isWinnerPost) {
                        OutlinedTextField(
                            value = winnerNameInput,
                            onValueChange = { winnerNameInput = it },
                            label = { Text("Winner Name / Team") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = eventTitleInput,
                            onValueChange = { eventTitleInput = it },
                            label = { Text("Event / Tournament Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = prizeInputText,
                            onValueChange = { prizeInputText = it },
                            label = { Text("Prize / Trophy Details") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (postInputText.isNotBlank()) {
                            posts.add(
                                0,
                                SocialPost(
                                    id = System.currentTimeMillis(),
                                    authorName = "Nitish Kumar",
                                    authorDept = "CSIT",
                                    authorRole = "STUDENT",
                                    timeAgo = "Just now",
                                    content = postInputText,
                                    mediaType = if (isWinnerPost) "VICTORY_SHOWCASE" else "GENERAL",
                                    isWinner = isWinnerPost,
                                    winnerName = if (isWinnerPost) winnerNameInput else null,
                                    eventTitle = if (isWinnerPost) eventTitleInput else null,
                                    prize = if (isWinnerPost) prizeInputText else null,
                                    likes = 0,
                                    isLiked = false,
                                    commentsCount = 0
                                )
                            )
                            postInputText = ""
                            isWinnerPost = false
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CUJ_NavyPrimary)
                ) {
                    Text("Publish Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SocialPostCard(
    post: SocialPost,
    onLikeToggle: () -> Unit,
    onShare: () -> Unit
) {
    var showComments by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (post.isWinner) Color(0xFF1A2634) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Post Author Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (post.isWinner) CUJ_Gold
                            else if (post.authorRole == "ALUMNI") Color(0xFF9C27B0)
                            else CUJ_NavyPrimary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.authorName.take(1),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (post.isWinner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🏆", fontSize = 14.sp)
                        }
                    }
                    Text(
                        "${post.authorDept} • ${post.timeAgo}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CUJ_Gold.copy(alpha = 0.18f)
                ) {
                    Text(
                        post.authorRole,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CUJ_NavyDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Post Content Text
            Text(post.content, fontSize = 14.5.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface)

            // Winner Showcase Banner (if victory post)
            if (post.isWinner) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2A1B00), Color(0xFF4A3500), Color(0xFF2A1B00))
                            )
                        )
                        .border(1.dp, CUJ_Gold, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🏆 OFFICIAL VICTORY SHOWCASE", color = CUJ_Gold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(post.winnerName ?: "Champions", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(post.eventTitle ?: "University Event", color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (post.prize != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CUJ_Gold.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    "Prize: ${post.prize}",
                                    color = CUJ_GoldLight,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Interaction Bar (Like, Comment, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLikeToggle() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) LiveRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.likes}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (post.isLiked) LiveRed else MaterialTheme.colorScheme.onSurface)
                }

                // Comment button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showComments = !showComments }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Comment, contentDescription = "Comment", tint = CUJ_BlueAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${post.comments.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                // Share button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShare() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = CUJ_Gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Expanded Comments Section
            if (showComments) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    post.comments.forEach { comment ->
                        Text("💬 $comment", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            placeholder = { Text("Write a campus comment...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (commentInput.isNotBlank()) {
                                    post.comments.add(commentInput)
                                    post.commentsCount = post.comments.size
                                    commentInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = CUJ_NavyPrimary)
                        }
                    }
                }
            }
        }
    }
}
