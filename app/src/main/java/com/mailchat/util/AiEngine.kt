package com.mailchat.util

import android.content.Context
import com.mailchat.ai.AppLogger
import com.mailchat.ai.GeminiService

object AiEngine {

    suspend fun configureAndInitialize(context: Context, contextTokens: Int = GeminiService.CONTEXT_TOKENS): Boolean {
        GeminiService.configureProvider(
            context,
            SettingsUtils.getAiProvider(context),
            SettingsUtils.getOpenAiEndpoint(context),
            SettingsUtils.getOpenAiApiKey(context),
            SettingsUtils.getOpenAiModel(context)
        )
        GeminiService.cleanupUnknownModels(context)
        var modelId = SettingsUtils.getAiModel(context)
        if (GeminiService.getModelById(modelId) == null) {
            // Gespeichertes Modell existiert nicht mehr im Enum – auf bekanntes zurueckfallen
            modelId = com.mailchat.ai.GeminiService.Model.GEMMA_4_E2B.id
        }
        // Wenn das ausgewaehlte Modell nicht heruntergeladen ist, nach irgendeinem vorhandenen suchen
        if (!GeminiService.isModelDownloaded(context, modelId)) {
            for (m in GeminiService.getAvailableModels()) {
                if (GeminiService.isModelDownloaded(context, m.id)) {
                    modelId = m.id
                    SettingsUtils.setAiModel(context, modelId)
                    AppLogger.log(context, "Found downloaded model: $modelId")
                    break
                }
            }
        }
        return GeminiService.initialize(context, modelId, contextTokens)
    }
}
