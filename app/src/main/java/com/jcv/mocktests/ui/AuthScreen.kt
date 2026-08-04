package com.jcv.mocktests.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    // Changed state variable name to be more accurate
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
        Text("JCV MOCK TESTS", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Mobile Number or Email") }, // Updated label
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
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
                
                // MIRRORING YOUR WEB APP LOGIC HERE
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Log in")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateToSignup) {
            Text("Don't have an account? Sign Up")
        }
    }
}
