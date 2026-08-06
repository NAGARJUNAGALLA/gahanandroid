package com.jcv.mocktests.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.net.URLEncoder

enum class BottomTab { HOME, FREE_COURSES, PRO_COURSES, PURCHASED_COURSES }

val ThemeBlue = Color(0xFF1976D2)
val LightGreyBg = Color(0xFFF5F6FA)
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

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("JcvAppCache", Context.MODE_PRIVATE) }
    
    val auth = FirebaseAuth.getInstance()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var allCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var freeCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var proCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    var purchasedCourses by remember { mutableStateOf<List<CourseModel>>(emptyList()) }
    
    // Payment Tracking States
    var pendingSheetIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var rejectedSheetIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPaymentDialog by remember { mutableStateOf<CourseModel?>(null) }
    var isSubmittingPayment by remember { mutableStateOf(false) }

    // Merchant Settings
    var upiId by remember { mutableStateOf("") }
    var merchantName by remember { mutableStateOf("JCV MOCK TESTS") }
    var staticQrUrl by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        // 1. Fetch Merchant Settings for QR Code
        db.collection("settings").get().addOnSuccessListener { settingsSnap ->
            if (!settingsSnap.isEmpty) {
                val sData = settingsSnap.documents[0]
                upiId = sData.getString("upiId") ?: sData.getString("upi_id") ?: ""
                merchantName = sData.getString("merchantName") ?: sData.getString("merchant_name") ?: "JCV MOCK TESTS"
                staticQrUrl = sData.getString("qrCodeLink") ?: sData.getString("qr_code_link") ?: sData.getString("qrcode") ?: ""
            }
        }

        // 2. Load Local Cache
        val cachedCoursesStr = prefs.getString("cached_courses", "") ?: ""
        val cachedPurchasedStr = prefs.getString("cached_purchased", "") ?: ""
        val cachedPendingStr = prefs.getString("cached_pending", "") ?: ""
        val cachedRejectedStr = prefs.getString("cached_rejected", "") ?: ""
        
        if (cachedCoursesStr.isNotEmpty()) {
            val parsedCourses = cachedCoursesStr.split("|||").mapNotNull {
                val parts = it.split("|")
                if (parts.size >= 4) {
                    CourseModel(parts[0], parts[1], parts[2].toDoubleOrNull() ?: 0.0, parts[3])
                } else null
            }
            val approvedSheetIds = cachedPurchasedStr.split(",").filter { it.isNotBlank() }
            pendingSheetIds = cachedPendingStr.split(",").filter { it.isNotBlank() }
            rejectedSheetIds = cachedRejectedStr.split(",").filter { it.isNotBlank() }
            
            allCourses = parsedCourses
            freeCourses = parsedCourses.filter { it.fee == 0.0 }
            purchasedCourses = parsedCourses.filter { it.fee > 0.0 && approvedSheetIds.contains(it.sheetId) }
            proCourses = parsedCourses.filter { it.fee > 0.0 && !approvedSheetIds.contains(it.sheetId) }
            
            isLoading = false
        }

        // 3. Background Fetch Courses
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
                    pendingSheetIds = payments.filter { it.status == "pending" }.map { it.sheetId }
                    rejectedSheetIds = payments.filter { it.status == "rejected" }.map { it.sheetId }
                    
                    allCourses = fetchedCourses
                    freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                    purchasedCourses = fetchedCourses.filter { it.fee > 0.0 && approvedSheetIds.contains(it.sheetId) }
                    proCourses = fetchedCourses.filter { it.fee > 0.0 && !approvedSheetIds.contains(it.sheetId) }
                    isLoading = false
                    
                    val coursesString = fetchedCourses.joinToString("|||") { "${it.sheetId}|${it.title}|${it.fee}|${it.topic}" }
                    prefs.edit()
                        .putString("cached_courses", coursesString)
                        .putString("cached_purchased", approvedSheetIds.joinToString(","))
                        .putString("cached_pending", pendingSheetIds.joinToString(","))
                        .putString("cached_rejected", rejectedSheetIds.joinToString(","))
                        .apply()
                }
            } else {
                allCourses = fetchedCourses
                freeCourses = fetchedCourses.filter { it.fee == 0.0 }
                proCourses = fetchedCourses.filter { it.fee > 0.0 }
                isLoading = false
                
                val coursesString = fetchedCourses.joinToString("|||") { "${it.sheetId}|${it.title}|${it.fee}|${it.topic}" }
                prefs.edit()
                    .putString("cached_courses", coursesString)
                    .putString("cached_purchased", "")
                    .apply()
            }
        }
    }

    // INTERCEPT CLICKS AND MANAGE PAYMENTS
    val handleCourseClick: (CourseModel) -> Unit = { course ->
        if (course.fee == 0.0 || purchasedCourses.any { it.sheetId == course.sheetId }) {
            onNavigateToCourse(course.sheetId)
        } else if (pendingSheetIds.contains(course.sheetId)) {
            Toast.makeText(context, "Your payment is currently under review by the admin. Please check back later.", Toast.LENGTH_LONG).show()
        } else {
            showPaymentDialog = course
        }
    }

    // PAYMENT MODAL OVERLAY
    // ---------------------------------------------------------------------------
