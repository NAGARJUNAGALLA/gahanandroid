package com.jcv.mocktests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    score: Int, // Represents the number of correct answers
    incorrectAnswers: Int, // Needed to calculate the negative penalty accurately
    totalQuestions: Int,
    onNavigateHome: () -> Unit
) {
    // 1. DYNAMIC GLOBAL THEME INTEGRATION
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val primaryGradient = Brush.horizontalGradient(listOf(themePrimaryColor.copy(alpha = 0.75f), themePrimaryColor))

    // Dropdown state variables
    val negativeMarkingOptions = listOf(0.0f, 0.25f, 0.33f, 0.5f, 1.0f)
    var expanded by remember { mutableStateOf(false) }
    var selectedPenalty by remember { mutableStateOf(0.0f) }

    // Dynamic calculation: Actual Score - (Incorrect Answers * Penalty)
    val finalScore = score - (incorrectAnswers * selectedPenalty)

    Scaffold(
        topBar = {
            // DYNAMIC CURVED HEADER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomEnd = 40.dp))
                    .background(primaryGradient)
            ) {
                TopAppBar(
                    title = { Text("Exam Results", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateHome) { Icon(Icons.Default.ArrowBack, tint = Color.White, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // BREAKDOWN CARD
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Result Breakdown", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Questions:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$totalQuestions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Correct (+$score):", color = Color(0xFF27AE60))
                        Text(text = "$score", fontWeight = FontWeight.Bold, color = Color(0xFF27AE60))
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Incorrect:", color = Color(0xFFE74C3C))
                        Text(text = "$incorrectAnswers", fontWeight = FontWeight.Bold, color = Color(0xFFE74C3C))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // NEGATIVE MARKING DROPDOWN
            // ==========================================
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
                        .fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themePrimaryColor,
                        focusedLabelColor = themePrimaryColor
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    negativeMarkingOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == 0f) "None (0.0)" else "-$option", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                selectedPenalty = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ==========================================
            // FINAL CALCULATED SCORE CARD
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Final Calculated Score", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .background(themePrimaryColor.copy(alpha = 0.1f), CircleShape)
                            .border(4.dp, themePrimaryColor, CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                // Formats score to 2 decimal places to handle fractions like .25 or .33 properly
                                text = String.format("%.2f", finalScore), 
                                fontSize = 32.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = themePrimaryColor
                            )
                            Text("out of $totalQuestions", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ==========================================
            // PRIMARY ACTION BUTTON (Pill Shaped)
            // ==========================================
            Button(
                onClick = onNavigateHome, 
                modifier = Modifier.fillMaxWidth(0.95f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themePrimaryColor),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Return to Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
