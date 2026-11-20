package com.mailchat.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import com.mailchat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen zoomable image viewer: pinch to zoom (1x-8x), drag to pan, double-tap to
 * toggle between 1x and 2.5x. The bitmap is decoded once (downsampled to keep memory low)
 * on a background thread.
 */
@Composable
fun ZoomableImage(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(filePath) {
        val b = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(filePath, opts)
                // Downsample so the longest side is at most ~2048px (enough for 8x zoom on
                // most screens, safe for memory).
                val target = 2048f
                var sample = 1
                val longest = if (opts.outWidth > opts.outHeight) opts.outWidth else opts.outHeight
                while (longest / (sample * 2f) > target) sample *= 2
                BitmapFactory.decodeFile(
                    filePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }
        if (b == null) loadFailed = true else bitmap = b
    }
    val b = bitmap
    if (b == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadFailed) {
                Text(stringResource(R.string.attachment_not_found), color = Color.White)
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }
    // Size of the bitmap when fitted (letterboxed) into the view. The transform is applied
    // around the view center, which is also the fitted image center.
    val fittedW: Float
    val fittedH: Float
    if (viewWidth == 0 || viewHeight == 0) {
        fittedW = 0f
        fittedH = 0f
    } else {
        val s = (viewWidth.toFloat() / b.width).coerceAtMost(viewHeight.toFloat() / b.height)
        fittedW = b.width * s
        fittedH = b.height * s
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                viewWidth = it.width
                viewHeight = it.height
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(b) {
                    // Compose 1.7 signature: (centroid, pan, zoom, rotation)
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 8f)
                        val applied = if (scale > 0f) newScale / scale else 1f
                        scale = newScale
                        if (scale <= 1.01f) {
                            // Fully zoomed out: snap back to center.
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val maxOX = (fittedW * scale - viewWidth) / 2f
                            val maxOY = (fittedH * scale - viewHeight) / 2f
                            val limX = if (maxOX > 0f) maxOX else 0f
                            val limY = if (maxOY > 0f) maxOY else 0f
                            offsetX = (offsetX * applied + pan.x).coerceIn(-limX, limX)
                            offsetY = (offsetY * applied + pan.y).coerceIn(-limY, limY)
                        }
                    }
                }
                .pointerInput(b) {
                    detectTapGestures(onDoubleTap = { offset ->
                        if (scale > 1.5f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val k = 2.5f
                            val maxOX = (fittedW * k - viewWidth) / 2f
                            val maxOY = (fittedH * k - viewHeight) / 2f
                            val limX = if (maxOX > 0f) maxOX else 0f
                            val limY = if (maxOY > 0f) maxOY else 0f
                            // Keep the tapped point in place while zooming.
                            offsetX = ((viewWidth / 2f - offset.x) * (k - 1f)).coerceIn(-limX, limX)
                            offsetY = ((viewHeight / 2f - offset.y) * (k - 1f)).coerceIn(-limY, limY)
                            scale = k
                        }
                    })
                }
        )
    }
}
