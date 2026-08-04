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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

val JcvBlue = Color(0xFF2563EB) 
val SurfaceGray = Color(0xFFF8FAFC)
val SuccessGreen = Color(0xFF16A34A)
val CardGradient = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCourse: (String) -> Unit
) {
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var activeTopic by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document("testList").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val testsList = document.get("tests") as? List<Map<String, Any>> ?: emptyList()
                    val fetchedCourses = testsList.map { map ->
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
                    courses = fetchedCourses
                } else {
                    errorMessage = "No test series available at the moment."
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                errorMessage = e.message
                isLoading = false
            }
    }

    val filteredCourses = courses.filter { course ->
        val matchesTopic = when (activeTopic) {
            "ALL" -> true
            "FREE" -> course.fee == 0.0
            else -> course.topic == activeTopic
        }
        val matchesSearch = course.title.contains(searchQuery, ignoreCase = true)
        matchesTopic && matchesSearch
    }

    val dynamicTopics = courses.map { it.topic }.distinct()
    val allTopics = listOf("ALL", "FREE") + dynamicTopics

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Hi, Aspirant \uD83D\uDC4B", fontSize = 14.sp, color = Color.LightGray)
                        Text("What are you preparing for?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JcvBlue),
                actions = {
                    IconButton(onClick = { /* TODO: Notifications */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(SurfaceGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JcvBlue)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = Color.DarkGray),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search for test series...", color = Color.Gray, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(3) { 
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Mega Mock Test Challenge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Fix: Replaced invalid `opacity = 0.9f` with `Color.White.copy(alpha = 0.9f)`
                            Text("Attempt live & check all-India rank", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Explore Categories", 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTopics) { topic ->
                    val isActive = topic == activeTopic
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isActive) Color(0xFFDBEAFE) else Color.White)
                            .border(1.dp, if (isActive) JcvBlue else Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
                            .clickable { activeTopic = topic }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = topic,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) JcvBlue else Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Recommended Test Series", 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JcvBlue)
                }
            } else if (filteredCourses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No test series found.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCourses) { course ->
                        TestSeriesCard(course = course, onClick = { onNavigateToCourse(course.sheetId) })
                    }
                }
            }
        }
    }
}

@Composable
fun TestSeriesCard(course: Course, onClick: () -> Unit) {
    val isFree = course.fee == 0.0
    val mockOriginalPrice = if (isFree) 0.0 else course.fee * 1.5
    val discountPercent = if (!isFree) (((mockOriginalPrice - course.fee) / mockOriginalPrice) * 100).toInt() else 0
    val dummyTestCount = remember { (20..150).random() } 

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(CardGradient)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("TEST SERIES", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = course.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("4.8  •  $dummyTestCount+ Tests", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (!isFree) {
                            Text(
                                text = "₹%.0f".format(mockOriginalPrice),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Text(
                            text = if (isFree) "FREE" else "₹%.0f".format(course.fee),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                    }
                    
                    if (!isFree) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("$discountPercent% OFF", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
