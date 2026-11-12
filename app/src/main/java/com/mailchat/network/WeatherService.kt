package com.mailchat.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches weather data from open-meteo.com (https://open-meteo.com/en/docs). No API key required.
 *
 * Used by the "Wo bist Du?" auto-reply to include the local weather for the next 4 hours.
 */
@Singleton
class WeatherService @Inject constructor() {

    /**
     * Fetches the weather for the next 4 hours at the given coordinates and returns a
     * human-readable text summary (one line per hour). Returns null if the request fails or
     * no data is available. Must be called off the main thread (runs on [Dispatchers.IO]).
     */
    suspend fun getWeatherForNext4Hours(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&hourly=temperature_2m,weathercode,precipitation&forecast_hours=4&timezone=auto"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mailchat/1.0")
                val code = conn.responseCode
                if (code != 200) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val hourly = json.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weathercode")
                val precip = hourly.optJSONArray("precipitation")

                val sb = StringBuilder()
                for (i in 0 until times.length()) {
                    val time = times.getString(i) // e.g. "2026-10-28T21:00"
                    val hour = if (time.length >= 16) time.substring(11, 16) else time
                    val temp = temps.getDouble(i).toInt()
                    val codeVal = codes.getInt(i)
                    val precipVal = if (precip != null && i < precip.length()) precip.getDouble(i) else 0.0
                    val desc = weatherCodeDescription(codeVal)
                    // Clean, aligned line: "21:00  15°C  klar" (+ precipitation when present).
                    sb.append("$hour  $temp°C  $desc")
                    if (precipVal > 0.0) sb.append("  (Regen: ${"%.0f".format(precipVal)} mm)")
                    if (i < times.length() - 1) sb.append("\n")
                }
                sb.toString().ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }

    /** Maps a WMO weather code (open-meteo) to a short German description. */
    private fun weatherCodeDescription(code: Int): String = when (code) {
        0 -> "klar"
        1 -> "überwiegend klar"
        2 -> "teilweise bewölkt"
        3 -> "bedeckt"
        45, 48 -> "Nebel"
        51, 53, 55 -> "Nieselregen"
        56, 57 -> "gefrierender Nieselregen"
        61 -> "leichter Regen"
        63 -> "mittlerer Regen"
        65 -> "starker Regen"
        66, 67 -> "gefrierender Regen"
        71 -> "leichter Schneefall"
        73 -> "mittlerer Schneefall"
        75 -> "starker Schneefall"
        77 -> "Schneegriesel"
        80, 81, 82 -> "Regenschauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> "Wetterlage $code"
    }
}
