package com.jcv.mocktests.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage
import kotlinx.coroutines.launch

enum class BottomTab { HOME, PRO_COURSES, PURCHASED_COURSES, STUDY_MATERIAL }

private val PastelColors = listOf(
    Color(0xFFFFF3E0), Color(0xFFFCE4EC), Color(0xFFF0F4C3), 
    Color(0xFFD7CCC8), Color(0xFFF3E5F5), Color(0xFFE8F5E9), Color(0xFFE0F7FA)  
)

data class CourseModel(
    val sheetId: String, val title: String, val fee: Double, val topic: String, val durationMonths: Int = 1 
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    initialTab: String,
    isDarkMode: Boolean,           
    onNavigateToCourse: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val primaryGradient = Brush.horizontalGradient(listOf(themePrimaryColor.copy(alpha = 0.75f), themePrimaryColor))

    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "pro_courses" -> BottomTab.PRO_COURSES
                "purchased_courses" -> BottomTab.PURCHASED_COURSES
                "study_material" -> BottomTab.STUDY_MATERIAL
                else -> BottomTab.HOME
            }
        )
    }
    
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("JcvAppCache", Context.MODE_PRIVATE) }
    val localStorage = remember { LocalStorage(context) }
    val localDeviceId = remember { localStorage.getOrCreateDeviceId() }
    
    val auth = FirebaseAuth.getInstance()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var allCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var proCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var purchasedCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var approvedSheetIds by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    var streakCount by remember { mutableIntStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else if (selectedTab != BottomTab.HOME) selectedTab = BottomTab.HOME
        else showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Exit App", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to exit JCV Mock Tests?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(onClick = { showExitDialog = false; activity?.finish() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), shape = RoundedCornerShape(50)) { Text("Yes, Exit", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant), shape = RoundedCornerShape(50)) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val dbDeviceId = snap.getString("deviceId") ?: ""
                    if (dbDeviceId.isNotEmpty() && dbDeviceId != localDeviceId) {
                        auth.signOut()
                        Toast.makeText(context, "Logged out: Your account was accessed from another device.", Toast.LENGTH_LONG).show()
                        onNavigateToLogin()
                    }
                    streakCount = (snap.getLong("streakCount") ?: 0).toInt()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("exams").document("testList").get().addOnSuccessListener { examDoc ->
            val fetchedCourses = (examDoc.get("tests") as? List<Map<String, Any>> ?: emptyList()).map { map ->
                CourseModel(map["sheetId"] as? String ?: "", map["title"] as? String ?: "JCV Course", (map["fee"]?.toString() ?: "0").toDoubleOrNull() ?: 0.0, "OTHERS", (map["durationMonths"] as? Number)?.toInt() ?: 1)
            }
            
            val uid = auth.currentUser?.uid
            if (uid != null) {
                db.collection("pending_registrations").whereEqualTo("uid", uid).whereEqualTo("status", "approved").get().addOnSuccessListener { paySnap ->
                    approvedSheetIds = paySnap.documents.mapNotNull { it.getString("sheetId") }
                    allCourses = fetchedCourses
                    purchasedCourses = fetchedCourses.filter { it.fee > 0.0 && approvedSheetIds.contains(it.sheetId) }
                    proCourses = fetchedCourses.filter { it.fee > 0.0 && !approvedSheetIds.contains(it.sheetId) }
                    isLoading = false
                }
            } else {
                allCourses = fetchedCourses; proCourses = fetchedCourses.filter { it.fee > 0.0 }; isLoading = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().background(primaryGradient).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("JCV HUB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        IconButton(onClick = { scope.launch { drawerState.close() } }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = themePrimaryColor) },
                        label = { Text("Home Dashboard", fontWeight = FontWeight.Bold) },
                        selected = selectedTab == BottomTab.HOME,
                        onClick = { selectedTab = BottomTab.HOME; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = "Study Material", tint = themePrimaryColor) },
                        label = { Text("Study Material", fontWeight = FontWeight.Bold) },
                        selected = selectedTab == BottomTab.STUDY_MATERIAL,
                        onClick = { selectedTab = BottomTab.STUDY_MATERIAL; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    if (purchasedCourses.isNotEmpty()) {
                        DrawerSectionHeader("PURCHASED COURSES", Icons.Default.List, Color(0xFF9C27B0))
                        purchasedCourses.forEach { course -> DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) } }
                    }

                    if (proCourses.isNotEmpty()) {
                        DrawerSectionHeader("PRO COURSES", Icons.Default.Star, Color(0xFFFF9800))
                        proCourses.forEach { course -> DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) } }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = "User", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(auth.currentUser?.displayName ?: "User Name", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(auth.currentUser?.email ?: "user@jcv.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { auth.signOut(); onNavigateToLogin() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 40.dp))
                        .background(primaryGradient)
                ) {
                    TopAppBar(
                        title = { Text("JCV MOCK TESTS", color = Color.White, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White) }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("🔥", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$streakCount", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            // REMOVED THE THEME TOGGLE ICON BUTTON FROM HERE
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface, 
                    contentColor = MaterialTheme.colorScheme.onSurface, 
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") }, label = { Text("Home", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.HOME, onClick = { selectedTab = BottomTab.HOME }, colors = navColors(isDarkMode)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "Pro Courses") }, label = { Text("Pro", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.PRO_COURSES, onClick = { selectedTab = BottomTab.PRO_COURSES }, colors = navColors(isDarkMode)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Purchased") }, label = { Text("Purchased", fontSize = 10.sp, maxLines = 1) },
                        selected = selectedTab == BottomTab.PURCHASED_COURSES, onClick = { if (auth.currentUser == null) onNavigateToLogin() else selectedTab = BottomTab.PURCHASED_COURSES }, colors = navColors(isDarkMode)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = "Study") }, label = { Text("Study", fontSize = 10.sp) },
                        selected = selectedTab == BottomTab.STUDY_MATERIAL, onClick = { selectedTab = BottomTab.STUDY_MATERIAL }, colors = navColors(isDarkMode)
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(MaterialTheme.colorScheme.background)) { 
                when (selectedTab) {
                    BottomTab.HOME -> DashboardHomeContent(allCourses, purchasedCourses, proCourses, approvedSheetIds, auth, isLoading, isDarkMode) { onNavigateToCourse(it.sheetId) }
                    BottomTab.PRO_COURSES -> if (isLoading && proCourses.isEmpty()) LoadingLogo() else CourseGridScreen("Pro Courses", proCourses, approvedSheetIds) { onNavigateToCourse(it.sheetId) }
                    BottomTab.PURCHASED_COURSES -> if (isLoading && purchasedCourses.isEmpty()) LoadingLogo() else CourseGridScreen("Purchased Courses", purchasedCourses, approvedSheetIds) { onNavigateToCourse(it.sheetId) }
                    BottomTab.STUDY_MATERIAL -> LocalStudyMaterialScreen() 
                }
            }
        }
    }
}

