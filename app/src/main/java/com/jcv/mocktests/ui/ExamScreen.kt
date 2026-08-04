package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.jcv.mocktests.models.Question
import com.jcv.mocktests.models.QuestionState
import com.jcv.mocktests.models.QuestionStatus
import kotlinx.coroutines.delay

@Composable
fun ExamScreen(
    courseId: String, 
    testName: String, 
    onFinalSubmit: (Int, Int) -> Unit
) {
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val questionStates = remember { mutableStateListOf<QuestionState>() }
    
    var currentQIndex by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(3600) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch Questions
    LaunchedEffect(courseId, testName) {
        val db = FirebaseFirestore.getInstance()
        db.collection("pro_course_questions").document(courseId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val testsMap = data?.get("tests") as? Map<String, Any>
                    
                    // Navigate down to: tests -> [Test Name] -> [Section Name] -> Array of Questions
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
                    
                    // Initialize empty states for each question
                    questionStates.clear()
                    questionStates.addAll(List(parsedQuestions.size) { QuestionState() })
                    
                    // Set timer based on question count (1 minute per question as per web app logic)
                    timeLeft = parsedQuestions.size * 60 
                } else {
                    errorMessage = "Test data not found."
                }
                isLoading = false
            }
            .addOnFailureListener {
                errorMessage = it.message
                isLoading = false
            }
    }

    // Helper function to calculate score and submit
    fun submitExam() {
        var score = 0
        questions.forEachIndexed { index, question ->
            if (questionStates[index].selectedOption == question.correct) {
                score++
            }
        }
        onFinalSubmit(score, questions.size)
    }

    // Timer logic
    LaunchedEffect(key1 = timeLeft, key2 = isLoading) {
        if (!isLoading && timeLeft > 0) {
            delay(1000L)
            timeLeft--
        } else if (!isLoading && timeLeft <= 0 && questions.isNotEmpty()) {
            submitExam()
        }
    }

    // Loading State UI
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    // Error / Empty State UI
    if (errorMessage != null || questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMessage ?: "No questions available in this test.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    // Actual Exam UI
    val currentQ = questions[currentQIndex]
    val currentState = questionStates[currentQIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(testName, color = MaterialTheme.colorScheme.onPrimary)
                Text("Time: ${timeLeft / 60}:${String.format("%02d", timeLeft % 60)}", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text("Q ${currentQIndex + 1}. ${currentQ.text}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            currentQ.options.forEachIndexed { index, text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (currentState.selectedOption == index),
                            onClick = { questionStates[currentQIndex] = currentState.copy(selectedOption = index) }
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (currentState.selectedOption == index),
                        onClick = null 
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = text)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                questionStates[currentQIndex] = currentState.copy(selectedOption = null, status = QuestionStatus.NOT_ANSWERED)
            }) { Text("Clear") }

            OutlinedButton(onClick = {
                val status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED_AND_MARKED else QuestionStatus.MARKED_FOR_REVIEW
                questionStates[currentQIndex] = currentState.copy(status = status)
                if (currentQIndex < questions.size - 1) currentQIndex++
            }) { Text("Mark & Next") }

            Button(onClick = {
                val status = if (currentState.selectedOption != null) QuestionStatus.ANSWERED else QuestionStatus.NOT_ANSWERED
                questionStates[currentQIndex] = currentState.copy(status = status)
                if (currentQIndex < questions.size - 1) currentQIndex++ else submitExam() 
            }) { Text(if (currentQIndex == questions.size - 1) "Submit" else "Save & Next") }
        }
    }
}
