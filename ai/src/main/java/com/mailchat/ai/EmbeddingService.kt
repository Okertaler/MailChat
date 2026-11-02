package com.mailchat.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EmbeddingService {

    private var engine: EmbeddingEngine? = null
    private var currentModelId: String = ""

    fun isAvailable(context: Context): Boolean =
        GeminiService.isModelDownloaded(context, GeminiService.getEmbeddingModel().id)

    private suspend fun ensureEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        val model = GeminiService.getEmbeddingModel()
        if (engine != null && currentModelId == model.id) return@withContext true
        try {
            engine?.close()
        } catch (_: Exception) {}
        engine = null
        val file = GeminiService.getModelFile(context, model.id)
        if (!file.exists() || file.length() == 0L) return@withContext false
        try {
            val config = EmbeddingEngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path
            )
            engine = EmbeddingEngine(config).also { it.initialize() }
            currentModelId = model.id
            true
        } catch (e: Exception) {
            AppLogger.log(context, "Embedding init failed: ${e.message}")
            engine = null
            false
        }
    }

    suspend fun embedBatch(context: Context, texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        if (!ensureEngine(context)) return@withContext emptyList()
        val eng = engine ?: return@withContext emptyList()
        try {
            val inputs = texts.map { listOf<com.google.ai.edge.litertlm.InputData>(InputData.Text(it.take(1200))) }
            eng.computeEmbeddingBatch(inputs).map { it.embedding }
        } catch (e: Exception) {
            AppLogger.log(context, "Embedding compute failed: ${e.message}")
            emptyList()
        }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    fun close() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        currentModelId = ""
    }
}
