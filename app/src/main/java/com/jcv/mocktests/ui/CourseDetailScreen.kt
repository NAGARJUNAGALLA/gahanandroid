package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.utils.LocalStorage 

val DarkHeaderColor = Color(0xFF181E2F)


data class TestSummary(
    val name: String,
    val questionCount: Int,
    val timeMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onNavigateToExam: (courseId: String, testName: String, isReviewMode: Boolean) -> Unit,
    onNavigateToStudyMaterial: () -> Unit, // Kept to prevent breaking MainActivity, but no longer used in UI
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("OVERVIEW", "CONTENT")
    
    var tests by remember { mutableStateOf<List<TestSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(courseId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    
                    val parsedTests = mutableListOf<TestSummary>()
                    
                    testsMap?.forEach { (testName, testData) ->
                        var qCount = 0
                        try {
                            val sectionsMap = testData as? Map<String, List<Any>>
                            sectionsMap?.forEach { (_, qList) ->
                                qCount += qList.size
                            }
                        } catch (e: Exception) {
                            // Fallback if structure varies
                        }
                        
                        parsedTests.add(TestSummary(
                            name = testName,
                            questionCount = qCount,
                            timeMinutes = qCount 
                        ))
                    }
                    tests = parsedTests
                }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Details", color = Color.White) },
                navigationIcon = { 
                    TextButton(onClick = onNavigateBack) { 
                        Text("Back", color = Color.White) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkHeaderColor
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                contentColor = ViewSeriesBlue,
                containerColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                text = title, 
                                color = if (selectedTab == index) ViewSeriesBlue else Color.Gray,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About this Course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Comprehensive mock tests designed to help you prepare and excel.")
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // NEW: Dynamic Stats Row for Overview
                    val totalQs = tests.sumOf { it.questionCount }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Card(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${tests.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
                                Text("Total Tests", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        
                        Card(
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalQs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
                                Text("Total Questions", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    OutlinedButton(
                        onClick = { selectedTab = 1 }, 
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ViewSeriesBlue)
                    ) {
                        Text("Go to Content", fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentAlignment = Alignment.TopCenter) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ViewSeriesBlue)
                        }
                    } else if (tests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tests found for this course.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(tests) { test ->
                                val alreadyAttempted = localStorage.isTestAttempted(courseId, test.name)
                                val testScore = localStorage.getTestScore(courseId, test.name)
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = test.name, 
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkHeaderColor
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.List, contentDescription = "Questions", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${test.questionCount} Questions", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DateRange, contentDescription = "Time", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${test.timeMinutes} Mins", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Divider(color = Color(0xFFF3F4F6))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        if (alreadyAttempted && testScore != null) {
                                            val formatScore = { value: Float -> 
                                                if (value % 1.0f == 0f) value.toInt().toString() else value.toString() 
                                            }
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Highest Score", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${formatScore(testScore.first)} / ${formatScore(testScore.second)}", 
                                                        color = Color(0xFF27AE60),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { onNavigateToExam(courseId, test.name, true) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ViewSeriesBlue),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Review Test", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { onNavigateToExam(courseId, test.name, false) },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                                            ) {
                                                Text("Take Test", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
