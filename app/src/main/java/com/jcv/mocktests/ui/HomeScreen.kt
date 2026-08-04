package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Course
import java.util.Locale

// Byju's Style Color Palette
val ByjusPurple = Color(0xFF6D28D9)
val ByjusGradient = Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
val AppBackground = Color(0xFFF4F6F8)

val SubjectColors = listOf(
    Color(0xFFFF7E67), // Coral / Orange
    Color(0xFF42A5F5), // Blue
    Color(0xFF66BB6A), // Green
    Color(0xFFAB47BC), // Purple
    Color(0xFFFFA726)  // Yellow-Orange
)

@Composable
fun HomeScreen(onNavigateToCourse: (String) -> Unit) {
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var activeTopic by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document("testList").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val testsList = document.get("tests") as? List<Map<String, Any>> ?: emptyList()
                    courses = testsList.map { map ->
                        val title = map["title"] as? String ?: "JCV Test Series"
                        val titleUpper = title.uppercase(Locale.getDefault())
                        
                        val topic = when {
                            titleUpper.contains("GROUP") -> "GROUP EXAMS"
                            titleUpper.contains("CURRENT") -> "CURRENT AFFAIRS"
                            titleUpper.contains("IIT") || titleUpper.contains("CONSTABLE") -> "IIT & POLICE"
                            titleUpper.contains("TET") || titleUpper.contains("DSC") -> "TET & DSC"
                            else -> "GENERAL"
                        }

                        Course(
                            sheetId = map["sheetId"] as? String ?: "",
                            title = title,
                            fee = (map["fee"] as? Number)?.toDouble() ?: 0.0,
                            description = map["description"] as? String ?: "",
                            topic = topic
                        )
                    }
                } else {
                    errorMessage = "No test series available."
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorMessage = e.message
                isLoading = false
            }
    }

    val filteredCourses = courses.filter { 
        if (activeTopic == "ALL") true else it.topic == activeTopic 
    }

    val dynamicTopics = courses.map { it.topic }.distinct()
    val allTopics = listOf("ALL") + dynamicTopics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // 1. Byju's Style Curved Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    ByjusGradient,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(top = 56.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Hello, Scholar \uD83D\uDC4B", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ready to learn something new?", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = ByjusPurple, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Vibrant Gamified Subject Tiles
        Text(
            text = "Pick a Subject", 
            fontSize = 18.sp, 
            fontWeight = FontWeight.Bold, 
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(allTopics) { index, topic ->
                ByjusSubjectCard(
                    topic = topic,
                    index = index,
                    isSelected = topic == activeTopic,
                    onClick = { activeTopic = topic }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Recommended / Main Content
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recommended Tests", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text("View All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ByjusPurple)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ByjusPurple)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1), // Changed to single column for larger, friendly cards
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCourses) { course ->
                    ByjusCourseCard(course = course, onClick = { onNavigateToCourse(course.sheetId) })
                }
            }
        }
    }
}

@Composable
fun ByjusSubjectCard(topic: String, index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val baseColor = SubjectColors[index % SubjectColors.size]
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    if (isSelected) baseColor else baseColor.copy(alpha = 0.15f), 
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Displays the first letter of the topic as a large icon
            Text(
                text = topic.take(1).uppercase(), 
                fontSize = 28.sp, 
                fontWeight = FontWeight.Black, 
                color = if (isSelected) Color.White else baseColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = topic.lowercase().replaceFirstChar { it.uppercase() }, 
            fontSize = 13.sp, 
            fontWeight = FontWeight.Bold, 
            color = if (isSelected) ByjusPurple else Color.Gray
        )
    }
}

@Composable
fun ByjusCourseCard(course: Course, onClick: () -> Unit) {
    val isFree = course.fee == 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), // Softer, rounder corners
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Play Icon Block
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFF3E8FF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ByjusPurple, modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle: Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.topic,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Side: Price Chip
            Box(
                modifier = Modifier
                    .background(
                        if (isFree) Color(0xFFDCFCE7) else Color(0xFFFFF7ED), 
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFree) "FREE" else "₹%.0f".format(course.fee),
                    color = if (isFree) Color(0xFF16A34A) else Color(0xFFEA580C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
