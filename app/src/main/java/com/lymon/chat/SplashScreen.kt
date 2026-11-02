package com.lymon.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** انترو التطبيق: اللوجو يظهر بتكبير وتلاشي ناعم ثم يدخل التطبيق */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(900)) }
        delay(2300)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(LogoBg), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.lymon_logo),
            contentDescription = "Lymon chat",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
        )
    }
}
