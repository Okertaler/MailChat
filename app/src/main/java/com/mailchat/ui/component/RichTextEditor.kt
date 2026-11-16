package com.mailchat.ui.component

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mailchat.R
import com.mailchat.network.SmtpService
import org.json.JSONObject

/**
 * Lets an external toolbar (e.g. one placed above the keyboard) execute formatting commands on a
 * [RichTextEditor]. The editor wires up [execCommand] once its WebView is created.
 */
class RichTextEditorController {
    internal var execCommand: ((String, String?) -> Unit)? = null

    fun exec(command: String, value: String? = null) {
        execCommand?.invoke(command, value)
    }
}

/**
 * A simple rich-text editor (WebView-based, contenteditable).
 *
 * The content is reported as HTML via [onHtmlChange]. Plain-text input is reported as plain
 * text (no tags), so callers can decide whether the result is HTML or not.
 *
 * Content is only pushed INTO the WebView for external changes (AI suggestion, draft restore,
 * forward prep) — never while the user is typing — so the caret is never reset and the display
 * does not flicker. The "user is typing" flag and the last reported content are stored on the
 * [HtmlBridge] as plain (non-observable) fields, so reading/writing them inside the AndroidView
 * `update` lambda (which runs during composition) never triggers a recomposition.
 *
 * The formatting toolbar can either be shown on top of the editor ([showToolbar] = true, the
 * default) or rendered separately via [RichTextToolbar] using the shared [controller] — the
 * latter is used to place the toolbar above the keyboard.
 *
 * Used for the detail-email reply / reply-all / forward compose fields.
 */
