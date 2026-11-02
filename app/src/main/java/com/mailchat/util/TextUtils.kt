package com.mailchat.util

import android.text.Html

fun String.truncateToLines(maxLines: Int = 5, charsPerLine: Int = 40): String {
    val maxChars = maxLines * charsPerLine
    if (length <= maxChars) return this
    val truncated = take(maxChars)
    val lastSpace = truncated.lastIndexOf(' ')
    return if (lastSpace > maxChars / 2) truncated.substring(0, lastSpace) + "…" else truncated + "…"
}

private val invisibleChars = buildSet {
    add('\u200B') // zero-width space
    add('\u200C') // zero-width non-joiner
    add('\u200D') // zero-width joiner
    add('\u200E') // left-to-right mark
    add('\u200F') // right-to-left mark
    add('\u2060') // word joiner
    add('\u2061') // function application
    add('\u2062') // invisible times
    add('\u2063') // invisible separator
    add('\u2064') // invisible plus
    add('\u034F') // combining grapheme joiner
    add('\u034E') // combining grapheme split
    add('\uFEFF') // zero-width no-break space / BOM
    add('\u00AD') // soft hyphen
    add('\u180E') // mongolian vowel separator
    add('\u202A') // lre
    add('\u202B') // rle
    add('\u202C') // pdf
    add('\u202D') // lro
    add('\u202E') // rlo
}

fun String.normalizeWhitespace(): String {
    var result = this
    for (c in invisibleChars) {
        result = result.replace(c, ' ')
    }
    return result
        .replace('\u00A0', ' ')
        .replace('\u2007', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("[\\uFE00-\\uFE0F]"), "")
        .replace(Regex("[\\uFFF9-\\uFFFB]"), "")
        .replace('\uFFFC', ' ')
        .replace(Regex("\\s*\\n\\s*"), "\n")
        .replace(Regex("\n{2,}"), "\n")
        .replace(Regex(" {2,}"), " ")
        .trim()
}

fun String.cleanInvisible(): String {
    var result = this
    for (c in invisibleChars) {
        result = result.replace(c, ' ')
    }
    return result
        .replace('\u00A0', ' ')
        .replace('\u2007', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("[\\uFE00-\\uFE0F]"), "")
        .replace(Regex("[\\uFFF9-\\uFFFB]"), "")
        .replace('\uFFFC', ' ')
}

fun String.stripHtml(): String {
    if (!contains("<")) return this
    val cleaned = this
        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
    val text: String = try {
        Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
    } catch (e: Exception) {
        cleaned.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>|</div>|</tr>|</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), " ")
    }
    return text
        .replace("&nbsp;", " ")
        .replace(Regex("&[a-z]+;"), " ")
        .normalizeWhitespace()
}

fun String.sanitizeFileName(): String {
    val cleaned = this
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
    return cleaned.ifBlank { "file" }
}
