package com.mailchat.ai

import android.text.Html

/**
 * Strip HTML tags from a string and return readable plain text.
 *
 * This mirrors the app module's [com.mailchat.util.stripHtml] so the AI pipeline can extract the
 * actual content BEFORE any length limit is applied. HTML emails carry a large head/CSS overhead
 * (often >4000 chars) before the real content starts; if the raw HTML were truncated first, the
 * content would be lost for summarization, classification, tagging and meeting extraction.
 *
 * The function is idempotent: plain text (no '<') is returned unchanged.
 */
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
        .replace(Regex("\\s*\\n\\s*"), "\n")
        .replace(Regex("\n{2,}"), "\n")
        .replace(Regex(" {2,}"), " ")
        .trim()
}