@Composable
fun LocalStudyMaterialScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true 
                }
                loadUrl("file:///android_asset/study_material.html")
            }
        }
    )
}

@Composable
fun LoadingLogo() {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(themePrimaryColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text("🎓", fontSize = 40.sp) }
    }
}

@Composable
fun navColors(isDarkMode: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, 
    indicatorColor = if(isDarkMode) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), 
    unselectedIconColor = Color.Gray, 
    unselectedTextColor = Color.Gray
)

@Composable
fun DrawerSectionHeader(title: String, icon: ImageVector, iconTint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DrawerCourseItem(title: String, onClick: () -> Unit) {
    Text(text = title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 40.dp, vertical = 10.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardHomeContent(
    allCourses: List<CourseModel>, purchasedCourses: List<CourseModel>, proCourses: List<CourseModel>,
    approvedSheetIds: List<String>, auth: FirebaseAuth, isLoading: Boolean, isDarkMode: Boolean, onCourseClick: (CourseModel) -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val homeCategories = listOf("Pro Courses", "Purchased", "AP TET", "APPSC", "IIT JEE MAINS", "AP EAPCET")

    if (selectedCategory == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UserProfileHeader(auth)
            Text(text = "Welcome to JCV MOCK Tests and Thanks for Choosing JCV MOCK TESTS", color = themePrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).basicMarquee())
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(homeCategories) { index, categoryName -> 
                    val catBg = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else PastelColors[index % PastelColors.size]
                    PastelCategoryCard(categoryName, catBg) { selectedCategory = categoryName } 
                }
            }
        }
    } else {
        val displayedCourses = remember(selectedCategory, allCourses) {
            when (selectedCategory) {
                "Pro Courses" -> proCourses; "Purchased" -> purchasedCourses
                "AP TET" -> allCourses.filter { it.title.contains("TET", ignoreCase = true) }
                "APPSC" -> allCourses.filter { it.title.contains("APPSC", ignoreCase = true) || it.title.contains("GROUP", ignoreCase = true) }
                "IIT JEE MAINS" -> allCourses.filter { it.title.contains("IIT", ignoreCase = true) || it.title.contains("JEE", ignoreCase = true) }
                "AP EAPCET" -> allCourses.filter { it.title.contains("EAPCET", ignoreCase = true) }
                else -> emptyList()
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                IconButton(onClick = { selectedCategory = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = themePrimaryColor) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = selectedCategory ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            if (isLoading && allCourses.isEmpty()) LoadingLogo() 
            else if (displayedCourses.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No courses found in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    itemsIndexed(displayedCourses) { _, course -> CourseCardView(course, approvedSheetIds.contains(course.sheetId)) { onCourseClick(course) } }
                }
            }
        }
    }
}

@Composable
fun UserProfileHeader(auth: FirebaseAuth) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val userName = auth.currentUser?.displayName?.uppercase() ?: "STUDENT NAME"
    val mobileNumber = auth.currentUser?.phoneNumber ?: "Mobile Number Not Set"
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
        shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = R.drawable.logo), contentDescription = "App Logo", modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(userName, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) 
                Text(mobileNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun PastelCategoryCard(title: String, backgroundColor: Color, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.height(110.dp).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.8f), CircleShape).align(Alignment.TopStart), contentAlignment = Alignment.Center) { Text("🎓", fontSize = 18.sp) }
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.align(Alignment.BottomEnd)) 
        }
    }
}

