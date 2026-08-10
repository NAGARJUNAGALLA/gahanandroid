package com.jcv.mocktests.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage

private val ViewSeriesBlue = Color(0xFF2962FF)

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    var email by remember { mutableStateOf("") } 
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // NEW: State for Forgot Password Dialog
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    // ==========================================
    // FORGOT PASSWORD DIALOG
    // ==========================================
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showResetDialog = false },
            containerColor = Color.White,
            title = {
                Text("Reset Password", fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
            },
            text = {
                Column {
                    Text("Enter your email address and we'll send you a link to reset your password.", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetEmail.isBlank()) {
                            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSendingReset = true
                        auth.sendPasswordResetEmail(resetEmail.trim())
                            .addOnCompleteListener { task ->
                                isSendingReset = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Password reset link sent to your email!", Toast.LENGTH_LONG).show()
                                    showResetDialog = false
                                } else {
                                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    },
                    enabled = !isSendingReset,
                    colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Send Link")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !isSendingReset
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // ==========================================
    // MAIN LOGIN UI
    // ==========================================
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp).padding(bottom = 16.dp)
        )

        Text("JCV MOCK TESTS", style = MaterialTheme.typography.headlineLarge, color = ViewSeriesBlue, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Welcome back! Please login.", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") }, 
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
        )
        
        // NEW: Forgot Password Text
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "Forgot Password?",
                color = ViewSeriesBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { 
                        resetEmail = email // Auto-fill if they already typed it
                        showResetDialog = true 
                    }
                    .padding(4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter both email and password"
                    return@Button
                }

                isLoading = true
                
                auth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            if (uid != null) {
                                // SINGLE DEVICE ENFORCEMENT
                                val deviceId = LocalStorage(context).getOrCreateDeviceId()
                                FirebaseFirestore.getInstance().collection("users").document(uid)
                                    .set(mapOf("deviceId" to deviceId), SetOptions.merge())
                                    .addOnSuccessListener {
                                        isLoading = false
                                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                                        onNavigateToHome()
                                    }
                            } else {
                                isLoading = false
                                onNavigateToHome()
                            }
                        } else {
                            isLoading = false
                            errorMessage = task.exception?.message ?: "Login Failed"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(8.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Log In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateToSignup) {
            Text("Don't have an account? Sign Up", color = ViewSeriesBlue)
        }
    }
}
