package com.jcv.mocktests.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.utils.LocalStorage
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TestSummary(val name: String, val questionCount: Int, val timeMinutes: Int)
data class BookmarkedQuestion(val testName: String, val sectionName: String, val questionText: String, val options: List<String>, val correctOptionIndex: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onNavigateToExam: (courseId: String, testName: String, isReviewMode: Boolean) -> Unit,
    onNavigateToStudyMaterial: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val primaryGradient = Brush.horizontalGradient(listOf(themePrimaryColor.copy(alpha = 0.75f), themePrimaryColor))

    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val examPrefs = remember { context.getSharedPreferences("JcvExamPrefs", Context.MODE_PRIVATE) } 
    val auth = remember { FirebaseAuth.getInstance() }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("OVERVIEW", "CONTENT", "SAVED") 
    
    var tests by remember { mutableStateOf<List<TestSummary>>(emptyList()) }
    var bookmarkedQuestions by remember { mutableStateOf<List<BookmarkedQuestion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var upiId by remember { mutableStateOf("") }
    var merchantName by remember { mutableStateOf("JCV MOCK TESTS") }
    var staticQrUrl by remember { mutableStateOf("") }
    var courseFee by remember { mutableDoubleStateOf(0.0) }
    var courseTitle by remember { mutableStateOf("Course") }
    var courseDuration by remember { mutableIntStateOf(1) }
    
    // NEW: Variable to hold the specific Study Material URL from Firebase
    var studyMaterialUrl by remember { mutableStateOf("") }
    var showStudyMaterialWebView by remember { mutableStateOf(false) }
    
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var subscriptionExpiry by remember { mutableStateOf<Date?>(null) } 
    var showPaymentDialog by remember { mutableStateOf(false) }
    var isSubmittingPayment by remember { mutableStateOf(false) }

    LaunchedEffect(courseId) {
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        db.collection("settings").get().addOnSuccessListener { snaps ->
            if (!snaps.isEmpty) {
                val sData = snaps.documents[0]
                upiId = sData.getString("upiId") ?: sData.getString("upi_id") ?: ""
                merchantName = sData.getString("merchantName") ?: sData.getString("merchant_name") ?: "JCV MOCK TESTS"
                staticQrUrl = sData.getString("qrCodeLink") ?: sData.getString("qr_code_link") ?: sData.getString("qrcode") ?: ""
            }
        }

        db.collection("exams").document("testList").get().addOnSuccessListener { doc ->
            val testsArray = doc.get("tests") as? List<Map<String, Any>> ?: emptyList()
            val matchedCourse = testsArray.find { it["sheetId"] == courseId }
            if (matchedCourse != null) {
                courseTitle = matchedCourse["title"] as? String ?: "Course"
                courseFee = (matchedCourse["fee"]?.toString()?.toDoubleOrNull()) ?: 0.0
                courseDuration = (matchedCourse["durationMonths"] as? Number)?.toInt() ?: 1
                // NEW: Fetch the URL if it exists
                studyMaterialUrl = matchedCourse["studyMaterialUrl"] as? String ?: ""
            }
        }

        if (uid != null) {
            db.collection("pending_registrations").whereEqualTo("uid", uid).whereEqualTo("sheetId", courseId).addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val latestDoc = snap.documents.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
                    paymentStatus = latestDoc?.getString("status")
                    if (paymentStatus == "approved") {
                        val createdAt = latestDoc?.getTimestamp("createdAt")?.toDate()
                        val savedDuration = (latestDoc?.get("durationMonths") as? Number)?.toInt() ?: courseDuration
                        if (createdAt != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = createdAt
                            calendar.add(Calendar.MONTH, savedDuration)
                            subscriptionExpiry = calendar.time
                            if (Date().after(subscriptionExpiry)) paymentStatus = "expired"
                        }
                    }
                } else {
                    paymentStatus = null; subscriptionExpiry = null
                }
            }
        }

        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val testsMap = doc.data?.get("tests") as? Map<String, Any>
                    val parsedTests = mutableListOf<TestSummary>()
                    val parsedBookmarks = mutableListOf<BookmarkedQuestion>()
                    
                    testsMap?.forEach { (testName, testData) ->
                        var qCount = 0
                        val specificTest = testData as? Map<String, List<Map<String, Any>>>
                        try { specificTest?.forEach { (_, qList) -> qCount += qList.size } } catch (e: Exception) { }
                        parsedTests.add(TestSummary(name = testName, questionCount = qCount, timeMinutes = qCount))

                        val prefKey = "exam_state_${courseId}_${testName}"
                        val savedBookmarksStr = examPrefs.getString("${prefKey}_bookmarks", "")
                        
                        if (!savedBookmarksStr.isNullOrBlank()) {
                            val bookmarkPairs = savedBookmarksStr.split(";").mapNotNull { 
                                val parts = it.split(",")
                                if(parts.size == 2) parts[0].toInt() to parts[1].toInt() else null
                            }
                            if (bookmarkPairs.isNotEmpty() && specificTest != null) {
                                val sectionEntries = specificTest.entries.toList()
                                bookmarkPairs.forEach { (sIdx, qIdx) ->
                                    if (sIdx < sectionEntries.size) {
                                        val secName = sectionEntries[sIdx].key
                                        val qList = sectionEntries[sIdx].value
                                        if (qIdx < qList.size) {
                                            val qMap = qList[qIdx]
                                            parsedBookmarks.add(BookmarkedQuestion(testName, secName, qMap["text"] as? String ?: "", qMap["options"] as? List<String> ?: emptyList(), (qMap["correct"] as? Number)?.toInt() ?: 0))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    tests = parsedTests
                    bookmarkedQuestions = parsedBookmarks 
                }
                isLoading = false
            }.addOnFailureListener { isLoading = false }
    }

    if (showPaymentDialog) {
        PaymentDialog(courseTitle, courseFee, courseDuration, paymentStatus == "rejected", isSubmittingPayment, upiId, merchantName, staticQrUrl, onDismiss = { showPaymentDialog = false }) { app, utr ->
            isSubmittingPayment = true
            val paymentData = hashMapOf(
                "uid" to auth.currentUser?.uid, "email" to auth.currentUser?.email, "sheetId" to courseId, "courseTitle" to courseTitle,
                "fee" to courseFee, "durationMonths" to courseDuration, "utr" to utr, "app" to app, "status" to "pending", "createdAt" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("pending_registrations").document("${auth.currentUser?.uid}_${courseTitle}").set(paymentData)
                .addOnSuccessListener { isSubmittingPayment = false; showPaymentDialog = false; Toast.makeText(context, "Registration submitted successfully!", Toast.LENGTH_LONG).show() }
                .addOnFailureListener { e -> isSubmittingPayment = false; Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // =======================================================
    // IN-SCREEN WEBVIEW (OVERLAYS THE COURSE WHEN OPENED)
    // =======================================================
    if (showStudyMaterialWebView) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var canGoBack by remember { mutableStateOf(false) }

        BackHandler {
            if (canGoBack) webViewRef?.goBack() else showStudyMaterialWebView = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(courseTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { 
                        IconButton(onClick = { if (canGoBack) webViewRef?.goBack() else showStudyMaterialWebView = false }) { 
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) 
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = themePrimaryColor)
                )
            }
        ) { padding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() == true
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        loadUrl(studyMaterialUrl)
                        webViewRef = this
                    }
                }
            )
        }
        return // Early return so the rest of the course screen doesn't draw underneath
    }

    // =======================================================
    // MAIN COURSE DETAILS UI
    // =======================================================
    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomEnd = 40.dp))
                    .background(primaryGradient)
            ) {
                TopAppBar(
                    title = { Text("Course Details", color = Color.White) },
                    navigationIcon = { 
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab, contentColor = themePrimaryColor, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index, onClick = { selectedTab = index },
                        text = { Text(text = title, color = if (selectedTab == index) themePrimaryColor else Color.Gray, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }
                    )
                }
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About this Course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Comprehensive mock tests and materials designed to help you prepare and excel.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val totalQs = tests.sumOf { it.questionCount }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Card(modifier = Modifier.weight(1f).padding(end = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${tests.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themePrimaryColor)
                                Text("Total Tests", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Card(modifier = Modifier.weight(1f).padding(start = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalQs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themePrimaryColor)
                                Text("Total Questions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // NEW: STUDY MATERIAL SMART BUTTON
                    if (studyMaterialUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                if (courseFee == 0.0 || paymentStatus == "approved") {
                                    showStudyMaterialWebView = true
                                } else {
                                    Toast.makeText(context, "Please unlock the course to access Study Materials.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(55.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (courseFee == 0.0 || paymentStatus == "approved") themePrimaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (courseFee == 0.0 || paymentStatus == "approved") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(if (courseFee == 0.0 || paymentStatus == "approved") Icons.Default.MenuBook else Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Read Study Material", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (paymentStatus == "approved") {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), border = BorderStroke(1.dp, Color(0xFF4CAF50)), shape = RoundedCornerShape(24.dp)) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(text = "Course Unlocked", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (subscriptionExpiry != null) Text(text = "Valid until ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(subscriptionExpiry!!)}", color = Color(0xFF388E3C), fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, themePrimaryColor), shape = RoundedCornerShape(24.dp)) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Subscription Validity", fontSize = 12.sp, color = Color.Gray)
                                    Text("$courseDuration Months Access", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("₹${courseFee.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = themePrimaryColor)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = { selectedTab = 1 }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.outlinedButtonColors(contentColor = themePrimaryColor)) {
                            Text("View Mock Tests", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = themePrimaryColor) }
                    } else if (paymentStatus != "approved") {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = if (paymentStatus == "expired") "Subscription Expired" else "Course Locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (paymentStatus == "pending") {
                                Text("Your payment is currently under review by the admin. Please check back later.", color = Color(0xFFE67E22), textAlign = TextAlign.Center)
                            } else {
                                Text(text = if (paymentStatus == "expired") "Your access has ended. Renew your subscription for $courseDuration Months to regain access." else "Unlock this course for $courseDuration Months to access all ${tests.size} premium mock tests.", color = Color.Gray, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                if (paymentStatus == "rejected") {
                                    Text("Your previous payment was rejected. Please try again or contact the admin.", color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                Button(
                                    onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth().height(55.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(50)
                                ) {
                                    Text(text = if (courseFee > 0) { if (paymentStatus == "expired") "RENEW FOR ₹${courseFee.toInt()}" else "PAY ₹${courseFee.toInt()} FOR $courseDuration MONTHS" } else "UNLOCK COURSE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    } else {
                        if (selectedTab == 1) {
                            if (tests.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No tests found for this course.", color = Color.Gray) }
                            else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (subscriptionExpiry != null) {
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), border = BorderStroke(1.dp, Color(0xFF4CAF50)), shape = RoundedCornerShape(24.dp)) {
                                                Text(text = "Active Subscription: Valid until ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(subscriptionExpiry!!)}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp).fillMaxWidth(), fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    items(tests) { test ->
                                        val alreadyAttempted = localStorage.isTestAttempted(courseId, test.name)
                                        val testScore = localStorage.getTestScore(courseId, test.name)
                                        
                                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = RoundedCornerShape(24.dp)) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(text = test.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.List, contentDescription = "Questions", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("${test.questionCount} Questions", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.DateRange, contentDescription = "Time", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("${test.timeMinutes} Mins", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                
                                                if (alreadyAttempted && testScore != null) {
                                                    val formatScore = { value: Float -> if (value % 1.0f == 0f) value.toInt().toString() else value.toString() }
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Column {
                                                            Text("Highest Score", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                                            Text(text = "${formatScore(testScore.first)} / ${formatScore(testScore.second)}", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                        }
                                                        OutlinedButton(onClick = { onNavigateToExam(courseId, test.name, true) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = themePrimaryColor), shape = RoundedCornerShape(50)) {
                                                            Text("Review Test", fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                } else {
                                                    Button(onClick = { onNavigateToExam(courseId, test.name, false) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = themePrimaryColor)) {
                                                        Text("Take Test", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedTab == 2) {
                            if (bookmarkedQuestions.isEmpty()) {
                                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Star, contentDescription = "Star", tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Saved Doubts", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "When taking a mock test, tap the ⭐ icon next to difficult questions to save them here for quick revision!", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    item { Text(text = "Review your saved questions. The correct answers are highlighted in green.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp)) }
                                    items(bookmarkedQuestions) { bq ->
                                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(24.dp)) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Star, contentDescription = "Saved", tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("${bq.testName} • ${bq.sectionName}", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    MathText(text = bq.questionText, fontSizePx = 16)
                                                    Box(modifier = Modifier.matchParentSize().background(Color.Transparent)) 
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                                bq.options.forEachIndexed { index, opt ->
                                                    val isCorrect = index == bq.correctOptionIndex
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if (isCorrect) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).border(1.dp, if (isCorrect) Color(0xFF4CAF50) else Color.Transparent, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        if (isCorrect) { Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)) } 
                                                        else { Spacer(modifier = Modifier.width(26.dp)) }
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            MathText(text = opt, fontSizePx = 14, textColorHex = if (isCorrect) "#166534" else "#888888")
                                                            Box(modifier = Modifier.matchParentSize().background(Color.Transparent))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    courseTitle: String, courseFee: Double, courseDuration: Int, isRejected: Boolean, isSubmitting: Boolean,
    upiId: String, merchantName: String, staticQrUrl: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    var selectedApp by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var utr by remember { mutableStateOf("") }
    var hasClickedPay by remember(isRejected) { mutableStateOf(isRejected) }
    val apps = listOf("PhonePe", "Google Pay (GPay)", "Paytm", "Other")
    val context = LocalContext.current
    
    val qrUrl = remember(upiId, staticQrUrl, courseFee) {
        if (upiId.isNotBlank()) "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${URLEncoder.encode("upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${courseFee}&cu=INR", "UTF-8")}" else staticQrUrl
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() }, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Unlock Course", fontWeight = FontWeight.Bold, color = themePrimaryColor, fontSize = 18.sp)
                IconButton(onClick = onDismiss, enabled = !isSubmitting) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("₹${courseFee.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                if (isRejected) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) { Text(text = "Your previous payment was rejected.\nPlease try again.", color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (upiId.isNotBlank()) {
                            try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${courseFee}&cu=INR")), "Pay securely with"))
                                hasClickedPay = true } catch (e: Exception) { Toast.makeText(context, "No UPI app found.", Toast.LENGTH_LONG).show(); hasClickedPay = true }
                        } else Toast.makeText(context, "UPI ID is not configured.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("PAY NOW", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "OR scan the QR code below", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
                Box(modifier = Modifier.size(160.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    if (qrUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(qrUrl).crossfade(true).build(), contentDescription = "UPI QR Code", modifier = Modifier.padding(8.dp).fillMaxSize()) else CircularProgressIndicator(color = themePrimaryColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedApp, onValueChange = {}, readOnly = true, label = { Text("Paid using app") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimaryColor, focusedLabelColor = themePrimaryColor)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        apps.forEach { app -> DropdownMenuItem(text = { Text(app) }, onClick = { selectedApp = app; expanded = false; hasClickedPay = true }) }
                    }
                }
                if (hasClickedPay || selectedApp.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = utr, onValueChange = { utr = it }, label = { Text("Transaction / UTR Number") }, placeholder = { Text("e.g. 123456789012") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimaryColor, focusedLabelColor = themePrimaryColor)
                    )
                }
            }
        },
        confirmButton = {
            if (hasClickedPay || selectedApp.isNotBlank()) {
                Button(
                    onClick = { onSubmit(if (selectedApp.isNotBlank()) selectedApp else "Direct UPI", utr) }, 
                    modifier = Modifier.fillMaxWidth().height(48.dp), enabled = utr.isNotBlank() && !isSubmitting, colors = ButtonDefaults.buttonColors(containerColor = themePrimaryColor), shape = RoundedCornerShape(50)
                ) { if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) else Text("Submit Details", fontWeight = FontWeight.Bold, color = Color.White) }
            }
        },
        dismissButton = null
    )
}
