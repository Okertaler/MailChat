package com.mailchat.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailchat.data.model.Message
import com.mailchat.data.repository.MessageRepository
import com.mailchat.util.AssistantQuery
import com.mailchat.util.AiEngine
import com.mailchat.util.SettingsUtils
import com.mailchat.util.normalizeWhitespace
import com.mailchat.util.stripHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantTurn(
    val isUser: Boolean,
    val text: String,
    val sources: List<Message> = emptyList()
)

data class AssistantUiState(
    val turns: List<AssistantTurn> = emptyList(),
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun ask(question: String) {
        val q = question.trim()
        if (q.isBlank() || _uiState.value.isProcessing) return
        _uiState.update { it.copy(turns = it.turns + AssistantTurn(isUser = true, text = q), isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                val since = AssistantQuery.sinceTimestamp(q)
                val keywords = AssistantQuery.extractKeywords(q)
                val retrieved = mutableListOf<Message>()
                val seen = mutableSetOf<Long>()
                // Meta-Fragen (Spam/ToDo-Markierungen) zuerst: passende Messages holen - ohne Zeitlimit
                val intents = com.mailchat.util.AssistantQuery.detectIntents(q)
                if (intents.isNotEmpty()) {
                    messageRepository.getFlaggedMessages(0L, 60).forEach { m ->
                        val relevant = (intents.contains("spam") && m.isSpam) || (intents.contains("todo") && m.isTodo)
                        if (relevant && seen.add(m.id)) retrieved.add(m)
                    }
                }
                for (kw in keywords) {
                    messageRepository.searchAll(kw, since, 8).forEach { m ->
                        if (seen.add(m.id)) retrieved.add(m)
                    }
                }
                var semanticUsed = false
                val topRaw = if (intents.isEmpty() && com.mailchat.ai.EmbeddingService.isAvailable(appContext)) {
                    val ranked = semanticRank(q, retrieved, since)
                    if (ranked != null) {
                        semanticUsed = true
                        ranked
                    } else {
                        if (retrieved.isEmpty()) {
                            messageRepository.searchAll("", System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000, 15).forEach { m ->
                                if (seen.add(m.id)) retrieved.add(m)
                            }
                        }
                        retrieved.sortedByDescending { it.timestamp }.take(40)
                    }
                } else if (intents.isNotEmpty()) {
                    retrieved.sortedWith(
                        compareByDescending<Message> { m ->
                            (intents.contains("spam") && m.isSpam) || (intents.contains("todo") && m.isTodo)
                        }.thenByDescending { it.timestamp }
                    ).take(40)
                } else {
                    retrieved.sortedByDescending { it.timestamp }.take(40)
                }
                // Relevanz-Gate: LIKE-Treffer auf Fuellwoerter sind Rauschen - ohne echten Keyword-Treffer wie "kein Kontext" behandeln
                val top = if (intents.isEmpty() && !semanticUsed) {
                    val strongKw = keywords.filter { it.length >= 5 }
                    if (strongKw.isEmpty()) emptyList() else topRaw.filter { m ->
                        val hay = "${m.subject} ${m.from} ${m.bodyFull.take(2000)}".lowercase()
                        strongKw.any { k -> hay.contains(k) }
                    }
                } else topRaw
                // Keine relevanten Datenbanktreffer: trotzdem antworten, dann aus eigenem Wissen des Modells
                val hasContext = top.isNotEmpty()

                val modelId = SettingsUtils.getAiModel(appContext)
                val ctxTokens = com.mailchat.ai.GeminiService.getAssistantContextTokens(modelId)
                val promptCap = com.mailchat.ai.GeminiService.assistantPromptCapFor(modelId)

                val dateFmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                // Vorgerechnete, eindeutige Liste fuer Meta-Fragen (kleine Modelle brauchen das)
                val summaryBlock = if (intents.isNotEmpty()) {
                    buildString {
                        if (intents.contains("spam")) {
                            val spamMsgs = retrieved.filter { it.isSpam }
                            append(if (spamMsgs.isEmpty()) "MARKIERTE SPAM-EMAILS: keine\n" else {
                                append("MARKIERTE SPAM-EMAILS (${spamMsgs.size} Stueck):\n")
                                spamMsgs.groupBy { it.from }.forEach { (sender, msgs) ->
                                    val cats = msgs.map { it.spamCategory.ifBlank { "spam" } }.distinct().joinToString(", ")
                                    append("- $sender (${msgs.size}x, Kategorie: $cats), zuletzt ${dateFmt.format(java.util.Date(msgs.maxOf { it.timestamp }))}\n")
                                }
                            })
                        }
                        if (intents.contains("todo")) {
                            val todoMsgs = retrieved.filter { it.isTodo }
                            append(if (todoMsgs.isEmpty()) "TODO-MARKIERUNGEN: keine\n" else {
                                append("TODO-MARKIERUNGEN (${todoMsgs.size} Stueck):\n")
                                todoMsgs.forEach { append("- ${it.from}: ${it.subject.ifBlank { "(ohne Betreff)" }} (${dateFmt.format(java.util.Date(it.timestamp))})\n") }
                            })
                        }
                    }
                } else ""

                val contextBlock = buildString {
                    if (summaryBlock.isNotBlank()) append(summaryBlock).append('\n')
                    var budget = promptCap - 8000
                    for (m in top) {
                        val snippet = m.bodyFull.stripHtml().normalizeWhitespace().take(800)
                        val flags = buildString {
                            if (m.isSpam) append(" | FLAG: ALS SPAM MARKIERT (${m.spamCategory.ifBlank { "spam" }})")
                            if (m.isTodo) append(" | FLAG: TODO")
                        }
                        val line = """[${m.id}] ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.timestamp))} | Von: ${m.from} | Betreff: ${m.subject}${flags}\n$snippet\n"""
                        if (budget - line.length < 0) break
                        budget -= line.length
                        append(line)
                    }
                }
                val historyBlock = buildString {
                    var budget = 3000
                    for (t in _uiState.value.turns.takeLast(4)) {
                        val entry = if (t.isUser) "Frage: ${t.text}" else "Antwort: ${t.text}"
                        if (budget - entry.length < 0) break
                        budget -= entry.length
                        append(entry).append('\n')
                    }
                }

                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    _uiState.update { it.copy(statusMessage = appContext.getString(com.mailchat.R.string.assistant_loading_model)) }
                }
                // RAM freigeben: Embedding-Engine nicht parallel zur Chat-Engine halten
                com.mailchat.ai.EmbeddingService.close()
                val engineOk = AiEngine.configureAndInitialize(appContext, ctxTokens)
                _uiState.update { it.copy(statusMessage = null) }
                if (!engineOk) {
                    _uiState.update {
                        it.copy(
                            turns = it.turns + AssistantTurn(isUser = false, text = appContext.getString(com.mailchat.R.string.assistant_model_missing)),
                            isProcessing = false
                        )
                    }
                    return@launch
                }
                val answer = com.mailchat.ai.GeminiService.askWithContext(q, contextBlock, historyBlock, hasContext, promptCap)
                _uiState.update {
                    it.copy(
                        turns = it.turns + AssistantTurn(isUser = false, text = answer.ifBlank { "Keine Antwort erhalten." }, sources = if (hasContext) top.take(10) else emptyList()),
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MailchatAssistant", "ask failed: ${e.message}")
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "KI-Fehler") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        com.mailchat.ai.EmbeddingService.close()
    }

    /** Semantische Suche: Einbettungen cached, Frage gegen Kandidaten ranken. */
    private suspend fun semanticRank(question: String, keywordHits: List<Message>, since: Long): List<Message>? {
        try {
            val pool = (keywordHits + messageRepository.searchAll("", since, 100))
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
                .take(150)
            if (pool.isEmpty()) return null

            val cached = messageRepository.getEmbeddingsByMessageIds(pool.map { it.id }).associateBy { it.messageId }
            val missing = pool.filter { it.id !in cached }
            if (missing.isNotEmpty()) {
                val texts = missing.map { "${it.subject}\n${it.bodyFull.stripHtml().normalizeWhitespace()}" }
                val vectors = com.mailchat.ai.EmbeddingService.embedBatch(appContext, texts)
                if (vectors.size != missing.size || vectors.isEmpty()) return null
                vectors.forEachIndexed { i, vec ->
                    messageRepository.upsertEmbedding(
                        com.mailchat.data.model.MessageEmbedding(missing[i].id, toBytes(vec))
                    )
                }
            }

            val qVec = com.mailchat.ai.EmbeddingService.embedBatch(appContext, listOf(question)).firstOrNull() ?: return null
            val fresh = messageRepository.getEmbeddingsByMessageIds(pool.map { it.id }).associateBy { it.messageId }
            return pool.mapNotNull { m ->
                val emb = fresh[m.id] ?: return@mapNotNull null
                m to com.mailchat.ai.EmbeddingService.cosine(qVec, fromBytes(emb.embedding))
            }
                .filter { it.second > 0.2f }
                .sortedByDescending { it.second }
                .take(30)
                .map { it.first }
                .ifEmpty { null }
        } catch (e: Exception) {
            android.util.Log.w("MailchatAssistant", "semantic rank failed", e)
            return null
        }
    }

    private fun toBytes(vec: FloatArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(vec.size * 4)
        vec.forEach { buf.putFloat(it) }
        return buf.array()
    }

    private fun fromBytes(bytes: ByteArray): FloatArray {
        val buf = java.nio.ByteBuffer.wrap(bytes)
        return FloatArray(bytes.size / 4) { buf.float }
    }
}
