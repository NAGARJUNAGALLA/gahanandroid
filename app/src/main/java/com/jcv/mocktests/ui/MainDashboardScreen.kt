package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

enum class BottomTab { HOME, MY_TESTS, PROFILE }

@Composable
fun MainDashboardScreen(
    initialTab: String,
    onNavigateToCourse: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "my_tests" -> BottomTab.MY_TESTS
                "profile" -> BottomTab.PROFILE
                else -> BottomTab.HOME
            }
        )
    }
    
    val auth = FirebaseAuth.getInstance()
    val ByjusPurple = Color(0xFF6D28D9)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = Color.Gray,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == BottomTab.HOME,
                    onClick = { selectedTab = BottomTab.HOME },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ByjusPurple,
                        selectedTextColor = ByjusPurple,
                        indicatorColor = ByjusPurple.copy(alpha = 0.1f),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "My Tests") },
                    label = { Text("My Tests", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == BottomTab.MY_TESTS,
                    onClick = {
                        if (auth.currentUser == null) {
                            onNavigateToLogin()
                        } else {
                            selectedTab = BottomTab.MY_TESTS
                        }
                    }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == BottomTab.PROFILE,
                    onClick = {
                        if (auth.currentUser == null) {
                            onNavigateToLogin()
                        } else {
                            selectedTab = BottomTab.PROFILE
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                BottomTab.HOME -> {
                    HomeScreen(onNavigateToCourse = onNavigateToCourse)
                }
                BottomTab.MY_TESTS -> {
                    MyPurchasedTestsScreen(onNavigateToCourse = onNavigateToCourse)
                }
                BottomTab.PROFILE -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Profile & Settings")
                    }
                }
            }
        }
    }
}
