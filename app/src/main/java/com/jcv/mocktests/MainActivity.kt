package com.jcv.mocktests

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                onNavigateToExam = { testName -> 
                    // URL encode the test name just in case it has spaces
                    val encodedTestName = URLEncoder.encode(testName, "UTF-8")
                    navController.navigate("exam/$courseId/$encodedTestName") 
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // Updated route to accept both courseId and testName
        composable("exam/{courseId}/{testName}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            val testName = URLDecoder.decode(backStackEntry.arguments?.getString("testName") ?: "", "UTF-8")
            
            ExamScreen(
                courseId = courseId,
                testName = testName,
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
