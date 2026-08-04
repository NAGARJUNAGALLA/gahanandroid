package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Course
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPurchasedTestsScreen(
    onNavigateToCourse: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var purchasedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            errorMessage = "Please log in to view your test series."
            isLoading = false
            return@LaunchedEffect
        }

        val db = FirebaseFirestore.getInstance()

        // Fetch user's purchased course IDs
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val purchasedIds = userDoc.get("purchasedCourses") as? List<String> ?: emptyList()

                if (purchasedIds.isEmpty()) {
                    isLoading = false
                    return@addOnSuccessListener
                }

                // Fetch the main test list and filter by purchased IDs
                db.collection("exams").document("testList").get()
                    .addOnSuccessListener { examsDoc ->
                        if (examsDoc.exists()) {
                            val testsList = examsDoc.get("tests") as? List<Map<String, Any>> ?: emptyList()
                            val fetchedCourses = testsList.mapNotNull { map ->
                                val sheetId = map["sheetId"] as? String ?: return@mapNotNull null
                                
                                if (purchasedIds.contains(sheetId)) {
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
                                        sheetId = sheetId,
                                        title = title,
                                        fee = (map["fee"] as? Number)?.toDouble() ?: 0.0,
                                        description = map["description"] as? String ?: "",
                                        topic = topic
                                    )
                                } else null
                            }
                            purchasedCourses = fetchedCourses
                        }
                        isLoading = false
                    }
                    .addOnFailureListener { e ->
                        errorMessage = e.message
                        isLoading = false
                    }
            }
            .addOnFailureListener { e ->
                errorMessage = e.message
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Learning", fontWeight = FontWeight.ExtraBold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ByjusPurple) // Using the Byju's purple theme
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBackground) // Using the soft gray background
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ByjusPurple)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage!!, color = Color.Red, fontSize = 14.sp)
                }
            } else if (purchasedCourses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No purchased tests yet.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Head to Home to start learning!", color = Color.LightGray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1), // Match the single-column gamified layout
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(purchasedCourses) { course ->
                        // Reusing the gamified card from the HomeScreen
                        ByjusCourseCard(course = course, onClick = { onNavigateToCourse(course.sheetId) })
                    }
                }
            }
        }
    }
}
