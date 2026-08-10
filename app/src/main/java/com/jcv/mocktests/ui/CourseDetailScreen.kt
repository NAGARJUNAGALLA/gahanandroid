package com.jcv.mocktests.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val DarkHeaderColor = Color(0xFF181E2F)
private val ViewSeriesBlue = Color(0xFF1E90FF)

data class TestSummary(
    val name: String,
    val questionCount: Int,
    val timeMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onNavigateToExam: (courseId: String, testName: String, isReviewMode: Boolean) -> Unit,
    onNavigateToStudyMaterial: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("OVERVIEW", "CONTENT")
    
    var tests by remember { mutableStateOf<List<TestSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Dynamic Payment & Subscription States from Firebase
    var upiId by remember { mutableStateOf("") }
    var merchantName by remember { mutableStateOf("JCV MOCK TESTS") }
    var staticQrUrl by remember { mutableStateOf("") }
    var courseFee by remember { mutableDoubleStateOf(0.0) }
    var courseTitle by remember { mutableStateOf("Course") }
    var courseDuration by remember { mutableIntStateOf(1) }
    
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var subscriptionExpiry by remember { mutableStateOf<Date?>(null) } // NEW: Holds Expiry Date
    var showPaymentDialog by remember { mutableStateOf(false) }
    var isSubmittingPayment by remember { mutableStateOf(false) }

    LaunchedEffect(courseId) {
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        // 1. Fetch Merchant Settings for Payment
        db.collection("settings").get().addOnSuccessListener { snaps ->
            if (!snaps.isEmpty) {
                val sData = snaps.documents[0]
                upiId = sData.getString("upiId") ?: sData.getString("upi_id") ?: ""
                merchantName = sData.getString("merchantName") ?: sData.getString("merchant_name") ?: "JCV MOCK TESTS"
                staticQrUrl = sData.getString("qrCodeLink") ?: sData.getString("qr_code_link") ?: sData.getString("qrcode") ?: ""
            }
        }

        // 2. Fetch Course Fee, Title, and DURATION from testList
        db.collection("exams").document("testList").get().addOnSuccessListener { doc ->
            val testsArray = doc.get("tests") as? List<Map<String, Any>> ?: emptyList()
            val matchedCourse = testsArray.find { it["sheetId"] == courseId }
            if (matchedCourse != null) {
                courseTitle = matchedCourse["title"] as? String ?: "Course"
                courseFee = (matchedCourse["fee"]?.toString()?.toDoubleOrNull()) ?: 0.0
                courseDuration = (matchedCourse["durationMonths"] as? Number)?.toInt() ?: 1
            }
        }

        // 3. Listen to Real-Time Payment Status and Calculate Expiry
        if (uid != null) {
            db.collection("pending_registrations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("sheetId", courseId)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && !snap.isEmpty) {
                        val latestDoc = snap.documents.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
                        paymentStatus = latestDoc?.getString("status")
                        
                        // NEW: Calculate Expiry Date if Approved
                        if (paymentStatus == "approved") {
                            val createdAt = latestDoc?.getTimestamp("createdAt")?.toDate()
                            val savedDuration = (latestDoc?.get("durationMonths") as? Number)?.toInt() ?: courseDuration
                            
                            if (createdAt != null) {
                                val calendar = Calendar.getInstance()
                                calendar.time = createdAt
                                calendar.add(Calendar.MONTH, savedDuration)
                                subscriptionExpiry = calendar.time
                            }
                        }
                    } else {
                        paymentStatus = null
                        subscriptionExpiry = null
                    }
                }
        }

        // 4. Load the actual tests data for the content tab
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    
                    val parsedTests = mutableListOf<TestSummary>()
                    
                    testsMap?.forEach { (testName, testData) ->
                        var qCount = 0
                        try {
                            val sectionsMap = testData as? Map<String, List<Any>>
                            sectionsMap?.forEach { (_, qList) ->
                                qCount += qList.size
                            }
                        } catch (e: Exception) {
                            // Fallback if structure varies
                        }
                        
                        parsedTests.add(TestSummary(
                            name = testName,
                            questionCount = qCount,
                            timeMinutes = qCount 
                        ))
                    }
                    tests = parsedTests
                }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    // REGISTRATION / PAYMENT MODAL OVERLAY
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
            onDismiss = { showPaymentDialog = false },
            onSubmit = { app, utr ->
                isSubmittingPayment = true
                val uid = auth.currentUser?.uid
                val email = auth.currentUser?.email
                val docId = "${uid}_${courseTitle}" 
                
                val paymentData = hashMapOf(
                    "uid" to uid,
                    "email" to email,
                    "sheetId" to courseId,
                    "courseTitle" to courseTitle,
                    "fee" to courseFee,
                    "durationMonths" to courseDuration, 
                    "utr" to utr,
                    "app" to app,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                FirebaseFirestore.getInstance().collection("pending_registrations")
                    .document(docId)
                    .set(paymentData)
                    .addOnSuccessListener {
                        isSubmittingPayment = false
                        showPaymentDialog = false
                        Toast.makeText(context, "Registration submitted successfully!", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        isSubmittingPayment = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Details", color = Color.White) },
                navigationIcon = { 
                    TextButton(onClick = onNavigateBack) { 
                        Text("Back", color = Color.White) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkHeaderColor
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                contentColor = ViewSeriesBlue,
                containerColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                text = title, 
                                color = if (selectedTab == index) ViewSeriesBlue else Color.Gray,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }

            if (selectedTab == 0) {
                // OVERVIEW TAB
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About this Course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Comprehensive mock tests designed to help you prepare and excel.")
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val totalQs = tests.sumOf { it.questionCount }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Card(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${tests.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
                                Text("Total Tests", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        
                        Card(
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalQs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
                                Text("Total Questions", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    if (paymentStatus == "approved") {
                        // NEW: Approved Banner showing the Expiry Date
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Course Unlocked",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    if (subscriptionExpiry != null) {
                                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                        Text(
                                            text = "Valid until ${dateFormat.format(subscriptionExpiry!!)}",
                                            color = Color(0xFF388E3C),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Pre-Purchase Duration Banner
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            border = BorderStroke(1.dp, ViewSeriesBlue)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Subscription Validity", fontSize = 12.sp, color = Color.Gray)
                                    Text("$courseDuration Months Access", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkHeaderColor)
                                }
                                Text("₹${courseFee.toInt()}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = ViewSeriesBlue)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedButton(
                            onClick = { selectedTab = 1 }, 
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ViewSeriesBlue)
                        ) {
                            Text("Unlock Course Content", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                // CONTENT TAB / PAYWALL
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentAlignment = Alignment.TopCenter) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ViewSeriesBlue)
                        }
                    } else if (paymentStatus != "approved") {
                        // THE PAYWALL SCREEN
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Course Locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkHeaderColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (paymentStatus == "pending") {
                                Text(
                                    "Your payment is currently under review by the admin. Please check back later.",
                                    color = Color(0xFFE67E22), 
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Unlock this course for $courseDuration Months to access all ${tests.size} premium mock tests.", 
                                    color = Color.Gray, 
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                
                                if (paymentStatus == "rejected") {
                                    Text("Your previous payment was rejected. Please try again or contact the admin.", color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                Button(
                                    onClick = { showPaymentDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(55.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (courseFee > 0) "PAY ₹${courseFee.toInt()} FOR $courseDuration MONTHS" else "UNLOCK COURSE", 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    } else if (tests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tests found for this course.", color = Color.Gray)
                        }
                    } else {
                        // COURSE UNLOCKED: SHOW THE TESTS
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            
                            // NEW: Top Expiry Banner in Content Tab
                            if (subscriptionExpiry != null) {
                                item {
                                    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Active Subscription: Valid until ${dateFormat.format(subscriptionExpiry!!)}",
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            items(tests) { test ->
                                val alreadyAttempted = localStorage.isTestAttempted(courseId, test.name)
                                val testScore = localStorage.getTestScore(courseId, test.name)
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = test.name, 
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkHeaderColor
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.List, contentDescription = "Questions", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${test.questionCount} Questions", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DateRange, contentDescription = "Time", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${test.timeMinutes} Mins", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Divider(color = Color(0xFFF3F4F6))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        if (alreadyAttempted && testScore != null) {
                                            val formatScore = { value: Float -> 
                                                if (value % 1.0f == 0f) value.toInt().toString() else value.toString() 
                                            }
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Highest Score", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${formatScore(testScore.first)} / ${formatScore(testScore.second)}", 
                                                        color = Color(0xFF27AE60),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { onNavigateToExam(courseId, test.name, true) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ViewSeriesBlue),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Review Test", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { onNavigateToExam(courseId, test.name, false) },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                                            ) {
                                                Text("Take Test", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

// ---------------------------------------------------------------------------
// PAYMENT MODAL DIALOG
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    courseTitle: String,
    courseFee: Double,
    courseDuration: Int,
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
    var hasClickedPay by remember(isRejected) { mutableStateOf(isRejected) }
    val apps = listOf("PhonePe", "Google Pay (GPay)", "Paytm", "Other")
    val context = LocalContext.current
    
    // Generate Dynamic QR URL
    val qrUrl = remember(upiId, staticQrUrl, courseFee) {
        if (upiId.isNotBlank()) {
            val upiString = "upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${courseFee}&cu=INR"
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
                Text("Unlock Course ($courseDuration Months)", fontWeight = FontWeight.Bold, color = ViewSeriesBlue, fontSize = 16.sp)
                IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("₹${courseFee.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                
                if (isRejected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), 
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
                    ) {
                        Text(
                            text = "Your previous payment was rejected.\n\nPlease click Pay Now again, OR scan the QR and re-enter your UTR.",
                            color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (upiId.isNotBlank()) {
                            val uriString = "upi://pay?pa=$upiId&pn=${URLEncoder.encode(merchantName, "UTF-8")}&am=${courseFee}&cu=INR"
                            val uri = Uri.parse(uriString)
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            val chooser = Intent.createChooser(intent, "Pay securely with")
                            
                            try {
                                context.startActivity(chooser)
                                hasClickedPay = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI app found on your phone.", Toast.LENGTH_LONG).show()
                                hasClickedPay = true 
                            }
                        } else {
                            Toast.makeText(context, "UPI ID is not configured. Please contact the Admin.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), 
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PAY NOW", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "OR scan the QR code below to pay manually",
                    fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp)
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
                            CircularProgressIndicator(color = ViewSeriesBlue, modifier = Modifier.size(24.dp))
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
                            focusedBorderColor = ViewSeriesBlue,
                            focusedLabelColor = ViewSeriesBlue
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
                                    hasClickedPay = true
                                }
                            )
                        }
                    }
                }

                // SHOW UTR INPUT IF "PAY NOW" WAS CLICKED OR PREVIOUSLY REJECTED
                if (hasClickedPay || selectedApp.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = utr,
                        onValueChange = { utr = it },
                        label = { Text("Transaction / UTR Number") },
                        placeholder = { Text("e.g. 123456789012") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ViewSeriesBlue,
                            focusedLabelColor = ViewSeriesBlue
                        )
                    )
                }
            }
        },
        confirmButton = {
            if (hasClickedPay || selectedApp.isNotBlank()) {
                Button(
                    onClick = { onSubmit(if (selectedApp.isNotBlank()) selectedApp else "Direct UPI", utr) }, 
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = utr.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue),
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
