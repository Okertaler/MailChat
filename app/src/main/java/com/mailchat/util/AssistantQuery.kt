package com.mailchat.util

import java.util.Calendar

object AssistantQuery {

    private val STOPWORDS = setOf(
        // Deutsch
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "einen", "einem",
        "und", "oder", "aber", "denn", "sondern", "auch", "noch", "schon", "nur", "sehr",
        "wie", "was", "wer", "wen", "wem", "wann", "wo", "warum", "welche", "welcher",
        "ich", "du", "er", "sie", "es", "wir", "ihr", "mich", "dich", "sich", "uns", "euch",
        "mit", "von", "auf", "aus", "bei", "fuer", "für", "über", "ueber", "unter", "zwischen",
        "an", "am", "im", "um", "zum", "zur", "hat", "habe", "hatte", "ist", "sind", "war",
        "als", "wird", "werden", "wurden", "bekommen", "habt", "sei", "seien", "könnte",
        "wurde", "werden", "kann", "koennen", "können", "mal", "dir", "mir", "dich",
        // Englisch
        "the", "a", "an", "and", "or", "but", "what", "who", "when", "where", "why",
        "which", "i", "you", "he", "she", "it", "we", "they", "me", "my", "your",
        "with", "from", "about", "have", "has", "had", "is", "are", "was", "were",
        "can", "could", "would", "should", "will", "tell", "show", "find", "search",
        "emails", "email", "mail", "mails", "message", "messages"
    )

    fun extractKeywords(question: String, max: Int = 5): List<String> {
        return question.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()
            .take(max)
    }

    /** Erkennt Meta-Fragen nach Markierungen (Spam/ToDo). */
    fun detectIntents(question: String): List<String> {
        val q = question.lowercase()
        val intents = mutableListOf<String>()
        if (listOf("spam", "phishing", "junk", "unerwünscht", "unwanted", "markiert als spam").any { q.contains(it) }) {
            intents.add("spam")
        }
        if (listOf("todo", "to-do", "erledigen", "aufgabe", "vorhaben", "memo").any { q.contains(it) }) {
            intents.add("todo")
        }
        return intents
    }

    /** Faengt bei deutschen Zeitangaben den Start-Zeitstempel (ms) ein. */
    fun sinceTimestamp(question: String, now: Long = System.currentTimeMillis()): Long {
        val q = question.lowercase()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        when {
            q.contains("vorgestern") -> { addDays(cal, -2); return cal.timeInMillis }
            q.contains("gestern") -> { addDays(cal, -1); return cal.timeInMillis }
            q.contains("heute") -> { setMidnight(cal); return cal.timeInMillis }
            q.contains("letzte woche") || q.contains("letzte woche") || q.contains("letzten woche") ||
                q.contains("vergangene woche") -> { addDays(cal, -7); return cal.timeInMillis }
            q.contains("diese woche") || q.contains(" diese woche") -> {
                setMidnight(cal)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                return cal.timeInMillis
            }
            q.contains("letzter monat") || q.contains("letzten monat") ||
                q.contains("vergangenen monat") -> { addDays(cal, -30); return cal.timeInMillis }
            q.contains("diesen monat") -> { addDays(cal, -30); return cal.timeInMillis }
            q.contains("letztes jahr") || q.contains("letzten monat") -> { addDays(cal, -365); return cal.timeInMillis }
        }
        val m = Regex("(\\d+)\\s*(tag|tage|woche|wochen|monat|monate|jahr|jahre)").find(q)
        if (m != null) {
            val n = m.groupValues[1].toIntOrNull() ?: 0
            addDays(cal, -(n * unitDays(m.groupValues[2])))
            return cal.timeInMillis
        }
        addDays(cal, -90)
        return cal.timeInMillis
    }

    private fun unitDays(unit: String): Int = when (unit) {
        "tag", "tage" -> 1
        "woche", "wochen" -> 7
        "monat", "monate" -> 30
        "jahr", "jahre" -> 365
        else -> 7
    }

    private fun addDays(cal: Calendar, days: Int) { cal.add(Calendar.DAY_OF_YEAR, days) }

    private fun setMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
