package com.jcv.mocktests.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jcv.mocktests.models.Course

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCourse: (String) -> Unit) { // <-- Changed parameter name
    val courses = listOf(
        Course("1", "GROUP 1 MOCK TEST", 0.0, "Free group 1 test series"),
        Course("2", "TET & DSC PRO", 499.0, "Premium DSC tests")
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("JCV HUB") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(courses) { course ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onNavigateToCourse(course.sheetId) } // <-- Updated click listener
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
