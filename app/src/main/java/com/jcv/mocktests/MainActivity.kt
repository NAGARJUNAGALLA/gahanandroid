package com.jcv.mocktests

import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Bundle
import android.view.WindowManager
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
        com.jcv.mocktests.utils.AnalyticsHelper.init(this)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            // -----------------------------------------------------------------
            // GLOBAL SYSTEM BAR THEMING
            // -----------------------------------------------------------------
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    
                    // Set ThemeBlue (#1976D2) for both top and bottom device bars
                    window.statusBarColor = android.graphics.Color.parseColor("#1976D2")
                    window.navigationBarColor = android.graphics.Color.parseColor("#1976D2")
                    
                    // Ensures the battery, time, and navigation icons are white
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                }
            }
            MockTestsApp()
        }
    }
}

@Composable
fun MockTestsApp() {
    val navController = rememberNavController()
    
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    
    // Always start at the splash screen
    val startDestination = "splash"

    NavHost(navController = navController, startDestination = startDestination) {
        
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    val nextRoute = if (currentUser != null) "main_dashboard/home" else "login"
                    navController.navigate(nextRoute) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // Dashboard Route handling tabs
        composable(
            route = "main_dashboard/{tab}",
            arguments = listOf(navArgument("tab") { type = NavType.StringType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "home"
            
            MainDashboardScreen(
                initialTab = tab,
                onNavigateToCourse = { courseId -> navController.navigate("course_details/$courseId") },
                onNavigateToLogin = { 
                    navController.navigate("login") {
                        popUpTo(0) 
                    }
                }
            )
        }

        composable("login") {
            AuthScreen(
                onNavigateToHome = { 
                    navController.navigate("main_dashboard/home") { 
                        popUpTo(0) 
                    } 
                },
                onNavigateToSignup = { navController.navigate("signup") }
            )
        }
        
        composable("signup") {
            SignupScreen(
                onNavigateToHome = { 
                    navController.navigate("main_dashboard/home") { popUpTo(0) } 
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
                onNavigateToStudyMaterial = { 
                    navController.navigate("main_dashboard/home") { popUpTo(0) }
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
            navController.navigate("results_screen/$score/$total") { 
                popUpTo("main_dashboard/home") 
            }
        },
        onExitReview = {
            navController.popBackStack()
        }
    )
        }
        
        composable(
    route = "results_screen/{score}/{totalQuestions}",
    arguments = listOf(
        navArgument("score") { type = NavType.IntType }, // <-- BACK TO IntType
        navArgument("totalQuestions") { type = NavType.IntType }
    )
) { backStackEntry ->
    
    val finalScore = backStackEntry.arguments?.getInt("score") ?: 0 // <-- BACK TO getInt
    val total = backStackEntry.arguments?.getInt("totalQuestions") ?: 0
    
    ResultScreen(
        score = finalScore, 
        totalQuestions = total,
        onNavigateHome = {
            navController.navigate("main_dashboard/home") {
                popUpTo(0)
            }
        }
    )
}
    }
}
