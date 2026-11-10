package com.mailchat.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mailchat.R
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Message
import com.mailchat.ui.theme.WhatsAppBubbleIn
import com.mailchat.ui.theme.WhatsAppBubbleOut
import com.mailchat.ui.theme.WhatsAppDarkBubbleIn
import com.mailchat.ui.theme.WhatsAppDarkBubbleOut
import com.mailchat.ui.theme.WhatsAppDarkTextSecondary
import java.io.File
import com.mailchat.ui.theme.WhatsAppTextSecondary
import com.mailchat.util.DateUtils
import com.mailchat.util.normalizeWhitespace
import com.mailchat.util.stripHtml

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    attachments: List<Attachment> = emptyList(),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onAttachmentClick: ((Attachment) -> Unit)? = null,
    senderEmail: String? = null,
    senderName: String? = null,
    senderPhotoUri: String? = null,
    isGroup: Boolean = false,
    isSummarizing: Boolean = false,
    isEncrypted: Boolean = false,
    onImportKey: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOutgoing = !message.isIncoming
    val darkTheme = when (com.mailchat.util.SettingsUtils.getDarkMode(context)) {
        com.mailchat.util.SettingsUtils.DARK_MODE_LIGHT -> false
        com.mailchat.util.SettingsUtils.DARK_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val cleanBody = remember(message.body) { message.body.stripHtml().normalizeWhitespace() }
    val isPgpKeyEmail = remember(message.bodyFull) { message.bodyFull.contains("MAILCHAT_PGP:") }
    val displayBody = if (isPgpKeyEmail) {
        stringResource(com.mailchat.R.string.pgp_key_received)
    } else if (message.summary.isNotBlank()) {
        message.summary.normalizeWhitespace()
    } else {
        cleanBody
    }
    val bubbleColor = if (isOutgoing) {
        if (darkTheme) WhatsAppDarkBubbleOut else WhatsAppBubbleOut
    } else {
        if (darkTheme) WhatsAppDarkBubbleIn else WhatsAppBubbleIn
    }
    val shape = if (isOutgoing) {
        RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp)
    }

    // Nur lokal vorliegende Anhänge/Einbettungen anzeigen - niemals Remote-URLs laden
    val firstImage = attachments.firstOrNull { isImageFile(it) && resolveLocalFile(it, context) != null }
    val firstImageUri = firstImage?.let { resolveLocalFile(it, context)?.absolutePath }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isOutgoing) 48.dp else 8.dp,
                    end = if (isOutgoing) 8.dp else 48.dp,
                    bottom = 8.dp
                ),
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (firstImage != null) Modifier.fillMaxWidth()
                        else Modifier.widthIn(max = 300.dp)
                    )
                    .align(Alignment.Bottom)
            ) {
                if (isOutgoing && senderEmail != null) {
                    Text(
                        text = senderEmail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (darkTheme) WhatsAppDarkTextSecondary else WhatsAppTextSecondary,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 2.dp)
                    )
                }
                if (!isOutgoing && isGroup && senderName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        if (senderPhotoUri != null) {
                            AsyncImage(
                                model = senderPhotoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(if (isOutgoing) Alignment.End else Alignment.Start)
                        .then(if (message.isSpam || message.summary.isNotBlank() || message.customTag.isNotBlank() || isEncrypted) Modifier.padding(bottom = 2.dp) else Modifier)
                ) {
                    if (isEncrypted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF4CAF50))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                        }
                    }
                    // Signature status badge (incoming messages that were signed + encrypted).
                    // Only set for incoming mail, so this naturally limits the badge to those.
                    val sigStatus = message.signatureStatus
                    if (sigStatus.isNotBlank()) {
                        val sigColor = when (sigStatus) {
                            "VERIFIED" -> Color(0xFF4CAF50)
                            "INVALID" -> Color(0xFFD32F2F)
                            else -> Color(0xFFF9A825) // NOT_SIGNED / NO_KEY
                        }
                        val sigIcon = when (sigStatus) {
                            "VERIFIED" -> Icons.Default.CheckCircle
                            "INVALID" -> Icons.Default.Close
                            else -> Icons.Default.Warning
                        }
                        val sigLabel = when (sigStatus) {
                            "VERIFIED" -> stringResource(R.string.sig_verified)
                            "INVALID" -> stringResource(R.string.sig_invalid)
                            "NOT_SIGNED" -> stringResource(R.string.sig_not_signed)
                            else -> stringResource(R.string.sig_no_key)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(sigColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = sigIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = sigLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    if (message.isSpam) {
                        val spamColor = when (message.spamCategory) {
                            "phishing" -> Color(0xFFD32F2F)
                            "unwanted_ad" -> Color(0xFFF9A825)
                            "newsletter" -> Color(0xFF388E3C)
                            else -> MaterialTheme.colorScheme.error
                        }
                        val spamLabel = when (message.spamCategory) {
                            "phishing" -> stringResource(R.string.spam_phishing)
                            "unwanted_ad" -> stringResource(R.string.spam_unwanted)
                            "newsletter" -> stringResource(R.string.spam_newsletter)
                            else -> stringResource(R.string.spam)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(spamColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = spamLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    if (message.summary.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF7C4DFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    if (message.customTag.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1565C0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = message.customTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(bubbleColor)
                        .then(
                            if (onClick != null || onLongClick != null) {
                                Modifier.combinedClickable(
                                    onClick = { onClick?.invoke() },
                                    onLongClick = { onLongClick?.invoke() }
                                )
                            } else Modifier
                        )
                ) {
                if (firstImageUri != null) {
                    AsyncImage(
                        model = firstImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(shape)
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = message.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isSummarizing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = Color(0xFF7C4DFF)
                            )
                        }
                    } else {
                        Text(
                            text = displayBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isPgpKeyEmail && onImportKey != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = { onImportKey() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(com.mailchat.R.string.import_key))
                        }
                    }

                    val audioAttachments = attachments.filter { isAudioFile(it) }
                    val otherAttachments = attachments.filter { !isAudioFile(it) && it.contentId.isEmpty() }
                    if (audioAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        audioAttachments.forEach { audio ->
                            AudioPlayer(attachment = audio)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    if (otherAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AttachmentBar(
                            attachments = otherAttachments,
                            onAttachmentClick = onAttachmentClick
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isProcessed) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (message.isTodo) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = DateUtils.formatRelative(context, message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (darkTheme) WhatsAppDarkTextSecondary else WhatsAppTextSecondary
                        )
                    }
                }
                }
            }
        }
    }
}

private fun isAudioFile(attachment: Attachment): Boolean {
    val name = attachment.fileName.lowercase()
    val mime = attachment.mimeType.lowercase()
    return name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".ogg") ||
        name.endsWith(".wav") || name.endsWith(".aac") ||
        mime.startsWith("audio/")
}

private fun isImageFile(attachment: Attachment): Boolean {
    val name = attachment.fileName.lowercase()
    val mime = attachment.mimeType.lowercase()
    return mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
        name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
}

private fun resolveLocalFile(attachment: Attachment, context: android.content.Context): File? {
    val original = File(attachment.uri)
    if (original.exists()) return original
    // Fallback: im attachments-Verzeichnis suchen
    val attDir = File(context.filesDir, "attachments")
    if (attDir.exists()) {
        val searchName = attachment.fileName.ifBlank { attachment.contentId }
        if (searchName.isNotBlank()) {
            val found = attDir.listFiles()?.firstOrNull { it.name.endsWith(searchName) }
            if (found != null) return found
        }
    }
    return null
}