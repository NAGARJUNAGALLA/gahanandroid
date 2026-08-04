package com.jcv.mocktests.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMaterialScreen(
    url: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Material") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // Enable JavaScript to render MathJax and other dynamic web elements
                    settings.javaScriptEnabled = true
                    
                    // Forces all links clicked inside the WebView to load inside this WebView 
                    // instead of kicking the user out to the device's default Chrome browser
                    webViewClient = WebViewClient() 
                    
                    loadUrl(url)
                }
            }
        )
    }
}
