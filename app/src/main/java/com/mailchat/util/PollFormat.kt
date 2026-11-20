package com.mailchat.util

import com.mailchat.data.model.PollVote
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds and parses the plain-text poll (Umfrage) format used in email bodies.
 *
 * The format is deliberately plain text so it also renders and can be answered in regular email
 * programs. MailChat additionally detects polls/answers via the machine markers:
 *
 *  - Poll:    `[[poll:<id>:<s|m|n><p>]]`  (s = single, m = multiple, n = numbers; optional
 *            trailing `p` = person-related poll, whose result is a person×option table)
 *  - Answer:  `[[poll-answer:<id>:<indices>]]`  (indices = comma-separated 0-based option indices)
 *  - Answer:  `[[poll-answer-num:<id>:<values>]]`  (values = comma-separated numbers, one per option)
 *
 * A numeric poll (marker `n`) has no selection: each respondent enters a number per option and the
 * result is the SUM of those numbers per option. A person poll (marker `…p`) answers the same way
 * but shows the result as a table (persons × options, bottom row = column sums).
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
 * [[poll:<id>:<s|m|n>]]
 * ```
 */
object PollFormat {

    // Mode = one of s/m/n, optionally followed by "p" (person-related poll).
    private val POLL_MARKER = Regex("\\[\\[poll:([A-Za-z0-9_-]+):([smn])(p?)\\]\\]")
    private val ANSWER_MARKER = Regex("\\[\\[poll-answer:([A-Za-z0-9_-]+):([0-9,]*)\\]\\]")
    // Numeric answer: values = comma-separated numbers (one per option, in order). Decimals and
    // negative numbers are allowed. Checked BEFORE [ANSWER_MARKER] (the literal "poll-answer:" is a
    // prefix of "poll-answer-num:", so the numeric marker must win).
    private val ANSWER_NUM_MARKER = Regex("\\[\\[poll-answer-num:([A-Za-z0-9_-]+):([0-9.,\\-]*)\\]\\]")
    // Result email (sent when a poll closes): marks the email as part of the poll flow so it
    // gets no AI tagging/spam detection, but it is rendered as a regular email (ASCII bars).
    // The optional payload is the structured result (base64 JSON, see [PollResultData]) so a
    // recipient can render the SAME result view as the creator (aggregated counts / person
    // table + who has voted).
    private val RESULT_MARKER = Regex("\\[\\[poll-result:([A-Za-z0-9_-]+)(?::([A-Za-z0-9+/=]+))?\\]\\]")
    // A checkbox line: "[ ] text", "[x] text" or "[ x] text" (also matches quoted "> [x] text").
    // The box may contain a space and/or an x — users often write "[ x]" when answering in a
    // regular email program (keeping the original space and adding the x).
    private val CHECKBOX_LINE = Regex("^\\s*(?:>\\s*)*\\[([ xX]*)\\]\\s*(.+)$")
    // A numeric-answer line: the number is written INSIDE the box, e.g. "[5] Option 1" or
    // "[ 5] Option 1" (also matches quoted "> [5] Option 1"). This is the format shown as an
    // example in the numeric poll email.
    private val NUMBER_BOX_LINE = Regex("^\\s*(?:>\\s*)*\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]\\s*(.+)$")

    /** The parsed poll marker: id + mode (single / multiple / numbers) + person flag. */
    data class PollMarker(
        val pollId: String,
        val multi: Boolean,
        val numberMode: Boolean,
        val personMode: Boolean = false
    )

    data class PollData(
        val pollId: String,
        val multi: Boolean,
        val numberMode: Boolean,
        val personMode: Boolean,
        val title: String,
        val question: String,
        val options: List<String>
    )

    data class AnswerData(
        val pollId: String,
        val optionIndices: List<Int>,
        // Numeric polls: the number entered per option (aligned to the options). Empty for
        // checkbox polls.
        val values: List<Double> = emptyList()
    )

    /**
     * The structured result of a closed poll. It is carried in the result email's marker
     * (base64 JSON) so a recipient can render the SAME result view as the creator: the
     * aggregated counts (bar chart) or the person-table rows, plus who has voted.
     *
     * [counts]/[total] are set for bar-chart polls; [rows]/[sums] for person-related polls.
     * [recipients] is the participant list with the voted flag (email, voted).
     */
    data class PollResultData(
        val counts: List<Int> = emptyList(),
        val total: Int = 0,
        val recipients: List<Pair<String, Boolean>> = emptyList(),
        val rows: List<List<String>> = emptyList(),
        val sums: List<Int> = emptyList()
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
        instruction: String,
        numberMode: Boolean = false,
        personMode: Boolean = false
    ): String = buildString {
        append("📊 ").append(title).append('\n')
        if (question.isNotBlank()) append("❓ ").append(question).append('\n')
        append(if (numberMode) "🔢 " else "🔘 ").append(choiceLabel).append("\n\n")
        for (opt in options) {
            append("[ ] ").append(opt).append('\n')
        }
        append("\n━━━━━━━━━━━━━━━\n")
        append(instruction).append('\n')
        append("[[poll:").append(pollId).append(':')
            .append(if (numberMode) 'n' else if (multi) 'm' else 's')
            .append(if (personMode) "p" else "").append("]]")
    }

    fun buildAnswerBody(
        options: List<String>,
        selected: List<Int>,
        pollId: String,
        noReplyHint: String = ""
    ): String =
        buildString {
            for (i in options.indices) {
                val mark = if (i in selected) "X" else " "
                append("[").append(mark).append("] ").append(options[i]).append('\n')
            }
            append('\n')
            if (noReplyHint.isNotBlank()) append(noReplyHint).append('\n')
            append("[[poll-answer:").append(pollId).append(':')
                .append(selected.joinToString(",")).append("]]")
        }

    /**
     * Builds the answer body for a numeric poll: each option shows the number entered, and the
     * marker carries the values (comma-separated, aligned to the options).
     */
    fun buildNumberAnswerBody(
        options: List<String>,
        values: List<Double>,
        pollId: String,
        noReplyHint: String = ""
    ): String =
        buildString {
            for (i in options.indices) {
                val v = values.getOrElse(i) { 0.0 }
                append("[").append(fmtNum(v)).append("] ").append(options[i]).append('\n')
            }
            append('\n')
            if (noReplyHint.isNotBlank()) append(noReplyHint).append('\n')
            append("[[poll-answer-num:").append(pollId).append(':')
                .append(values.joinToString(",") { fmtNum(it) }).append("]]")
        }

    /** Formats a number without a trailing ".0" for whole values (5.0 → "5", 3.5 → "3.5"). */
    fun fmtNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /**
     * Builds the ASCII bar-chart result email body.
     * @param counts vote count per option (same order as [options]).
     * @param totalVotes total number of votes cast (sum of selected options across all voters).
     */
    fun buildAsciiResult(
        title: String,
        question: String,
        options: List<String>,
        counts: List<Int>,
        totalVotes: Int,
        resultLabel: String,
        votesLabel: String
    ): String {
        // Scale by the largest absolute value so negative values get a bar too. For a negative
        // count the number is printed BEFORE the bar, so the bar "points left" from the number.
        val maxAbs = counts.maxOfOrNull { abs(it) } ?: 0
        return buildString {
            append("📊 ").append(title).append(" — ").append(resultLabel).append("\n")
            if (question.isNotBlank()) append("❓ ").append(question).append("\n")
            append("\n")
            for (i in options.indices) {
                val count = counts.getOrElse(i) { 0 }
                val barLen = when {
                    count == 0 -> 0
                    maxAbs == 0 -> 1
                    else -> max(1, (abs(count).toDouble() / maxAbs * 10).roundToInt())
                }
                val pct = if (totalVotes != 0) (count.toDouble() / totalVotes * 100).roundToInt() else 0
                append(options[i]).append(' ')
                if (count < 0) {
                    append(count).append(' ').append("█".repeat(barLen))
                } else {
                    append("█".repeat(barLen)).append(' ').append(count)
                }
                append(" (").append(pct).append("%)")
                append('\n')
            }
            append('\n').append(totalVotes).append(' ').append(votesLabel)
        }
    }

    /**
     * Builds an ASCII TABLE result (for person-related polls). [header] is the first row (e.g.
     * "Person" + the option names), [rows] are the data rows (each = person + one cell per
     * option), and [footer] is the bottom row (e.g. "Summe" + the column sums). Column widths are
     * computed from the content so the pipes line up.
     */
    fun buildAsciiTable(
        title: String,
        question: String,
        header: List<String>,
        rows: List<List<String>>,
        footer: List<String>,
        resultLabel: String
    ): String {
        val allRows = listOf(header) + rows + listOf(footer)
        val colCount = header.size
        val widths = IntArray(colCount) { c -> allRows.maxOf { it.getOrElse(c) { "" }.length } }
        fun fmtRow(row: List<String>): String =
            row.mapIndexed { c, cell -> cell.padEnd(widths[c]) }
                .joinToString(" | ").trimEnd()
        val sep = widths.joinToString("-+-") { "-".repeat(it) }
        return buildString {
            append("📊 ").append(title).append(" — ").append(resultLabel).append("\n")
            if (question.isNotBlank()) append("❓ ").append(question).append("\n")
            append("\n")
            append(fmtRow(header)).append("\n")
            append(sep).append("\n")
            for (row in rows) append(fmtRow(row)).append("\n")
            append(sep).append("\n")
            append(fmtRow(footer)).append("\n")
        }
    }

    /**
     * Computes the table rows for a person-related poll: one row per voter (already deduped —
     * last answer wins), each = [voterEmail, cell per option]. For a checkbox poll a cell is "✓"
     * when the option was selected, else blank; for a numeric poll it is the entered number
     * (blank when 0).
     */
    fun personTableRows(options: List<String>, votes: List<PollVote>, numberMode: Boolean): List<List<String>> =
        votes.map { vote ->
            val cells = options.indices.map { i ->
                if (numberMode) {
                    val v = valuesFromJson(vote.values).getOrElse(i) { 0.0 }
                    if (v == 0.0) "" else fmtNum(v)
                } else {
                    if (i in indicesFromJson(vote.optionIndices)) "✓" else ""
                }
            }
            listOf(vote.voterEmail) + cells
        }

    /** The column sums for a person-related poll (one sum per option, over the given votes). */
    fun personColumnSums(options: List<String>, votes: List<PollVote>, numberMode: Boolean): List<Int> {
        val sums = IntArray(options.size)
        for (vote in votes) {
            if (numberMode) {
                val values = valuesFromJson(vote.values)
                for (i in options.indices) sums[i] += values.getOrElse(i) { 0.0 }.toInt()
            } else {
                val indices = indicesFromJson(vote.optionIndices)
                for (i in indices) if (i in options.indices) sums[i]++
            }
        }
        return sums.toList()
    }

    // --- Parsing ---

    /** Extracts the poll marker (id + mode + person flag) from a body, or null. */
    fun parsePollMarker(body: String): PollMarker? {
        val m = POLL_MARKER.find(body) ?: return null
        val mode = m.groupValues[2]
        return PollMarker(m.groupValues[1], mode == "m", mode == "n", m.groupValues[3] == "p")
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

    /** Extracts the numeric answer marker (id + values per option) from a body, or null. */
    fun parseAnswerNumMarker(body: String): AnswerData? {
        val m = ANSWER_NUM_MARKER.find(body) ?: return null
        val values = m.groupValues[2]
            .split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
        if (values.isEmpty()) return null
        return AnswerData(m.groupValues[1], emptyList(), values)
    }

    /** Extracts the result marker (poll id) from a body, or null. */
    fun parseResultMarker(body: String): String? = RESULT_MARKER.find(body)?.groupValues?.get(1)

    /**
     * Builds the result marker for a result email. When [data] is given, the structured result
     * is appended as a base64 JSON payload (so the recipient's result view matches the
     * creator's). Regular email programs just see a short machine line at the end.
     */
    fun buildResultMarker(pollId: String, data: PollResultData? = null): String {
        val payload = data?.let {
            ":" + android.util.Base64.encodeToString(
                resultDataToJson(it).toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
        } ?: ""
        return "[[poll-result:$pollId$payload]]"
    }

    /** Extracts the structured result from a result email body, or null (legacy marker). */
    fun parseResultData(body: String): PollResultData? {
        val m = RESULT_MARKER.find(body) ?: return null
        val payload = m.groupValues[2]
        if (payload.isBlank()) return null
        return try {
            val json = String(android.util.Base64.decode(payload, android.util.Base64.NO_WRAP), Charsets.UTF_8)
            resultDataFromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun resultDataToJson(d: PollResultData): String {
        val obj = org.json.JSONObject()
        obj.put("c", org.json.JSONArray(d.counts))
        obj.put("t", d.total)
        val rec = org.json.JSONArray()
        for ((email, voted) in d.recipients) rec.put(org.json.JSONArray().put(email).put(if (voted) 1 else 0))
        val rows = org.json.JSONArray()
        for (row in d.rows) rows.put(org.json.JSONArray(row))
        obj.put("r", rec)
        obj.put("p", rows)
        obj.put("s", org.json.JSONArray(d.sums))
        return obj.toString()
    }

    private fun resultDataFromJson(json: String): PollResultData {
        val obj = org.json.JSONObject(json)
        fun ints(name: String): List<Int> {
            val arr = obj.optJSONArray(name) ?: return emptyList()
            return (0 until arr.length()).map { arr.getInt(it) }
        }
        val rec = obj.optJSONArray("r")
        val recipients = if (rec != null) (0 until rec.length()).map { i ->
            val pair = rec.getJSONArray(i)
            pair.getString(0) to (pair.getInt(1) == 1)
        } else emptyList()
        val p = obj.optJSONArray("p")
        val rows = if (p != null) (0 until p.length()).map { i ->
            val row = p.getJSONArray(i)
            (0 until row.length()).map { row.getString(it) }
        } else emptyList()
        return PollResultData(
            counts = ints("c"),
            total = obj.optInt("t", 0),
            recipients = recipients,
            rows = rows,
            sums = ints("s")
        )
    }

    /**
     * Parses a full poll body into a [PollData]. Returns null when the body is not a poll
     * (no poll marker). The title/question/options are read from the structured lines.
     */
    fun parsePollBody(body: String): PollData? {
        val marker = parsePollMarker(body) ?: return null
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
        return PollData(marker.pollId, marker.multi, marker.numberMode, marker.personMode, title, question, options)
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

    /**
     * Parses a numeric answer from a regular email reply (e.g. Thunderbird).
     *
     * Primary format (the one shown as an example in the poll email): the number is written INSIDE
     * the box, e.g. "[5] Option 1". Fallback: the number follows the option text, e.g.
     * "Option 1 = 5", "Option 1 (5)" or "Option 1 5". Returns the values aligned to the options
     * (0.0 when no number is found for an option).
     */
    fun parseNumberAnswer(body: String, options: List<String>): List<Double> {
        val boxValues = parseNumberBoxAnswer(body, options)
        if (boxValues.any { it != 0.0 }) return boxValues
        return parseNumberAnswerFromBody(body, options)
    }

    /**
     * Parses the primary numeric-answer format: the number is written inside the box, e.g.
     * "[5] Option 1" (also quoted "> [5] Option 1"). Returns the values aligned to the options
     * (0.0 when no number is found for an option).
     */
    fun parseNumberBoxAnswer(body: String, options: List<String>): List<Double> {
        val values = DoubleArray(options.size) { 0.0 }
        for (rawLine in body.lineSequence()) {
            val m = NUMBER_BOX_LINE.find(rawLine) ?: continue
            val num = m.groupValues[1].toDouble()
            val optText = m.groupValues[2].trim()
            val idx = options.indexOfFirst { it.trim().equals(optText, ignoreCase = true) }
            if (idx >= 0) values[idx] = num
        }
        return values.toList()
    }

    /**
     * Fallback numeric-answer parsing: for each option, finds a line containing the option text
     * and extracts the number that follows it. Supported formats (after the option text): "(5)"
     * (parentheses), "= 5" (equals sign), or "5" (plain, e.g. space-separated). Quoted lines
     * ("> …") and checkbox prefixes ("[ ] …") are handled. Returns the values aligned to the
     * options (0.0 when no number is found for an option).
     */
    fun parseNumberAnswerFromBody(body: String, options: List<String>): List<Double> {
        val values = DoubleArray(options.size) { 0.0 }
        val lines = body.lineSequence().map { it.trim() }.toList()
        for (i in options.indices) {
            val opt = options[i].trim()
            if (opt.isBlank()) continue
            for (line in lines) {
                if (!line.contains(opt, ignoreCase = true)) continue
                val num = extractNumberAfter(line, opt)
                if (num != null) {
                    values[i] = num
                    break
                }
            }
        }
        return values.toList()
    }

    /**
     * Extracts the number that follows [optionText] in [line], or null. The character right after
     * the option text must not be a digit (so "Option 1" does not match inside "Option 10").
     */
    private fun extractNumberAfter(line: String, optionText: String): Double? {
        val idx = line.indexOf(optionText, ignoreCase = true)
        if (idx < 0) return null
        val end = idx + optionText.length
        if (end < line.length && line[end].isDigit()) return null
        val remainder = line.substring(end)
        // "(5)" / "( 5 )"
        NUM_IN_PARENS.find(remainder)?.let { return it.groupValues[1].toDouble() }
        // "= 5"
        val eq = remainder.indexOf('=')
        if (eq >= 0) {
            NUMBER.find(remainder.substring(eq + 1))?.let { return it.value.toDouble() }
        }
        // plain " 5"
        return NUMBER.find(remainder)?.value?.toDouble()
    }

    private val NUM_IN_PARENS = Regex("\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)")
    private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")

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

    fun valuesFromJson(json: String): List<Double> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getDouble(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun valuesToJson(values: List<Double>): String =
        org.json.JSONArray().apply { values.forEach { put(it) } }.toString()
}
