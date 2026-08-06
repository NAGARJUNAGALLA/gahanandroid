package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class BottomTab { HOME, FREE_COURSES, PRO_COURSES, PURCHASED_COURSES }

val ThemeBlue = Color(0xFF1976D2)
val ThemeDarkBlue = Color(0xFF0D47A1)
val LightGreyBg = Color(0xFFF5F6FA)

// Data model for dynamic Firebase fetching
data class CourseModel(
    val id: String,
    val title: String,
    val price: String,
    val keywords: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    initialTab: String,
    onNavigateToCourse: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "free_courses" -> BottomTab.FREE_COURSES
                "pro_courses" -> BottomTab.PRO_COURSES
                "purchased_courses" -> BottomTab.PURCHASED_COURSES
                else -> BottomTab.HOME
            }
        )
    }

    val auth = FirebaseAuth.getInstance()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // State for dynamic Firebase data
    var courses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    // Fetch Courses and Categories dynamically
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        // Assuming your courses are stored in a "courses" or "pro_course_questions" collection
        // Adjust the collection name if yours is different in Firebase
        db.collection("courses").get().addOnSuccessListener { result ->
            val fetchedCourses = result.documents.mapNotNull { doc ->
                val title = doc.getString("title") ?: doc.getString("name") ?: "Course"
                val price = doc.getString("price") ?: "Free"
                val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                CourseModel(doc.id, title, price, keywords)
            }
            courses = fetchedCourses
            
            // Extract unique keywords for dynamic categories
            val extractedCategories = fetchedCourses.flatMap { it.keywords }.distinct()
            categories = extractedCategories
        }
    }

    // Categorize courses
    val freeCourses = courses.filter { it.price.equals("free", ignoreCase = true) || it.price == "0" }
    val proCourses = courses.filter { !it.price.equals("free", ignoreCase = true) && it.price != "0" }
    // You would typically fetch purchased courses via a separate user-specific subcollection
    val purchasedCourses = emptyList<CourseModel>() 

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                // Background color applied directly to Column to fix containerColor error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ThemeBlue)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("JCV HUB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = ThemeBlue) },
                        label = { Text("Home Dashboard", fontWeight = FontWeight.Bold) },
                        selected = selectedTab == BottomTab.HOME,
                        onClick = { 
                            selectedTab = BottomTab.HOME
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp)) // Fixed HorizontalDivider

                    // DYNAMIC FREE COURSES
                    if (freeCourses.isNotEmpty()) {
                        DrawerSectionHeader("FREE COURSES", Icons.Default.Star, Color(0xFF4CAF50))
                        freeCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.id) }
                        }
                    }

                    // DYNAMIC PURCHASED COURSES (Placeholder for your logic)
                    if (purchasedCourses.isNotEmpty()) {
                        DrawerSectionHeader("PURCHASED COURSES", Icons.Default.List, Color(0xFF9C27B0))
                        purchasedCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.id) }
                        }
                    }

                    // DYNAMIC PRO COURSES
                    if (proCourses.isNotEmpty()) {
                        DrawerSectionHeader("PRO COURSES", Icons.Default.Star, Color(0xFFFF9800))
                        proCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.id) }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Divider()
                    
                    // Profile & Logout
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "User", modifier = Modifier.size(40.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(auth.currentUser?.displayName ?: "User Name", fontWeight = FontWeight.Bold)
                            Text(auth.currentUser?.email ?: "user@jcv.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            auth.signOut()
                            onNavigateToLogin()
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("JCV MOCK TESTS", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Notifications */ }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeBlue)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = Color.Gray,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.HOME,
                        onClick = { selectedTab = BottomTab.HOME },
                        colors = navColors()
                    )
                    
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "Free Courses") },
                        label = { Text("Free Courses", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.FREE_COURSES,
                        onClick = { selectedTab = BottomTab.FREE_COURSES },
                        colors = navColors()
                    )
                    
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "Pro Courses") },
                        label = { Text("Pro Courses", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.PRO_COURSES,
                        onClick = { selectedTab = BottomTab.PRO_COURSES },
                        colors = navColors()
                    )
                    
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Purchased") },
                        label = { Text("Purchased", fontSize = 10.sp, maxLines = 1) },
                        selected = selectedTab == BottomTab.PURCHASED_COURSES,
                        onClick = { 
                            if (auth.currentUser == null) onNavigateToLogin() 
                            else selectedTab = BottomTab.PURCHASED_COURSES 
                        },
                        colors = navColors()
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(LightGreyBg)) {
                when (selectedTab) {
                    BottomTab.HOME -> {
                        DashboardHomeContent(categories, onNavigateToCourse)
                    }
                    BottomTab.FREE_COURSES -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Free Courses List") }
                    }
                    BottomTab.PRO_COURSES -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Pro Courses List") }
                    }
                    BottomTab.PURCHASED_COURSES -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Purchased Courses List") }
                    }
                }
            }
        }
    }
}

@Composable
fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = ThemeBlue,
    selectedTextColor = ThemeBlue,
    indicatorColor = ThemeBlue.copy(alpha = 0.1f),
    unselectedIconColor = Color.Gray,
    unselectedTextColor = Color.Gray
)

@Composable
fun DrawerSectionHeader(title: String, icon: ImageVector, iconTint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DrawerCourseItem(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 40.dp, vertical = 10.dp)
    )
}

// ---------------------------------------------------------------------------
// HOME TAB CONTENT 
// ---------------------------------------------------------------------------

@Composable
fun DashboardHomeContent(dynamicCategories: List<String>, onNavigateToCourse: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Image Slider Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(ThemeDarkBlue),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Slider", tint = Color.White, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Promotional Banner / Image Slider", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What are you looking for?",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fallback categories if Firebase returns nothing
        val displayCategories = dynamicCategories.ifEmpty { 
            listOf("ALL COURSES", "TET & DSC", "GENERAL EXAMS", "TEST SERIES") 
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(max = 600.dp) // Bounds grid height
        ) {
            items(displayCategories) { categoryTitle ->
                CategoryCard(categoryTitle)
            }
        }
    }
}

@Composable
fun CategoryCard(title: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ThemeBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .aspectRatio(0.9f)
            .clickable { /* Handle Category Filter Click */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                // Using a generic list icon for dynamically fetched categories
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}
