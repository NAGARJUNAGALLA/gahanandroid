package com.jcv.mocktests

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jcv.mocktests.utils.LocalStorage
class MainActivity : FragmentActivity() {

    // 1. The permission request launcher for Android 13+ Notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, Firebase can send notifications!
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 2. Ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3. Initialize Analytics
        com.jcv.mocktests.utils.AnalyticsHelper.init(this)
        
        // 4. Security: Prevent Screenshots and Screen Recording
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
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val systemTheme = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(localStorage.isDarkMode(systemTheme)) }
    val navController = rememberNavController()
    
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    
    // Always start at the splash screen
    val startDestination = "splash"

    NavHost(navController = navController, startDestination = startDestination) {
        
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    val nextRoute = if (currentUser != null) "unlock" else "login"
                    navController.navigate(nextRoute) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        // ADD THIS NEW ROUTE
        composable("unlock") {
            UnlockScreen(
                onUnlockSuccess = {
                    // Fingerprint matched! Proceed to the main dashboard.
                    navController.navigate("main_dashboard/home") {
                        popUpTo("unlock") { inclusive = true }
                    }
                },
                onLogout = {
                    // User chose to log out
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo("unlock") { inclusive = true }
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
                isDarkMode = isDarkMode, // Passes the dark mode state down
                onToggleTheme = {        // Handles the toggle click from the dashboard
                    isDarkMode = !isDarkMode
                    localStorage.setDarkMode(isDarkMode)
                },
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
        
        // -----------------------------------------------------------------
        // UPDATED EXAM ROUTE (Handles the merged Results & Review modes)
        // -----------------------------------------------------------------
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
                onNavigateBack = {
                    navController.popBackStack()
                },
                onReviewTest = {
                    val encodedTestName = URLEncoder.encode(testName, "UTF-8")
                    // Relaunch the exam screen but force isReviewMode to true
                    navController.navigate("exam/$courseId/$encodedTestName/true") {
                        popUpTo("course_details/$courseId")
                    }
                }
            )
        }
        
    }
}
