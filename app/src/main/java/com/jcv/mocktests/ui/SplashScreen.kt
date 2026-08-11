package com.jcv.mocktests.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.jcv.mocktests.AppTheme
import com.jcv.mocktests.R 

@Composable
fun SplashScreen(
    currentTheme: AppTheme, 
    onThemeChange: (AppTheme) -> Unit, 
    onSplashFinished: () -> Unit
) {
    // DYNAMIC THEME COLORS
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val primaryGradient = Brush.verticalGradient(
        colors = listOf(themePrimaryColor.copy(alpha = 0.75f), themePrimaryColor)
    )

    // Wait for 2.5 seconds then trigger the navigation
    LaunchedEffect(Unit) {
        delay(2500)
        onSplashFinished()
    }

    // ROOT BACKGROUND (Adapts to Light/Dark Mode)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        
        // 1. MASSIVE DYNAMIC CURVE (Matches Auth Screen Style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f) // Takes up 80% of the screen height
                .clip(RoundedCornerShape(bottomStart = 120.dp)) // Sweeping bottom-left curve
                .background(primaryGradient)
        ) {
            
            // MAIN CONTENT CENTERED INSIDE THE CURVE
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.White, RoundedCornerShape(32.dp))
                        .padding(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // App Title
                Text(
                    text = "JCV MOCK TESTS",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                Text(
                    text = "Your Path to Success",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 2. LOADING INDICATOR (At the very bottom of the screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            CircularProgressIndicator(
                color = themePrimaryColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }

        // 3. THEME SWITCHER (Absolute Top Layer for clickability)
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
                        .border(
                            width = if (currentTheme == themeOption) 2.dp else 1.dp, 
                            color = Color.White, 
                            shape = CircleShape
                        )
                        .clickable { onThemeChange(themeOption) }
                )
            }
        }
    }
}
