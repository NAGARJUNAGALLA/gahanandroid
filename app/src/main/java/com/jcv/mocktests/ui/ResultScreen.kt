package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    score: Int, // Represents the number of correct answers
    incorrectAnswers: Int, // Needed to calculate the negative penalty accurately
    totalQuestions: Int,
    onNavigateHome: () -> Unit
) {
    // Dropdown state variables
    val negativeMarkingOptions = listOf(0.0f, 0.25f, 0.33f, 0.5f, 1.0f)
    var expanded by remember { mutableStateOf(false) }
    var selectedPenalty by remember { mutableStateOf(0.0f) }

    // Dynamic calculation: Actual Score - (Incorrect Answers * Penalty)
    val finalScore = score - (incorrectAnswers * selectedPenalty)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Exam Submitted!", 
            style = MaterialTheme.typography.headlineMedium, 
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Breakdown Card
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Result Breakdown", 
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Questions:")
                    Text(text = "$totalQuestions", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Correct (+$score):", color = MaterialTheme.colorScheme.primary)
                    Text(text = "$score", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Incorrect:", color = MaterialTheme.colorScheme.error)
                    Text(text = "$incorrectAnswers", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Negative Marking Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (selectedPenalty == 0f) "Negative Marking: None" else "Negative Marking: -$selectedPenalty",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                negativeMarkingOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (option == 0f) "None (0.0)" else "-$option") },
                        onClick = {
                            selectedPenalty = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Final Calculated Score Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Final Calculated Score", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    // Formats score to 2 decimal places to handle fractions like .25 or .33 properly
                    text = String.format("%.2f / %d", finalScore, totalQuestions),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNavigateHome, modifier = Modifier.fillMaxWidth()) {
            Text("Return to Dashboard")
        }
    }
}