// PAYMENT MODAL DIALOG (DIRECT APP INTENT)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    course: CourseModel,
    isRejected: Boolean,
    isSubmitting: Boolean,
    upiId: String,
    merchantName: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val context = LocalContext.current
    var utr by remember { mutableStateOf("") }
    
    // State to track if the user has clicked "Pay Now" yet
    var hasClickedPay by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unlock Course", fontWeight = FontWeight.Bold, color = ThemeBlue, fontSize = 18.sp)
                IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("₹${course.fee.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                
                if (isRejected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), 
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Your previous registration was rejected. Please pay the fee or check your UTR number and resend.",
                            color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // DIRECT "PAY NOW" BUTTON
                Button(
                    onClick = {
                        if (upiId.isNotBlank()) {
                            // 1. Generate the UPI intent URL
                            val uriString = "upi://pay?pa=$upiId&pn=${java.net.URLEncoder.encode(merchantName, "UTF-8")}&am=${course.fee}&cu=INR"
                            val uri = android.net.Uri.parse(uriString)
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            
                            // 2. Ask Android to show installed UPI apps (GPay, PhonePe, etc.)
                            val chooser = Intent.createChooser(intent, "Pay securely with")
                            
                            try {
                                context.startActivity(chooser)
                                // 3. Reveal the UTR input box
                                hasClickedPay = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI app found on your phone. Please install GPay, PhonePe, or Paytm.", Toast.LENGTH_LONG).show()
                                hasClickedPay = true // Fallback to let them enter it manually if needed
                            }
                        } else {
                            Toast.makeText(context, "UPI ID is not configured. Please contact the Admin.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Green color for the Pay button
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PAY NOW", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Click to open GPay, PhonePe, Paytm, or any UPI App",
                    fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center
                )

                // ONLY SHOW THIS SECTION AFTER THEY CLICK "PAY NOW"
                if (hasClickedPay) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = Color.LightGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "After successful payment, enter the 12-digit UTR/Reference number below to verify.",
                        fontSize = 12.sp, color = ThemeBlue, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = utr,
                        onValueChange = { utr = it },
                        label = { Text("Transaction / UTR Number") },
                        placeholder = { Text("e.g. 123456789012") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemeBlue,
                            focusedLabelColor = ThemeBlue
                        )
                    )
                }
            }
        },
        confirmButton = {
            // ONLY SHOW THE SUBMIT BUTTON AFTER THEY CLICK "PAY NOW"
            if (hasClickedPay) {
                Button(
                    onClick = { onSubmit("Direct UPI", utr) }, // App type is hardcoded since they use the direct intent
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = utr.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit Details", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        },
        dismissButton = null
    )
}

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(ThemeBlue).padding(16.dp),
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
                            DrawerCourseItem(course.title) { handleCourseClick(course) }
                        }
                    }

                    if (purchasedCourses.isNotEmpty()) {
                        DrawerSectionHeader("PURCHASED COURSES", Icons.Default.List, Color(0xFF9C27B0))
                        purchasedCourses.forEach { course ->
                            DrawerCourseItem(course.title) { handleCourseClick(course) }
                        }
                    }

                    if (proCourses.isNotEmpty()) {
                        DrawerSectionHeader("PRO COURSES", Icons.Default.Star, Color(0xFFFF9800))
                        proCourses.forEach { course ->
                            DrawerCourseItem(course.title) { handleCourseClick(course) }
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
                when (selectedTab) {
                    BottomTab.HOME -> {
                        DashboardHomeContent(
                            allCourses = allCourses,
                            freeCourses = freeCourses,
                            purchasedCourses = purchasedCourses,
                            proCourses = proCourses,
                            auth = auth,
                            isLoading = isLoading,
                            onCourseClick = handleCourseClick
                        )
                    }
                    BottomTab.FREE_COURSES -> {
                        if (isLoading && freeCourses.isEmpty()) LoadingLogo() 
                        else CourseGridScreen("Free Courses", freeCourses, handleCourseClick)
                    }
                    BottomTab.PRO_COURSES -> {
                        if (isLoading && proCourses.isEmpty()) LoadingLogo() 
                        else CourseGridScreen("Pro Courses", proCourses, handleCourseClick)
                    }
                    BottomTab.PURCHASED_COURSES -> {
                        if (isLoading && purchasedCourses.isEmpty()) LoadingLogo() 
                        else CourseGridScreen("Purchased Courses", purchasedCourses, handleCourseClick)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PAYMENT MODAL DIALOG (WITH LIVE QR)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    course: CourseModel,
    isRejected: Boolean,
    isSubmitting: Boolean,
    upiId: String,
    merchantName: String,
    staticQrUrl: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var selectedApp by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var utr by remember { mutableStateOf("") }
    val apps = listOf("PhonePe", "Google Pay (GPay)", "Paytm", "Other")

    // Generate Dynamic QR URL
    val qrUrl = remember(upiId, staticQrUrl, course.fee) {
        if (upiId.isNotBlank()) {
            val upiString = "upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${course.fee}&cu=INR"
            "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${URLEncoder.encode(upiString, "UTF-8")}"
        } else {
            staticQrUrl
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unlock Course", fontWeight = FontWeight.Bold, color = ThemeBlue, fontSize = 18.sp)
                IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("₹${course.fee.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                
                if (isRejected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), 
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Your previous registration was rejected. Please pay the fee or check your UTR number and resend.",
                            color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Text(
                    "Scan the QR code below to pay. If already paid, submit your UTR.",
                    fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp)
                )
                
                // ACTUAL QR CODE
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color(0xFFF5F6FA), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(qrUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "UPI QR Code",
                            modifier = Modifier.padding(8.dp).fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ThemeBlue, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading QR...", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedApp,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid using app") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemeBlue,
                            focusedLabelColor = ThemeBlue
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        apps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app) },
                                onClick = {
                                    selectedApp = app
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = utr,
                    onValueChange = { utr = it },
                    label = { Text("Transaction / UTR Number") },
                    placeholder = { Text("e.g. 123456789012") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeBlue,
                        focusedLabelColor = ThemeBlue
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedApp, utr) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = selectedApp.isNotBlank() && utr.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = ThemeBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Submit Details", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun LoadingLogo() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ThemeBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎓", fontSize = 40.sp)
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
    isLoading: Boolean,
    onCourseClick: (CourseModel) -> Unit
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

            if (isLoading && allCourses.isEmpty()) {
                LoadingLogo()
            } else if (displayedCourses.isEmpty()) {
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
                    itemsIndexed(displayedCourses) { index, course ->
                        CourseCardView(
                            course = course, 
                            backgroundColor = PastelColors[index % PastelColors.size]
                        ) { onCourseClick(course) }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
fun CourseGridScreen(title: String, courses: List<CourseModel>, onCourseClick: (CourseModel) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$title (${courses.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(courses) { index, course ->
                CourseCardView(
                    course = course,
                    backgroundColor = PastelColors[index % PastelColors.size]
                ) { onCourseClick(course) }
            }
        }
    }
}

@Composable
fun CourseCardView(course: CourseModel, backgroundColor: Color, onClick: () -> Unit) {
    val isFree = course.fee == 0.0
    val originalPrice = if (!isFree) course.fee * 1.5 else 0.0 
    
    val context = LocalContext.current
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.White.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThemeBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JCV MOCK TESTS", color = ThemeBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.background(RedBadgeColor, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(course.title.take(15).uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("FULL COURSE", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = course.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
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
                        Text("100+", fontSize = 10.sp, color = Color.DarkGray)
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
                
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.5f), thickness = 1.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("₹", fontSize = 10.sp, color = Color.DarkGray)
                        Text(
                            text = if (isFree) "Free" else course.fee.toInt().toString(), 
                            fontSize = 14.sp, 
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (!isFree) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${originalPrice.toInt()}", fontSize = 10.sp, color = Color.DarkGray, textDecoration = TextDecoration.LineThrough)
                            Text("30% OFF", fontSize = 10.sp, color = RedBadgeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
