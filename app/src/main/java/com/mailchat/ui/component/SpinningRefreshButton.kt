package com.mailchat.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.mailchat.R

/**
 * A refresh icon button that spins continuously while [isSpinning] is true. Used for manual
 * sync actions (chat overview and chat detail). The button is disabled while spinning so a
 * running sync cannot be re-triggered.
 */
@Composable
fun SpinningRefreshButton(isSpinning: Boolean, onClick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(1000),
                    RepeatMode.Restart
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }
    IconButton(onClick = onClick, enabled = !isSpinning) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = stringResource(R.string.sync),
            modifier = Modifier.rotate(rotation.value)
        )
    }
}
