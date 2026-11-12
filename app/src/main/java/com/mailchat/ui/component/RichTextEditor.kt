package com.mailchat.ui.component

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mailchat.R
import com.mailchat.network.SmtpService
import org.json.JSONObject

/**
 * A simple rich-text editor (WebView-based, contenteditable) with a formatting toolbar:
 * bold, italic, underline, alignment, font size, font family and bullet/numbered lists.
 *
 * The content is reported as HTML via [onHtmlChange]. Plain-text input is reported as plain
 * text (no tags), so callers can decide whether the result is HTML or not.
 *
 * Used for the detail-email reply / reply-all / forward compose fields.
 */
@Composable
fun RichTextEditor(
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // Tracks the last content the editor itself reported, so external changes (AI suggestion,
    // draft restore) can be detected and pushed into the WebView without clobbering user input.
    var lastExternal by remember { mutableStateOf(initialHtml) }
    var fontSizeLevel by remember { mutableIntStateOf(3) } // browser font sizes 2..5
    var showFontMenu by remember { mutableStateOf(false) }

    val bridge = remember { HtmlBridge() }
    bridge.onChange = { html ->
        lastExternal = html
        onHtmlChange(html)
    }

    fun exec(command: String, value: String? = null) {
        val wv = webViewRef ?: return
        val js = if (value != null) {
            "document.getElementById('editor').focus();" +
                "document.execCommand('$command', false, ${JSONObject.quote(value)});" +
                "notify();"
        } else {
            "document.getElementById('editor').focus();" +
                "document.execCommand('$command', false);" +
                "notify();"
        }
        wv.evaluateJavascript(js, null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
    ) {
        // Formatting toolbar (horizontally scrollable when the screen is narrow).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            ToolButton(Icons.Default.FormatBold, stringResource(R.string.format_bold)) { exec("bold") }
            ToolButton(Icons.Default.FormatItalic, stringResource(R.string.format_italic)) { exec("italic") }
            ToolButton(Icons.Default.FormatUnderlined, stringResource(R.string.format_underline)) { exec("underline") }
            ToolDivider()
            ToolButton(Icons.Default.FormatAlignLeft, stringResource(R.string.format_align_left)) { exec("justifyLeft") }
            ToolButton(Icons.Default.FormatAlignCenter, stringResource(R.string.format_align_center)) { exec("justifyCenter") }
            ToolButton(Icons.Default.FormatAlignRight, stringResource(R.string.format_align_right)) { exec("justifyRight") }
            ToolDivider()
            // Font size: step the selection's font size down/up (2=small … 5=extra large).
            TextButton(onClick = {
                fontSizeLevel = (fontSizeLevel - 1).coerceAtLeast(2)
                exec("fontSize", fontSizeLevel.toString())
            }) {
                Text("A\u2212", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = {
                fontSizeLevel = (fontSizeLevel + 1).coerceAtMost(5)
                exec("fontSize", fontSizeLevel.toString())
            }) {
                Text("A+", style = MaterialTheme.typography.labelMedium)
            }
            Box {
                TextButton(onClick = { showFontMenu = true }) {
                    Text(stringResource(R.string.format_font), style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                    FONT_FAMILIES.forEach { fam ->
                        DropdownMenuItem(
                            text = { Text(fam) },
                            onClick = {
                                showFontMenu = false
                                exec("fontName", fam)
                            }
                        )
                    }
                }
            }
            ToolDivider()
            ToolButton(Icons.Default.FormatListBulleted, stringResource(R.string.format_bullet_list)) { exec("insertUnorderedList") }
            ToolButton(Icons.Default.FormatListNumbered, stringResource(R.string.format_number_list)) { exec("insertOrderedList") }
        }
        HorizontalDivider()
        // The editable area (WebView). Fixed height with internal scrolling.
        AndroidView(
            factory = { ctx ->
                createEditorWebView(ctx, bridge, initialHtml, placeholder, darkTheme).also { webViewRef = it }
            },
            update = { wv ->
                if (initialHtml != lastExternal) {
                    // External change (AI suggestion applied, draft restored) — push it in.
                    lastExternal = initialHtml
                    val js = if (SmtpService.looksLikeHtml(initialHtml)) {
                        "document.getElementById('editor').innerHTML = ${JSONObject.quote(initialHtml)};"
                    } else {
                        "document.getElementById('editor').textContent = ${JSONObject.quote(initialHtml)};"
                    }
                    wv.evaluateJavascript(js, null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 1.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

private val FONT_FAMILIES = listOf(
    "Arial",
    "Verdana",
    "Georgia",
    "Times New Roman",
    "Courier New",
    "Tahoma"
)

/** JS bridge: forwards editor content changes to Compose on the main thread. */
private class HtmlBridge {
    var onChange: ((String) -> Unit)? = null

    @JavascriptInterface
    fun onHtmlChanged(html: String) {
        Handler(Looper.getMainLooper()).post { onChange?.invoke(html) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createEditorWebView(
    context: android.content.Context,
    bridge: HtmlBridge,
    initialContent: String,
    placeholder: String,
    darkTheme: Boolean
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    isHorizontalScrollBarEnabled = false
    isVerticalScrollBarEnabled = false
    addJavascriptInterface(bridge, "AndroidBridge")
    loadDataWithBaseURL(null, buildEditorHtml(initialContent, placeholder, darkTheme), "text/html", "utf-8", null)
}

private fun buildEditorHtml(initialContent: String, placeholder: String, darkTheme: Boolean): String {
    val textColor = if (darkTheme) "#e0e0e0" else "#222222"
    val placeholderColor = if (darkTheme) "#888888" else "#999999"
    val contentJs = if (SmtpService.looksLikeHtml(initialContent)) {
        "document.getElementById('editor').innerHTML = ${JSONObject.quote(initialContent)};"
    } else {
        "document.getElementById('editor').textContent = ${JSONObject.quote(initialContent)};"
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            html, body { margin: 0; padding: 0; background: transparent; }
            body { font-family: Arial, Helvetica, sans-serif; font-size: 16px; line-height: 1.5; color: $textColor; }
            #editor { min-height: 100%; padding: 12px; outline: none; white-space: pre-wrap; word-wrap: break-word; }
            #editor:empty::before { content: attr(data-placeholder); color: $placeholderColor; }
        </style>
        </head>
        <body>
        <div id="editor" contenteditable="true" data-placeholder="${placeholder.replace("\"", "&quot;")}"></div>
        <script>
            function notify() {
                var h = document.getElementById('editor').innerHTML;
                var t = h.replace(/<br\s*\/?>/g, '').replace(/<div>\s*<\/div>/g, '').replace(/<p>\s*<\/p>/g, '').trim();
                if (t === '') h = '';
                AndroidBridge.onHtmlChanged(h);
            }
            var ed = document.getElementById('editor');
            ed.addEventListener('input', notify);
            ed.addEventListener('keyup', notify);
            ed.addEventListener('mouseup', notify);
            $contentJs
        </script>
        </body>
        </html>
    """.trimIndent()
}
