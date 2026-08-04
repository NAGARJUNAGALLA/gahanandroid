package com.jcv.mocktests.ui

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Course
import java.util.Locale

val JcvBlue = Color(0xFF1E90FF)
val DarkGradient = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A8A)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCourse: (String) -> Unit) {
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var activeTopic by remember { mutableStateOf("ALL") }

    // Fetch and auto-categorize courses (Mirroring web app logic)
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document("testList").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val testsList = document.get("tests") as? List<Map<String, Any>> ?: emptyList()
                    val fetchedCourses = testsList.map { map ->
                        val title = map["title"] as? String ?: "JCV Course"
                        val titleUpper = title.uppercase(Locale.getDefault())
                        
                        // Web app's auto-categorization logic
                        val topic = when {
                            titleUpper.contains("GROUP") -> "GROUP EXAMS"
                            titleUpper.contains("CURRENT") -> "CURRENT AFFAIRS"
                            titleUpper.contains("IIT") || titleUpper.contains("CONSTABLE") -> "IIT"
                            titleUpper.contains("TET") || titleUpper.contains("DSC") -> "TET & DSC"
                            else -> "OTHERS"
                        }

                        Course(
                            sheetId = map["sheetId"] as? String ?: "",
                            title = title,
                            fee = (map["fee"] as? Number)?.toDouble() ?: 0.0,
                            description = map["description"] as? String ?: "",
                            topic = topic
                        )
                    }
                    courses = fetchedCourses
                } else {
                    errorMessage = "No courses available at the moment."
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorMessage = e.message
                isLoading = false
            }
    }

    // Filter Logic
    val filteredCourses = courses.filter { course ->
        val matchesTopic = when (activeTopic) {
            "ALL" -> true
            "FREE" -> course.fee == 0.0
            else -> course.topic == activeTopic
        }
        val matchesSearch = course.title.contains(searchQuery, ignoreCase = true)
        matchesTopic && matchesSearch
    }

    // Extract dynamic topics for chips
    val dynamicTopics = courses.map { it.topic }.distinct()
    val allTopics = listOf("ALL", "FREE") + dynamicTopics

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JCV MOCK TESTS", fontWeight = FontWeight.Black, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JcvBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)) // Web app background color
                .padding(16.dp)
        ) {
            // 1. Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.DarkGray),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search for a course...", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Filter Chips
            Text("FILTER BY TOPICS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allTopics) { topic ->
                    val isActive = topic == activeTopic
                    val count = when (topic) {
                        "ALL" -> courses.size
                        "FREE" -> courses.count { it.fee == 0.0 }
                        else -> courses.count { it.topic == topic }
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActive) Color(0xFFEFF6FF) else Color.White,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isActive) JcvBlue else Color(0xFFE2E8F0),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { activeTopic = topic }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$topic ($count)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) JcvBlue else Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Course Grid Header
            Text("COURSES (${filteredCourses.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // 4. Course Grid (2 Columns just like the web app)
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JcvBlue)
                }
            } else if (filteredCourses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No courses found matching your criteria.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCourses) { course ->
                        CourseCard(course = course, onClick = { onNavigateToCourse(course.sheetId) })
                    }
                }
            }
        }
    }
}

@Composable
fun CourseCard(course: Course, onClick: () -> Unit) {
    val isFree = course.fee == 0.0
    val mockOriginalPrice = if (isFree) 0.0 else course.fee * 2.0 // Mocking original price logic
    val discountPercent = if (!isFree) (((mockOriginalPrice - course.fee) / mockOriginalPrice) * 100).toInt() else 0
    val mockLikes = remember { (100..999).random() } // Mocking likes as per web app

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Recreating the SVG Thumbnail from Web
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f) // Standard banner ratio
                    .background(DarkGradient)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "JCV MOCK TESTS",
                        color = Color(0xFFFACC15),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEF4444), RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = course.title.take(20).uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "FULL COURSE",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Course Details (Bottom Half)
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = course.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
                
                // Likes
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, contentDescription = "Likes", tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(mockLikes.toString(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Pricing Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (!isFree) Text("₹", fontSize = 9.sp, color = Color.Gray)
                        Text(
                            text = if (isFree) "Free" else "%.0f".format(course.fee),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    
                    if (!isFree) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹%.0f".format(mockOriginalPrice),
                                fontSize = 9.sp,
                                color = Color.Gray,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Text(
                                text = "$discountPercent% OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }
    }
}
