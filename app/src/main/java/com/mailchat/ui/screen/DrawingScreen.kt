package com.mailchat.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailchat.R
import java.io.File
import java.io.FileOutputStream

/**
 * A simple finger-drawing screen. The user can draw freehand on a white canvas and optionally
 * load an image to draw on top of it. When the user taps "Save", the drawing is rendered to a
 * PNG file and handed back via [onConfirm] (so it can be attached to the email being composed).
 *
 * The drawing is done directly onto an Android [Bitmap] (mutated in place). A [version] counter
 * is bumped on every draw operation to force the [Image] to recompose and re-read the bitmap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit
) {
    val context = LocalContext.current
    // The drawing surface (mutated in place on every stroke).
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Bumped on every draw operation so the Image recomposes and re-reads the bitmap.
    var version by remember { mutableStateOf(0) }
    // The image the user loaded to draw on top of (null = plain white canvas).
    var baseImage by remember { mutableStateOf<Bitmap?>(null) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 8f
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { picked ->
            try {
                context.contentResolver.openInputStream(picked)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        baseImage = bmp
                        // Redraw the canvas with the new base image.
                        bitmap?.let { existing ->
                            val canvas = android.graphics.Canvas(existing)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            drawBaseImage(canvas, bmp, existing.width, existing.height)
                            version++
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore — the user can pick another image.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawing_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            bitmap?.let { bmp ->
                                saveBitmapToPng(context, bmp) { file ->
                                    onConfirm(file)
                                }
                            }
                        },
                        enabled = bitmap != null
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Toolbar: color swatches (scrollable) + load image / clear / undo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Color.Black,
                        Color(0xFFD32F2F),
                        Color(0xFF1976D2),
                        Color(0xFF388E3C),
                        Color(0xFFFF6D00),
                        Color(0xFFAA00FF),
                        Color.White
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (strokeColor == color) 3.dp else 1.dp,
                                    color = if (strokeColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { strokeColor = color }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.ImageIcon, contentDescription = stringResource(R.string.drawing_load_image))
                }
                IconButton(onClick = {
                    bitmap?.let { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        baseImage?.let { drawBaseImage(canvas, it, bmp.width, bmp.height) }
                        version++
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.drawing_clear))
                }
            }
            // The drawing canvas.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    // Create the bitmap when the canvas size is known.
                    .onSizeChanged { intSize ->
                        if (intSize.width > 0 && intSize.height > 0) {
                            val bmp = Bitmap.createBitmap(intSize.width, intSize.height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            baseImage?.let { drawBaseImage(canvas, it, intSize.width, intSize.height) }
                            bitmap = bmp
                            version++
                        }
                    }
                    // The pointer input lives on the (stable) Box, not on the Image, so the
                    // in-progress drag is not interrupted when the Image recomposes.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            var isDrawing = false
                            var lastPos: Offset? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val bmp = bitmap ?: continue
                                val canvas = android.graphics.Canvas(bmp)
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        isDrawing = true
                                        val pos = change.position
                                        paint.color = strokeColor.toArgb()
                                        canvas.drawCircle(pos.x, pos.y, paint.strokeWidth / 2, paint)
                                        lastPos = pos
                                        version++
                                        change.consume()
                                    }
                                    PointerEventType.Move -> {
                                        if (isDrawing) {
                                            val pos = change.position
                                            val last = lastPos
                                            if (last != null) {
                                                canvas.drawLine(last.x, last.y, pos.x, pos.y, paint)
                                            }
                                            lastPos = pos
                                            version++
                                            change.consume()
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        isDrawing = false
                                        lastPos = null
                                        change.consume()
                                    }
                                    else -> { /* ignore */ }
                                }
                            }
                        }
                    }
            ) {
                // The drawing surface. key(version) forces the Image to recompose and re-read
                // the (mutated) bitmap on every draw operation.
                key(version) {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** Draws [img] onto [canvas], scaled to fit (centered) within [width]x[height]. */
private fun drawBaseImage(canvas: android.graphics.Canvas, img: Bitmap, width: Int, height: Int) {
    if (img.width <= 0 || img.height <= 0) return
    val scale = minOf(width.toFloat() / img.width, height.toFloat() / img.height)
    val drawWidth = (img.width * scale).toInt()
    val drawHeight = (img.height * scale).toInt()
    val left = (width - drawWidth) / 2
    val top = (height - drawHeight) / 2
    val src = Rect(0, 0, img.width, img.height)
    val dst = Rect(left, top, left + drawWidth, top + drawHeight)
    canvas.drawBitmap(img, src, dst, null)
}

/** Saves [bmp] as a PNG file in the cache directory and hands the file back via [onSaved]. */
private fun saveBitmapToPng(context: Context, bmp: Bitmap, onSaved: (File) -> Unit) {
    try {
        val file = File(context.cacheDir, "drawing_${System.currentTimeMillis()}.png")
        context.cacheDir.mkdirs()
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        onSaved(file)
    } catch (e: Exception) {
        // Ignore — the user can try again.
    }
}
