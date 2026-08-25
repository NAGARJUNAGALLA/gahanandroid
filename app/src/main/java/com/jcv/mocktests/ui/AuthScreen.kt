package com.jcv.mocktests.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcv.mocktests.AppTheme
import com.jcv.mocktests.R
import com.jcv.mocktests.utils.LocalStorage

@Composable
fun AuthScreen(
    currentTheme: AppTheme, 
    onThemeChange: (AppTheme) -> Unit, 
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary

    var email by remember { mutableStateOf("") } 
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    
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

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showResetDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Reset Password", fontWeight = FontWeight.Bold, color = themePrimaryColor) },
            text = {
                Column {
                    Text("Enter your email address and we'll send you a link to reset your password.", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail, onValueChange = { resetEmail = it }, label = { Text("Email Address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimaryColor, focusedLabelColor = themePrimaryColor)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emailToReset = resetEmail.trim()
                        if (emailToReset.isBlank()) { Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show(); return@Button }
                        isSendingReset = true
                        FirebaseFirestore.getInstance().collection("users").whereEqualTo("email", emailToReset).get().addOnSuccessListener { documents ->
                            if (documents.isEmpty) { isSendingReset = false; Toast.makeText(context, "This email is not registered.", Toast.LENGTH_LONG).show() } 
                            else {
                                auth.sendPasswordResetEmail(emailToReset).addOnCompleteListener { task ->
                                    isSendingReset = false
                                    if (task.isSuccessful) { Toast.makeText(context, "Link sent to your email!", Toast.LENGTH_LONG).show(); showResetDialog = false } 
                                    else { Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show() }
                                }
                            }
                        }.addOnFailureListener { e -> isSendingReset = false; Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    },
                    enabled = !isSendingReset, colors = ButtonDefaults.buttonColors(containerColor = themePrimaryColor)
                ) { if (isSendingReset) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Send Link") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }, enabled = !isSendingReset) { Text("Cancel", color = Color.Gray) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        // MAIN SCROLLABLE CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp)) // Space for Theme Switcher

            // 1. HEADER SECTION (Left Aligned like image)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo), contentDescription = "App Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Welcome Back!", fontSize = 32.sp, color = themePrimaryColor, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Log in to continue practicing\nwith JCV Mock Tests.", fontSize = 16.sp, color = Color.Gray, lineHeight = 22.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. LOGIN CARD (Solid primary background like image)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = themePrimaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // EMAIL FIELD
                    Text("Email Or User Name", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = email, 
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.Gray) },
                        trailingIcon = { if(email.isNotBlank()) Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = themePrimaryColor) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F4F8),
                            unfocusedContainerColor = Color(0xFFF0F4F8),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = themePrimaryColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    // PASSWORD FIELD
                    Text("Password", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = password, 
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = Color.Gray) },
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle Password Visibility", tint = Color.Gray)
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F4F8),
                            unfocusedContainerColor = Color(0xFFF0F4F8),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = themePrimaryColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // REMEMBER ME & FORGOT PASSWORD ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.White,
                                    uncheckedColor = Color.White,
                                    checkmarkColor = themePrimaryColor
                                )
                            )
                            Text("Remember me", color = Color.White, fontSize = 12.sp)
                        }
                        Text(
                            text = "Forgot Password?", 
                            color = Color.White, 
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { resetEmail = email; showResetDialog = true }.padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ERRORS & VERIFICATION
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Color(0xFFFFCDD2), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (needsVerification) {
                        OutlinedButton(
                            onClick = {
                                isResending = true
                                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { task ->
                                    isResending = false
                                    if (task.isSuccessful) { Toast.makeText(context, "Link resent! Check inbox.", Toast.LENGTH_LONG).show(); auth.signOut() }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                        ) { if (isResending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Resend Verification Link", fontWeight = FontWeight.Bold) }
                    }

                    // SIGN IN BUTTON
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) { errorMessage = "Please enter both fields"; return@Button }
                            isLoading = true; needsVerification = false; errorMessage = null
                            auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    if (user != null && user.isEmailVerified) {
                                        val deviceId = LocalStorage(context).getOrCreateDeviceId()
                                        FirebaseFirestore.getInstance().collection("users").document(user.uid).set(mapOf("deviceId" to deviceId), SetOptions.merge()).addOnSuccessListener {
                                            isLoading = false; onNavigateToHome()
                                        }
                                    } else { isLoading = false; needsVerification = true; errorMessage = "Email not verified." }
                                } else { isLoading = false; errorMessage = task.exception?.message ?: "Login Failed" }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(50),
                        // Soft contrasting gradient style button for the primary box
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) { 
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) 
                        else Text("Sign in", fontWeight = FontWeight.Bold, fontSize = 16.sp) 
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // 3. BOTTOM SECTION (Social login placeholders & Register)
            Text("OR LOGIN WITH", color = themePrimaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Placeholder circles mimicking the image
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(40.dp)
                            .border(2.dp, themePrimaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(16.dp).background(themePrimaryColor, CircleShape))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account? ", color = Color.Gray, fontSize = 14.sp)
                Text(
                    text = "Register Now", 
                    color = themePrimaryColor, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onNavigateToSignup() }.padding(4.dp)
                )
            }
        }

        // 4. THEME SWITCHER LAYER (Absolute Top Layer - Guarantees Clickability!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, end = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTheme.values().forEach { themeOption ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (currentTheme == themeOption) 26.dp else 20.dp)
                        .clip(CircleShape)
                        .background(themeOption.swatchColor)
                        .border(width = if (currentTheme == themeOption) 2.dp else 1.dp, color = themePrimaryColor.copy(alpha = 0.5f), shape = CircleShape)
                        .clickable { onThemeChange(themeOption) }
                )
            }
        }
    }
}
