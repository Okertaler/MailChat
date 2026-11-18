package com.mailchat.util

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds and parses the plain-text poll (Umfrage) format used in email bodies.
 *
 * The format is deliberately plain text so it also renders and can be answered in regular email
 * programs. MailChat additionally detects polls/answers via the machine markers:
 *
 *  - Poll:    `[[poll:<id>:<m|s>]]`   (m = multiple answers, s = single answer)
 *  - Answer:  `[[poll-answer:<id>:<indices>]]`  (indices = comma-separated 0-based option indices)
 *
 * A poll body looks like:
 * ```
 * 📊 <title>
 * ❓ <question>
 * 🔘 <choice label>
 *
 * [ ] <option 1>
 * [ ] <option 2>
 *
 * ━━━━━━━━━━━━━━━
 * 💡 <instruction>
 * [[poll:<id>:<m|s>]]
 * ```
 */
object PollFormat {

    private val POLL_MARKER = Regex("\\[\\[poll:([A-Za-z0-9_-]+):([ms])\\]\\]")
    private val ANSWER_MARKER = Regex("\\[\\[poll-answer:([A-Za-z0-9_-]+):([0-9,]*)\\]\\]")
    // Result email (sent when a poll closes): marks the email as part of the poll flow so it
    // gets no AI tagging/spam detection, but it is rendered as a regular email (ASCII bars).
    private val RESULT_MARKER = Regex("\\[\\[poll-result:([A-Za-z0-9_-]+)\\]\\]")
    // A checkbox line: "[ ] text", "[x] text" or "[ x] text" (also matches quoted "> [x] text").
    // The box may contain a space and/or an x — users often write "[ x]" when answering in a
    // regular email program (keeping the original space and adding the x).
    private val CHECKBOX_LINE = Regex("^\\s*(?:>\\s*)*\\[([ xX]*)\\]\\s*(.+)$")

    data class PollData(
        val pollId: String,
        val multi: Boolean,
        val title: String,
        val question: String,
        val options: List<String>
    )

    data class AnswerData(
        val pollId: String,
        val optionIndices: List<Int>
    )

    /** Generates a short, URL-safe poll ID. */
    fun newPollId(): String =
        (1..8).map { ('a' + kotlin.random.Random.nextInt(26)).toString() }.joinToString("") +
            System.currentTimeMillis().toString(36).takeLast(4)

    // --- Building ---

    fun buildPollBody(
        title: String,
        question: String,
        options: List<String>,
        multi: Boolean,
        pollId: String,
        choiceLabel: String,
        instruction: String
    ): String = buildString {
        append("📊 ").append(title).append('\n')
        if (question.isNotBlank()) append("❓ ").append(question).append('\n')
        append("🔘 ").append(choiceLabel).append("\n\n")
        for (opt in options) {
            append("[ ] ").append(opt).append('\n')
        }
        append("\n━━━━━━━━━━━━━━━\n")
        append(instruction).append('\n')
        append("[[poll:").append(pollId).append(':').append(if (multi) 'm' else 's').append("]]")
    }

    fun buildAnswerBody(options: List<String>, selected: List<Int>, pollId: String): String =
        buildString {
            for (i in options.indices) {
                val mark = if (i in selected) "X" else " "
                append("[").append(mark).append("] ").append(options[i]).append('\n')
            }
            append('\n')
            append("[[poll-answer:").append(pollId).append(':')
                .append(selected.joinToString(",")).append("]]")
        }

    /**
     * Builds the ASCII bar-chart result email body.
     * @param counts vote count per option (same order as [options]).
     * @param totalVotes total number of votes cast (sum of selected options across all voters).
     */
    fun buildAsciiResult(
        title: String,
        options: List<String>,
        counts: List<Int>,
        totalVotes: Int,
        resultLabel: String,
        votesLabel: String
    ): String {
        val maxCount = counts.maxOrNull() ?: 0
        return buildString {
            append("📊 ").append(title).append(" — ").append(resultLabel).append("\n\n")
            for (i in options.indices) {
                val count = counts.getOrElse(i) { 0 }
                val barLen = when {
                    count <= 0 -> 0
                    maxCount <= 0 -> 1
                    else -> max(1, (count.toDouble() / maxCount * 10).roundToInt())
                }
                val pct = if (totalVotes > 0) (count.toDouble() / totalVotes * 100).roundToInt() else 0
                append(options[i]).append(' ')
                append("█".repeat(barLen))
                append(' ').append(count).append(" (").append(pct).append("%)")
                append('\n')
            }
            append('\n').append(totalVotes).append(' ').append(votesLabel)
        }
    }

    // --- Parsing ---

    /** Extracts the poll marker (id + multi) from a body, or null. */
    fun parsePollMarker(body: String): Pair<String, Boolean>? {
        val m = POLL_MARKER.find(body) ?: return null
        return m.groupValues[1] to (m.groupValues[2] == "m")
    }

    /** Extracts the answer marker (id + selected indices) from a body, or null. */
    fun parseAnswerMarker(body: String): AnswerData? {
        val m = ANSWER_MARKER.find(body) ?: return null
        val indices = m.groupValues[2]
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
        return AnswerData(m.groupValues[1], indices)
    }

    /** Extracts the result marker (poll id) from a body, or null. */
    fun parseResultMarker(body: String): String? = RESULT_MARKER.find(body)?.groupValues?.get(1)

    /**
     * Parses a full poll body into a [PollData]. Returns null when the body is not a poll
     * (no poll marker). The title/question/options are read from the structured lines.
     */
    fun parsePollBody(body: String): PollData? {
        val (pollId, multi) = parsePollMarker(body) ?: return null
        var title = ""
        var question = ""
        val options = mutableListOf<String>()
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("📊 ") && title.isBlank() -> title = line.removePrefix("📊 ").trim()
                line.startsWith("❓ ") && question.isBlank() -> question = line.removePrefix("❓ ").trim()
                else -> {
                    val cb = CHECKBOX_LINE.find(rawLine)
                    if (cb != null) options.add(cb.groupValues[2].trim())
                }
            }
        }
        if (options.isEmpty()) return null
        return PollData(pollId, multi, title, question, options)
    }

    /**
     * Returns the option texts that are marked with X in the body (used to parse answers from
     * regular email programs, where the user X's out the options in the quoted poll).
     */
    fun parseXMarkedOptions(body: String): List<String> =
        body.lineSequence()
            .mapNotNull { CHECKBOX_LINE.find(it) }
            .filter { it.groupValues[1].contains('x', ignoreCase = true) }
            .map { it.groupValues[2].trim() }
            .distinct()
            .toList()

    /**
     * Matches [markedTexts] (option texts from a parsed answer) against the poll's [options] and
     * returns the 0-based indices of the matching options.
     */
    fun matchOptionsToIndices(markedTexts: List<String>, options: List<String>): List<Int> {
        val result = mutableListOf<Int>()
        for (text in markedTexts) {
            val idx = options.indexOfFirst { it.trim().equals(text, ignoreCase = true) }
            if (idx >= 0 && idx !in result) result.add(idx)
        }
        return result
    }

    // --- JSON helpers (options / indices are stored as JSON arrays) ---

    fun optionsFromJson(json: String): List<String> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun indicesFromJson(json: String): List<Int> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getInt(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun optionsToJson(options: List<String>): String =
        org.json.JSONArray().apply { options.forEach { put(it) } }.toString()

    fun indicesToJson(indices: List<Int>): String =
        org.json.JSONArray().apply { indices.forEach { put(it) } }.toString()
}
