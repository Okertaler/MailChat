package com.mailchat.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mailchat.data.model.Chat
import com.mailchat.ui.theme.WhatsAppDarkTextSecondary
import com.mailchat.ui.theme.WhatsAppTextSecondary
import com.mailchat.ui.theme.WhatsAppUnreadBadge
import com.mailchat.util.DateUtils
import com.mailchat.util.EmailGrouping

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: Chat,
    avatarColor: Int,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSelectionToggle: (() -> Unit)? = null,
    photoUri: String? = null,
    @Suppress("UNUSED_PARAMETER") messageCount: Int = 0,
    spamCategory: String = "",
    displayName: String? = null,
    noReply: Boolean = false,
    isActive: Boolean = false,
    encryptionType: String = "",
    customTag: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val isGroup = EmailGrouping.isGroupKey(chat.participantEmail)
    val memberCount = if (isGroup) chat.participantEmail.split(";").count { it.isNotBlank() } else 0
    val shownName = displayName?.takeIf { it.isNotBlank() } ?: chat.participantName
    val secondaryTextColor = if (darkTheme) WhatsAppDarkTextSecondary else WhatsAppTextSecondary
    val subjectPreview = remember(chat.lastMessageSubject) {
        val subject = chat.lastMessageSubject
        if (subject.contains(":")) {
            subject.substringAfter(":").trim()
        } else {
            subject
        }.ifBlank { chat.participantEmail }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onSelectionToggle?.invoke() else onClick()
                },
                onLongClick = { onLongClick?.invoke() }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onSelectionToggle?.invoke() }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier.clickable {
                onLongClick?.invoke()
            }
        ) {
            if (isGroup) {
                GroupAvatar(
                    color = avatarColor,
                    size = 48
                )
            } else if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(avatarColor), CircleShape)
                )
            } else {
                AccountAvatar(
                    name = shownName,
                    color = avatarColor,
                    size = 48
                )
            }
            if (noReply) {
                Text(
                    text = "🚫",
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                )
            }
            if (encryptionType != "") {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    // green = OpenPGP key, blue = legacy MailChat key
                    tint = if (encryptionType == "openpgp") Color(0xFF4CAF50) else Color(0xFF2196F3),
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(Color.White, CircleShape)
                )
            }
            if (customTag.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = customTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        maxLines = 1
                    )
                }
            }
            // Auto-reply "Wo bist Du?" indicators (1:1 chats only): location bottom-left,
            // weather bottom-right — same as on the chat detail screen. Drawn last so they
            // are always in the foreground (on top of the photo and the custom-tag badge).
            if (!isGroup) {
                val mainParticipant = chat.participantEmail.trim().lowercase()
                val autoLocationEnabled = mainParticipant.isNotBlank() &&
                    com.mailchat.util.SettingsUtils.getAutoLocationReply(context, mainParticipant)
                val autoWeatherEnabled = autoLocationEnabled &&
                    com.mailchat.util.SettingsUtils.getAutoWeather(context, mainParticipant)
                if (autoLocationEnabled) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .size(15.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = (-2).dp, y = 2.dp)
                            .background(Color.White, CircleShape)
                    )
                }
                if (autoWeatherEnabled) {
                    Icon(
                        Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .size(15.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shownName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isGroup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$memberCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (chat.favorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (chat.important) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (spamCategory.isNotBlank() && spamCategory != "ham") {
                    val spamColor = when (spamCategory) {
                        "phishing" -> Color(0xFFD32F2F)
                        "unwanted_ad" -> Color(0xFFF9A825)
                        "newsletter" -> Color(0xFF388E3C)
                        else -> Color.Gray
                    }
                    val spamLabel = when (spamCategory) {
                        "phishing" -> "Phishing"
                        "unwanted_ad" -> "Unwanted"
                        "newsletter" -> "Newsletter"
                        else -> "Spam"
                    }
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(spamColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = spamLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = DateUtils.formatRelative(context, chat.lastMessageTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unreadCount > 0) WhatsAppUnreadBadge else secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.size(2.dp))

            Text(
                text = subjectPreview,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (chat.unreadCount > 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onSurface else secondaryTextColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (chat.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(WhatsAppUnreadBadge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}