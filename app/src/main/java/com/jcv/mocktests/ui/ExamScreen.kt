package com.jcv.mocktests.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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

val JcvGradient = Brush.horizontalGradient(listOf(Color(0xFF104E8B), Color(0xFF1E90FF)))
val StatusAnsweredColor = Color(0xFF27AE60)
val StatusNotAnsweredColor = Color(0xFFE74C3C)
val StatusMarkedColor = Color(0xFF9B59B6)
val StatusNotVisitedColor = Color(0xFFFFFFFF)

enum class ExamStep { INSTRUCTIONS, EXAM, SUBMIT_CONFIRM, RESULT }

data class ExamSection(
    val name: String,
    val questions: List<Question>,
    val globalStartIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    courseId: String,
    testName: String,
    isReviewMode: Boolean = false, 
    onNavigateBack: () -> Unit,
    onReviewTest: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val prefs = remember { context.getSharedPreferences("JcvExamPrefs", Context.MODE_PRIVATE) }
    val prefKey = "exam_state_${courseId}_${testName}"

    var sections by remember { mutableStateOf<List<ExamSection>>(emptyList()) }
    val questionStates = remember { mutableStateListOf<MutableList<QuestionState>>() }
    
    // UI State Management
    var examStep by remember { mutableStateOf(if (isReviewMode) ExamStep.EXAM else ExamStep.INSTRUCTIONS) }
    var currentSecIndex by remember { mutableIntStateOf(0) }
    var currentQIndex by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(0) }
    var totalQuestions by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasAgreedToRules by remember { mutableStateOf(false) }
    
    // Marking Scheme States
    var positiveMark by remember { mutableFloatStateOf(1f) }
    var negativeMark by remember { mutableFloatStateOf(0f) }
    var isPosDropdownExpanded by remember { mutableStateOf(false) }
    var isNegDropdownExpanded by remember { mutableStateOf(false) }

    // Result States
    var finalResultScore by remember { mutableFloatStateOf(0f) }
    var finalMaxScore by remember { mutableFloatStateOf(0f) }
    var totalCorrect by remember { mutableIntStateOf(0) }
    var totalIncorrect by remember { mutableIntStateOf(0) }
    var totalSkipped by remember { mutableIntStateOf(0) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val userName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val userIdentifier = currentUser?.email ?: currentUser?.uid ?: "JCV-USER"

    // Helper function to save progress locally
    fun saveProgressLocally() {
        if (isReviewMode || sections.isEmpty()) return
        
        val flatStates = questionStates.flatten()
        val statesString = flatStates.joinToString(";") { "${it.status.name},${it.selectedOption ?: -1}" }
        
        prefs.edit()
            .putString(prefKey, statesString)
            .putInt("${prefKey}_time", timeLeft)
            .apply()
    }

    LaunchedEffect(courseId, testName) {
        val db = FirebaseFirestore.getInstance()
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    val specificTest = testsMap?.get(testName) as? Map<String, List<Map<String, Any>>>
                    
                    val parsedSections = mutableListOf<ExamSection>()
                    var globalIndex = 0
                    
                    specificTest?.forEach { (secName, qList) ->
                        val sectionQs = qList.map { qMap ->
                            Question(
                                id = (qMap["id"] as? Number)?.toInt() ?: 0,
                                text = qMap["text"] as? String ?: "",
                                options = qMap["options"] as? List<String> ?: emptyList(),
                                correct = (qMap["correct"] as? Number)?.toInt() ?: 0
                            )
                        }
                        parsedSections.add(ExamSection(secName, sectionQs, globalIndex))
                        globalIndex += sectionQs.size
                    }
                    
                    sections = parsedSections
                    totalQuestions = globalIndex
                    questionStates.clear()
                    
                    val savedStatesStr = prefs.getString(prefKey, "")
                    val savedTime = prefs.getInt("${prefKey}_time", totalQuestions * 60)
                    
                    if (!savedStatesStr.isNullOrBlank()) {
                        try {
                            val restoredFlat = savedStatesStr.split(";").map {
                                val parts = it.split(",")
                                QuestionState(
                                    status = QuestionStatus.valueOf(parts[0]),
                                    selectedOption = parts[1].toInt().takeIf { opt -> opt != -1 }
                                )
                            }
                            if (restoredFlat.size == totalQuestions) {
                                var offset = 0
                                parsedSections.forEach { sec ->
                                    val secStates = restoredFlat.subList(offset, offset + sec.questions.size).toMutableList()
                                    questionStates.add(secStates)
                                    offset += sec.questions.size
                                }
                            } else {
                                throw Exception("Size mismatch")
                            }
                        } catch (e: Exception) {
                            parsedSections.forEach { sec ->
                                questionStates.add(MutableList(sec.questions.size) { QuestionState() })
                            }
                        }
                    } else {
                        parsedSections.forEach { sec ->
                            questionStates.add(MutableList(sec.questions.size) { QuestionState() })
                        }
                    }
                    
                    if (!isReviewMode) {
                        timeLeft = savedTime 
                        if (sections.isNotEmpty() && questionStates[0][0].status == QuestionStatus.NOT_VISITED) {
                            questionStates[0][0] = questionStates[0][0].copy(status = QuestionStatus.NOT_ANSWERED)
                        }
                    }
                }
                isLoading = false
            }
    }

    LaunchedEffect(key1 = timeLeft, key2 = isLoading, key3 = examStep) {
        if (!isReviewMode && !isLoading && timeLeft > 0 && examStep == ExamStep.EXAM) {
            delay(1000L)
            timeLeft--
            if (timeLeft % 10 == 0) saveProgressLocally() 
        }
    }

    fun moveToNextQuestion() {
        if (currentQIndex < sections[currentSecIndex].questions.size - 1) {
            currentQIndex++
            if (!isReviewMode && questionStates[currentSecIndex][currentQIndex].status == QuestionStatus.NOT_VISITED) {
                questionStates[currentSecIndex][currentQIndex] = questionStates[currentSecIndex][currentQIndex].copy(status = QuestionStatus.NOT_ANSWERED)
                saveProgressLocally()
            }
        } else if (currentSecIndex < sections.size - 1) {
            currentSecIndex++
            currentQIndex = 0
            if (!isReviewMode && questionStates[currentSecIndex][currentQIndex].status == QuestionStatus.NOT_VISITED) {
                questionStates[currentSecIndex][currentQIndex] = questionStates[currentSecIndex][currentQIndex].copy(status = QuestionStatus.NOT_ANSWERED)
                saveProgressLocally()
            }
        } else {
            if (!isReviewMode) examStep = ExamStep.SUBMIT_CONFIRM else onNavigateBack()
        }
    }

    fun moveToPrevQuestion() {
        if (currentQIndex > 0) {
            currentQIndex--
        } else if (currentSecIndex > 0) {
            currentSecIndex--
            currentQIndex = sections[currentSecIndex].questions.size - 1
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (examStep == ExamStep.INSTRUCTIONS) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(testName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, tint = Color.White, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF104E8B))
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasAgreedToRules, 
                                onCheckedChange = { hasAgreedToRules = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1E90FF))
                            )
                            Text("Choose Language: English | I have read and understood the instructions.", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                            OutlinedButton(
                                onClick = { isPosDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF27AE60)) 
                            ) {
                                Text("Correct Answer: +$positiveMark Mark(s)")
                            }
                            DropdownMenu(
                                expanded = isPosDropdownExpanded,
                                onDismissRequest = { isPosDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                            ) {
                                listOf(1f, 2f, 3f, 4f).forEach { mark ->
                                    DropdownMenuItem(
                                        text = { Text("+$mark Marks ${if(mark==4f) "(IIT JEE)" else if(mark==2f) "(SSC CGL)" else ""}") },
                                        onClick = { positiveMark = mark; isPosDropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            OutlinedButton(
                                onClick = { isNegDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE74C3C))
                            ) {
                                Text("Wrong Answer: -$negativeMark Mark(s)")
                            }
                            DropdownMenu(
                                expanded = isNegDropdownExpanded,
                                onDismissRequest = { isNegDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                            ) {
                                listOf(0f, 0.25f, 0.33f, 0.5f, 1f).forEach { mark ->
                                    DropdownMenuItem(
                                        text = { Text("-$mark Marks ${if(mark==0f) "(No Negative Marking)" else if(mark==0.5f) "(SSC CGL)" else if(mark==1f) "(IIT JEE)" else ""}") },
                                        onClick = { negativeMark = mark; isNegDropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { 
                                examStep = ExamStep.EXAM 
                                saveProgressLocally()
                                com.jcv.mocktests.utils.AnalyticsHelper.logEvent("start_exam") {
                                    putString("test_name", testName)
                                }
                            }, 
                            enabled = hasAgreedToRules,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                        ) {
                            Text(if (timeLeft < totalQuestions * 60) "Resume Test" else "Start Test", fontWeight = FontWeight.Bold)
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
                Text("Please read the following instructions carefully", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Total Number of Questions: $totalQuestions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Total Time Available: ${totalQuestions} Mins", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    color = Color.White
                ){
                    Column {
                        Row(modifier = Modifier.background(Color(0xFFF3F4F6)).padding(8.dp)) {
                            Text("Section Name", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Qs", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text("Max", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text("Marks", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text("Neg", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Divider(color = Color.LightGray)
                        sections.forEach { sec ->
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(sec.name, modifier = Modifier.weight(1.5f), fontSize = 12.sp)
                                Text("${sec.questions.size}", modifier = Modifier.weight(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                Text("${sec.questions.size}", modifier = Modifier.weight(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                Text("1", modifier = Modifier.weight(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                Text("0", modifier = Modifier.weight(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                            Divider(color = Color.LightGray)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(StatusNotVisitedColor, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Text("1", fontSize = 12.sp) }
                        Text(" You have not visited the question yet.", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(StatusNotAnsweredColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)), contentAlignment = Alignment.Center) { Text("3", color = Color.White, fontSize = 12.sp) }
                        Text(" You have not answered the question.", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(StatusAnsweredColor, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)), contentAlignment = Alignment.Center) { Text("5", color = Color.White, fontSize = 12.sp) }
                        Text(" You have answered the question.", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(StatusMarkedColor, CircleShape), contentAlignment = Alignment.Center) { Text("7", color = Color.White, fontSize = 12.sp) }
                        Text(" You have NOT answered the question, but have marked the question for review.", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(StatusMarkedColor, CircleShape), contentAlignment = Alignment.Center) { 
                            Text("9", color = Color.White, fontSize = 12.sp)
                            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(12.dp).background(Color(0xFF2ECC71), CircleShape).border(1.dp, Color.White, CircleShape))
                        }
                        Text(" You have answered the question, but marked it for review.", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                
                Text("Navigating to a Question:", fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, modifier = Modifier.padding(bottom = 8.dp))
                Text("1. Click on the question number on the palette to go to that question directly.\n2. Click on Save and Next to save answer and move forward.\n3. Click on Mark for Review and Next to save answer, mark it, and move forward.", fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
        return
    }

    if (examStep == ExamStep.SUBMIT_CONFIRM) {
        val flatStates = questionStates.flatten()
        val answered = flatStates.count { it.status == QuestionStatus.ANSWERED || it.status == QuestionStatus.ANSWERED_AND_MARKED }
        val marked = flatStates.count { it.status == QuestionStatus.MARKED_FOR_REVIEW }
        val notAnswered = flatStates.count { it.status == QuestionStatus.NOT_ANSWERED }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Submit Examination", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF104E8B))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you sure you want to submit the exam?", textAlign = TextAlign.Center, color = Color.DarkGray)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(answered.toString(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StatusAnsweredColor)
                            Text("Answered", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(notAnswered.toString(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StatusNotAnsweredColor)
                            Text("Skipped", fontSize = 10.sp, color = Color.Gray)
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
                                
                                var correctAnswers = 0
                                var wrongAnswers = 0
                                var skippedAnswers = 0
                                
                                sections.forEachIndexed { sIdx, section ->
                                    section.questions.forEachIndexed { qIdx, q ->
                                        val selected = questionStates[sIdx][qIdx].selectedOption
                                        if (selected == null || selected == -1) {
                                            skippedAnswers++
                                        } else if (selected == q.correct) {
                                            correctAnswers++
                                        } else {
                                            wrongAnswers++
                                        }
                                    }
                                }
                                
                                val posScore = correctAnswers * positiveMark
                                val negScore = wrongAnswers * negativeMark
                                
                                finalResultScore = posScore - negScore
                                finalMaxScore = totalQuestions * positiveMark
                                
                                totalCorrect = correctAnswers
                                totalIncorrect = wrongAnswers
                                totalSkipped = skippedAnswers
                                
                                localStorage.saveTestScore(courseId, testName, finalResultScore, finalMaxScore)
                                
                                com.jcv.mocktests.utils.AnalyticsHelper.logEvent("submit_exam") {
                                    putString("test_name", testName)
                                    putDouble("score", finalResultScore.toDouble())
                                }
                                
                                examStep = ExamStep.RESULT
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

    if (examStep == ExamStep.RESULT) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Exam Results", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Close, tint = Color.White, contentDescription = "Close") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF104E8B))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF3F4F6))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(testName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(120.dp).background(Color(0xFFEFF6FF), CircleShape).border(4.dp, Color(0xFF1E90FF), CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$finalResultScore", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF104E8B))
                                Text("out of $finalMaxScore", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(modifier = Modifier.weight(1f), title = "Correct", value = totalCorrect.toString(), color = StatusAnsweredColor)
                    StatCard(modifier = Modifier.weight(1f), title = "Wrong", value = totalIncorrect.toString(), color = StatusNotAnsweredColor)
                    StatCard(modifier = Modifier.weight(1f), title = "Skipped", value = totalSkipped.toString(), color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onReviewTest,
                    modifier = Modifier.fillMaxWidth(0.9f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review Answers", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(0.9f).height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF104E8B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Return to Course", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        return
    }

    val currentSection = sections.getOrNull(currentSecIndex) ?: return
    val currentQ = currentSection.questions.getOrNull(currentQIndex) ?: return
    val currentState = questionStates[currentSecIndex][currentQIndex]

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(320.dp),
                        drawerContainerColor = Color.White,
                        drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxHeight().background(Color.White)) {
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

                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!isReviewMode) {
                                    val flatStates = questionStates.flatten()
                                    val answered = flatStates.count { it.status == QuestionStatus.ANSWERED }
                                    val notAnswered = flatStates.count { it.status == QuestionStatus.NOT_ANSWERED }
                                    val marked = flatStates.count { it.status == QuestionStatus.MARKED_FOR_REVIEW }
                                    val answeredMarked = flatStates.count { it.status == QuestionStatus.ANSWERED_AND_MARKED }
                                    val notVisited = flatStates.count { it.status == QuestionStatus.NOT_VISITED }

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
                                    var correct = 0; var incorrect = 0; var skipped = 0
                                    sections.forEachIndexed { sIdx, sec ->
                                        sec.questions.forEachIndexed { qIdx, q ->
                                            val state = questionStates[sIdx][qIdx]
                                            if (state.selectedOption == null) skipped++
                                            else if (state.selectedOption == q.correct) correct++
                                            else incorrect++
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        LegendItem(correct, "Correct", StatusAnsweredColor, RoundedCornerShape(4.dp))
                                        LegendItem(incorrect, "Incorrect", StatusNotAnsweredColor, RoundedCornerShape(4.dp))
                                        LegendItem(skipped, "Skipped", StatusNotVisitedColor, RoundedCornerShape(4.dp))
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f).background(Color(0xFFF4F7FB)).padding(16.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${currentSection.name} Palette",
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center, color = Color(0xFF104E8B), fontWeight = FontWeight.Bold, fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(currentSection.questions.size) { idx ->
                                        val state = questionStates[currentSecIndex][idx]
                                        val shape = when (state.status) {
                                            QuestionStatus.ANSWERED -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                                            QuestionStatus.NOT_ANSWERED -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                                            QuestionStatus.MARKED_FOR_REVIEW, QuestionStatus.ANSWERED_AND_MARKED -> CircleShape
                                            else -> RoundedCornerShape(4.dp)
                                        }
                                        val color = if (isReviewMode) {
                                            if (state.selectedOption == null) Color.White
                                            else if (state.selectedOption == currentSection.questions[idx].correct) StatusAnsweredColor
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
                                                    if (!isReviewMode && questionStates[currentSecIndex][idx].status == QuestionStatus.NOT_VISITED) {
                                                        questionStates[currentSecIndex][idx] = questionStates[currentSecIndex][idx].copy(status = QuestionStatus.NOT_ANSWERED)
                                                        saveProgressLocally()
                                                    }
                                                    scope.launch { drawerState.close() }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${currentSection.globalStartIndex + idx + 1}", color = textColor, fontWeight = FontWeight.Bold)
                                            if (!isReviewMode && state.status == QuestionStatus.ANSWERED_AND_MARKED) {
                                                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(6.dp).background(Color.Green, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }

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
                        Column {
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
                                        Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3A68))) {
                                            Text("Exit Review")
                                        }
                                    }
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Palette", tint = Color.White)
                                    }
                                }
                            }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6)).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                itemsIndexed(sections) { idx, section ->
                                    val isSelected = currentSecIndex == idx
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) Color(0xFF1E90FF) else Color.White,
                                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                            )
                                            .border(1.dp, if (isSelected) Color(0xFF1E90FF) else Color.LightGray, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .clickable { 
                                                currentSecIndex = idx
                                                currentQIndex = 0
                                                if (!isReviewMode && questionStates[idx][0].status == QuestionStatus.NOT_VISITED) {
                                                    questionStates[idx][0] = questionStates[idx][0].copy(status = QuestionStatus.NOT_ANSWERED)
                                                    saveProgressLocally()
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = section.name,
                                            color = if (isSelected) Color.White else Color.DarkGray,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
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
                                            onClick = { moveToPrevQuestion() },
                                            modifier = Modifier.weight(1f),
                                            enabled = !(currentSecIndex == 0 && currentQIndex == 0)
                                        ) { Text("Prev") }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                questionStates[currentSecIndex][currentQIndex] = currentState.copy(
                                                    status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED_AND_MARKED else QuestionStatus.MARKED_FOR_REVIEW
                                                )
                                                saveProgressLocally()
                                                moveToNextQuestion()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMarkedColor)
                                        ) { Text("Mark", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                questionStates[currentSecIndex][currentQIndex] = currentState.copy(selectedOption = null, status = QuestionStatus.NOT_ANSWERED)
                                                saveProgressLocally()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Clear") }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            questionStates[currentSecIndex][currentQIndex] = currentState.copy(
                                                status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED else QuestionStatus.NOT_ANSWERED
                                            )
                                            saveProgressLocally()
                                            moveToNextQuestion()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                                    ) { Text(if (currentSecIndex == sections.size - 1 && currentQIndex == currentSection.questions.size - 1) "Submit Exam" else "Save & Next") }
                                }
                            }
                        } else {
                            Surface(shadowElevation = 8.dp) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    OutlinedButton(
                                        onClick = { moveToPrevQuestion() }, 
                                        enabled = !(currentSecIndex == 0 && currentQIndex == 0)
                                    ) { Text("Previous") }
                                    
                                    Button(
                                        onClick = { moveToNextQuestion() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E90FF))
                                    ) { Text(if (currentSecIndex == sections.size - 1 && currentQIndex == currentSection.questions.size - 1) "Exit" else "Next") }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6)).border(1.dp, Color.LightGray).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Question Type: Multiple Choice", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Marks: +$positiveMark", fontSize = 12.sp, color = StatusAnsweredColor, fontWeight = FontWeight.Bold)
                                Text("Negative: -$negativeMark", fontSize = 12.sp, color = StatusNotAnsweredColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("Q ${currentSection.globalStartIndex + currentQIndex + 1}. ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Box(modifier = Modifier.weight(1f)) {
                                        MathText(
                                            text = currentQ.text, 
                                            fontSizePx = 18
                                        )
                                        Box(modifier = Modifier.matchParentSize().background(Color.Transparent))
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            items(currentQ.options.size) { optIdx ->
                                val optionText = currentQ.options[optIdx]
                                val isSelected = currentState.selectedOption == optIdx
                                val isCorrectAnswer = currentQ.correct == optIdx
                                
                                val bgColor = if (isReviewMode) {
                                    if (isCorrectAnswer) Color(0xFFF0FDF4) else if (isSelected) Color(0xFFFEF2F2) else Color.Transparent
                                } else if (isSelected) Color(0xFFEFF6FF) else Color.Transparent
                                
                                val borderColor = if (isReviewMode) {
                                    if (isCorrectAnswer) StatusAnsweredColor else if (isSelected) StatusNotAnsweredColor else Color.LightGray.copy(alpha = 0.5f)
                                } else if (isSelected) Color(0xFF60A5FA) else Color.LightGray.copy(alpha = 0.5f)

                                val hexTextColor = if (isReviewMode) {
                                    if (isCorrectAnswer) "#166534" 
                                    else if (isSelected) "#991B1B" 
                                    else "#333333"
                                } else {
                                    if (isSelected) "#1E40AF" 
                                    else "#333333"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                        .background(bgColor, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isReviewMode) {
                                            questionStates[currentSecIndex][currentQIndex] = currentState.copy(selectedOption = optIdx)
                                            saveProgressLocally()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val radioColor = if (isReviewMode) {
                                        if (isCorrectAnswer) StatusAnsweredColor
                                        else if (isSelected) StatusNotAnsweredColor
                                        else Color.LightGray
                                    } else {
                                        if (isSelected) Color(0xFF1E90FF) else Color.Gray
                                    }

                                    RadioButton(
                                        selected = if (isReviewMode) isSelected || isCorrectAnswer else isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = radioColor,
                                            unselectedColor = radioColor
                                        ),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    
                                    Box(modifier = Modifier.weight(1f)) {
                                        MathText(
                                            text = optionText,
                                            fontSizePx = 16,
                                            textColorHex = hexTextColor
                                        )
                                        Box(modifier = Modifier.matchParentSize().background(Color.Transparent))
                                    }
                                    
                                    if (isReviewMode) {
                                        if (isCorrectAnswer) {
                                            Icon(Icons.Default.Check, tint = StatusAnsweredColor, contentDescription = "Correct")
                                        } else if (isSelected) {
                                            Icon(Icons.Default.Clear, tint = StatusNotAnsweredColor, contentDescription = "Incorrect")
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
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

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

@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    textColorHex: String = "#333333",
    fontSizePx: Int = 16
) {
    val htmlData = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <script>
                MathJax = {
                    tex: {
                        inlineMath: [['$', '$'], ['\\(', '\\)']],
                        displayMath: [['$$', '$$'], ['\\[', '\\]']]
                    },
                    startup: { typeset: true }
                };
            </script>
            <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            <style>
                body {
                    font-family: sans-serif;
                    font-size: ${fontSizePx}px;
                    color: $textColorHex;
                    margin: 0;
                    padding: 0;
                    background-color: transparent;
                    word-wrap: break-word;
                    
                    -webkit-touch-callout: none; 
                    -webkit-user-select: none;   
                    user-select: none;           
                }
                img { max-width: 100%; height: auto; border-radius: 4px; margin-top: 4px; pointer-events: none; }
            </style>
        </head>
        <body>
            $text
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                isLongClickable = false
                setOnLongClickListener { true }
                
                loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
        }
    )
}
