package com.jcv.mocktests.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage

// App Theme Colors
private val ViewSeriesBlue = Color(0xFF2962FF)
private val ViewSeriesLightBlue = Color(0xFF64B5F6)
private val PrimaryGradient = Brush.verticalGradient(listOf(Color(0xFF1565C0), ViewSeriesBlue))

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    var email by remember { mutableStateOf("") } 
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    var needsVerification by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ==========================================
    // FORGOT PASSWORD DIALOG
    // ==========================================
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showResetDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Reset Password", fontWeight = FontWeight.Bold, color = ViewSeriesBlue) },
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
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
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
                    if (isSendingReset) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Send Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }, enabled = !isSendingReset) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7FB))) {
        // ==========================================
        // DYNAMIC SHAPE HEADER
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(bottomStart = 80.dp)) // Unique bottom-left curve
                .background(PrimaryGradient)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("JCV MOCK TESTS", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Black)
                Text("Welcome Back", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // ==========================================
        // FLOATING LOGIN CARD
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(240.dp)) // Pushes the card down to overlap the header

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Login to Continue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(24.dp))

                    // "Petal" Shaped Text Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp), // Unique Shape
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
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp), // Unique Shape
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "Forgot Password?",
                            color = ViewSeriesBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { 
                                    resetEmail = email 
                                    showResetDialog = true 
                                }
                                .padding(4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (needsVerification) {
                        OutlinedButton(
                            onClick = {
                                isResending = true
                                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { task ->
                                    isResending = false
                                    if (task.isSuccessful) {
                                        Toast.makeText(context, "Verification email resent! Check your inbox.", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(50), // Fully rounded pill shape
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00))
                        ) {
                            if (isResending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFF57C00), strokeWidth = 2.dp)
                            else Text("Resend Verification Link", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Primary Login Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both email and password"
                                return@Button
                            }

                            isLoading = true
                            needsVerification = false
                            errorMessage = null
                            
                            auth.signInWithEmailAndPassword(email.trim(), password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = auth.currentUser
                                        if (user != null && user.isEmailVerified) {
                                            val deviceId = LocalStorage(context).getOrCreateDeviceId()
                                            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                                .set(mapOf("deviceId" to deviceId), SetOptions.merge())
                                                .addOnSuccessListener {
                                                    isLoading = false
                                                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                                                    onNavigateToHome()
                                                }
                                        } else {
                                            isLoading = false
                                            needsVerification = true
                                            errorMessage = "Your email is not verified. Please check your inbox/spam."
                                        }
                                    } else {
                                        isLoading = false
                                        errorMessage = task.exception?.message ?: "Login Failed"
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(50), // Fully rounded pill shape
                        colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Log In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.clickable { onNavigateToSignup() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                Text("Sign Up", color = ViewSeriesBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
