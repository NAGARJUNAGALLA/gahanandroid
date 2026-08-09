package com.jcv.mocktests.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.jcv.mocktests.R

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

    // UI State for the custom biometric dialog
    var showAuthDialog by remember { mutableStateOf(true) } // Set to true to show instantly on launch
    var biometricError by remember { mutableStateOf<String?>(null) }

    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    // THE CUSTOM BIOMETRIC DIALOG
    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFFB300), // Yellow Warning Color
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("User Authentication", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                }
            },
            text = {
                Text("Select a method to login", fontSize = 16.sp, color = Color.Black, modifier = Modifier.padding(start = 44.dp))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAuthDialog = false
                        
                        // TRIGGER THE FINGERPRINT SCANNER
                        val activity = context as? androidx.fragment.app.FragmentActivity
                        if (activity != null) {
                            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                            val biometricPrompt = androidx.biometric.BiometricPrompt(
                                activity, 
                                executor,
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        // SUCCESS! Navigate to home securely
                                        onNavigateToHome()
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                        biometricError = errString.toString()
                                    }
                                }
                            )

                            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Sign in to Mock Tests")
                                .setSubtitle("Use your fingerprint or screen lock")
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()

                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            biometricError = "Biometric authentication is not supported on this device."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8BC34A)), // Green Button
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Use Screen Lock", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAuthDialog = false }, // Dismiss and let them type password
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.Black)
                ) {
                    Text("Use login", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Show a quick Toast error if they cancel or fail the fingerprint scan
    biometricError?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            biometricError = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        
        // APP LOGO
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        )

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
            shape = RoundedCornerShape(8.dp), 
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
