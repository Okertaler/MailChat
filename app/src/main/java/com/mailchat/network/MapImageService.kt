package com.mailchat.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.pow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Generates a small OpenStreetMap map image for the "Wo bist Du?" auto-reply: a 3×3 grid of
 * OSM tiles (zoom 15, 768×768 px) with a red marker at the exact position. The tiles are
 * fetched from the public OSM tile server (a valid User-Agent is set, as required by the OSM
 * tile usage policy) and the result is saved as a PNG in the cache directory.
 *
 * Returns null if the map cannot be generated (network failure, all tiles missing, …) — the
 * caller must treat the map as optional and send the reply without it.
 */
@Singleton
class MapImageService @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val zoom = 15
    private val tileCount = 3
    private val tileSize = 256

    /**
     * Generates the map image for [lat]/[lon]. Runs on the IO dispatcher. Returns the PNG file
     * or null on failure.
     */
    suspend fun generateMapImage(lat: Double, lon: Double): File? = withContext(Dispatchers.IO) {
        try {
            val (centerX, centerY) = tileXY(lat, lon, zoom)
            val size = tileSize * tileCount
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Fetch all 9 tiles IN PARALLEL (9 concurrent requests) instead of sequentially —
            // the sequential version took up to ~72 s in the worst case (8 s timeout per tile),
            // which made the "Wo bist Du?" auto-reply feel broken. In parallel the worst case
            // is a single tile timeout (~8 s).
            val tiles = coroutineScope {
                (0 until tileCount * tileCount).map { i ->
                    val dx = i % tileCount
                    val dy = i / tileCount
                    async {
                        fetchTile(zoom, centerX + dx - (tileCount - 1) / 2, centerY + dy - (tileCount - 1) / 2)
                    }
                }.awaitAll()
            }
            val placeholder = Paint()
            placeholder.color = Color.rgb(229, 227, 223)
            var fetched = 0
            tiles.forEachIndexed { i, tile ->
                val dx = i % tileCount
                val dy = i / tileCount
                if (tile != null) {
                    canvas.drawBitmap(tile, (dx * tileSize).toFloat(), (dy * tileSize).toFloat(), null)
                    tile.recycle()
                    fetched++
                } else {
                    // OSM "no data" background color so a missing tile is visible but not jarring.
                    // (Only the missing tile's rect — drawColor() would overwrite the whole canvas.)
                    canvas.drawRect(
                        (dx * tileSize).toFloat(), (dy * tileSize).toFloat(),
                        ((dx + 1) * tileSize).toFloat(), ((dy + 1) * tileSize).toFloat(),
                        placeholder
                    )
                }
            }
            if (fetched == 0) {
                bitmap.recycle()
                com.mailchat.ai.AppLogger.log(appContext, "Map image: no tiles could be fetched")
                return@withContext null
            }
            // Marker at the exact position (not just the grid center): the location's world
            // pixel coordinates minus the grid's top-left corner.
            val (px, py) = worldPixelXY(lat, lon, zoom)
            val gridX = (centerX - (tileCount - 1) / 2) * tileSize.toDouble()
            val gridY = (centerY - (tileCount - 1) / 2) * tileSize.toDouble()
            val mx = (px - gridX).toFloat().coerceIn(0f, size.toFloat())
            val my = (py - gridY).toFloat().coerceIn(0f, size.toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(mx, my, 14f, paint)
            paint.color = Color.rgb(220, 40, 40)
            canvas.drawCircle(mx, my, 10f, paint)
            // Zoom in by 30%: crop the rendered grid to 1/1.3 of its size, centered on the
            // grid center. The marker is always inside the center tile (at most 128 px from
            // the grid center), so it stays fully visible in the cropped image.
            val cropSize = (size / 1.3).toInt()
            val cropOffset = (size - cropSize) / 2
            val cropped = Bitmap.createBitmap(bitmap, cropOffset, cropOffset, cropSize, cropSize)
            bitmap.recycle()
            val file = File(appContext.cacheDir, "map_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            cropped.recycle()
            com.mailchat.ai.AppLogger.log(appContext, "Map image generated: ${file.name} (${file.length()} bytes, $fetched/${tileCount * tileCount} tiles)")
            file
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(appContext, "Map image generation failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches a single OSM tile. Returns null on any failure (the caller draws a placeholder).
     */
    private fun fetchTile(z: Int, x: Int, y: Int): Bitmap? {
        return try {
            val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "MailChat/2.8 (personal use, auto-reply map; contact: florescu@web.de)"
            )
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return null
            }
            val bmp = BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Web-Mercator tile coordinates for [lat]/[lon] at zoom [z]. */
    private fun tileXY(lat: Double, lon: Double, z: Int): Pair<Int, Int> {
        val n = 2.0.pow(z)
        val x = Math.floor((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val y = Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return x to y
    }

    /** Web-Mercator world-pixel coordinates for [lat]/[lon] at zoom [z]. */
    private fun worldPixelXY(lat: Double, lon: Double, z: Int): Pair<Double, Double> {
        val worldSize = tileSize * 2.0.pow(z)
        val px = (lon + 180.0) / 360.0 * worldSize
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val py = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * worldSize
        return px to py
    }
}
