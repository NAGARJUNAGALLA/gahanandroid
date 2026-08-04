package com.jcv.mocktests.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Course
import java.util.Locale

// Testbook Style Color Palette
val TbNavy = Color(0xFF0F172A) // Dark slate for headers
val TbBlue = Color(0xFF2563EB) // Primary action blue
val TbBackground = Color(0xFFF1F5F9) // Light grayish blue background
val TbYellow = Color(0xFFEAB308) // Warning/Pro color
val TbGreen = Color(0xFF16A34A) // Success/Free color

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
            .background(TbBackground)
    ) {
        // 1. Testbook Dark Navy Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TbNavy)
                .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("JCV Tests", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mock Language Selector typical of Testbook
                    Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("A/అ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Color.White)
                }
            }
        }

        // 2. "Pass Pro" Promotional Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = TbBlue)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = TbYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JCV PASS PRO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Unlock 500+ Mock Tests & PYQs", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Button(
                    onClick = { /* Navigate to subscription */ },
                    colors = ButtonDefaults.buttonColors(containerColor = TbYellow),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("View Plans", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 3. Category Filter Chips (Testbook Style)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allTopics) { topic ->
                val isSelected = activeTopic == topic
                Surface(
                    modifier = Modifier.clickable { activeTopic = topic },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) TbBlue.copy(alpha = 0.1f) else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) TbBlue else Color.LightGray)
                ) {
                    Text(
                        text = topic,
                        color = if (isSelected) TbBlue else Color.DarkGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Test Series Grid
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TbBlue)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCourses) { course ->
                    TestbookCourseCard(course = course, onClick = { onNavigateToCourse(course.sheetId) })
                }
            }
        }
    }
}

@Composable
fun TestbookCourseCard(course: Course, onClick: () -> Unit) {
    val isFree = course.fee == 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Full Mocks • Sectional Tests • PYQs", // Analytical metadata
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                // Top Right Icon/Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(TbBlue.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = TbBlue)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Starts From", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = if (isFree) "FREE" else "₹%.0f".format(course.fee),
                        color = if (isFree) TbGreen else Color(0xFF1E293B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFree) TbGreen else TbBlue
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(if (isFree) "Start Free Test" else "View Series", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
