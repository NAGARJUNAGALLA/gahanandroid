package com.jcv.mocktests.ui

import androidx.compose.animation.core.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.jcv.mocktests.AppTheme
import com.jcv.mocktests.R 

@Composable
fun SplashScreen(
    currentTheme: AppTheme, 
    onThemeChange: (AppTheme) -> Unit, 
    onSplashFinished: () -> Unit
) {
    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val primaryGradient = Brush.verticalGradient(
        colors = listOf(themePrimaryColor.copy(alpha = 0.75f), themePrimaryColor)
    )

    // ==========================================
    // ANIMATION STATES
    // ==========================================
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    
    val textOffset = remember { Animatable(30f) }
    val textAlpha = remember { Animatable(0f) }
    
    val loaderAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Animate Logo (Bouncy Scale & Fade)
        launch {
            logoAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 800))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }

        // 2. Animate Text (Slide Up & Fade) with a slight delay so it follows the logo
        launch {
            delay(300)
            textAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 800))
        }
        launch {
            delay(300)
            textOffset.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing))
        }

        // 3. Fade in the loader indicator last
        launch {
            delay(800)
            loaderAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 500))
        }

        // Wait for 2.5 seconds total, then trigger the navigation to Login/Dashboard
        delay(2500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f) 
                .clip(RoundedCornerShape(bottomStart = 120.dp)) 
                .background(primaryGradient)
        ) {
            
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ==========================================
                // ANIMATED LOGO
                // ==========================================
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        }
                        .size(120.dp)
                        .background(Color.White, RoundedCornerShape(32.dp))
                        .padding(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // ==========================================
                // ANIMATED TEXT BLOCK
                // ==========================================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(y = textOffset.value.dp)
                        .alpha(textAlpha.value)
                ) {
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
        }

        // ==========================================
        // ANIMATED LOADING INDICATOR
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
                .alpha(loaderAlpha.value),
            contentAlignment = Alignment.BottomCenter
        ) {
            CircularProgressIndicator(
                color = themePrimaryColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }

        // ==========================================
        // THEME SWITCHER (Top Layer)
        // ==========================================
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
