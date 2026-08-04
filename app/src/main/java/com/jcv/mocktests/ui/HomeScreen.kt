package com.jcv.mocktests.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Course

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCourse: (String) -> Unit) {
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document("testList").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Match the web app array structure
                    val testsList = document.get("tests") as? List<Map<String, Any>> ?: emptyList()
                    val fetchedCourses = testsList.map { map ->
                        Course(
                            sheetId = map["sheetId"] as? String ?: "",
                            title = map["title"] as? String ?: "JCV Course",
                            fee = (map["fee"] as? Number)?.toDouble() ?: 0.0,
                            description = map["description"] as? String ?: ""
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("JCV HUB") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(courses) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { onNavigateToCourse(course.sheetId) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(course.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (course.fee == 0.0) "Free" else "₹${course.fee}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
