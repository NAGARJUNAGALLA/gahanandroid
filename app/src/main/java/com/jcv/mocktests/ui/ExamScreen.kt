package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    courseId: String,
    testName: String,
    isReviewMode: Boolean = false, // Toggle between Exam and Review
    onFinalSubmit: (Int, Int) -> Unit,
    onExitReview: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val questionStates = remember { mutableStateListOf<QuestionState>() }
    
    var currentQIndex by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(3600) }
    var isLoading by remember { mutableStateOf(true) }

    // Navigation Drawer State
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Firebase Auth for Username
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val userName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val userIdentifier = currentUser?.email ?: currentUser?.uid ?: "JCV-USER"

    // Fetch Questions
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
                    if (!isReviewMode) {
                        questionStates.clear()
                        questionStates.addAll(List(parsedQuestions.size) { QuestionState() })
                        timeLeft = parsedQuestions.size * 60 
                        if (parsedQuestions.isNotEmpty()) {
                            questionStates[0] = questionStates[0].copy(status = QuestionStatus.NOT_ANSWERED)
                        }
                    }
                }
                isLoading = false
            }
    }

    // Timer logic (Only in Exam Mode)
    LaunchedEffect(key1 = timeLeft, key2 = isLoading) {
        if (!isReviewMode && !isLoading && timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val currentQ = questions.getOrNull(currentQIndex) ?: return
    val currentState = questionStates.getOrNull(currentQIndex) ?: QuestionState()

    // Wrap in RTL so the drawer opens from the right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
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
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                }
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
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF104E8B),
                                        fontWeight = FontWeight.Bold
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
                                                .aspectRatio(1f)
                                                .clip(shape)
                                                .background(color)
                                                .border(
                                                    width = if (isActive) 2.dp else if (color == StatusNotVisitedColor) 1.dp else 0.dp,
                                                    color = if (isActive) Color.Blue else if (color == StatusNotVisitedColor) Color.LightGray else Color.Transparent,
                                                    shape = shape
                                                )
                                                .clickable {
                                                    currentQIndex = idx
                                                    if (!isReviewMode && questionStates[idx].status == QuestionStatus.NOT_VISITED) {
                                                        questionStates[idx] = questionStates[idx].copy(status = QuestionStatus.NOT_ANSWERED)
                                                    }
                                                    scope.launch { drawerState.close() }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${idx + 1}", color = textColor, fontWeight = FontWeight.Bold)
                                            if (!isReviewMode && state.status == QuestionStatus.ANSWERED_AND_MARKED) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(2.dp)
                                                        .size(6.dp)
                                                        .background(Color.Green, CircleShape)
                                                )
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
                                            var score = 0
                                            questions.forEachIndexed { i, q -> if (questionStates[i].selectedOption == q.correct) score++ }
                                            localStorage.markTestAsAttempted(courseId, testName)
                                            onFinalSubmit(score, questions.size)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Submit Exam", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            // Flip the main screen scaffold back to LTR so it looks normal
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
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!isReviewMode) {
                                    Row(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                                if (currentQIndex < questions.size - 1) currentQIndex++
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMarkedColor)
                                        ) { Text("Mark") }
                                        
                                        OutlinedButton(
                                            onClick = {
                                                questionStates[currentQIndex] = currentState.copy(selectedOption = null, status = QuestionStatus.NOT_ANSWERED)
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
                                            if (currentQIndex < questions.size - 1) {
                                                currentQIndex++
                                                if (questionStates[currentQIndex].status == QuestionStatus.NOT_VISITED) {
                                                    questionStates[currentQIndex] = questionStates[currentQIndex].copy(status = QuestionStatus.NOT_ANSWERED)
                                                }
                                            } else {
                                                var score = 0
                                                questions.forEachIndexed { i, q -> if (questionStates[i].selectedOption == q.correct) score++ }
                                                localStorage.markTestAsAttempted(courseId, testName)
                                                onFinalSubmit(score, questions.size)
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

// Reusable Legend Item for Drawer
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(6.dp)
                        .background(Color.Green, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 12.sp, color = Color(0xFF475569))
    }
}
