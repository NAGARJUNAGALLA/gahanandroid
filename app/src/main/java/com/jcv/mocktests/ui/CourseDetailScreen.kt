package com.jcv.mocktests.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String?,
    onNavigateToExam: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("OVERVIEW", "CONTENT")
    
    // Mock data for tests inside this course
    val mockTests = listOf("Grand Test 1", "Grand Test 2", "Subject Test - History")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Details") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
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
                // Overview Tab
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About this Course", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Comprehensive mock tests designed to help you prepare and excel in your upcoming examinations.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { selectedTab = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Go to Content")
                    }
                }
            } else {
                // Content Tab
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(mockTests) { testName ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onNavigateToExam(testName) } // Pass test ID to exam screen
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(testName, style = MaterialTheme.typography.bodyLarge)
                                Text(">")
                            }
                        }
                    }
                }
            }
        }
    }
}
