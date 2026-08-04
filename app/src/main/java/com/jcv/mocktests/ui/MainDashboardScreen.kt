package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info // Changed from Book to Info to fix the compile error
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
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Study Material") },
                    label = { Text("Study") },
                    selected = selectedTab == BottomTab.STUDY_MATERIAL,
                    onClick = { selectedTab = BottomTab.STUDY_MATERIAL }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Tests") },
                    label = { Text("Tests") },
                    selected = selectedTab == BottomTab.TESTS,
                    onClick = {
                        if (auth.currentUser == null) {
                            onNavigateToLogin()
                        } else {
                            selectedTab = BottomTab.TESTS
                        }
                    }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Videos") },
                    label = { Text("Videos") },
                    selected = selectedTab == BottomTab.VIDEOS,
                    onClick = { selectedTab = BottomTab.VIDEOS }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                BottomTab.STUDY_MATERIAL -> {
                    StudyMaterialScreen(
                        url = "https://jcv-mock-tests.web.app/studymaterial.html", 
                        onNavigateBack = {} 
                    )
                }
                BottomTab.TESTS -> {
                    // Passes the required parameter to fix line 85 error
                    HomeScreen(
                        onNavigateToCourse = onNavigateToCourse,
                        onNavigateToStudyMaterial = { selectedTab = BottomTab.STUDY_MATERIAL }
                    )
                }
                BottomTab.VIDEOS -> {
                    StudyMaterialScreen(
                        url = "https://jcv-mock-tests.web.app/videos.html", 
                        onNavigateBack = {} 
                    )
                }
            }
        }
    }
}
