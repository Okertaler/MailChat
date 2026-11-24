package com.mailchat.util

import androidx.compose.ui.graphics.Color

/**
 * The colors available for sticky notes (Notizzettel). Each color has a light and a dark
 * variant for the bubble background, plus an accent (used for the icon/badge) and a text color.
 *
 * The [key] is stored on the message ([com.mailchat.data.model.Message.stickyNoteColor]).
 */
data class StickyNoteColor(
    val key: String,
    val labelDe: String,
    val labelEn: String,
    val accent: Color,
    val bubbleLight: Color,
    val bubbleDark: Color,
    val textLight: Color,
    val textDark: Color
)

object StickyNoteColors {
    const val YELLOW = "yellow"
    const val PINK = "pink"
    const val ORANGE = "orange"
    const val PURPLE = "purple"

    val ALL = listOf(
        StickyNoteColor(
            key = YELLOW,
            labelDe = "Gelb",
            labelEn = "Yellow",
            accent = Color(0xFFFFC107),
            bubbleLight = Color(0xFFFFF9C4),
            bubbleDark = Color(0xFF6B5B1E),
            textLight = Color(0xFF5D4037),
            textDark = Color(0xFFFFF9C4)
        ),
        StickyNoteColor(
            key = PINK,
            labelDe = "Pink",
            labelEn = "Pink",
            accent = Color(0xFFFF4081),
            bubbleLight = Color(0xFFFFD1E8),
            bubbleDark = Color(0xFF8E2449),
            textLight = Color(0xFF880E4F),
            textDark = Color(0xFFFFD1E8)
        ),
        StickyNoteColor(
            key = ORANGE,
            labelDe = "Orange",
            labelEn = "Orange",
            accent = Color(0xFFFF6D00),
            bubbleLight = Color(0xFFFFE0B2),
            bubbleDark = Color(0xFF8A4B08),
            textLight = Color(0xFFE65100),
            textDark = Color(0xFFFFE0B2)
        ),
        StickyNoteColor(
            key = PURPLE,
            labelDe = "Lila",
            labelEn = "Purple",
            accent = Color(0xFFAA00FF),
            bubbleLight = Color(0xFFE1BEE7),
            bubbleDark = Color(0xFF4A148C),
            textLight = Color(0xFF6A1B9A),
            textDark = Color(0xFFE1BEE7)
        )
    )

    /** Resolves a stored color key to its definition (falls back to yellow for unknown keys). */
    fun byKey(key: String): StickyNoteColor =
        ALL.firstOrNull { it.key == key } ?: ALL.first()
}
