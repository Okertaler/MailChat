package com.mailchat.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Redo
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
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** The shape drawn by a long-press + drag gesture (a quick touch/drag is freehand). */
private enum class DrawShape { Line, Circle }

/**
 * A simple finger-drawing screen. The user can draw freehand on a white canvas and optionally
 * load an image to draw on top of it. When the user taps "Save", the drawing is rendered to a
 * PNG file and handed back via [onConfirm] (so it can be attached to the email being composed).
 *
 * A quick touch/drag draws freehand. A long-press (held still) starts a shape: dragging then
 * previews a straight line (from the press point) or a circle (centered at the press point,
 * radius = distance to the finger), which is committed when the finger lifts.
 *
 * The drawing is done directly onto an Android [Bitmap] (mutated in place). A [version] counter
 * is bumped on every draw operation to force the canvas to recompose and re-read the bitmap.
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
    // The display name of the loaded image (used for the saved file name).
    var baseImageName by remember { mutableStateOf<String?>(null) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    // Stroke width level: 1 = thin, 2 = medium, 3 = thick. Clicking the already-selected color
    // cycles through the three levels.
    var strokeWidthLevel by remember { mutableStateOf(1) }
    // The shape drawn by a long-press + drag gesture.
    var selectedShape by remember { mutableStateOf(DrawShape.Line) }
    // The in-progress shape preview (set while a long-press drag is active).
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeCurrent by remember { mutableStateOf<Offset?>(null) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 8f
        }
    }

    fun strokeWidthForLevel(level: Int): Float = when (level) {
        1 -> 8f
        2 -> 16f
        else -> 28f
    }
    // Undo/redo stacks: each entry is a full copy of the bitmap taken BEFORE a stroke (or clear)
    // started. Bounded to keep memory usage reasonable.
    val undoStack = remember { ArrayDeque<Bitmap>() }
    val redoStack = remember { ArrayDeque<Bitmap>() }
    val maxUndoSteps = 15
    // Explicit state for the top-bar buttons: the stacks themselves are not observable, so the
    // TopAppBar would not recompose (and the buttons would stay disabled) when they change.
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    fun pushUndoState() {
        bitmap?.let { current ->
            undoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, false))
            if (undoStack.size > maxUndoSteps) undoStack.removeFirst()
            redoStack.clear()
            canUndo = true
            canRedo = false
        }
    }

    fun undo() {
        val bmp = bitmap ?: return
        if (undoStack.isEmpty()) return
        redoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
        bitmap = undoStack.removeLast()
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        version++
    }

    fun redo() {
        val bmp = bitmap ?: return
        if (redoStack.isEmpty()) return
        undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
        bitmap = redoStack.removeLast()
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        version++
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { picked ->
            try {
                // Capture the display name (used for the saved file name).
                var name: String? = null
                context.contentResolver.query(picked, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
                baseImageName = name
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
                    IconButton(onClick = { undo() }, enabled = canUndo) {
                        Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.drawing_undo))
                    }
                    IconButton(onClick = { redo() }, enabled = canRedo) {
                        Icon(Icons.Default.Redo, contentDescription = stringResource(R.string.drawing_redo))
                    }
                    TextButton(
                        onClick = {
                            bitmap?.let { bmp ->
                                saveBitmapToPng(context, bmp, baseImageName) { file ->
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
                        val isSelected = strokeColor == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (isSelected) {
                                        // Same color again -> cycle the stroke width (1 -> 2 -> 3 -> 1).
                                        strokeWidthLevel = (strokeWidthLevel % 3) + 1
                                    } else {
                                        strokeColor = color
                                        strokeWidthLevel = 1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Show the current stroke-width level (1-3) in the selected swatch.
                            if (isSelected) {
                                Text(
                                    text = strokeWidthLevel.toString(),
                                    color = if (color == Color.White) Color.Black else Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Shape selector for the long-press gesture (line / circle).
                IconButton(
                    onClick = { selectedShape = DrawShape.Line },
                    modifier = Modifier.background(
                        if (selectedShape == DrawShape.Line) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Default.HorizontalRule,
                        contentDescription = stringResource(R.string.drawing_shape_line),
                        tint = if (selectedShape == DrawShape.Line) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { selectedShape = DrawShape.Circle },
                    modifier = Modifier.background(
                        if (selectedShape == DrawShape.Circle) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = stringResource(R.string.drawing_shape_circle),
                        tint = if (selectedShape == DrawShape.Circle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.ImageIcon, contentDescription = stringResource(R.string.drawing_load_image))
                }
                IconButton(onClick = {
                    bitmap?.let { bmp ->
                        // Save the current state so the clear can be undone.
                        pushUndoState()
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
                    // in-progress drag is not interrupted when the canvas recomposes.
                    // A quick touch/drag draws freehand; holding still (long-press) starts a
                    // shape (line/circle) that is previewed while dragging and committed on release.
                    .pointerInput(Unit) {
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        awaitPointerEventScope {
                            var isDown = false
                            var mode = "idle" // "idle" | "freehand" | "shape"
                            var startPos: Offset? = null
                            var lastPos: Offset? = null
                            var longPressRunnable: Runnable? = null
                            val slop = 20f
                            val longPressMs = 400L
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val bmp = bitmap ?: continue
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        isDown = true
                                        mode = "idle"
                                        startPos = change.position
                                        lastPos = change.position
                                        shapeStart = change.position
                                        shapeCurrent = change.position
                                        // If the finger is held still for longPressMs, switch to shape mode.
                                        longPressRunnable = Runnable {
                                            if (isDown && mode == "idle") {
                                                pushUndoState()
                                                mode = "shape"
                                                version++
                                            }
                                        }
                                        handler.postDelayed(longPressRunnable, longPressMs)
                                        change.consume()
                                    }
                                    PointerEventType.Move -> {
                                        if (!isDown) continue
                                        val pos = change.position
                                        val start = startPos
                                        when (mode) {
                                            "idle" -> {
                                                // Moved beyond the slop before the long-press fired -> freehand.
                                                if (start != null && dist(start, pos) > slop) {
                                                    longPressRunnable?.let { handler.removeCallbacks(it) }
                                                    pushUndoState()
                                                    mode = "freehand"
                                                    val canvas = android.graphics.Canvas(bmp)
                                                    paint.color = strokeColor.toArgb()
                                                    paint.strokeWidth = strokeWidthForLevel(strokeWidthLevel)
                                                    canvas.drawLine(start.x, start.y, pos.x, pos.y, paint)
                                                    lastPos = pos
                                                    version++
                                                }
                                            }
                                            "freehand" -> {
                                                val canvas = android.graphics.Canvas(bmp)
                                                val last = lastPos
                                                if (last != null) {
                                                    paint.color = strokeColor.toArgb()
                                                    paint.strokeWidth = strokeWidthForLevel(strokeWidthLevel)
                                                    canvas.drawLine(last.x, last.y, pos.x, pos.y, paint)
                                                }
                                                lastPos = pos
                                                version++
                                            }
                                            "shape" -> {
                                                // Update the live preview (committed on release).
                                                shapeCurrent = pos
                                                version++
                                            }
                                        }
                                        change.consume()
                                    }
                                    PointerEventType.Release -> {
                                        longPressRunnable?.let { handler.removeCallbacks(it) }
                                        when (mode) {
                                            "shape" -> {
                                                val canvas = android.graphics.Canvas(bmp)
                                                paint.color = strokeColor.toArgb()
                                                paint.strokeWidth = strokeWidthForLevel(strokeWidthLevel)
                                                val s = shapeStart
                                                val c = shapeCurrent
                                                if (s != null && c != null) {
                                                    when (selectedShape) {
                                                        DrawShape.Line -> canvas.drawLine(s.x, s.y, c.x, c.y, paint)
                                                        DrawShape.Circle -> canvas.drawCircle(s.x, s.y, dist(s, c), paint)
                                                    }
                                                }
                                                version++
                                            }
                                            "idle" -> {
                                                // Quick tap -> draw a dot.
                                                val canvas = android.graphics.Canvas(bmp)
                                                paint.color = strokeColor.toArgb()
                                                paint.strokeWidth = strokeWidthForLevel(strokeWidthLevel)
                                                startPos?.let { p ->
                                                    canvas.drawCircle(p.x, p.y, paint.strokeWidth / 2, paint)
                                                }
                                                version++
                                            }
                                            // "freehand": already drawn incrementally, nothing to commit.
                                        }
                                        isDown = false
                                        mode = "idle"
                                        lastPos = null
                                        shapeStart = null
                                        shapeCurrent = null
                                        change.consume()
                                    }
                                    else -> { /* ignore */ }
                                }
                            }
                        }
                    }
            ) {
                // The drawing surface. key(version) forces the Canvas to recompose and re-draw
                // the (mutated/replaced) bitmap on every draw operation, including undo/redo.
                // A Canvas re-draws on every recomposition, which is more reliable than an
                // Image with an ImageBitmap (which may cache the rendered pixels).
                key(version) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        bitmap?.let { bmp ->
                            // The bitmap is created at the canvas size (onSizeChanged), so drawing
                            // it at its natural size fills the canvas 1:1.
                            drawImage(image = bmp.asImageBitmap())
                        }
                        // Live preview of the in-progress shape (long-press + drag).
                        val s = shapeStart
                        val c = shapeCurrent
                        if (s != null && c != null) {
                            val color = strokeColor
                            val width = strokeWidthForLevel(strokeWidthLevel)
                            when (selectedShape) {
                                DrawShape.Line -> drawLine(color, s, c, width)
                                DrawShape.Circle -> drawCircle(
                                    color = color,
                                    radius = dist(s, c),
                                    center = s,
                                    style = Stroke(width)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Euclidean distance between two points. */
private fun dist(a: Offset, b: Offset): Float =
    kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

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

/**
 * Builds the saved file name: without a base image it is "Drawing" + date/time
 * (e.g. "Drawing_2026-11-24_11-30.png"); with a base image it is "Drawing_" + the image's base
 * name (e.g. "Drawing_photo.png").
 */
private fun drawingFileName(baseImageName: String?): String {
    return if (!baseImageName.isNullOrBlank()) {
        // Strip the extension and sanitize, then append .png.
        val base = baseImageName.substringBeforeLast('.')
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "image" }
        "Drawing_$safe.png"
    } else {
        val dt = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
            .format(java.util.Date())
        "Drawing_$dt.png"
    }
}

/** Saves [bmp] as a PNG file in the cache directory and hands the file back via [onSaved]. */
private fun saveBitmapToPng(context: Context, bmp: Bitmap, baseImageName: String?, onSaved: (File) -> Unit) {
    try {
        context.cacheDir.mkdirs()
        val file = File(context.cacheDir, drawingFileName(baseImageName))
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        onSaved(file)
    } catch (e: Exception) {
        // Ignore — the user can try again.
    }
}
