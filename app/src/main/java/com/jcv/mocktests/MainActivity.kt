package com.jcv.mocktests

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.jcv.mocktests.ui.*
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MockTestsApp()
        }
    }
}

@Composable
fun MockTestsApp() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser != null) "home" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            AuthScreen(
                onNavigateToHome = { navController.navigate("home") { popUpTo("login") { inclusive = true } } },
                onNavigateToSignup = { navController.navigate("signup") }
            )
        }
        composable("signup") {
            SignupScreen(
                onNavigateToHome = { navController.navigate("home") { popUpTo("signup") { inclusive = true } } },
                onNavigateToLogin = { navController.navigate("login") { popUpTo("signup") { inclusive = true } } }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToCourse = { courseId -> navController.navigate("course_details/$courseId") }
            )
        }
        composable("course_details/{courseId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            CourseDetailScreen(
                courseId = courseId,
                // Updated lambda to accept courseId, testName, and isReviewMode
                onNavigateToExam = { cId, testName, isReviewMode -> 
                    // URL encode the test name just in case it has spaces
                    val encodedTestName = URLEncoder.encode(testName, "UTF-8")
                    // Append isReviewMode to the navigation route
                    navController.navigate("exam/$cId/$encodedTestName/$isReviewMode") 
                },
                // NEW: Added navigation to Study Material here
                onNavigateToStudyMaterial = { navController.navigate("study_material") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // NEW ROUTE: Study Material Web App
        composable("study_material") {
            StudyMaterialScreen(
                // Replace this URL with your actual live web app link
                url = "https://your-web-app-link.com/maths-notes", 
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Updated route to accept courseId, testName, and isReviewMode
        composable(
            route = "exam/{courseId}/{testName}/{isReviewMode}",
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType },
                navArgument("testName") { type = NavType.StringType },
                navArgument("isReviewMode") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            val testName = URLDecoder.decode(backStackEntry.arguments?.getString("testName") ?: "", "UTF-8")
            val isReviewMode = backStackEntry.arguments?.getBoolean("isReviewMode") ?: false
            
            ExamScreen(
                courseId = courseId,
                testName = testName,
                isReviewMode = isReviewMode,
                onFinalSubmit = { score, total -> 
                    navController.navigate("results/$score/$total") { popUpTo("home") }
                }
            )
        }
        composable("results/{score}/{total}") { backStackEntry ->
            val score = backStackEntry.arguments?.getString("score")?.toInt() ?: 0
            val total = backStackEntry.arguments?.getString("total")?.toInt() ?: 0
            ResultScreen(
                score = score,
                totalQuestions = total,
                onNavigateHome = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }
    }
}
