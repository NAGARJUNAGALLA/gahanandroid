package com.jcv.mocktests.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Question
import com.jcv.mocktests.models.QuestionState
import com.jcv.mocktests.models.QuestionStatus
import com.jcv.mocktests.utils.LocalStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// EXACT WEB APP COLORS
val JcvGradient = Brush.horizontalGradient(listOf(Color(0xFF104E8B), Color(0xFF1E90FF)))
val StatusAnsweredColor = Color(0xFF27AE60)
val StatusNotAnsweredColor = Color(0xFFE74C3C)
val StatusMarkedColor = Color(0xFF9B59B6)
val StatusNotVisitedColor = Color(0xFFFFFFFF)

enum class ExamStep { INSTRUCTIONS, EXAM, SUBMIT_CONFIRM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    courseId: String,
    testName: String,
    isReviewMode: Boolean = false, 
    onFinalSubmit: (Int, Int) -> Unit,
    onExitReview: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val prefs = remember { context.getSharedPreferences("JcvExamPrefs", Context.MODE_PRIVATE) }
    val prefKey = "exam_state_${courseId}_${testName}"

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val questionStates = remember { mutableStateListOf<QuestionState>() }
    
    // UI State Management
    var examStep by remember { mutableStateOf(if (isReviewMode) ExamStep.EXAM else ExamStep.INSTRUCTIONS) }
    var currentQIndex by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasAgreedToRules by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val userName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val userIdentifier = currentUser?.email ?: currentUser?.uid ?: "JCV-USER"

    // Helper function to save progress locally
    fun saveProgressLocally() {
        if (isReviewMode || questions.isEmpty()) return
        val statesString = questionStates.joinToString(";") { "${it.status.name},${it.selectedOption ?: -1}" }
        prefs.edit()
            .putString(prefKey, statesString)
            .putInt("${prefKey}_time", timeLeft)
            .apply()
    }

