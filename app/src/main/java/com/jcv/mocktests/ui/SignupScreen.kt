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
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage

private val ViewSeriesBlue = Color(0xFF2962FF)
private val PrimaryGradient = Brush.verticalGradient(listOf(Color(0xFF1565C0), ViewSeriesBlue))

@Composable
fun SignupScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showVerificationDialog by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ==========================================
    // VERIFICATION INSTRUCTIONS DIALOG
    // ==========================================
    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = { /* Force them to click OK */ },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Verify Your Email", fontWeight = FontWeight.Bold, color = ViewSeriesBlue) },
            text = {
                Text(
                    text = "Your account has been created! We have sent a verification link to $email.\n\nPlease check your inbox (and spam folder) and click the link to activate your account before logging in.",
                    color = Color.DarkGray,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVerificationDialog = false
                        onNavigateToLogin() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                ) {
                    Text("Go to Login", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7FB))) {
        // ==========================================
        // DYNAMIC SHAPE HEADER (Opposite curve of Login)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(bottomEnd = 80.dp)) // Unique bottom-right curve
                .background(PrimaryGradient)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 40.dp, start = 32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(70.dp)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Create Account", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Black)
                Text("Start your learning journey", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // ==========================================
        // FLOATING SIGNUP CARD
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(200.dp)) 

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
                    Text("Join JCV Mock Tests", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (Min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ViewSeriesBlue, focusedLabelColor = ViewSeriesBlue)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            if (name.isBlank() || email.isBlank() || password.isBlank() || mobile.isBlank()) {
                                errorMessage = "Please fill all fields"
                                return@Button
                            }
                            
                            isLoading = true
                            auth.createUserWithEmailAndPassword(email.trim(), password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = auth.currentUser
                                        if (user != null) {
                                            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                                            user.updateProfile(profileUpdates)

                                            val deviceId = LocalStorage(context).getOrCreateDeviceId()
                                            val userData = hashMapOf(
                                                "name" to name.trim(),
                                                "email" to email.trim(),
                                                "mobile" to mobile.trim(),
                                                "deviceId" to deviceId,
                                                "streakCount" to 0,
                                                "role" to "student",
                                                "createdAt" to FieldValue.serverTimestamp()
                                            )

                                            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                                .set(userData, SetOptions.merge())
                                                .addOnSuccessListener {
                                                    user.sendEmailVerification().addOnCompleteListener {
                                                        auth.signOut() 
                                                        isLoading = false
                                                        showVerificationDialog = true 
                                                    }
                                                }
                                        }
                                    } else {
                                        isLoading = false
                                        errorMessage = task.exception?.message ?: "Signup Failed"
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(50), // Fully rounded pill shape
                        colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text("Sign Up", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.clickable { onNavigateToLogin() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", color = Color.Gray, fontSize = 14.sp)
                Text("Log In", color = ViewSeriesBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
