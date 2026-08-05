package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

// Matching the "View Series" blue from the image
val ViewSeriesBlue = Color(0xFF2962FF)

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    var identifier by remember { mutableStateOf("") } 
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "JCV MOCK TESTS", 
            style = MaterialTheme.typography.headlineLarge, 
            color = ViewSeriesBlue,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Mobile Number or Email") }, 
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ViewSeriesBlue,
                focusedLabelColor = ViewSeriesBlue
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ViewSeriesBlue,
                focusedLabelColor = ViewSeriesBlue
            )
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                isLoading = true
                val trimmedIdentifier = identifier.trim()
                
                val finalAuthEmail = if (trimmedIdentifier.contains("@")) {
                    trimmedIdentifier
                } else {
                    "$trimmedIdentifier@jcv.com"
                }

                auth.signInWithEmailAndPassword(finalAuthEmail, password)
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            onNavigateToHome()
                        } else {
                            errorMessage = task.exception?.message ?: "Login Failed"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(8.dp), // Matching the slight rounding from the image
            colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Log in", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateToSignup) {
            Text("Don't have an account? Sign Up", color = ViewSeriesBlue)
        }
    }
}
