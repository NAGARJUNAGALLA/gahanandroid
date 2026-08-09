package com.jcv.mocktests.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jcv.mocktests.R

@Composable
fun UnlockScreen(
    onUnlockSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Function to trigger the biometric scanner
    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(
            activity, 
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // FINGERPRINT SUCCESS -> Enter App
                    onUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    errorMessage = errString.toString()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock JCV Mock Tests")
            .setSubtitle("Confirm it's you to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // Automatically trigger the fingerprint scanner the moment this screen opens!
    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    // UI shown underneath the fingerprint scanner (or if they cancel the scan)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp).padding(bottom = 16.dp)
        )

        Text("App Locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2962FF))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Please authenticate to continue.", color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = { triggerBiometric() },
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8BC34A))
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tap to Unlock", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onLogout) {
            Text("Log Out", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}
