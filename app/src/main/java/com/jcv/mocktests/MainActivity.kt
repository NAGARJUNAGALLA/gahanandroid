package com.jcv.mocktests

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.jcv.mocktests.ui.*
import com.jcv.mocktests.utils.LocalStorage
import java.net.URLDecoder
import java.net.URLEncoder

// ==========================================
// 1. DEFINE THE 5 GLOBAL THEMES
// ==========================================
enum class AppTheme(val themeName: String, val primaryColor: Color, val isDark: Boolean, val swatchColor: Color) {
    BLUE("Blue", Color(0xFF2962FF), false, Color(0xFF2962FF)),
    GREEN("Green", Color(0xFF2E7D32), false, Color(0xFF2E7D32)),
    PURPLE("Purple", Color(0xFF6200EA), false, Color(0xFF6200EA)),
    ORANGE("Orange", Color(0xFFE65100), false, Color(0xFFE65100)),
    DARK("Dark", Color(0xFF64B5F6), true, Color(0xFF1E293B)) 
}

@Composable
fun GlobalAppTheme(appTheme: AppTheme, content: @Composable () -> Unit) {
    val colorScheme = if (appTheme.isDark) {
        darkColorScheme(
            primary = appTheme.primaryColor,
            background = Color(0xFF0F172A), surface = Color(0xFF1E293B),
            onBackground = Color(0xFFF8FAFC), onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155), onSurfaceVariant = Color(0xFF94A3B8)
        )
    } else {
        lightColorScheme(
            primary = appTheme.primaryColor,
            background = Color(0xFFF8FAFC), surface = Color.White,
            onBackground = Color(0xFF0F172A), onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF64748B)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        com.jcv.mocktests.utils.AnalyticsHelper.init(this)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            MockTestsApp()
        }
    }
}

@Composable
fun MockTestsApp() {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    
    // Theme State
    var currentThemeName by remember { mutableStateOf(localStorage.getAppTheme()) }
    val currentTheme = AppTheme.values().find { it.themeName == currentThemeName } ?: AppTheme.BLUE
    
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser

    // -----------------------------------------------------------------
    // DYNAMIC SYSTEM BARS BASED ON CHOSEN THEME
    // -----------------------------------------------------------------
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val barColor = if (currentTheme.isDark) android.graphics.Color.parseColor("#0F172A") else currentTheme.primaryColor.toArgb()
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    GlobalAppTheme(appTheme = currentTheme) {
        NavHost(navController = navController, startDestination = "splash") {
            
            composable("splash") {
                SplashScreen(
                    onSplashFinished = {
                        val nextRoute = if (currentUser != null) "main_dashboard/home" else "login"
                        navController.navigate(nextRoute) { popUpTo("splash") { inclusive = true } }
                    }
                )
            }

            composable(route = "main_dashboard/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) { backStackEntry ->
                val tab = backStackEntry.arguments?.getString("tab") ?: "home"
                MainDashboardScreen(
                    initialTab = tab,
                    isDarkMode = currentTheme.isDark,
                    onNavigateToCourse = { courseId -> navController.navigate("course_details/$courseId") },
                    onNavigateToLogin = { navController.navigate("login") { popUpTo(0) } }
                )
            }

            composable("login") {
                AuthScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentThemeName = newTheme.themeName
                        localStorage.saveAppTheme(newTheme.themeName)
                    },
                    onNavigateToHome = { navController.navigate("main_dashboard/home") { popUpTo(0) } },
                    onNavigateToSignup = { navController.navigate("signup") }
                )
            }
            
            composable("signup") {
                SignupScreen(
                    onNavigateToHome = { navController.navigate("main_dashboard/home") { popUpTo(0) } },
                    onNavigateToLogin = { navController.navigate("login") { popUpTo("signup") { inclusive = true } } }
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
                    onNavigateToStudyMaterial = { navController.navigate("main_dashboard/home") { popUpTo(0) } },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(route = "exam/{courseId}/{testName}/{isReviewMode}", arguments = listOf(navArgument("courseId") { type = NavType.StringType }, navArgument("testName") { type = NavType.StringType }, navArgument("isReviewMode") { type = NavType.BoolType })) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val testName = URLDecoder.decode(backStackEntry.arguments?.getString("testName") ?: "", "UTF-8")
                val isReviewMode = backStackEntry.arguments?.getBoolean("isReviewMode") ?: false
                
                ExamScreen(
                    courseId = courseId, testName = testName, isReviewMode = isReviewMode,
                    onNavigateBack = { navController.popBackStack() },
                    onReviewTest = {
                        val encodedTestName = URLEncoder.encode(testName, "UTF-8")
                        navController.navigate("exam/$courseId/$encodedTestName/true") { popUpTo("course_details/$courseId") }
                    }
                )
            }
        }
    }
}
