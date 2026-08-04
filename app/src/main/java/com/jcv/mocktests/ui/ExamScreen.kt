package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jcv.mocktests.models.Question
import com.jcv.mocktests.models.QuestionState
import com.jcv.mocktests.models.QuestionStatus
import kotlinx.coroutines.delay

@Composable
fun ExamScreen(onFinalSubmit: () -> Unit) {
    // Dummy Data mirroring Firebase/Sheet response
    val questions = listOf(
        Question(1, "What is the capital of India?", listOf("Delhi", "Mumbai", "Chennai", "Kolkata"), 0),
        Question(2, "29 - 6 = ?", listOf("21", "22", "23", "24"), 2)
    )

    var currentQIndex by remember { mutableStateOf(0) }
    val questionStates = remember { mutableStateListOf(*Array(questions.size) { QuestionState() }) }
    var timeLeft by remember { mutableIntStateOf(3600) } // 60 mins

    // Timer Logic
    LaunchedEffect(key1 = timeLeft) {
        if (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        } else {
            onFinalSubmit()
        }
    }

    val currentQ = questions[currentQIndex]
    val currentState = questionStates[currentQIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CBT Simulator", color = MaterialTheme.colorScheme.onPrimary)
                Text("Time: ${timeLeft / 60}:${String.format("%02d", timeLeft % 60)}", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        // Question Area
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
                        onClick = null // Handled by selectable Row
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = text)
                }
            }
        }

        // Footer Actions (Clear, Mark & Next, Save & Next)
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
                if (currentQIndex < questions.size - 1) currentQIndex++ else onFinalSubmit()
            }) { Text(if (currentQIndex == questions.size - 1) "Submit" else "Save & Next") }
        }
    }
}
