package com.mailchat.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenAiService {

    private var logContext: Context? = null

    fun setContext(context: Context) {
        logContext = context
    }

    private fun log(msg: String) {
        logContext?.let { AppLogger.log(it, msg) }
    }

    suspend fun chat(endpoint: String, apiKey: String, model: String, prompt: String, systemPrompt: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                val trimmedModel = model.trim()
                val trimmedEndpoint = endpoint.trim().trimEnd('/')
                val trimmedKey = apiKey.trim()
                val url = URL(trimmedEndpoint + "/chat/completions")
                log("Request: $url model=$trimmedModel")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 60000
                connection.readTimeout = 180000
                connection.setRequestProperty("Content-Type", "application/json")
                if (trimmedKey.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer $trimmedKey")
                }
                connection.doOutput = true

                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                messages.put(JSONObject().put("role", "user").put("content", prompt))

                val body = JSONObject()
                    .put("model", trimmedModel)
                    .put("messages", messages)
                    .put("max_tokens", 2048)
                    .put("temperature", 0.7)
                    .toString()

                connection.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = connection.responseCode
                log("Response code: $responseCode")
                if (responseCode != 200) {
                    val errorStream = if (responseCode >= 400) connection.errorStream else connection.inputStream
                    val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    log("ERROR: $errorMsg")
                    throw Exception(errorMsg.take(200))
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                log("Raw response (first 500): '${response.take(500)}'")

                val json = JSONObject(response)
                val choices = json.getJSONArray("choices")
                if (choices.length() == 0) {
                    log("WARNING: empty choices array")
                    return@withContext ""
                }
                val result = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                log("Result (first 80): '${result.take(80)}'")
                result
            } catch (e: Exception) {
                log("FAILED: ${e.message}")
                throw e
            }
        }
}
