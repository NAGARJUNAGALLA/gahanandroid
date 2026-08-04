package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth

enum class BottomTab { STUDY_MATERIAL, TESTS, VIDEOS }

@Composable
fun MainDashboardScreen(
    initialTab: String,
    onNavigateToCourse: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    // Determine which tab to show based on navigation arguments
    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "tests" -> BottomTab.TESTS
                "videos" -> BottomTab.VIDEOS
                else -> BottomTab.STUDY_MATERIAL
            }
        )
    }
    
    val auth = FirebaseAuth.getInstance()

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                // 1. STUDY MATERIAL TAB
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Book, contentDescription = "Study Material") },
                    label = { Text("Study") },
                    selected = selectedTab == BottomTab.STUDY_MATERIAL,
                    onClick = { selectedTab = BottomTab.STUDY_MATERIAL }
                )
                
                // 2. TESTS TAB (Protected by Login)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Tests") },
                    label = { Text("Tests") },
                    selected = selectedTab == BottomTab.TESTS,
                    onClick = {
                        if (auth.currentUser == null) {
                            // If not logged in, trigger login navigation
                            onNavigateToLogin()
                        } else {
                            // If logged in, show the tests screen
                            selectedTab = BottomTab.TESTS
                        }
                    }
                )
                
                // 3. VIDEOS TAB
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Videos") },
                    label = { Text("Videos") },
                    selected = selectedTab == BottomTab.VIDEOS,
                    onClick = { selectedTab = BottomTab.VIDEOS }
                )
            }
        }
    ) { paddingValues ->
        // Render the content of the selected tab
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                BottomTab.STUDY_MATERIAL -> {
                    StudyMaterialScreen(
                        url = "https://your-web-app-link.com/study-notes", 
                        onNavigateBack = {} // Left empty since it's a top-level tab now
                    )
                }
                BottomTab.TESTS -> {
                    // This calls your existing HomeScreen content
                    HomeScreen(
                        onNavigateToCourse = onNavigateToCourse,
                        onNavigateToStudyMaterial = { selectedTab = BottomTab.STUDY_MATERIAL }
                    )
                }
                BottomTab.VIDEOS -> {
                    // Reusing your WebView logic for the videos page
                    StudyMaterialScreen(
                        url = "https://your-web-app-link.com/videos", 
                        onNavigateBack = {} 
                    )
                }
            }
        }
    }
}
