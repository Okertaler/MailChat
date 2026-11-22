package com.mailchat.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds and parses the plain-text TERMIN (appointment) format used in email bodies.
 *
 * An appointment poll reuses the poll machinery (see [PollFormat] and
 * [com.mailchat.data.model.Poll] with [com.mailchat.data.model.Poll.appointmentMode]): the options
 * are the fixed Ja/Nein/Vielleicht (Yes/No/Maybe) choices, and the poll carries the appointment
 * details (start/end date+time, all-day, location). The format is deliberately plain text so it
 * also renders and can be answered in regular email programs (e.g. Thunderbird).
 *
 * Machine markers (detected by [com.mailchat.service.PollProcessor]):
 *  - Appointment: `[[appt:<id>:<startISO>:<endISO>:<allDay>]]`
 *      startISO/endISO = "YYYY-MM-DDTHH:mm" (timed) or "YYYY-MM-DD" (all-day, inclusive end date);
 *      allDay = 0 or 1. The dates live in the marker (a single reliable line) so a recipient's app
 *      can create the local poll record even when the body is quoted/HTML.
 *  - Answer:      `[[appt-answer:<id>:<choice>:<comment>]]`
 *      choice = 0 (Ja/Yes), 1 (Nein/No) or 2 (Vielleicht/Maybe); comment = optional free text
 *      (sanitized: no "]]" sequences).
 *  - Result:      `[[appt-result:<id>[:<base64 JSON>]]]`
 *      The optional payload is the structured result (see [ApptResultData]) so a recipient can
 *      render the SAME result view as the creator.
 *
 * An appointment body looks like:
 * ```
 * 📅 <title>
 * ❓ <question>
 *
 * 🗓 <date label, human readable>
 * 📍 <location>
 *
 * [ ] Ja
 * [ ] Nein
 * [ ] Vielleicht
 *
 * ━━━━━━━━━━━━━━━
 * 💡 <instruction>
 * [[appt:<id>:<startISO>:<endISO>:<allDay>]]
 * ```
 */
object AppointmentFormat {

