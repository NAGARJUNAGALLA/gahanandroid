package com.jcv.mocktests

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    
    // NEW: Check Firebase Auth state to determine starting screen
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    
    // CHANGE: Start at login if not authenticated, otherwise go to dashboard
    val startDestination = if (currentUser != null) {
        "main_dashboard/study_material"
    } else {
        "login"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        
        // Dashboard Route handling tabs
        composable(
            route = "main_dashboard/{tab}",
            arguments = listOf(navArgument("tab") { type = NavType.StringType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "study_material"
            
            MainDashboardScreen(
                initialTab = tab,
                onNavigateToCourse = { courseId -> navController.navigate("course_details/$courseId") },
                onNavigateToLogin = { 
                    navController.navigate("login") {
                        popUpTo(0) // Clear backstack on logout
                    }
                }
            )
        }

        composable("login") {
            AuthScreen(
                // After successful login, route back to the Dashboard on the "tests" tab
                onNavigateToHome = { 
                    navController.navigate("main_dashboard/tests") { 
                        popUpTo(0) // Clear the backstack so they don't hit the login screen again by pressing back
                    } 
                },
                onNavigateToSignup = { navController.navigate("signup") }
            )
        }
        
        composable("signup") {
            SignupScreen(
                onNavigateToHome = { 
                    navController.navigate("main_dashboard/tests") { popUpTo(0) } 
                },
                onNavigateToLogin = { 
                    navController.navigate("login") { popUpTo("signup") { inclusive = true } } 
                }
            )
        }
        
        composable("course_details/{courseId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            CourseDetailScreen(
                courseId = courseId,
                onNavigateToExam = { cId, testName, isReviewMode -> 
                    val encodedTestName = URLEncoder.encode(testName, "UTF-8")
                    navController.navigate("exam/$cId/$encodedTestName/$isReviewMode") 
                },
                // Route to the study material tab in the dashboard
                onNavigateToStudyMaterial = { 
                    navController.navigate("main_dashboard/study_material") { popUpTo(0) }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
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
                    navController.navigate("results/$score/$total") { 
                        popUpTo("main_dashboard/tests") 
                    }
                },
                onExitReview = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("results/{score}/{total}") { backStackEntry ->
            val score = backStackEntry.arguments?.getString("score")?.toInt() ?: 0
            val total = backStackEntry.arguments?.getString("total")?.toInt() ?: 0
            ResultScreen(
                score = score,
                totalQuestions = total,
                onNavigateHome = { 
                    navController.navigate("main_dashboard/tests") { popUpTo(0) } 
                }
            )
        }
    }
}
