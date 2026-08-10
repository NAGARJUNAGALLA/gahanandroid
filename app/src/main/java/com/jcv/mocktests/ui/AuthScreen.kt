package com.jcv.mocktests.ui

import android.widget.Toast
import androidx.compose.foundation.Image
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
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage

private val ViewSeriesBlue = Color(0xFF2962FF)

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
    
    // NEW: State for the Verification Popup
    var showVerificationDialog by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    // ==========================================
    // VERIFICATION INSTRUCTIONS DIALOG
    // ==========================================
    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = { /* Force them to click OK */ },
            containerColor = Color.White,
            title = {
                Text("Verify Your Email", fontWeight = FontWeight.Bold, color = ViewSeriesBlue)
            },
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
                        onNavigateToLogin() // Send them to login screen
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
                ) {
                    Text("Go to Login", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(100.dp).padding(bottom = 16.dp)
        )

        Text("Create an Account", style = MaterialTheme.typography.headlineMedium, color = ViewSeriesBlue, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = { Text("Mobile Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (Min 6 characters)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
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
                                // 1. Update Firebase Auth Profile with their Name
                                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                                user.updateProfile(profileUpdates)

                                // 2. Save User details to Firestore
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
                                        // 3. Send Verification Email & Show Popup
                                        user.sendEmailVerification().addOnCompleteListener {
                                            auth.signOut() // Immediately sign them out so they can't bypass verification
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
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ViewSeriesBlue)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("Sign Up", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Log In", color = ViewSeriesBlue)
        }
    }
}