    // startISO/endISO: digits, "T", ":" and "-" (e.g. "2026-12-15T14:00" or "2026-12-15").
    private val APPT_MARKER =
        Regex("\\[\\[appt:([A-Za-z0-9_-]+):([0-9T:-]+):([0-9T:-]+):([01])\\]\\]")
    // Answer: choice = 0/1/2, comment = everything up to the final "]]" (sanitized on build).
    private val APPT_ANSWER_MARKER =
        Regex("\\[\\[appt-answer:([A-Za-z0-9_-]+):([0-2]):(.*)\\]\\]")
    // Result: optional base64 JSON payload (same shape as the poll result marker).
    private val APPT_RESULT_MARKER =
        Regex("\\[\\[appt-result:([A-Za-z0-9_-]+)(?::([A-Za-z0-9+/=]+))?\\]\\]")
    // A "Kommentar: ..." / "Comment: ..." line (for regular-email answers).
    private val COMMENT_LINE =
        Regex("^(?:Kommentar|Comment)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** The parsed appointment marker: id + the ISO start/end + all-day flag. */
    data class ApptMarker(
        val apptId: String,
        val startISO: String,
        val endISO: String,
        val allDay: Boolean
    )

    data class ApptAnswerData(
        val apptId: String,
        val choice: Int,
        val comment: String
    )

    /** A fully parsed appointment (marker + body). */
    data class ApptData(
        val apptId: String,
        val startDate: String, // "YYYY-MM-DD"
        val startTime: String, // "HH:mm" (empty for all-day)
        val endDate: String,   // "YYYY-MM-DD" (inclusive)
        val endTime: String,   // "HH:mm" (empty for all-day)
        val allDay: Boolean,
        val location: String,
        val title: String,
        val question: String
    )

    /** One person's answer in the structured result. */
    data class ApptResponse(
        val email: String,
        val choice: Int, // 0=Ja, 1=Nein, 2=Vielleicht
        val comment: String
    )

    /**
     * The structured result of a closed appointment, carried in the result email's marker
     * (base64 JSON) so a recipient can render the SAME result view as the creator.
     */
    data class ApptResultData(
        val responses: List<ApptResponse> = emptyList()
    )

    // --- Building ---

    fun buildApptMarker(apptId: String, startISO: String, endISO: String, allDay: Boolean): String =
        "[[appt:$apptId:$startISO:$endISO:${if (allDay) 1 else 0}]]"

    /**
     * Builds the appointment email body (plain text). [options] are the three choice labels in the
     * creator's UI language (e.g. Ja/Nein/Vielleicht or Yes/No/Maybe). [dateLabel] is the
     * human-readable date line (see [dateLabel]).
     */
    fun buildApptBody(
        title: String,
        question: String,
        dateLabel: String,
        location: String,
        options: List<String>,
        instruction: String,
        apptId: String,
        startISO: String,
        endISO: String,
        allDay: Boolean
    ): String = buildString {
        append("📅 ").append(title).append('\n')
        if (question.isNotBlank()) append("❓ ").append(question).append('\n')
        append('\n')
        append("🗓 ").append(dateLabel).append('\n')
        if (location.isNotBlank()) append("📍 ").append(location).append('\n')
        append('\n')
        for (opt in options) {
            append("[ ] ").append(opt).append('\n')
        }
        append("\n━━━━━━━━━━━━━━━\n")
        append(instruction).append('\n')
        append(buildApptMarker(apptId, startISO, endISO, allDay))
    }

    /**
     * Builds the answer body (plain text): the selected choice is X-marked, the optional comment
     * is on its own "Kommentar:" line, and the marker carries the choice + comment.
     */
    fun buildApptAnswerBody(
        options: List<String>,
        choice: Int,
        comment: String,
        apptId: String,
        noReplyHint: String = "",
        commentLabel: String = "Kommentar"
    ): String {
        val safeComment = comment.replace("]]", "] ]").trim()
        return buildString {
            for (i in options.indices) {
                val mark = if (i == choice) "X" else " "
                append("[").append(mark).append("] ").append(options[i]).append('\n')
            }
            append('\n')
            if (safeComment.isNotBlank()) {
                append(commentLabel).append(": ").append(safeComment).append('\n')
                append('\n')
            }
            if (noReplyHint.isNotBlank()) append(noReplyHint).append('\n')
            append("[[appt-answer:").append(apptId).append(':').append(choice).append(':')
                .append(safeComment).append("]]")
        }
    }

    /**
     * Builds the ASCII result email body for a closed appointment: a table (Person | Antwort |
     * Kommentar) plus a summary line (Ja/Nein/Vielleicht counts).
     */
    fun buildApptAsciiResult(
        title: String,
        question: String,
        dateLabel: String,
        location: String,
        responses: List<ApptResponse>,
        optionLabels: List<String>,
        resultLabel: String,
        personLabel: String,
        answerLabel: String,
        commentLabel: String
    ): String {
        val header = listOf(personLabel, answerLabel, commentLabel)
        val rows = responses.map { r ->
            listOf(r.email, optionLabels.getOrElse(r.choice) { "?" }, r.comment)
        }
        val allRows = listOf(header) + rows
        val widths = IntArray(3) { c -> allRows.maxOf { it.getOrElse(c) { "" }.length } }
        fun fmtRow(row: List<String>): String =
            row.mapIndexed { c, cell -> cell.padEnd(widths[c]) }.joinToString(" | ").trimEnd()
        val sep = widths.joinToString("-+-") { "-".repeat(it) }
        val counts = IntArray(3)
        for (r in responses) if (r.choice in 0..2) counts[r.choice]++
        return buildString {
            append("📅 ").append(title).append(" — ").append(resultLabel).append("\n")
            if (question.isNotBlank()) append("❓ ").append(question).append("\n")
            append("🗓 ").append(dateLabel).append("\n")
            if (location.isNotBlank()) append("📍 ").append(location).append("\n")
            append("\n")
            append(fmtRow(header)).append("\n")
            append(sep).append("\n")
            for (row in rows) append(fmtRow(row)).append("\n")
            append("\n")
            append(optionLabels.getOrElse(0) { "Ja" }).append(": ").append(counts[0])
                .append(" · ").append(optionLabels.getOrElse(1) { "Nein" }).append(": ").append(counts[1])
                .append(" · ").append(optionLabels.getOrElse(2) { "Vielleicht" }).append(": ").append(counts[2])
        }
    }

    /**
     * Builds the result marker for a result email. When [data] is given, the structured result is
     * appended as a base64 JSON payload (so the recipient's result view matches the creator's).
     */
    fun buildApptResultMarker(apptId: String, data: ApptResultData? = null): String {
        val payload = data?.let {
            ":" + android.util.Base64.encodeToString(
                apptResultDataToJson(it).toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
        } ?: ""
        return "[[appt-result:$apptId$payload]]"
    }

    // --- Parsing ---

    /** Extracts the appointment marker (id + ISO start/end + all-day) from a body, or null. */
    fun parseApptMarker(body: String): ApptMarker? {
        val m = APPT_MARKER.find(body) ?: return null
        return ApptMarker(
            m.groupValues[1],
            m.groupValues[2],
            m.groupValues[3],
            m.groupValues[4] == "1"
        )
    }

    /** Extracts the answer marker (id + choice + comment) from a body, or null. */
    fun parseApptAnswerMarker(body: String): ApptAnswerData? {
        val m = APPT_ANSWER_MARKER.find(body) ?: return null
        return ApptAnswerData(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].trim())
    }

    /** Extracts the result marker (appointment id) from a body, or null. */
    fun parseApptResultMarker(body: String): String? =
        APPT_RESULT_MARKER.find(body)?.groupValues?.get(1)

    /** Extracts the structured result from a result email body, or null (legacy marker). */
    fun parseApptResultData(body: String): ApptResultData? {
        val m = APPT_RESULT_MARKER.find(body) ?: return null
        val payload = m.groupValues[2]
        if (payload.isBlank()) return null
        return try {
            val json = String(android.util.Base64.decode(payload, android.util.Base64.NO_WRAP), Charsets.UTF_8)
            apptResultDataFromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a full appointment body into an [ApptData]. Returns null when the body is not an
     * appointment (no appointment marker). The dates come from the marker; the title/question/
     * location are read from the structured body lines.
     */
    fun parseApptBody(body: String): ApptData? {
        val marker = parseApptMarker(body) ?: return null
        val (startDate, startTime) = splitISO(marker.startISO)
        val (endDate, endTime) = splitISO(marker.endISO)
        var title = ""
        var question = ""
        var location = ""
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("📅 ") && title.isBlank() -> title = line.removePrefix("📅 ").trim()
                line.startsWith("❓ ") && question.isBlank() -> question = line.removePrefix("❓ ").trim()
                line.startsWith("📍 ") && location.isBlank() -> location = line.removePrefix("📍 ").trim()
            }
        }
        return ApptData(
            apptId = marker.apptId,
            startDate = startDate,
            startTime = startTime,
            endDate = endDate,
            endTime = endTime,
            allDay = marker.allDay,
            location = location,
            title = title,
            question = question
        )
    }

    /**
     * Parses the optional comment from a regular-email answer (a "Kommentar: ..." / "Comment: ..."
     * line that is NOT part of the quoted original email). Returns "" when none is found.
     */
    fun parseApptComment(body: String): String {
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith(">")) continue // skip the quoted original email
            val m = COMMENT_LINE.find(line) ?: continue
            return m.groupValues[1].trim()
        }
        return ""
    }

    /** Splits an ISO value ("YYYY-MM-DDTHH:mm" or "YYYY-MM-DD") into (date, time). */
    private fun splitISO(iso: String): Pair<String, String> {
        val idx = iso.indexOf('T')
        return if (idx >= 0) iso.substring(0, idx) to iso.substring(idx + 1) else iso to ""
    }

    // --- Date label (human readable) ---

    private val DE_DATE = DateTimeFormatter.ofPattern("d.M.yyyy")
    private val EN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

    /**
     * Builds the human-readable date label:
     *  - timed, single day:  "15.12.2026, 14:00 – 16:00 Uhr"
     *  - timed, multi-day:   "15.12.2026, 14:00 – 17.12.2026, 16:00 Uhr"
     *  - all-day, single:    "15.12.2026 (ganztagig)"
     *  - all-day, multi-day: "15.12.2026 – 17.12.2026 (ganztagig)"
     */
    fun dateLabel(
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String,
        allDay: Boolean,
        isGerman: Boolean
    ): String {
        val fmt = if (isGerman) DE_DATE else EN_DATE
        fun d(date: String): String = try {
            LocalDate.parse(date).format(fmt)
        } catch (_: Exception) {
            date
        }
        val sameDay = startDate == endDate
        return if (allDay) {
            if (sameDay) "${d(startDate)} (${if (isGerman) "ganztagig" else "all day"})"
            else "${d(startDate)} – ${d(endDate)} (${if (isGerman) "ganztagig" else "all day"})"
        } else {
            if (sameDay) "${d(startDate)}, $startTime – $endTime${if (isGerman) " Uhr" else ""}"
            else "${d(startDate)}, $startTime – ${d(endDate)}, $endTime${if (isGerman) " Uhr" else ""}"
        }
    }

    // --- ICS generation (for "In Kalender übernehmen" via share) ---

    /**
     * Builds an ICS (iCalendar) file for the appointment. All-day events use VALUE=DATE (the end
     * date is EXCLUSIVE in iCalendar, so it is the inclusive end date + 1 day). Timed events use
     * floating local time (no timezone) which all calendar apps accept.
     */
    fun buildIcs(
        title: String,
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String,
        allDay: Boolean,
        location: String,
        description: String,
        apptId: String
    ): String {
        val stamp = java.time.LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//MailChat//Termin//DE\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:").append(apptId).append("@mailchat\r\n")
            append("DTSTAMP:").append(stamp).append("Z\r\n")
            if (allDay) {
                append("DTSTART;VALUE=DATE:").append(startDate.replace("-", "")).append("\r\n")
                val endExclusive = try {
                    LocalDate.parse(endDate).plusDays(1).toString().replace("-", "")
                } catch (_: Exception) {
                    startDate.replace("-", "")
                }
                append("DTEND;VALUE=DATE:").append(endExclusive).append("\r\n")
            } else {
                append("DTSTART:").append(startDate.replace("-", "")).append('T')
                    .append(startTime.replace(":", "")).append("00\r\n")
                append("DTEND:").append(endDate.replace("-", "")).append('T')
                    .append(endTime.replace(":", "")).append("00\r\n")
            }
            append("SUMMARY:").append(icsEscape(title)).append("\r\n")
            if (location.isNotBlank()) append("LOCATION:").append(icsEscape(location)).append("\r\n")
            if (description.isNotBlank()) append("DESCRIPTION:").append(icsEscape(description)).append("\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    /** Escapes a text value for an ICS TEXT property (backslash, semicolon, comma, newline). */
    private fun icsEscape(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\n")
            .replace("\n", "\\n")

    // --- Structured result JSON ---

    private fun apptResultDataToJson(d: ApptResultData): String {
        val obj = org.json.JSONObject()
        val arr = org.json.JSONArray()
        for (r in d.responses) arr.put(org.json.JSONArray().put(r.email).put(r.choice).put(r.comment))
        obj.put("r", arr)
        return obj.toString()
    }

    private fun apptResultDataFromJson(json: String): ApptResultData {
        val obj = org.json.JSONObject(json)
        val arr = obj.optJSONArray("r")
        val responses = if (arr != null) (0 until arr.length()).map { i ->
            val a = arr.getJSONArray(i)
            ApptResponse(a.getString(0), a.getInt(1), a.optString(2, ""))
        } else emptyList()
        return ApptResultData(responses)
    }
}