@Composable
fun CourseGridScreen(title: String, courses: List<CourseModel>, approvedSheetIds: List<String>, onCourseClick: (CourseModel) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(text = "$title (${courses.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            itemsIndexed(courses) { _, course -> CourseCardView(course, approvedSheetIds.contains(course.sheetId)) { onCourseClick(course) } }
        }
    }
}

@Composable
fun CourseCardView(course: CourseModel, isUnlocked: Boolean, onClick: () -> Unit) {
    val bannerGradient = Brush.verticalGradient(listOf(Color(0xFF3B0000), Color(0xFF050000)))
    val ribbonGradient = Brush.horizontalGradient(listOf(Color(0xFF8B0000), Color(0xFFD32F2F), Color(0xFF8B0000)))
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(130.dp).height(95.dp).background(bannerGradient, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(6.dp)) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(18.dp).clip(CircleShape).background(Color.White))
                        Box(modifier = Modifier.background(ribbonGradient, RoundedCornerShape(4.dp)).border(0.5.dp, Color(0xFFFFD700), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = if (course.fee > 0) "PRO" else "FREE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(text = course.title.uppercase(), color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                    Box(modifier = Modifier.background(Color(0xFF1E3A8A), RoundedCornerShape(4.dp)).border(0.5.dp, Color(0xFF60A5FA), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = "${course.durationMonths} MONTHS", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { CourseTag("TESTS"); CourseTag("VIDEOS") }
                    Icon(if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock, contentDescription = null, tint = if (isUnlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
                Text(text = course.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp), lineHeight = 18.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "₹${course.fee.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "₹${(course.fee * 5).toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(bottom = 2.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "80% OFF", fontSize = 11.sp, color = Color(0xFFE07A5F), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
fun CourseTag(text: String) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
