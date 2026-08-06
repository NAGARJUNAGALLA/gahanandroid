package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class BottomTab { HOME, FREE_COURSES, PRO_COURSES, PURCHASED_COURSES }

val ThemeBlue = Color(0xFF1976D2)
val ThemeDarkBlue = Color(0xFF181E2F)
val LightGreyBg = Color(0xFFF5F6FA)
val GoldColor = Color(0xFFFFC107)
val RedBadgeColor = Color(0xFFE53935)

// Data models mirroring your Firebase structure
data class CourseModel(
    val sheetId: String,
    val title: String,
    val fee: Double,
    val topic: String
)

data class PaymentModel(
    val sheetId: String,
    val status: String
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
    var allCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var freeCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var proCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var purchasedCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var dynamicTopics by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch logic based on source code instructions
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        db.collection("exams").document("testList").get().addOnSuccessListener { examDoc ->
            val testsArray = examDoc.get("tests") as? List<Map<String, Any>> ?: emptyList()
            
            val fetchedCourses = testsArray.map { map ->
                val title = map["title"] as? String ?: "JCV Course"
                val feeString = map["fee"]?.toString() ?: "0"
                val fee = feeString.toDoubleOrNull() ?: 0.0
                val sheetId = map["sheetId"] as? String ?: ""
                
                // Categorization logic matching your web app rules
                val topic = when {
                    title.uppercase().contains("GROUP") -> "GROUP EXAMS"
                    title.uppercase().contains("CURRENT") -> "CURRENT AFFAIRS"
                    title.uppercase().contains("IIT") || title.uppercase().contains("CONSTABLE") -> "IIT"
                    title.uppercase().contains("TET") || title.uppercase().contains("DSC") -> "TET & DSC"
                    else -> "OTHERS"
                }
                
                CourseModel(sheetId, title, fee, topic)
            }

            if (uid != null) {
                db.collection("pending_registrations").whereEqualTo("uid", uid).get().addOnSuccessListener { paySnap ->
                    val payments = paySnap.documents.mapNotNull { doc ->
                        val sheetId = doc.getString("sheetId") ?: ""
                        val status = doc.getString("status") ?: ""
                        PaymentModel(sheetId, status)
                    }
                    
                    // Approved purchases
                    val approvedSheetIds = payments.filter { it.status == "approved" }.map { it.sheetId }
                    
                    allCourses = fetchedCourses
                    freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                    purchasedCourses = fetchedCourses.filter { it.fee > 0.0 && approvedSheetIds.contains(it.sheetId) }
                    proCourses = fetchedCourses.filter { it.fee > 0.0 && !approvedSheetIds.contains(it.sheetId) }
                    
                    val topicsList = mutableListOf("ALL", "FREE", "PURCHASED", "PRO")
                    topicsList.addAll(fetchedCourses.map { it.topic }.distinct())
                    dynamicTopics = topicsList
                    
                    isLoading = false
                }
            } else {
                allCourses = fetchedCourses
                freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                proCourses = fetchedCourses.filter { it.fee > 0.0 }
                
                val topicsList = mutableListOf("ALL", "FREE", "PRO")
                topicsList.addAll(fetchedCourses.map { it.topic }.distinct())
                dynamicTopics = topicsList
                
                isLoading = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
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

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // DYNAMIC FREE COURSES
                    if (freeCourses.isNotEmpty()) {
                        DrawerSectionHeader("FREE COURSES", Icons.Default.Star, Color(0xFF4CAF50))
                        freeCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    // DYNAMIC PURCHASED COURSES
                    if (purchasedCourses.isNotEmpty()) {
                        DrawerSectionHeader("PURCHASED COURSES", Icons.Default.MenuBook, Color(0xFF9C27B0))
                        purchasedCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    // DYNAMIC PRO COURSES
                    if (proCourses.isNotEmpty()) {
                        DrawerSectionHeader("PRO COURSES", Icons.Default.Star, Color(0xFFFF9800))
                        proCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Divider()
                    
                    // Profile & Logout Info
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
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Purchased") },
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
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ThemeBlue)
                } else {
                    when (selectedTab) {
                        BottomTab.HOME -> {
                            DashboardHomeContent(allCourses, freeCourses, purchasedCourses, proCourses, dynamicTopics, onNavigateToCourse)
                        }
                        BottomTab.FREE_COURSES -> {
                            CourseGridScreen("Free Courses", freeCourses, onNavigateToCourse)
                        }
                        BottomTab.PRO_COURSES -> {
                            CourseGridScreen("Pro Courses", proCourses, onNavigateToCourse)
                        }
                        BottomTab.PURCHASED_COURSES -> {
                            CourseGridScreen("Purchased Courses", purchasedCourses, onNavigateToCourse)
                        }
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
        color = Color.DarkGray,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 40.dp, vertical = 10.dp)
    )
}

// ---------------------------------------------------------------------------
// HOME TAB CONTENT & FILTER LOGIC
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHomeContent(
    allCourses: List<CourseModel>,
    freeCourses: List<CourseModel>,
    purchasedCourses: List<CourseModel>,
    proCourses: List<CourseModel>,
    dynamicTopics: List<String>, 
    onNavigateToCourse: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeTopic by remember { mutableStateOf("ALL") }

    // Filtering logic mirroring your web app
    val displayedCourses = remember(searchQuery, activeTopic, allCourses) {
        var filtered = when (activeTopic) {
            "ALL" -> allCourses
            "FREE" -> freeCourses
            "PURCHASED" -> purchasedCourses
            "PRO" -> proCourses
            else -> allCourses.filter { it.topic == activeTopic }
        }
        
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        filtered
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Image Slider Placeholder
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp).background(ThemeDarkBlue),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, contentDescription = "Slider", tint = Color.White, modifier = Modifier.size(48.dp))
                Text("Promotional Image Slider", color = Color.White)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search for a course...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeBlue,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("FILTER BY TOPICS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            
            // Dynamic Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            ) {
                items(dynamicTopics) { topic ->
                    val isSelected = activeTopic == topic
                    val count = when (topic) {
                        "ALL" -> allCourses.size
                        "FREE" -> freeCourses.size
                        "PURCHASED" -> purchasedCourses.size
                        "PRO" -> proCourses.size
                        else -> allCourses.count { it.topic == topic }
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) Color(0xFFE3F2FD) else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) ThemeBlue else Color.LightGray),
                        modifier = Modifier.clickable { activeTopic = topic }
                    ) {
                        Text(
                            text = "$topic ($count)",
                            color = if (isSelected) ThemeBlue else Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Text("COURSES (${displayedCourses.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            
            Spacer(modifier = Modifier.height(8.dp))

            // Courses Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedCourses) { course ->
                    CourseCardView(course) { onNavigateToCourse(course.sheetId) }
                }
            }
        }
    }
}

@Composable
fun CourseGridScreen(title: String, courses: List<CourseModel>, onNavigateToCourse: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$title (${courses.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(courses) { course ->
                CourseCardView(course) { onNavigateToCourse(course.sheetId) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// INDIVIDUAL COURSE CARD DESIGN (Matches Provided Images)
// ---------------------------------------------------------------------------
@Composable
fun CourseCardView(course: CourseModel, onClick: () -> Unit) {
    val isFree = course.fee == 0.0
    val originalPrice = if (!isFree) course.fee * 1.5 else 0.0 
    
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.clickable { onClick() }.fillMaxWidth()
    ) {
        Column {
            // Dark Header Graphic
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp).background(ThemeDarkBlue),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GoldColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JCV MOCK TESTS", color = GoldColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.background(RedBadgeColor, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(course.title.take(15).uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("FULL COURSE", color = Color(0xFF4FC3F7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Card Content Details
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = course.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = "Likes", tint = RedBadgeColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("100+", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.LightGray, thickness = 0.5.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("₹", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = if (isFree) "Free" else course.fee.toInt().toString(), 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (!isFree) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${originalPrice.toInt()}", fontSize = 10.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough)
                            Text("30% OFF", fontSize = 10.sp, color = RedBadgeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