    // Fetch Questions & Restore State
    LaunchedEffect(courseId, testName) {
        val db = FirebaseFirestore.getInstance()
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    val specificTest = testsMap?.get(testName) as? Map<String, List<Map<String, Any>>>
                    
                    val parsedQuestions = mutableListOf<Question>()
                    specificTest?.values?.forEach { sectionQuestions ->
                        sectionQuestions.forEach { qMap ->
                            parsedQuestions.add(
                                Question(
                                    id = (qMap["id"] as? Number)?.toInt() ?: 0,
                                    text = qMap["text"] as? String ?: "",
                                    options = qMap["options"] as? List<String> ?: emptyList(),
                                    correct = (qMap["correct"] as? Number)?.toInt() ?: 0
                                )
                            )
                        }
                    }
                    questions = parsedQuestions
                    questionStates.clear()
                    
                    // Attempt to restore saved state
                    val savedStatesStr = prefs.getString(prefKey, "")
                    val savedTime = prefs.getInt("${prefKey}_time", parsedQuestions.size * 60)
                    
                    if (!savedStatesStr.isNullOrBlank()) {
                        try {
                            val restored = savedStatesStr.split(";").map {
                                val parts = it.split(",")
                                QuestionState(
                                    status = QuestionStatus.valueOf(parts[0]),
                                    selectedOption = parts[1].toInt().takeIf { opt -> opt != -1 }
                                )
                            }
                            if (restored.size == parsedQuestions.size) {
                                questionStates.addAll(restored)
                            } else {
                                questionStates.addAll(List(parsedQuestions.size) { QuestionState() })
                            }
                        } catch (e: Exception) {
                            questionStates.addAll(List(parsedQuestions.size) { QuestionState() })
                        }
                    } else {
                        questionStates.addAll(List(parsedQuestions.size) { QuestionState() })
                    }
                    
                    if (!isReviewMode) {
                        timeLeft = savedTime 
                        if (questions.isNotEmpty() && questionStates[0].status == QuestionStatus.NOT_VISITED) {
                            questionStates[0] = questionStates[0].copy(status = QuestionStatus.NOT_ANSWERED)
                        }
                    }
                }
                isLoading = false
            }
    }

    // Timer logic
    LaunchedEffect(key1 = timeLeft, key2 = isLoading, key3 = examStep) {
        if (!isReviewMode && !isLoading && timeLeft > 0 && examStep == ExamStep.EXAM) {
            delay(1000L)
            timeLeft--
            if (timeLeft % 10 == 0) saveProgressLocally() // Auto-save every 10 seconds
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // ----------------------------------------------------------------------------------
    // STEP 1: INSTRUCTIONS SCREEN
    // ----------------------------------------------------------------------------------
    if (examStep == ExamStep.INSTRUCTIONS) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(testName, color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onExitReview) { Icon(Icons.Default.ArrowBack, tint = Color.White, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF104E8B))
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasAgreedToRules, onCheckedChange = { hasAgreedToRules = it })
                            Text("I have read and understood the instructions.", fontSize = 14.sp)
                        }
                        Button(
                            onClick = { 
                                examStep = ExamStep.EXAM 
                                saveProgressLocally()
                            },
                            enabled = hasAgreedToRules,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                        ) {
                            Text("Start Test", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Please read the following instructions carefully", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Questions: ${questions.size}", fontWeight = FontWeight.Bold)
                        Text("Total Time Available: ${questions.size} Mins", fontWeight = FontWeight.Bold)
                    }
                }

                Text("Color Legend:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(StatusNotVisitedColor, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)))
                        Text(" You have not visited the question yet.", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(StatusNotAnsweredColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)))
                        Text(" You have not answered the question.", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(StatusAnsweredColor, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)))
                        Text(" You have answered the question.", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(StatusMarkedColor, CircleShape))
                        Text(" Marked for review without answering.", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                
                Text("General Rules:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Text("1. The clock has been set at the server and will display the remaining time at the top of your screen.\n2. Click 'Save & Next' to save your answer.\n3. Click 'Mark for Review' if you want to double-check your answer later.\n4. You can navigate between questions using the Palette menu.", fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
        return
    }

    // ----------------------------------------------------------------------------------
    // STEP 2: SUBMIT CONFIRMATION SCREEN
    // ----------------------------------------------------------------------------------
    if (examStep == ExamStep.SUBMIT_CONFIRM) {
        val answered = questionStates.count { it.status == QuestionStatus.ANSWERED || it.status == QuestionStatus.ANSWERED_AND_MARKED }
        val marked = questionStates.count { it.status == QuestionStatus.MARKED_FOR_REVIEW }
        val notAnswered = questionStates.count { it.status == QuestionStatus.NOT_ANSWERED }
        val notVisited = questionStates.count { it.status == QuestionStatus.NOT_VISITED }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Submit Examination", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF104E8B))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you sure you want to submit the exam? You will not be able to return to the questions.", textAlign = TextAlign.Center, color = Color.DarkGray)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Stats Grid
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(answered.toString(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StatusAnsweredColor)
                            Text("Answered", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(notAnswered.toString(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StatusNotAnsweredColor)
                            Text("Skipped", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(marked.toString(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StatusMarkedColor)
                            Text("Marked", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { examStep = ExamStep.EXAM },
                            modifier = Modifier.weight(1f)
                        ) { Text("Return") }
                        
                        Button(
                            onClick = {
                                saveProgressLocally()
                                var score = 0
                                questions.forEachIndexed { i, q -> if (questionStates[i].selectedOption == q.correct) score++ }
                                localStorage.markTestAsAttempted(courseId, testName)
                                onFinalSubmit(score, questions.size)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                        ) { Text("Yes, Submit") }
                    }
                }
            }
        }
        return
    }

    // ----------------------------------------------------------------------------------
    // STEP 3: MAIN EXAM / REVIEW SHELL
    // ----------------------------------------------------------------------------------
    val currentQ = questions.getOrNull(currentQIndex) ?: return
    val currentState = questionStates.getOrNull(currentQIndex) ?: QuestionState()

    // Wrap in RTL so the drawer opens from the right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                // Flip drawer contents back to LTR
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(320.dp),
                        drawerContainerColor = Color.White,
                        drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(Color.White)
                        ) {
                            // 1. Drawer Header (User Info)
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(50.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp)) }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(userName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                    Text(userIdentifier, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            Divider(color = Color.LightGray.copy(alpha = 0.5f))

                            // 2. Legend Section
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!isReviewMode) {
                                    val answered = questionStates.count { it.status == QuestionStatus.ANSWERED }
                                    val notAnswered = questionStates.count { it.status == QuestionStatus.NOT_ANSWERED }
                                    val marked = questionStates.count { it.status == QuestionStatus.MARKED_FOR_REVIEW }
                                    val answeredMarked = questionStates.count { it.status == QuestionStatus.ANSWERED_AND_MARKED }
                                    val notVisited = questionStates.count { it.status == QuestionStatus.NOT_VISITED }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        LegendItem(answered, "Answered", StatusAnsweredColor, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                                        LegendItem(notAnswered, "Not Answered", StatusNotAnsweredColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        LegendItem(notVisited, "Not Visited", StatusNotVisitedColor, RoundedCornerShape(4.dp))
                                        LegendItem(marked, "Marked", StatusMarkedColor, CircleShape)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LegendItem(answeredMarked, "Answered & Marked", StatusMarkedColor, CircleShape, hasDot = true)
                                } else {
                                    val correct = questions.indices.count { i -> questionStates.getOrNull(i)?.selectedOption == questions[i].correct }
                                    val incorrect = questions.indices.count { i -> questionStates.getOrNull(i)?.selectedOption != null && questionStates.getOrNull(i)?.selectedOption != questions[i].correct }
                                    val skipped = questions.indices.count { i -> questionStates.getOrNull(i)?.selectedOption == null }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        LegendItem(correct, "Correct", StatusAnsweredColor, RoundedCornerShape(4.dp))
                                        LegendItem(incorrect, "Incorrect", StatusNotAnsweredColor, RoundedCornerShape(4.dp))
                                        LegendItem(skipped, "Skipped", StatusNotVisitedColor, RoundedCornerShape(4.dp))
                                    }
                                }
                            }

                            // 3. Grid Section
                            Column(modifier = Modifier.weight(1f).background(Color(0xFFF4F7FB)).padding(16.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isReviewMode) "REVIEW PALETTE" else "QUESTION PALETTE",
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center, color = Color(0xFF104E8B), fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(questions.size) { idx ->
                                        val state = questionStates[idx]
                                        val shape = when (state.status) {
                                            QuestionStatus.ANSWERED -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                                            QuestionStatus.NOT_ANSWERED -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                                            QuestionStatus.MARKED_FOR_REVIEW, QuestionStatus.ANSWERED_AND_MARKED -> CircleShape
                                            else -> RoundedCornerShape(4.dp)
                                        }
                                        val color = if (isReviewMode) {
                                            if (state.selectedOption == null) Color.White
                                            else if (state.selectedOption == questions[idx].correct) StatusAnsweredColor
                                            else StatusNotAnsweredColor
                                        } else {
                                            when (state.status) {
                                                QuestionStatus.ANSWERED -> StatusAnsweredColor
                                                QuestionStatus.NOT_ANSWERED -> StatusNotAnsweredColor
                                                QuestionStatus.MARKED_FOR_REVIEW, QuestionStatus.ANSWERED_AND_MARKED -> StatusMarkedColor
                                                else -> StatusNotVisitedColor
                                            }
                                        }

                                        val textColor = if (color == StatusNotVisitedColor) Color.Black else Color.White
                                        val isActive = currentQIndex == idx

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f).clip(shape).background(color)
                                                .border(
                                                    width = if (isActive) 2.dp else if (color == StatusNotVisitedColor) 1.dp else 0.dp,
                                                    color = if (isActive) Color.Blue else if (color == StatusNotVisitedColor) Color.LightGray else Color.Transparent,
                                                    shape = shape
                                                )
                                                .clickable {
                                                    currentQIndex = idx
                                                    if (!isReviewMode && questionStates[idx].status == QuestionStatus.NOT_VISITED) {
                                                        questionStates[idx] = questionStates[idx].copy(status = QuestionStatus.NOT_ANSWERED)
                                                        saveProgressLocally()
                                                    }
                                                    scope.launch { drawerState.close() }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${idx + 1}", color = textColor, fontWeight = FontWeight.Bold)
                                            if (!isReviewMode && state.status == QuestionStatus.ANSWERED_AND_MARKED) {
                                                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(6.dp).background(Color.Green, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Bottom Drawer Button
                            if (!isReviewMode) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch { drawerState.close() }
                                            examStep = ExamStep.SUBMIT_CONFIRM
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) { Text("Submit Exam", color = Color.Black, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isReviewMode) Brush.horizontalGradient(listOf(Color(0xFF104E8B), Color(0xFF104E8B))) else JcvGradient)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isReviewMode) "REVIEW MODE" else "CBT Simulator",
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!isReviewMode) {
                                    Row(
                                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "Time", tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(String.format("%02d:%02d", timeLeft / 60, timeLeft % 60), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(onClick = onExitReview, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3A68))) {
                                        Text("Exit Review")
                                    }
                                }
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Palette", tint = Color.White)
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (!isReviewMode) {
                            Surface(shadowElevation = 8.dp) {
                                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { if (currentQIndex > 0) currentQIndex-- },
                                            modifier = Modifier.weight(1f),
                                            enabled = currentQIndex > 0
                                        ) { Text("Prev") }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                questionStates[currentQIndex] = currentState.copy(
                                                    status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED_AND_MARKED else QuestionStatus.MARKED_FOR_REVIEW
                                                )
                                                saveProgressLocally()
                                                if (currentQIndex < questions.size - 1) currentQIndex++
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMarkedColor)
                                        ) { Text("Mark") }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                questionStates[currentQIndex] = currentState.copy(selectedOption = null, status = QuestionStatus.NOT_ANSWERED)
                                                saveProgressLocally()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Clear") }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            questionStates[currentQIndex] = currentState.copy(
                                                status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED else QuestionStatus.NOT_ANSWERED
                                            )
                                            saveProgressLocally()
                                            if (currentQIndex < questions.size - 1) {
                                                currentQIndex++
                                                if (questionStates[currentQIndex].status == QuestionStatus.NOT_VISITED) {
                                                    questionStates[currentQIndex] = questionStates[currentQIndex].copy(status = QuestionStatus.NOT_ANSWERED)
                                                    saveProgressLocally()
                                                }
                                            } else {
                                                examStep = ExamStep.SUBMIT_CONFIRM
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                                    ) { Text(if (currentQIndex == questions.size - 1) "Submit Exam" else "Save & Next") }
                                }
                            }
                        } else {
                            Surface(shadowElevation = 8.dp) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    OutlinedButton(onClick = { if (currentQIndex > 0) currentQIndex-- }, enabled = currentQIndex > 0) { Text("Previous") }
                                    Button(
                                        onClick = { if (currentQIndex < questions.size - 1) currentQIndex++ else onExitReview() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                                    ) { Text(if (currentQIndex == questions.size - 1) "Exit" else "Next") }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        // Info Bar
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6)).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Multiple Choice Question", fontSize = 12.sp, color = Color.DarkGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Marks: +1", fontSize = 12.sp, color = StatusAnsweredColor, fontWeight = FontWeight.Bold)
                                Text("Negative: 0", fontSize = 12.sp, color = StatusNotAnsweredColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Question Area
                        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("Q ${currentQIndex + 1}. ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(currentQ.text, fontSize = 18.sp, lineHeight = 26.sp)
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            items(currentQ.options.size) { optIdx ->
                                val optionText = currentQ.options[optIdx]
                                val isSelected = currentState.selectedOption == optIdx
                                val isCorrectAnswer = currentQ.correct == optIdx
                                
                                val bgColor = if (isReviewMode) {
                                    if (isCorrectAnswer) Color(0xFFF0FDF4) else if (isSelected) Color(0xFFFEF2F2) else Color(0xFFF9FAFB)
                                } else if (isSelected) Color(0xFFEFF6FF) else Color.White
                                
                                val borderColor = if (isReviewMode) {
                                    if (isCorrectAnswer) StatusAnsweredColor else if (isSelected) StatusNotAnsweredColor else Color.LightGray
                                } else if (isSelected) Color(0xFF60A5FA) else Color.LightGray

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                        .background(bgColor, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isReviewMode) {
                                            questionStates[currentQIndex] = currentState.copy(selectedOption = optIdx)
                                            saveProgressLocally()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                if (isReviewMode && isCorrectAnswer) StatusAnsweredColor 
                                                else if (isReviewMode && isSelected) StatusNotAnsweredColor 
                                                else Color(0xFFF3F4F6), 
                                                CircleShape
                                            )
                                            .border(1.dp, if (isReviewMode && (isCorrectAnswer || isSelected)) Color.Transparent else Color.LightGray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ('A' + optIdx).toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReviewMode && (isCorrectAnswer || isSelected)) Color.White else Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = optionText, modifier = Modifier.weight(1f), fontSize = 16.sp)
                                    
                                    if (isReviewMode) {
                                        if (isCorrectAnswer) Icon(Icons.Default.Check, tint = StatusAnsweredColor, contentDescription = "Correct")
                                        else if (isSelected) Icon(Icons.Default.Clear, tint = StatusNotAnsweredColor, contentDescription = "Incorrect")
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

// Reusable Legend Item
@Composable
fun LegendItem(count: Int, label: String, color: Color, shape: androidx.compose.ui.graphics.Shape, hasDot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(130.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, shape)
                .border(width = if (color == StatusNotVisitedColor) 1.dp else 0.dp, color = Color.LightGray, shape = shape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = count.toString(), fontSize = 12.sp, color = if (color == StatusNotVisitedColor) Color.Black else Color.White)
            if (hasDot) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).size(6.dp).background(Color.Green, CircleShape))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 12.sp, color = Color(0xFF475569))
    }
}
