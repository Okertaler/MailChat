package com.mailchat.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
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
 * The strokes live on a transparent [drawingLayer] bitmap, separate from the background: an
 * image loaded (even after drawing) is rendered BEHIND the existing strokes. A [version]
 * counter is bumped on every draw operation to force the canvas to recompose and re-read the
 * bitmaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit
) {
    val context = LocalContext.current
    // The drawing layer: only the user's strokes on a transparent background (mutated in place
    // on every stroke). Kept separate from the background so an image loaded later appears
    // BEHIND the existing drawing.
    var drawingLayer by remember { mutableStateOf<Bitmap?>(null) }
    // Bumped on every draw operation so the canvas recomposes and re-reads the bitmaps.
    var version by remember { mutableStateOf(0) }
    // The loaded image at its (sampled) natural size — the source for the background.
    var baseImageSource by remember { mutableStateOf<Bitmap?>(null) }
    // The pre-rendered background (canvas size: white + image scaled to fit, centered).
    var baseImage by remember { mutableStateOf<Bitmap?>(null) }
    // The display name of the loaded image (used for the saved file name).
    var baseImageName by remember { mutableStateOf<String?>(null) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    // Stroke width level: 1 = thin, 2 = medium, 3 = thick. Clicking the already-selected color
    // cycles through the three levels.
    var strokeWidthLevel by remember { mutableStateOf(1) }
    // The shape drawn by a long-press + drag gesture.
    var selectedShape by remember { mutableStateOf(DrawShape.Line) }
    // Whether the help dialog is shown.
    var showHelp by remember { mutableStateOf(false) }
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

    // Re-renders the background (white + the loaded image scaled to fit, centered) at the
    // current canvas size. Called when an image is loaded and when the canvas is (re)created.
    fun renderBaseImage() {
        val source = baseImageSource ?: return
        val layer = drawingLayer ?: return
        try {
            val bg = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bg)
            canvas.drawColor(android.graphics.Color.WHITE)
            drawBaseImage(canvas, source, layer.width, layer.height)
            baseImage = bg
        } catch (e: OutOfMemoryError) {
            baseImage = null
        }
    }

    // Rotates the loaded background image 90° clockwise. The strokes on the drawing layer
    // are left untouched (they stay where the user drew them). Only available with an image.
    fun rotateBaseImage90() {
        val source = baseImageSource ?: return
        try {
            val rotated = Bitmap.createBitmap(source.height, source.width, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply {
                postRotate(90f)
                postTranslate(source.height.toFloat(), 0f)
            }
            val canvas = android.graphics.Canvas(rotated)
            canvas.drawBitmap(source, matrix, null)
            source.recycle()
            baseImageSource = rotated
            renderBaseImage()
            version++
        } catch (e: OutOfMemoryError) {
            // Out of memory: keep the old (unrotated) image.
        }
    }

    // Undo/redo stacks: each entry is a full copy of the drawing layer taken BEFORE a stroke
    // (or clear) started. Bounded to keep memory usage reasonable.
    val undoStack = remember { ArrayDeque<Bitmap>() }
    val redoStack = remember { ArrayDeque<Bitmap>() }
    // Each entry is a full canvas copy (~10 MB on a phone, ~16 MB on a tablet), so keep the
    // stack small to avoid running out of memory during long drawing sessions.
    val maxUndoSteps = 10
    // Explicit state for the top-bar buttons: the stacks themselves are not observable, so the
    // TopAppBar would not recompose (and the buttons would stay disabled) when they change.
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    fun pushUndoState() {
        drawingLayer?.let { current ->
            try {
                // isMutable = true: the restored bitmap must be drawable onto again.
                undoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
            } catch (e: OutOfMemoryError) {
                // Out of memory: skip this undo step instead of crashing.
                return
            }
            if (undoStack.size > maxUndoSteps) undoStack.removeFirst()
            redoStack.clear()
            canUndo = true
            canRedo = false
        }
    }

    fun undo() {
        val bmp = drawingLayer ?: return
        if (undoStack.isEmpty()) return
        try {
            redoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, true))
        } catch (e: OutOfMemoryError) {
            // Out of memory: the undo still works, only the redo state is lost.
        }
        drawingLayer = undoStack.removeLast()
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        version++
    }

    fun redo() {
        val bmp = drawingLayer ?: return
        if (redoStack.isEmpty()) return
        try {
            undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, true))
        } catch (e: OutOfMemoryError) {
            // Out of memory: the redo still works, only the undo state is lost.
        }
        drawingLayer = redoStack.removeLast()
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
                // First pass: read the dimensions without decoding the pixels.
                var outWidth = 0
                var outHeight = 0
                context.contentResolver.openInputStream(picked)?.use { input ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, bounds)
                    outWidth = bounds.outWidth
                    outHeight = bounds.outHeight
                }
                // Second pass: decode with a sample size so the bitmap stays small enough
                // (a full-resolution photo can be 50-200 MB and crash the app). The image is
                // displayed scaled to fit the canvas, so the canvas size is the target.
                val targetMax = drawingLayer?.let { maxOf(it.width, it.height) } ?: 2048
                var sample = 1
                val maxDim = maxOf(outWidth, outHeight)
                while (maxDim > 0 && maxDim / sample > targetMax && sample < 32) sample *= 2
                context.contentResolver.openInputStream(picked)?.use { input ->
                    val opts = BitmapFactory.Options().apply { if (sample > 1) inSampleSize = sample }
                    val bmp = BitmapFactory.decodeStream(input, null, opts)
                    if (bmp != null) {
                        // Store the image as the background source and re-render the background.
                        // The drawing layer is left untouched, so the image appears BEHIND any
                        // existing strokes (even if the user drew before loading the image).
                        baseImageSource = bmp
                        renderBaseImage()
                        version++
                    }
                }
            } catch (e: Throwable) {
                // Ignore (including OutOfMemoryError) — the user can pick another image.
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
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = stringResource(R.string.drawing_help))
                    }
                    IconButton(onClick = { undo() }, enabled = canUndo) {
                        Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.drawing_undo))
                    }
                    IconButton(onClick = { redo() }, enabled = canRedo) {
                        Icon(Icons.Default.Redo, contentDescription = stringResource(R.string.drawing_redo))
                    }
                    IconButton(
                        onClick = {
                            drawingLayer?.let { layer ->
                                saveBitmapToPng(context, layer, baseImage, baseImageName) { file ->
                                    onConfirm(file)
                                }
                            }
                        },
                        enabled = drawingLayer != null
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                    IconButton(onClick = { rotateBaseImage90() }, enabled = baseImageSource != null) {
                        Icon(Icons.Filled.RotateRight, contentDescription = stringResource(R.string.drawing_rotate))
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
                // Shape toggle (line/circle) for the long-press gesture, right next to the
                // load-image button. Shows the currently selected shape; tapping switches it.
                IconButton(
                    onClick = {
                        selectedShape = if (selectedShape == DrawShape.Line) DrawShape.Circle else DrawShape.Line
                    }
                ) {
                    Icon(
                        if (selectedShape == DrawShape.Line) Icons.Default.HorizontalRule else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (selectedShape == DrawShape.Line) R.string.drawing_shape_line else R.string.drawing_shape_circle
                        )
                    )
                }
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.ImageIcon, contentDescription = stringResource(R.string.drawing_load_image))
                }
                IconButton(onClick = {
                    drawingLayer?.let { layer ->
                        // Save the current state so the clear can be undone. Only the strokes
                        // are cleared; the background image (if any) stays in place.
                        pushUndoState()
                        val canvas = android.graphics.Canvas(layer)
                        canvas.drawColor(android.graphics.Color.TRANSPARENT)
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
                    // Create the (transparent) drawing layer when the canvas size is known.
                    .onSizeChanged { intSize ->
                        if (intSize.width > 0 && intSize.height > 0) {
                            try {
                                val layer = Bitmap.createBitmap(intSize.width, intSize.height, Bitmap.Config.ARGB_8888)
                                drawingLayer = layer
                                renderBaseImage()
                                version++
                            } catch (e: OutOfMemoryError) {
                                // Canvas too large for the available memory — keep the previous layer.
                            }
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
                                val bmp = drawingLayer ?: continue
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
                        // Background first (white + the loaded image, pre-rendered at canvas
                        // size), then the drawing layer (strokes only) on top. Both bitmaps are
                        // created at the canvas size (onSizeChanged), so they fill it 1:1.
                        baseImage?.let { bmp ->
                            drawImage(image = bmp.asImageBitmap())
                        }
                        drawingLayer?.let { bmp ->
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

    // Help dialog (opened via the "?" icon in the top bar).
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.drawing_help)) },
            text = {
                Text(
                    text = stringResource(R.string.drawing_help_text),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
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

/**
 * Composites the background (the loaded image or plain white) with the drawing layer and saves
 * the result as a PNG file in the cache directory, handing the file back via [onSaved].
 */
private fun saveBitmapToPng(
    context: Context,
    drawingLayer: Bitmap,
    baseImage: Bitmap?,
    baseImageName: String?,
    onSaved: (File) -> Unit
) {
    try {
        context.cacheDir.mkdirs()
        val file = File(context.cacheDir, drawingFileName(baseImageName))
        // Start from the background (canvas size) or a plain white bitmap.
        val out = if (baseImage != null) {
            baseImage.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createBitmap(drawingLayer.width, drawingLayer.height, Bitmap.Config.ARGB_8888).also {
                android.graphics.Canvas(it).drawColor(android.graphics.Color.WHITE)
            }
        }
        android.graphics.Canvas(out).drawBitmap(drawingLayer, 0f, 0f, null)
        FileOutputStream(file).use { outStream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        }
        onSaved(file)
    } catch (e: Throwable) {
        // Ignore (including OutOfMemoryError) — the user can try again.
    }
}
