package com.jcv.mocktests.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.utils.LocalStorage // Make sure this matches your package

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    // Updated signature to match the Navigation requirements
    onNavigateToExam: (courseId: String, testName: String, isReviewMode: Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    // Initialize Context and LocalStorage
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("OVERVIEW", "CONTENT")
    
    var testNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(courseId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    // Get just the names of the tests (e.g. "Mock Test 1")
                    testNames = testsMap?.keys?.toList() ?: emptyList()
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
                title = { Text("Course Details") },
                navigationIcon = { TextButton(onClick = onNavigateBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About this Course", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Comprehensive mock tests designed to help you prepare and excel.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { selectedTab = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Go to Content")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else if (testNames.isEmpty()) {
                        Text("No tests found for this course.")
                    } else {
                        LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            items(testNames) { testName ->
                                // Check if attempted for UI display purposes
                                val alreadyAttempted = localStorage.isTestAttempted(courseId, testName)
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable { 
                                            // Check state and navigate with the boolean flag
                                            onNavigateToExam(courseId, testName, alreadyAttempted) 
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(testName, style = MaterialTheme.typography.bodyLarge)
                                        
                                        // Update UI indicator based on local storage
                                        if (alreadyAttempted) {
                                            Text(
                                                "Review", 
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        } else {
                                            Text(">")
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
