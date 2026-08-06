package com.jcv.mocktests.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.R
import kotlinx.coroutines.launch

enum class BottomTab { HOME, FREE_COURSES, PRO_COURSES, PURCHASED_COURSES }

val ThemeBlue = Color(0xFF1976D2)
val ThemeDarkBlue = Color(0xFF181E2F)
val LightGreyBg = Color(0xFFF5F6FA)
val GoldColor = Color(0xFFFFC107)
val RedBadgeColor = Color(0xFFE53935)

val PastelColors = listOf(
    Color(0xFFFFF3E0), 
    Color(0xFFFCE4EC), 
    Color(0xFFF0F4C3), 
    Color(0xFFD7CCC8), 
    Color(0xFFF3E5F5), 
    Color(0xFFE8F5E9), 
    Color(0xFFE0F7FA)  
)

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

    var allCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var freeCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var proCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var purchasedCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

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
                val topic = "OTHERS"
                
                CourseModel(sheetId, title, fee, topic)
            }

            if (uid != null) {
                db.collection("pending_registrations").whereEqualTo("uid", uid).get().addOnSuccessListener { paySnap ->
                    val payments = paySnap.documents.mapNotNull { doc ->
                        val sheetId = doc.getString("sheetId") ?: ""
                        val status = doc.getString("status") ?: ""
                        PaymentModel(sheetId, status)
                    }
                    
                    val approvedSheetIds = payments.filter { it.status == "approved" }.map { it.sheetId }
                    
                    allCourses = fetchedCourses
                    freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                    purchasedCourses = fetchedCourses.filter { it.fee > 0.0 && approvedSheetIds.contains(it.sheetId) }
                    proCourses = fetchedCourses.filter { it.fee > 0.0 && !approvedSheetIds.contains(it.sheetId) }
                    
                    isLoading = false
                }
            } else {
                allCourses = fetchedCourses
                freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                proCourses = fetchedCourses.filter { it.fee > 0.0 }
                
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
                ) {
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

                    if (freeCourses.isNotEmpty()) {
                        DrawerSectionHeader("FREE COURSES", Icons.Default.Star, Color(0xFF4CAF50))
                        freeCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    if (purchasedCourses.isNotEmpty()) {
                        DrawerSectionHeader("PURCHASED COURSES", Icons.Default.List, Color(0xFF9C27B0))
                        purchasedCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    if (proCourses.isNotEmpty()) {
                        DrawerSectionHeader("PRO COURSES", Icons.Default.Star, Color(0xFFFF9800))
                        proCourses.forEach { course ->
                            DrawerCourseItem(course.title) { onNavigateToCourse(course.sheetId) }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Divider()
                    
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
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color.White)) { 
                if (isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Loading Logo",
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = ThemeBlue)
                    }
                } else {
                    when (selectedTab) {
                        BottomTab.HOME -> {
                            DashboardHomeContent(allCourses, freeCourses, purchasedCourses, proCourses, auth, onNavigateToCourse)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardHomeContent(
    allCourses: List<CourseModel>,
    freeCourses: List<CourseModel>,
    purchasedCourses: List<CourseModel>,
    proCourses: List<CourseModel>,
    auth: FirebaseAuth,
    onNavigateToCourse: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    val homeCategories = listOf(
        "Free Courses", "Pro Courses", "Purchased", 
        "AP TET", "APPSC", "IIT JEE MAINS", "AP EAPCET"
    )

    if (selectedCategory == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            
            UserProfileHeader(auth)
            
            Text(
                text = "Welcome to JCV MOCK Tests and Thanks for Choosing JCV MOCK TESTS",
                color = ThemeBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .basicMarquee() 
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(homeCategories) { index, categoryName ->
                    PastelCategoryCard(
                        title = categoryName,
                        backgroundColor = PastelColors[index % PastelColors.size]
                    ) {
                        selectedCategory = categoryName
                    }
                }
            }
        }
    } else {
        val displayedCourses = remember(selectedCategory, allCourses) {
            when (selectedCategory) {
                "Free Courses" -> freeCourses
                "Pro Courses" -> proCourses
                "Purchased" -> purchasedCourses
                "AP TET" -> allCourses.filter { it.title.contains("TET", ignoreCase = true) }
                "APPSC" -> allCourses.filter { 
                    it.title.contains("APPSC", ignoreCase = true) || 
                    it.title.contains("GROUP", ignoreCase = true) 
                }
                "IIT JEE MAINS" -> allCourses.filter { 
                    it.title.contains("IIT", ignoreCase = true) || 
                    it.title.contains("JEE", ignoreCase = true) 
                }
                "AP EAPCET" -> allCourses.filter { it.title.contains("EAPCET", ignoreCase = true) }
                else -> emptyList()
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = { selectedCategory = null },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ThemeBlue)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedCategory ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }

            if (displayedCourses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No courses found in this category.", color = Color.Gray)
                }
            } else {
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
}

@Composable
fun UserProfileHeader(auth: FirebaseAuth) {
    val userName = auth.currentUser?.displayName?.uppercase() ?: "STUDENT NAME"
    val mobileNumber = auth.currentUser?.phoneNumber ?: "Mobile Number Not Set"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.LightGray, RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(50.dp), tint = Color.White)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(userName, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.Black)
                Text(mobileNumber, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun PastelCategoryCard(
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .height(110.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White, CircleShape)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Text("🎓", fontSize = 18.sp)
            }
            
            Text(
                text = title,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun CourseGridScreen(title: String, courses: List<CourseModel>, onNavigateToCourse: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$title (${courses.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
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

@Composable
fun CourseCardView(course: CourseModel, onClick: () -> Unit) {
    val isFree = course.fee == 0.0
    val originalPrice = if (!isFree) course.fee * 1.5 else 0.0 
    
    val context = LocalContext.current
    
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
    ) {
        Column {
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

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = course.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = "Likes", tint = RedBadgeColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("100+", fontSize = 10.sp, color = Color.Gray)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    IconButton(
                        onClick = {
                            val shareText = "Check out this amazing course: ${course.title} on the JCV Mock Tests App!"
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Course via")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Share Course", 
                            tint = ThemeBlue, 
                            modifier = Modifier.size(14.dp)
                        )
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
