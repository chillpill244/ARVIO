package com.arflix.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.ui.theme.AnimationConstants
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.SuccessGreen
import com.arflix.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay

enum class ToastType {
    SUCCESS, ERROR, INFO
}

/**
 * Glass morphism toast notification
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Toast(
    message: String,
    type: ToastType = ToastType.INFO,
    isVisible: Boolean,
    durationMs: Long = 3000,
    onDismiss: () -> Unit = {}
) {
    var visible by remember(isVisible) { mutableStateOf(isVisible) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            visible = true
            delay(durationMs)
            visible = false
            onDismiss()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = AnimationConstants.SPRING_DAMPING_FOCUS,
                    stiffness = AnimationConstants.SPRING_STIFFNESS_FOCUS
                )
            ) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = spring(
                    dampingRatio = AnimationConstants.SPRING_DAMPING_GENTLE,
                    stiffness = AnimationConstants.SPRING_STIFFNESS_GENTLE
                )
            ),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
        ) {
            val (accentColor, icon) = when (type) {
                ToastType.SUCCESS -> Pair(SuccessGreen, Icons.Default.Check)
                ToastType.ERROR -> Pair(Color(0xFFFF453A), Icons.Default.Close)
                ToastType.INFO -> Pair(Color(0xFF0A84FF), Icons.Default.Info)
            }
            
            val toastShape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .clip(toastShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = toastShape
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = ArflixTypography.body,
                    color = TextPrimary
                )
            }
        }
    }
}
