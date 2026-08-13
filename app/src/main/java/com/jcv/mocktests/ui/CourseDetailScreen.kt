package com.jcv.mocktests.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ==========================================
// 1. BULLETPROOF NATIVE TTS MANAGER
// ==========================================
class TTSManager(context: Context, private val onUtteranceDone: () -> Unit) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onUtteranceDone()
                    }
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun speak(text: String, rate: Float) {
        if (isReady) {
            tts?.setSpeechRate(rate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        } else {
            // If user clicks immediately before TTS is ready, delay and retry
            Handler(Looper.getMainLooper()).postDelayed({ speak(text, rate) }, 500)
        }
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown() }
}

class WebAppInterface(private val ttsManager: TTSManager) {
    @JavascriptInterface
    fun speak(text: String, rateStr: String) {
        // Accepts String to prevent Javascript Number vs Kotlin Float crashes
        val rate = rateStr.toFloatOrNull() ?: 1.0f
        ttsManager.speak(text, rate)
    }

    @JavascriptInterface
    fun stop() {
        ttsManager.stop()
    }
}

data class TestSummary(val name: String, val questionCount: Int, val timeMinutes: Int)
data class BookmarkedQuestion(val testName: String, val sectionName: String, val questionText: String, val options: List<String>, val correctOptionIndex: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onNavigateToExam: (courseId: String, testName: String, isReviewMode: Boolean) -> Unit,
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
    var courseSubjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var upiId by remember { mutableStateOf("") }
    var merchantName by remember { mutableStateOf("JCV MOCK TESTS") }
    var staticQrUrl by remember { mutableStateOf("") }
    var courseFee by remember { mutableDoubleStateOf(0.0) }
    var courseTitle by remember { mutableStateOf("Course") }
    var courseDuration by remember { mutableIntStateOf(1) }
    
    var studyMaterialUrl by remember { mutableStateOf("") }
    var showStudyMaterialWebView by remember { mutableStateOf(false) }
    var currentContentFolder by remember { mutableStateOf<String?>(null) }
    
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var subscriptionExpiry by remember { mutableStateOf<Date?>(null) } 
    var showPaymentDialog by remember { mutableStateOf(false) }
    var isSubmittingPayment by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) currentContentFolder = null
    }

    BackHandler(enabled = (selectedTab == 1 && currentContentFolder != null && !showStudyMaterialWebView)) {
        currentContentFolder = null
    }

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
                    val subjectsSet = mutableSetOf<String>() 
                    
                    testsMap?.forEach { (testName, testData) ->
                        var qCount = 0
                        val specificTest = testData as? Map<String, List<Map<String, Any>>>
                        
                        specificTest?.keys?.forEach { subjectName ->
                            subjectsSet.add(subjectName)
                        }

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
                    courseSubjects = subjectsSet.toList()
                }
                isLoading = false
            }.addOnFailureListener { isLoading = false }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            courseTitle = courseTitle, 
            courseFee = courseFee, 
            courseDuration = courseDuration, 
            isRejected = paymentStatus == "rejected", 
            isSubmitting = isSubmittingPayment, 
            upiId = upiId, 
            merchantName = merchantName, 
            staticQrUrl = staticQrUrl, 
            onDismiss = { showPaymentDialog = false }
        ) { app, utr, totalPaid ->
            isSubmittingPayment = true
            val paymentData = hashMapOf(
                "uid" to auth.currentUser?.uid, 
                "email" to auth.currentUser?.email, 
                "sheetId" to courseId, 
                "courseTitle" to courseTitle,
                "fee" to courseFee, 
                "totalPaid" to totalPaid,
                "durationMonths" to courseDuration, 
                "utr" to utr, 
                "app" to app, 
                "status" to "pending", 
                "createdAt" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("pending_registrations").document("${auth.currentUser?.uid}_${courseTitle}").set(paymentData)
                .addOnSuccessListener { isSubmittingPayment = false; showPaymentDialog = false; Toast.makeText(context, "Registration submitted successfully!", Toast.LENGTH_LONG).show() }
                .addOnFailureListener { e -> isSubmittingPayment = false; Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // ==========================================
    // WEBVIEW WITH FULL HTML TTS SUPPORT
    // ==========================================
    if (showStudyMaterialWebView) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var canGoBack by remember { mutableStateOf(false) }

        // Initialize TTS Manager securely
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val ttsManager = remember(context) {
            TTSManager(context) {
                // When native TTS finishes reading a sentence, it alerts the HTML
                mainHandler.post {
                    webViewRef?.evaluateJavascript("if(window.onAndroidTtsDone) window.onAndroidTtsDone();", null)
                }
            }
        }

        fun stopAllSpeech() {
            ttsManager.stop()
            webViewRef?.evaluateJavascript("if(window.stopTTS) window.stopTTS();", null)
        }

        BackHandler {
            stopAllSpeech()
            if (canGoBack) webViewRef?.goBack() else showStudyMaterialWebView = false
        }

        DisposableEffect(Unit) {
            onDispose { 
                ttsManager.shutdown() 
            }
        }

        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(primaryGradient)
                ) {
                    TopAppBar(
                        title = { Text(courseTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { 
                            IconButton(onClick = { 
                                stopAllSpeech()
                                if (canGoBack) webViewRef?.goBack() else showStudyMaterialWebView = false 
                            }) { 
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) 
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
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
                        webChromeClient = WebChromeClient() 

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false 
                            cacheMode = if (isOnline(ctx)) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        
                        // Link the TTS Manager to the HTML window
                        addJavascriptInterface(WebAppInterface(ttsManager), "AndroidTTS")
                        
                        loadUrl(studyMaterialUrl)
                        webViewRef = this
                    }
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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

                    var attemptedCount = 0
                    var totalAchievedScore = 0f
                    var totalPossibleScore = 0f

                    tests.forEach { test ->
                        if (localStorage.isTestAttempted(courseId, test.name)) {
                            attemptedCount++
                            val score = localStorage.getTestScore(courseId, test.name)
                            if (score != null) {
                                totalAchievedScore += score.first
                                totalPossibleScore += score.second
                            }
                        }
                    }

                    if (attemptedCount > 0) {
                        val formatScore = { value: Float -> if (value % 1.0f == 0f) value.toInt().toString() else value.toString() }
                        val accuracy = if (totalPossibleScore > 0) ((totalAchievedScore / totalPossibleScore) * 100).toInt() else 0
                        val accuracyColor = if (accuracy >= 70) Color(0xFF2E7D32) else if (accuracy >= 40) Color(0xFFE67E22) else Color(0xFFC62828)

                        Text("Your Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tests Completed", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    Text("$attemptedCount / ${tests.size}", fontWeight = FontWeight.Black, color = themePrimaryColor, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = attemptedCount.toFloat() / tests.size.coerceAtLeast(1).toFloat(),
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                    color = themePrimaryColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Overall Score", fontSize = 12.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${formatScore(totalAchievedScore)} / ${formatScore(totalPossibleScore)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themePrimaryColor)
                                    }
                                    Divider(modifier = Modifier.height(40.dp).width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Accuracy", fontSize = 12.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("$accuracy%", fontSize = 22.sp, fontWeight = FontWeight.Black, color = accuracyColor)
                                    }
                                }
                            }
                        }
                        
                        if (courseSubjects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Subject Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    courseSubjects.forEachIndexed { index, subject ->
                                        val displayProgress = accuracy / 100f
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(subject.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("$accuracy%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accuracyColor)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = displayProgress,
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = accuracyColor,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        if (index < courseSubjects.size - 1) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
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
                            Text("View Course Content", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(24.dp)) 
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = themePrimaryColor) }
                    } else if (paymentStatus != "approved") {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Spacer(modifier = Modifier.height(40.dp))
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
                            if (tests.isEmpty() && studyMaterialUrl.isBlank()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                                    Text("No content found for this course yet.", color = Color.Gray) 
                                }
                            } else if (currentContentFolder == null) {
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    if (subscriptionExpiry != null) {
                                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), border = BorderStroke(1.dp, Color(0xFF4CAF50)), shape = RoundedCornerShape(24.dp)) {
                                            Text(text = "Active Subscription: Valid until ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(subscriptionExpiry!!)}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp).fillMaxWidth(), fontSize = 13.sp)
                                        }
                                    }

                                    if (studyMaterialUrl.isNotBlank()) {
                                        FolderCard(
                                            title = "Study Material",
                                            icon = Icons.Default.Folder,
                                            themeColor = themePrimaryColor
                                        ) {
                                            if (courseFee == 0.0 || paymentStatus == "approved") {
                                                showStudyMaterialWebView = true
                                            } else {
                                                Toast.makeText(context, "Please unlock the course to access Study Materials.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

                                    if (tests.isNotEmpty()) {
                                        FolderCard(
                                            title = "Mock Tests",
                                            icon = Icons.Default.Folder,
                                            themeColor = themePrimaryColor
                                        ) {
                                            currentContentFolder = "MOCK_TESTS"
                                        }
                                    }
                                }
                            } else if (currentContentFolder == "MOCK_TESTS") {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { currentContentFolder = null }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = themePrimaryColor)
                                        }
                                        Text("Mock Tests", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            }
                        } else if (selectedTab == 2) {
                            if (bookmarkedQuestions.isEmpty()) {
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Spacer(modifier = Modifier.height(40.dp))
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

@Composable
fun FolderCard(title: String, icon: ImageVector, themeColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(themeColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = themeColor, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    courseTitle: String, courseFee: Double, courseDuration: Int, isRejected: Boolean, isSubmitting: Boolean,
    upiId: String, merchantName: String, staticQrUrl: String, onDismiss: () -> Unit, onSubmit: (String, String, Double) -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    
    var currentStep by remember { mutableIntStateOf(1) }
    val internetFee = courseFee * 0.05
    val totalFee = courseFee + internetFee
    
    var selectedApp by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var utr by remember { mutableStateOf("") }
    val apps = listOf("PhonePe", "Google Pay (GPay)", "Paytm", "Other")
    
    val qrUrl = remember(upiId, staticQrUrl, totalFee) {
        if (upiId.isNotBlank()) "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${URLEncoder.encode("upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${totalFee}&cu=INR", "UTF-8")}" else staticQrUrl
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() }, 
        containerColor = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (currentStep == 1) "Payment Summary" else "Make Payment", fontWeight = FontWeight.Bold, color = themePrimaryColor, fontSize = 18.sp)
                IconButton(onClick = { 
                    if (!isSubmitting) {
                        if (currentStep == 2) currentStep = 1 else onDismiss()
                    }
                }) { 
                    Icon(if (currentStep == 2) Icons.Default.ArrowBack else Icons.Default.Close, contentDescription = "Close/Back", tint = Color.Gray) 
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                
                if (isRejected && currentStep == 1) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()) { 
                        Text(text = "Your previous payment was rejected. Please try again.", color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) 
                    }
                }

                if (currentStep == 1) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Course Fee", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.getDefault(), "₹%.2f", courseFee), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Internet Handling Fee", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.getDefault(), "₹%.2f", internetFee), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total Amount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(String.format(Locale.getDefault(), "₹%.2f", totalFee), fontWeight = FontWeight.Black, fontSize = 20.sp, color = themePrimaryColor)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.fillMaxWidth().height(54.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), 
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BUY NOW", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                    }
                }

                if (currentStep == 2) {
                    Text(text = "Scan QR to pay exactly", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "₹%.2f", totalFee), fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.size(160.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        if (qrUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(qrUrl).crossfade(true).build(), contentDescription = "UPI QR Code", modifier = Modifier.padding(8.dp).fillMaxSize()) 
                        else CircularProgressIndicator(color = themePrimaryColor, modifier = Modifier.size(24.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Or pay to this UPI ID", fontSize = 12.sp) },
                        trailingIcon = { 
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
                                Toast.makeText(context, "UPI ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = themePrimaryColor)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF90CAF9))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Instructions: Open any UPI app, make the payment, and copy the 12-digit UTR/Ref Number from the success screen to verify your purchase.", fontSize = 11.sp, color = Color(0xFF0D47A1), lineHeight = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedApp, onValueChange = {}, readOnly = true, label = { Text("I paid using app") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimaryColor, focusedLabelColor = themePrimaryColor)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            apps.forEach { app -> DropdownMenuItem(text = { Text(app) }, onClick = { selectedApp = app; expanded = false }) }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = utr, onValueChange = { utr = it }, label = { Text("12-Digit UTR Number") }, placeholder = { Text("e.g. 123456789012") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimaryColor, focusedLabelColor = themePrimaryColor)
                    )
                }
            }
        },
        confirmButton = {
            if (currentStep == 2) {
                Button(
                    onClick = { onSubmit(if (selectedApp.isNotBlank()) selectedApp else "Direct UPI", utr, totalFee) }, 
                    modifier = Modifier.fillMaxWidth().height(48.dp), enabled = utr.isNotBlank() && !isSubmitting, colors = ButtonDefaults.buttonColors(containerColor = themePrimaryColor), shape = RoundedCornerShape(50)
                ) { 
                    if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) 
                    else Text("Submit Details", fontWeight = FontWeight.Bold, color = Color.White) 
                }
            }
        },
        dismissButton = null
    )
}

fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        else -> false
    }
}