@Composable
fun RichTextEditor(
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    controller: RichTextEditorController = remember { RichTextEditorController() },
    showToolbar: Boolean = true
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    val bridge = remember { HtmlBridge() }
    // The editor reported a change (user typing). While this is set, we must NOT push content
    // back into the WebView — doing so resets the caret and makes the display flicker.
    bridge.onChange = { html ->
        bridge.isUserEdit = true
        bridge.lastReported = html
        onHtmlChange(html)
    }
    // The WebView stretches to the content height (min 220dp) so the whole message can be
    // scrolled with the surrounding layout (finger swipe) instead of inside a fixed box.
    var editorHeightDp by remember { mutableIntStateOf(220) }
    bridge.onHeightChange = { h -> editorHeightDp = h }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
    ) {
        if (showToolbar) {
            RichTextToolbar(controller = controller)
            HorizontalDivider()
        }
        // The editable area (WebView). Stretches to the content height (min 220dp); the
        // surrounding scrollable layout provides the scrolling.
        AndroidView(
            factory = { ctx ->
                createEditorWebView(ctx, bridge, initialHtml, placeholder, darkTheme).also { wv ->
                    controller.execCommand = { cmd, value ->
                        // Highlight color without a selection: set the background of the
                        // message field itself (visual only, not part of the email content)
                        // instead of coloring the entire text.
                        val js = if (cmd == "hiliteColor" && value != null) {
                            "var _sel = window.getSelection();" +
                                "if (_sel && !_sel.isCollapsed) {" +
                                "document.getElementById('editor').focus();" +
                                "document.execCommand('hiliteColor', false, ${JSONObject.quote(value)});" +
                                "} else {" +
                                "document.getElementById('editor').style.backgroundColor = ${JSONObject.quote(value)};" +
                                "}" +
                                "notify();"
                        } else {
                            // All other commands: if no text is selected, select the entire
                            // content first so the formatting is applied to the whole text
                            // (the user expects this).
                            val selectAllFirst = "var _sel = window.getSelection();" +
                                "if (!_sel || _sel.isCollapsed) {" +
                                "var _ed = document.getElementById('editor'); _ed.focus();" +
                                "var _r = document.createRange(); _r.selectNodeContents(_ed);" +
                                "_sel.removeAllRanges(); _sel.addRange(_r);}"
                            if (value != null) {
                                selectAllFirst +
                                    "document.getElementById('editor').focus();" +
                                    "document.execCommand('$cmd', false, ${JSONObject.quote(value)});" +
                                    "notify();"
                            } else {
                                selectAllFirst +
                                    "document.getElementById('editor').focus();" +
                                    "document.execCommand('$cmd', false);" +
                                    "notify();"
                            }
                        }
                        wv.evaluateJavascript(js, null)
                    }
                }
            },
            update = { wv ->
                if (bridge.isUserEdit) {
                    // The last change came from the user typing — swallow it so we never push
                    // content back into the WebView (which would reset the caret / flicker).
                    bridge.isUserEdit = false
                } else if (initialHtml != bridge.lastReported) {
                    // External change (AI suggestion applied, draft restored, forward prep) —
                    // push it in, preserving the caret so it does not jump to the start.
                    bridge.lastReported = initialHtml
                    val isHtml = SmtpService.looksLikeHtml(initialHtml)
                    wv.evaluateJavascript(
                        "setContent(${JSONObject.quote(initialHtml)}, $isHtml);",
                        null
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(editorHeightDp.dp.coerceAtLeast(220.dp))
        )
    }
}

/**
 * The formatting toolbar for a [RichTextEditor]: bold, italic, underline, text color, highlight
 * (background) color, alignment, font size, font family and bullet/numbered lists. Horizontally
 * scrollable when the screen is narrow. Commands are executed through the shared [controller].
 */
@Composable
fun RichTextToolbar(
    controller: RichTextEditorController,
    modifier: Modifier = Modifier,
    // Optional content rendered at the very start of the toolbar (e.g. the compress / mic /
    // AI-answer action icons in the compose overlay).
    leadingContent: @Composable () -> Unit = {}
) {
    var fontSizeLevel by remember { mutableIntStateOf(3) } // browser font sizes 2..5
    var showFontMenu by remember { mutableStateOf(false) }
    var showTextColorMenu by remember { mutableStateOf(false) }
    var showBgColorMenu by remember { mutableStateOf(false) }

    fun exec(command: String, value: String? = null) {
        controller.exec(command, value)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        leadingContent()
        ToolButton(Icons.Default.FormatBold, stringResource(R.string.format_bold)) { exec("bold") }
        ToolButton(Icons.Default.FormatItalic, stringResource(R.string.format_italic)) { exec("italic") }
        ToolButton(Icons.Default.FormatUnderlined, stringResource(R.string.format_underline)) { exec("underline") }
        // Text color.
        Box {
            IconButton(onClick = { showTextColorMenu = true }, modifier = Modifier.padding(horizontal = 1.dp)) {
                Icon(Icons.Default.FormatColorText, contentDescription = stringResource(R.string.format_text_color), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showTextColorMenu, onDismissRequest = { showTextColorMenu = false }) {
                Box(modifier = Modifier.padding(8.dp)) {
                    ColorPalette(TEXT_COLORS) { c ->
                        showTextColorMenu = false
                        exec("foreColor", c)
                    }
                }
            }
        }
        // Highlight (background) color.
        Box {
            IconButton(onClick = { showBgColorMenu = true }, modifier = Modifier.padding(horizontal = 1.dp)) {
                Icon(Icons.Default.FormatColorFill, contentDescription = stringResource(R.string.format_bg_color), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showBgColorMenu, onDismissRequest = { showBgColorMenu = false }) {
                Box(modifier = Modifier.padding(8.dp)) {
                    ColorPalette(BG_COLORS) { c ->
                        showBgColorMenu = false
                        exec("hiliteColor", c)
                    }
                }
            }
        }
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

/** A small grid of preset color swatches for the text / highlight color pickers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPalette(
    colors: List<String>,
    onColorSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.width(180.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(color)))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

private val FONT_FAMILIES = listOf(
    "Arial",
    "Verdana",
    "Georgia",
    "Times New Roman",
    "Courier New",
    "Tahoma"
)

// Preset palettes for the text color and highlight (background) color pickers.
private val TEXT_COLORS = listOf(
    "#000000", "#7f7f7f", "#ffffff", "#880015",
    "#ed1c24", "#ff7f27", "#fff200", "#22b14c",
    "#00a2e8", "#3f48cc", "#a349a4", "#b97a57"
)
private val BG_COLORS = listOf(
    "#ffffff", "#fff2cc", "#f2f2f2", "#ffcc99",
    "#ff9999", "#ffccff", "#ccffcc", "#99ff99",
    "#99ccff", "#cce6ff", "#ffff99", "#ffcc66"
)

/** JS bridge: forwards editor content changes to Compose on the main thread. */
private class HtmlBridge {
    var onChange: ((String) -> Unit)? = null
    // Content height in CSS px (= dp in the WebView viewport). Used to stretch the WebView
    // to the content height so the whole message can be scrolled with the outer layout.
    var onHeightChange: ((Int) -> Unit)? = null
    // Non-observable fields (plain vars) so that reading/writing them in the AndroidView
    // `update` lambda (which runs during composition) never triggers a recomposition.
    var lastReported: String = ""
    var isUserEdit: Boolean = false

    @JavascriptInterface
    fun onHtmlChanged(html: String) {
        Handler(Looper.getMainLooper()).post { onChange?.invoke(html) }
    }

    @JavascriptInterface
    fun onHeightChanged(heightPx: Int) {
        Handler(Looper.getMainLooper()).post { onHeightChange?.invoke(heightPx) }
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
            #editor { min-height: 200px; padding: 12px; outline: none; white-space: pre-wrap; word-wrap: break-word; }
            #editor:empty::before { content: attr(data-placeholder); color: $placeholderColor; }
        </style>
        </head>
        <body>
        <div id="editor" contenteditable="true" data-placeholder="${placeholder.replace("\"", "&quot;")}"></div>
        <script>
            var lastNotified = '';
            // Report the editor's content height (CSS px = dp) so the WebView can stretch
            // to it and the surrounding layout can scroll the whole message.
            function reportHeight() {
                AndroidBridge.onHeightChanged(document.getElementById('editor').scrollHeight);
            }
            function notify() {
                var h = document.getElementById('editor').innerHTML;
                var t = h.replace(/<br\s*\/?>/g, '').replace(/<div>\s*<\/div>/g, '').replace(/<p>\s*<\/p>/g, '').trim();
                if (t === '') h = '';
                if (h !== lastNotified) {
                    lastNotified = h;
                    AndroidBridge.onHtmlChanged(h);
                }
                reportHeight();
            }
            // Replace the editor content while preserving the caret position, so an external
            // push (AI suggestion, draft restore) does not jump the caret to the start.
            function setContent(html, isHtml) {
                var ed = document.getElementById('editor');
                var caretOffset = 0;
                var sel = window.getSelection();
                if (sel && sel.rangeCount > 0) {
                    var range = sel.getRangeAt(0);
                    var pre = range.cloneRange();
                    pre.selectNodeContents(ed);
                    pre.setEnd(range.endContainer, range.endOffset);
                    caretOffset = pre.toString().length;
                }
                if (isHtml) { ed.innerHTML = html; } else { ed.textContent = html; }
                lastNotified = ed.innerHTML;
                var newSel = window.getSelection();
                var r = document.createRange();
                var offset = caretOffset;
                var done = false;
                var walker = document.createTreeWalker(ed, NodeFilter.SHOW_TEXT, null);
                var tn;
                while ((tn = walker.nextNode()) && !done) {
                    if (offset <= tn.length) { r.setStart(tn, offset); r.collapse(true); done = true; }
                    else { offset -= tn.length; }
                }
                if (!done) { r.selectNodeContents(ed); r.collapse(false); }
                newSel.removeAllRanges();
                newSel.addRange(r);
                ed.focus();
                reportHeight();
            }
            var ed = document.getElementById('editor');
            ed.addEventListener('input', notify);
            ed.addEventListener('keyup', notify);
            ed.addEventListener('mouseup', notify);
            $contentJs
            reportHeight();
        </script>
        </body>
        </html>
    """.trimIndent()
}
