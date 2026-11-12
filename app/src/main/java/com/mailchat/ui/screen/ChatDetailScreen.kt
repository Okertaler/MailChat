package com.mailchat.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.mailchat.util.stripHtml
import com.mailchat.util.normalizeWhitespace
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mailchat.R
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Message
import com.mailchat.ui.component.ChatBubble
import com.mailchat.ui.component.AccountAvatar
import com.mailchat.ui.component.GroupAvatar
import com.mailchat.ui.viewmodel.ChatDetailViewModel
import com.mailchat.util.DateUtils
import com.mailchat.util.EmailGrouping
import com.mailchat.util.SettingsUtils
import com.mailchat.util.VoiceRecorder
import com.mailchat.util.cleanInvisible
import com.mailchat.util.sanitizeFileName
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: Long,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val aiEnabled = SettingsUtils.getAiEnabled(context)
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var messageMenuFor by remember { mutableStateOf<Message?>(null) }
    var customTagMessage by remember { mutableStateOf<Message?>(null) }
    var replyToneMessage by remember { mutableStateOf<Message?>(null) }
    var showComposeAiTone by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var showEmailAttachDialog by remember { mutableStateOf(false) }
    var selectedEmailIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var archiveFile by remember { mutableStateOf<java.io.File?>(null) }
    val openAttachmentSmart: (Attachment) -> Unit = { att ->
        if ((att.fileName.endsWith(".zip", ignoreCase = true) || att.mimeType.contains("zip")) && java.io.File(att.uri).exists()) {
            archiveFile = java.io.File(att.uri)
        } else {
            com.mailchat.util.AttachmentUtils.openAttachment(context, att)
        }
    }

    var selectedSubject by remember { mutableStateOf("") }
    var showSubjectDropdown by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var messageSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMessageEditSheet by remember { mutableStateOf(false) }
    var showSpamCategoryDialog by remember { mutableStateOf(false) }
    // True when the spam category dialog was opened from the header button and therefore applies
    // to the whole conversation (all incoming messages), not just the selected messages.
    var spamDialogWholeChat by remember { mutableStateOf(false) }
    // Set when the spam category dialog was opened from the "full email" detail view; the dialog
    // then applies to that single message (and, for phishing/ham, to the whole sender).
    var spamDialogSingleMessage by remember { mutableStateOf<Message?>(null) }
    val chatSubjects = remember(uiState.messages) {
        val subjects = uiState.messages.map { it.subject }.filter { it.isNotBlank() }.toSet()
        (listOf("MailChat für Dich", "Sprachnachricht für Dich") + subjects.toList()).distinct()
    }
    val maxEmailSizeBytes = remember {
        SettingsUtils.getMaxEmailSizeMb(context) * 1024L * 1024L
    }
    val lazyListState = rememberLazyListState()
    var previousMessageCount by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.draftSubject) {
        uiState.draftSubject?.let { draft ->
            if (selectedSubject.isBlank()) selectedSubject = draft
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // permission denied, do nothing
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds += 1
            }
        } else {
            recordingSeconds = 0
        }
    }

    // Scroll to the bottom whenever the message count grows: on initial load, when the user
    // sends a message, and when a new email arrives in the chat. animateScrollToItem uses a
    // smooth spring animation (no pause, no jump) so the list glides down to the newest message.
    LaunchedEffect(uiState.messages.size) {
        val currentCount = uiState.messages.size
        if (currentCount > 0 && currentCount > previousMessageCount) {
            lazyListState.animateScrollToItem(currentCount - 1)
        }
        previousMessageCount = currentCount
    }

    // Auto-open a specific message in full view (e.g. from the home-screen widget when
    // configured to open the email detail). We observe BOTH the pending id and the message
    // list: the list may still be loading when the id arrives (cold start), and the id may
    // arrive while this chat is already open (widget tap). The pending id is only cleared
    // once the message has actually been found, so a premature clear can't lose the request.
    LaunchedEffect(Unit) {
        snapshotFlow {
            com.mailchat.ui.navigation.PendingMessageStore.messageId to uiState.messages
        }
            .filter { (pendingId, _) -> pendingId != null }
            .collect { (pendingId, messages) ->
                val message = messages.firstOrNull { it.id == pendingId }
                if (message != null) {
                    com.mailchat.ui.navigation.PendingMessageStore.messageId = null
                    selectedMessage = message
                }
            }
    }



    val filteredMessages = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.messages
        } else {
            uiState.messages.filter {
                it.subject.contains(searchQuery, ignoreCase = true) ||
                    it.body.contains(searchQuery, ignoreCase = true) ||
                    it.bodyFull.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            val file = copyUriToFile(context, picked)
            if (file != null) viewModel.addComposeAttachment(file)
        }
    }

    var photoPickEmail by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val email = photoPickEmail
        if (uri != null && email != null) {
            viewModel.setContactPhoto(email, uri)
        }
        photoPickEmail = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val file = java.io.File(context.cacheDir, "camera_capture.jpg")
            if (file.exists()) {
                val compressed = compressImage(file, 800)
                if (compressed != null) {
                    viewModel.addComposeAttachment(compressed)
                }
            }
        }
    }
    val cameraFile = remember { java.io.File(context.cacheDir, "camera_capture.jpg") }
    val cameraUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", cameraFile
        )
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val contactUri = data?.data
            if (contactUri != null) {
                val cursor = context.contentResolver.query(contactUri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val emailIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
                    val email = if (emailIdx >= 0) cursor.getString(emailIdx) ?: "" else ""
                    cursor.close()
                    val vCard = "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nEMAIL:$email\nEND:VCARD"
                    val vCardFile = java.io.File(context.cacheDir, "contact_${name.ifBlank { "unknown" }}.vcf")
                    vCardFile.writeText(vCard)
                    viewModel.addComposeAttachment(vCardFile)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.markAsRead()
        if (uiState.chat?.participantEmail == "florescu@web.de") {
            selectedSubject = context.resources.getString(R.string.dev_chat_subject)
        }
    }

    BackHandler(enabled = messageSelectionMode) {
        messageSelectionMode = false
        selectedMessageIds = emptySet()
    }

    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
    }

    BackHandler(enabled = selectedMessage != null) {
        selectedMessage = null
    }

    BackHandler(enabled = messageMenuFor != null) {
        messageMenuFor = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (messageSelectionMode) {
                        Text(
                            text = stringResource(R.string.select_messages, selectedMessageIds.size),
                            style = MaterialTheme.typography.titleLarge
                        )
                    } else {
                        val isGroup = EmailGrouping.isGroupKey(uiState.chat?.participantEmail ?: "")
                        val members = uiState.chat?.participantEmail
                            ?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
                        val selectedRecipient = uiState.selectedRecipient
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showMembersDialog = true }
                                .padding(vertical = 4.dp)
                        ) {
                            if (isGroup) {
                                GroupAvatar(color = uiState.accountColor, size = 36)
                            } else {
                                val encryptionType = when {
                                    com.mailchat.crypto.OpenPgpKeyService.getPeer(context, uiState.chat?.participantEmail ?: "") != null -> "openpgp"
                                    com.mailchat.crypto.CryptoService.getPeers(context)
                                        .any { it.email.lowercase() == (uiState.chat?.participantEmail ?: "").lowercase() && it.pinVerified } -> "legacy"
                                    else -> ""
                                }
                                val isNoReply = com.mailchat.util.EmailGrouping.isNoReplyEmail(uiState.chat?.participantEmail ?: "")
                                // Auto-reply "Wo bist Du?" indicators on the contact photo.
                                val mainParticipant = (uiState.chat?.participantEmail ?: "").trim().lowercase()
                                val autoLocationEnabled = mainParticipant.isNotBlank() &&
                                    com.mailchat.util.SettingsUtils.getAutoLocationReply(context, mainParticipant)
                                val autoWeatherEnabled = autoLocationEnabled &&
                                    com.mailchat.util.SettingsUtils.getAutoWeather(context, mainParticipant)
                                Box {
                                    ChatHeaderAvatar(
                                        name = uiState.contactName ?: uiState.chat?.participantName ?: "",
                                        photoUri = uiState.contactPhotoUri,
                                        accountColor = uiState.accountColor
                                    )
                                    if (isNoReply) {
                                        Text(
                                            text = "🚫",
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = (-4).dp, y = (-4).dp)
                                        )
                                    }
                                    if (encryptionType != "") {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            // green = OpenPGP key, blue = legacy MailChat key
                                            tint = if (encryptionType == "openpgp") Color(0xFF4CAF50) else Color(0xFF2196F3),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                    if (autoLocationEnabled) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = stringResource(R.string.auto_reply_location),
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .align(Alignment.BottomStart)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                    if (autoWeatherEnabled) {
                                        Icon(
                                            Icons.Outlined.Cloud,
                                            contentDescription = stringResource(R.string.auto_reply_weather),
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .align(Alignment.BottomEnd)
                                                .offset(x = (-2).dp, y = 2.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = uiState.chat?.participantName
                                        ?.takeIf { it.isNotBlank() }
                                        ?: uiState.contactName
                                        ?: stringResource(R.string.chat),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (isGroup) {
                                    GroupMemberAvatars(
                                        emails = members,
                                        contactPhotos = uiState.contactPhotos,
                                        contactNames = uiState.contactNames
                                    )
                                }
                                if (isGroup) {
                                    Text(
                                        text = stringResource(R.string.members_count, members.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedRecipient != null) {
                                    Text(
                                        text = stringResource(R.string.reply_to, selectedRecipient),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (messageSelectionMode) {
                        IconButton(onClick = {
                            messageSelectionMode = false
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (messageSelectionMode) {
                        // Explicit button to leave multi-select mode (in addition to the back arrow).
                        IconButton(onClick = {
                            messageSelectionMode = false
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.done)
                            )
                        }
                        if (selectedMessageIds.isNotEmpty()) {
                            val allSelected = filteredMessages.isNotEmpty() && selectedMessageIds.size >= filteredMessages.size
                            val selectedMsgs = filteredMessages.filter { it.id in selectedMessageIds }
                            val isSingle = selectedMsgs.size == 1
                            val singleMsg = selectedMsgs.firstOrNull()
                            Box {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { showMessageEditSheet = true }) {
                                        Text(
                                            stringResource(R.string.edit),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    com.mailchat.ui.component.HelpIcon(
                                        title = stringResource(R.string.help_selection_title),
                                        text = stringResource(R.string.help_selection_text)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMessageEditSheet,
                                    onDismissRequest = { showMessageEditSheet = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)) },
                                        leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                        onClick = {
                                            selectedMessageIds = if (allSelected) emptySet() else filteredMessages.map { it.id }.toSet()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.reverse_selection)) },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                                        onClick = {
                                            val allIds = filteredMessages.map { it.id }.toSet()
                                            selectedMessageIds = allIds - selectedMessageIds
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.select_recipients)) },
                                        leadingIcon = { Icon(Icons.Default.Person, null) },
                                        onClick = {
                                            selectedMessageIds = filteredMessages.filter { it.isIncoming }.map { it.id }.toSet()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.select_senders)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                                        onClick = {
                                            selectedMessageIds = filteredMessages.filter { !it.isIncoming }.map { it.id }.toSet()
                                        }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    val isNoReply = uiState.chat?.participantEmail
                                        ?.let { com.mailchat.util.EmailGrouping.isNoReplyEmail(it) } ?: false
                                    val isPhishing = selectedMsgs.any { it.spamCategory == "phishing" }
                                    val canReply = isSingle && !isNoReply && !isPhishing
                                    if (aiEnabled) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ai_reply)) },
                                            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, tint = if (canReply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                                            enabled = canReply,
                                            onClick = {
                                                singleMsg?.let { replyToneMessage = it }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ai_summarize)) },
                                            leadingIcon = {
                                                if (uiState.isAiProcessing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Outlined.Summarize, null)
                                                }
                                            },
                                            enabled = !uiState.isAiProcessing,
                                            onClick = {
                                                viewModel.summarizeMessages(selectedMsgs)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.reply_quote_5)) },
                                        leadingIcon = { Icon(Icons.Default.Reply, null, tint = if (canReply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                                        enabled = canReply,
                                        onClick = {
                                            singleMsg?.let { viewModel.replyWithFiveLinesQuote(it) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.forward_5_lines)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, null, tint = if (isSingle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                                        enabled = isSingle,
                                        onClick = {
                                            singleMsg?.let { forwardMessage = it }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.copy_5_lines)) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = if (isSingle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                                        enabled = isSingle,
                                        onClick = {
                                            singleMsg?.let { viewModel.copyFiveLines(it) }
                                        }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.spam_category)) },
                                        leadingIcon = { Icon(Icons.Default.Report, null) },
                                        onClick = {
                                            spamDialogWholeChat = false
                                            showSpamCategoryDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_custom_tags)) },
                                        leadingIcon = { Icon(Icons.Default.Label, null) },
                                        onClick = {
                                            customTagMessage = singleMsg
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.to_processed)) },
                                        leadingIcon = { Icon(Icons.Default.Flag, null, tint = Color(0xFFF44336)) },
                                        onClick = {
                                            viewModel.setTodoForMessages(selectedMsgs, true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.mark_processed)) },
                                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)) },
                                        onClick = {
                                            viewModel.setProcessedForMessages(selectedMsgs, true)
                                        }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            viewModel.deleteMessages(selectedMessageIds.toList())
                                            selectedMessageIds = emptySet()
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        if (uiState.messages.any { it.body.isBlank() && it.bodyFull.isBlank() }) {
                            IconButton(onClick = { viewModel.refetchEmptyMessages() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-fetch empty")
                            }
                        }
                        IconButton(
                            onClick = { messageSelectionMode = true },
                            enabled = !uiState.isSending
                        ) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_messages, 0)
                            )
                        }
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        // Spam classification for the whole conversation (all incoming messages).
                        IconButton(onClick = {
                            spamDialogWholeChat = true
                            showSpamCategoryDialog = true
                        }) {
                            Icon(
                                Icons.Default.Report,
                                contentDescription = stringResource(R.string.spam_category)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val isNoReply = uiState.chat?.participantEmail
                ?.let { EmailGrouping.isNoReplyEmail(it) } ?: false
            val hasPhishing = uiState.messages.any { it.isIncoming && it.spamCategory == "phishing" }
            if (!isNoReply && !hasPhishing) {
                // imePadding: push the whole bottom bar (input field) up by the keyboard
                // height so the input stays visible when the on-screen keyboard is open.
                Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                if (uiState.aiSuggestion.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        viewModel.onComposeTextChange(uiState.aiSuggestion)
                                        viewModel.clearAiSuggestion()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = uiState.aiSuggestion.take(80),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else if (uiState.isGeneratingSuggestion) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ai_generating),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (uiState.aiError.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI: ${uiState.aiError.take(60)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.generateReplySuggestion(selectedSubject)
                            }) {
                                Text("Retry", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        val isGroupChat = EmailGrouping.isGroupKey(uiState.chat?.participantEmail ?: "")
                        if (isGroupChat) {
                            // Group chats always use the group name as the subject: fixed, not editable.
                            OutlinedTextField(
                                value = uiState.chat?.participantName ?: "",
                                onValueChange = {},
                                enabled = false,
                                placeholder = { Text(stringResource(R.string.subject)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        } else {
                            OutlinedTextField(
                                value = selectedSubject,
                                onValueChange = {
                                    selectedSubject = it
                                    viewModel.onSubjectChange(it)
                                },
                                placeholder = { Text(stringResource(R.string.subject)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { showSubjectDropdown = true }) {
                                        Text("▼", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                        }
                    }
                    // Subject picker: a dialog with a list that opens scrolled to the bottom so
                    // the most recent subject is visible. (A DropdownMenu + verticalScroll crashes
                    // because the menu measures its content with unbounded height constraints.)
                    if (showSubjectDropdown) {
                        val subjectListState = rememberLazyListState()
                        LaunchedEffect(Unit) {
                            if (chatSubjects.isNotEmpty()) {
                                subjectListState.scrollToItem(chatSubjects.size - 1)
                            }
                        }
                        Dialog(
                            onDismissRequest = { showSubjectDropdown = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(stringResource(R.string.subject), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyColumn(
                                        state = subjectListState,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(chatSubjects) { subject ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        selectedSubject = subject
                                                        showSubjectDropdown = false
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = subject,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (subject == selectedSubject) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.compose_options),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.upload_media)) },
                                onClick = {
                                    showAddMenu = false
                                    filePicker.launch(arrayOf("*/*"))
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.take_photo)) },
                                onClick = {
                                    showAddMenu = false
                                    cameraFile.delete()
                                    cameraLauncher.launch(cameraUri)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.attach_contact)) },
                                onClick = {
                                    showAddMenu = false
                                    val intent = android.content.Intent(android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_TYPE).apply {
                                        action = android.content.Intent.ACTION_PICK
                                        data = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI
                                    }
                                    contactPickerLauncher.launch(intent)
                                }
                            )
                            if (aiEnabled) {
                                androidx.compose.material3.DropdownMenuItem(
                                    enabled = !uiState.isGeneratingSuggestion,
                                    leadingIcon = {
                                        if (uiState.isGeneratingSuggestion) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("✦", color = androidx.compose.ui.graphics.Color(0xFF7C4DFF), fontSize = 20.sp)
                                        }
                                    },
                                    text = { Text(stringResource(R.string.ai_reply)) },
                                    onClick = {
                                        showAddMenu = false
                                        val latestIncoming = uiState.messages.lastOrNull { it.isIncoming }
                                        if (latestIncoming != null) {
                                            replyToneMessage = latestIncoming
                                        }
                                    }
                                )
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.attach_emails)) },
                                onClick = {
                                    showAddMenu = false
                                    selectedEmailIds = emptySet()
                                    showEmailAttachDialog = true
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.add_my_location)) },
                                onClick = {
                                    showAddMenu = false
                                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (!hasPermission) {
                                        android.widget.Toast.makeText(context, R.string.location_permission_needed, android.widget.Toast.LENGTH_SHORT).show()
                                        return@DropdownMenuItem
                                    }
                                    try {
                                        val location = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                            ?: lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
                                        if (location != null) {
                                            val lat = location.latitude
                                            val lon = location.longitude
                                            val locationText = "\n📍 Mein Standort:\n" +
                                                "OpenStreetMap: https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon\n" +
                                                "Google Maps: https://www.google.com/maps?q=$lat,$lon\n" +
                                                "Apple Maps: https://maps.apple.com/?ll=$lat,$lon"
                                            viewModel.onComposeTextChange(uiState.composeText + locationText)
                                            android.widget.Toast.makeText(context, R.string.location_added, android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, R.string.location_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, R.string.location_error, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            // "Wo bist Du?" — sends the question as a plain text message. If the
                            // contact has the auto-location-reply enabled, their app replies with
                            // their location (and optionally the local weather).
                            val whereAreYouText = stringResource(R.string.where_are_you_text)
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.where_are_you)) },
                                onClick = {
                                    showAddMenu = false
                                    viewModel.onComposeTextChange(
                                        (uiState.composeText + "\n" + whereAreYouText).trim()
                                    )
                                }
                            )
                            if (uiState.chat?.participantEmail == "florescu@web.de") {
                                androidx.compose.material3.DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Default.Description, contentDescription = null)
                                    },
                                    text = { Text(stringResource(R.string.attach_log)) },
                                    onClick = {
                                        showAddMenu = false
                                        val logFile = com.mailchat.ai.AppLogger.getLogFile(context)
                                        if (logFile != null) {
                                            val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
                                            val copy = File(cacheDir, "mailchat_log.txt")
                                            logFile.copyTo(copy, overwrite = true)
                                            viewModel.addComposeAttachment(copy)
                                        }
                                        if (selectedSubject.isBlank() || selectedSubject == null) {
                                            selectedSubject = "MailChat Anfrage"
                                        }
                                    }
                                )
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Compress, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.compress)) },
                                onClick = {
                                    showAddMenu = false
                                    showCompressDialog = true
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    if (uiState.quoteEnabled) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(24.dp))
                                    }
                                },
                                text = { Text(stringResource(R.string.quote_toggle)) },
                                onClick = {
                                    showAddMenu = false
                                    viewModel.toggleQuote()
                                }
                            )
                        }
                    }
                }
                if (uiState.composeCc.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CC:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.composeCc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearComposeCc() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                ComposeBar(
                    text = uiState.composeText,
                    onTextChange = viewModel::onComposeTextChange,
                    attachments = uiState.composeAttachments,
                    onRemoveAttachment = viewModel::removeComposeAttachment,
                    onRemoveAllAttachments = viewModel::removeAllComposeAttachments,
                    onSend = {
                        val size = calculateEmailSize(uiState.composeText, uiState.composeAttachments)
                        if (size > maxEmailSizeBytes) {
                            showSizeWarning = true
                        } else {
                            viewModel.send(subject = selectedSubject.ifBlank { null })
                            selectedSubject = ""
                            viewModel.onSubjectChange("")
                        }
                    },
                    isSending = uiState.isSending,
                    sendError = uiState.sendError,
                    isRecording = isRecording,
                    recordingSeconds = recordingSeconds,
                    onRecordStart = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val file = VoiceRecorder.start(context)
                            if (file != null) {
                                isRecording = true
                            }
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRecordStop = {
                        if (isRecording) {
                            val file = VoiceRecorder.stop()
                            isRecording = false
                            if (file != null) {
                                viewModel.addComposeAttachment(file)
                            }
                        }
                    },
                    onRecordCancel = {
                        VoiceRecorder.cancel()
                        isRecording = false
                    }
                )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_messages)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            // "AI in use" banner with a stop button (same as in the chat list overview).
            if (uiState.isAiProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai_in_use),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.stopAiProcessing() }) {
                        Text(
                            text = stringResource(R.string.ai_stop),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val bgTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(150.dp)
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 20.dp)
                        .alpha(0.06f)
                )
                Icon(Icons.Outlined.Notifications, null, tint = bgTint,
                    modifier = Modifier.size(90.dp).align(Alignment.TopCenter).padding(top = 24.dp))
                Icon(Icons.Outlined.SmartToy, null, tint = bgTint,
                    modifier = Modifier.size(120.dp).align(Alignment.TopEnd).padding(end = 24.dp, top = 48.dp))
                Icon(Icons.Outlined.Lock, null, tint = bgTint,
                    modifier = Modifier.size(110.dp).align(Alignment.CenterStart).padding(start = 36.dp))
                Icon(Icons.Outlined.VerifiedUser, null, tint = bgTint,
                    modifier = Modifier.size(100.dp).align(Alignment.Center))
                Icon(Icons.Outlined.Key, null, tint = bgTint,
                    modifier = Modifier.size(110.dp).align(Alignment.CenterEnd).padding(end = 36.dp))
                Icon(Icons.Outlined.Public, null, tint = bgTint,
                    modifier = Modifier.size(120.dp).align(Alignment.BottomStart).padding(start = 24.dp, bottom = 48.dp))
                Icon(Icons.Outlined.Cloud, null, tint = bgTint,
                    modifier = Modifier.size(90.dp).align(Alignment.BottomCenter).padding(bottom = 24.dp))
                Icon(Icons.Outlined.Save, null, tint = bgTint,
                    modifier = Modifier.size(110.dp).align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 20.dp))
                Icon(Icons.Outlined.Email, null, tint = bgTint,
                    modifier = Modifier.size(80.dp).align(Alignment.TopCenter).offset(x = (-90).dp, y = 90.dp))
                Icon(Icons.Outlined.Event, null, tint = bgTint,
                    modifier = Modifier.size(70.dp).align(Alignment.TopCenter).offset(x = 90.dp, y = 90.dp))
                Icon(Icons.Outlined.Fingerprint, null, tint = bgTint,
                    modifier = Modifier.size(80.dp).align(Alignment.Center).offset(x = 90.dp, y = 90.dp))
                Icon(Icons.Outlined.Star, null, tint = bgTint,
                    modifier = Modifier.size(70.dp).align(Alignment.Center).offset(x = (-90).dp, y = (-90).dp))
                Icon(Icons.Outlined.Send, null, tint = bgTint,
                    modifier = Modifier.size(80.dp).align(Alignment.BottomCenter).offset(x = (-90).dp, y = (-90).dp))
                Icon(Icons.Outlined.Wifi, null, tint = bgTint,
                    modifier = Modifier.size(70.dp).align(Alignment.Center).offset(y = 130.dp))
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (filteredMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Note: no imePadding here — the bottom bar (input field) applies it, which
                    // grows the bottom bar by the keyboard height and shrinks this list accordingly.
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        reverseLayout = false
                    ) {
                        items(
                            items = filteredMessages,
                            key = { it.id }
                        ) { message ->
                            val attachments = uiState.attachmentsByMessage[message.id] ?: emptyList()
                            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                                positionalThreshold = { total -> total * 0.3f },
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            if (messageSelectionMode) {
                                                selectedMessageIds = selectedMessageIds - message.id
                                            } else {
                                                messageToDelete = message
                                            }
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            if (messageSelectionMode) {
                                                selectedMessageIds = selectedMessageIds + message.id
                                            } else {
                                                viewModel.cycleMessageFlags(message)
                                            }
                                        }
                                        else -> {}
                                    }
                                    false
                                }
                            )
                            androidx.compose.material3.SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled ||
                                        dismissState.currentValue != SwipeToDismissBoxValue.Settled
                                    if (active) {
                                        val isRightSwipe = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
                                            (dismissState.targetValue == SwipeToDismissBoxValue.Settled && dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd)
                                        Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        if (messageSelectionMode) {
                                            Icon(
                                                imageVector = if (isRightSwipe) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp),
                                                tint = if (isRightSwipe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Icon(
                                                imageVector = when {
                                                    !isRightSwipe -> Icons.Default.DeleteOutline
                                                    message.isTodo -> Icons.Default.CheckCircle
                                                    message.isProcessed -> Icons.Default.Block
                                                    else -> Icons.Default.Flag
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp),
                                                tint = when {
                                                    !isRightSwipe -> MaterialTheme.colorScheme.error
                                                    message.isTodo -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                    message.isProcessed -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    else -> androidx.compose.ui.graphics.Color(0xFFF44336)
                                                }
                                            )
                                        }
                                    }
                                    }
                                }
                            ) {
                            val isGroupChat = com.mailchat.util.EmailGrouping.isGroupKey(uiState.chat?.participantEmail ?: "")
                            val senderEmailLower = message.from.lowercase()
                            val isSelected = message.id in selectedMessageIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (messageSelectionMode) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedMessageIds = if (isSelected) selectedMessageIds - message.id else selectedMessageIds + message.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ChatBubble(
                                        message = message,
                                        attachments = attachments,
                                        onClick = {
                                            if (messageSelectionMode) {
                                                selectedMessageIds = if (isSelected) selectedMessageIds - message.id else selectedMessageIds + message.id
                                            } else {
                                                selectedMessage = message
                                            }
                                        },
                                        onLongClick = {
                                            if (messageSelectionMode) {
                                                selectedMessageIds = if (isSelected) selectedMessageIds - message.id else selectedMessageIds + message.id
                                            } else {
                                                messageSelectionMode = true
                                                selectedMessageIds = setOf(message.id)
                                            }
                                        },
                                        onAttachmentClick = { attachment ->
                                            if (message.spamCategory == "phishing") {
                                                android.widget.Toast.makeText(context, "Anhänge aus Phishing-E-mails können nicht geöffnet werden", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                openAttachmentSmart(attachment)
                                            }
                                        },
                                        senderEmail = if (!message.isIncoming) message.from else null,
                                        senderName = if (!message.isIncoming && isGroupChat) (uiState.contactNames[senderEmailLower] ?: message.from) else null,
                                        senderPhotoUri = if (!message.isIncoming && isGroupChat) uiState.contactPhotos[senderEmailLower] else null,
                                        isGroup = isGroupChat,
                                        isSummarizing = uiState.currentSummarizingMessageId == message.id,
                                        isEncrypted = message.isEncrypted,
                                        onImportKey = {
                                            val parsed = com.mailchat.crypto.KeyExchangeService.parseQrScanResult(message.bodyFull)
                                            if (parsed != null) {
                                                val (email, fingerprint, pubKey) = parsed
                                                val effectiveEmail = if (email.isNotBlank()) email else message.from.lowercase()
                                                com.mailchat.crypto.CryptoService.addPeer(
                                                    context,
                                                    com.mailchat.crypto.CryptoService.PeerKey(
                                                        email = effectiveEmail,
                                                        publicKeyArmored = pubKey,
                                                        fingerprint = fingerprint,
                                                        pinVerified = false
                                                    )
                                                )
                                                android.widget.Toast.makeText(context, R.string.key_imported, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                }

                messageMenuFor?.let { actionMessage ->
                    ModalBottomSheet(
                        onDismissRequest = { messageMenuFor = null }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(androidx.compose.ui.graphics.Color(0xFF9E9E9E))
                            )
                        }
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.toggleProcessed(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = if (actionMessage.isProcessed) 1f else 0.5f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.mark_processed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (actionMessage.isProcessed) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.toggleTodo(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = androidx.compose.ui.graphics.Color(0xFFF44336).copy(alpha = if (actionMessage.isTodo) 1f else 0.5f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.mark_todo),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (actionMessage.isTodo) androidx.compose.ui.graphics.Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.copyFiveLines(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.copy_5_lines),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.forwardFiveLines(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Forward,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.forward_5_lines),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.toggleSpam(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Report,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (actionMessage.isSpam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (actionMessage.isSpam) stringResource(R.string.not_spam) else stringResource(R.string.mark_spam),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (actionMessage.isSpam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        customTagMessage = actionMessage
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (actionMessage.customTag.isNotBlank()) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (actionMessage.customTag.isNotBlank()) "${stringResource(R.string.ai_custom_tags)}: ${actionMessage.customTag}" else stringResource(R.string.ai_custom_tags),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (actionMessage.customTag.isNotBlank()) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        viewModel.replyWithFiveLinesQuote(actionMessage)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.reply_quote_5),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (aiEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            messageMenuFor = null
                                            viewModel.summarizeMessage(actionMessage)
                                        }
                                        .padding(horizontal = 24.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Summarize,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.ai_summarize),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !uiState.isGeneratingSuggestion) {
                                            messageMenuFor = null
                                            replyToneMessage = actionMessage
                                        }
                                        .padding(horizontal = 24.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (uiState.isGeneratingSuggestion) 0.4f else 1f)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.ai_reply),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (uiState.isGeneratingSuggestion) 0.4f else 1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageMenuFor = null
                                        messageToDelete = actionMessage
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.delete),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    customTagMessage?.let { targetMessage ->
        val tags = com.mailchat.util.SettingsUtils.getCustomTags(androidx.compose.ui.platform.LocalContext.current).filter { it.name.isNotBlank() }
        AlertDialog(
            onDismissRequest = { customTagMessage = null },
            title = { Text(stringResource(R.string.ai_custom_tags)) },
            text = {
                Column {
                    tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCustomTag(targetMessage, tag.name)
                                    customTagMessage = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                tint = if (targetMessage.customTag == tag.name) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setCustomTag(targetMessage, "")
                                customTagMessage = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { customTagMessage = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.encryptionErrorDialog?.let {
        // Not dismissible via back button or outside tap: the user must explicitly
        // confirm with "Ja" (send unencrypted) or "Nein" (abort).
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.encryption_error_title)) },
            text = { Text(stringResource(R.string.encryption_error_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissEncryptionErrorDialog()
                    viewModel.retryEncryptionErrorUnencrypted()
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEncryptionErrorDialog() }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (showSpamCategoryDialog) {
        val spamCategories = listOf(
            "ham" to stringResource(R.string.spam_ham),
            "newsletter" to stringResource(R.string.spam_newsletter),
            "unwanted_ad" to stringResource(R.string.spam_unwanted),
            "phishing" to stringResource(R.string.spam_phishing)
        )
        // Whole-conversation mode (header button) targets all incoming messages of the chat
        // (ignoring any active search filter); single-message mode (full email view) targets the
        // one open message; selection mode targets only the selected messages.
        val targetMessages = if (spamDialogWholeChat) {
            uiState.messages.filter { it.isIncoming }
        } else if (spamDialogSingleMessage != null) {
            listOfNotNull(uiState.messages.firstOrNull { it.id == spamDialogSingleMessage?.id })
        } else {
            filteredMessages.filter { it.id in selectedMessageIds }
        }
        AlertDialog(
            onDismissRequest = {
                showSpamCategoryDialog = false
                spamDialogSingleMessage = null
            },
            title = { Text(stringResource(R.string.spam_category)) },
            text = {
                Column {
                    spamCategories.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSpamCategoryForMessages(targetMessages, value)
                                    showSpamCategoryDialog = false
                                    spamDialogSingleMessage = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Report,
                                contentDescription = null,
                                tint = when (value) {
                                    "phishing" -> Color(0xFFD32F2F)
                                    "unwanted_ad" -> Color(0xFFF9A825)
                                    "newsletter" -> Color(0xFF388E3C)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // Custom tags section (single-message mode only): the same dialog also lets
                    // the user set the custom tag for this message, so one button covers both
                    // the spam category and the own tags.
                    val singleMsg = spamDialogSingleMessage
                    if (singleMsg != null) {
                        val tags = com.mailchat.util.SettingsUtils
                            .getCustomTags(androidx.compose.ui.platform.LocalContext.current)
                            .filter { it.name.isNotBlank() }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.ai_custom_tags),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        tags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setCustomTag(singleMsg, tag.name)
                                        showSpamCategoryDialog = false
                                        spamDialogSingleMessage = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Label,
                                    contentDescription = null,
                                    tint = if (singleMsg.customTag == tag.name) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = tag.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCustomTag(singleMsg, "")
                                    showSpamCategoryDialog = false
                                    spamDialogSingleMessage = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showSpamCategoryDialog = false
                    spamDialogSingleMessage = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    selectedMessage?.let { message ->
        val liveMessage = uiState.messages.firstOrNull { it.id == message.id } ?: message
        val attachments = uiState.attachmentsByMessage[liveMessage.id] ?: emptyList()
        val toAddress = if (liveMessage.isIncoming) {
            uiState.accountEmail ?: ""
        } else {
            uiState.chat?.participantEmail ?: ""
        }
        val msgIndex = uiState.messages.indexOfFirst { it.id == liveMessage.id }
        FullEmailView(
            message = liveMessage,
            toAddress = toAddress,
            attachments = attachments,
            noReply = uiState.chat?.let { EmailGrouping.isNoReply(it) } ?: false,
            onBack = { selectedMessage = null },
            onReplyWithQuote = { msg ->
                viewModel.replyWithQuote(msg)
                selectedMessage = null
            },
            onReply = { msg ->
                viewModel.replyWithQuote(msg)
                viewModel.toggleQuote()
                val subj = msg.subject
                val reSubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
                selectedSubject = reSubject
                viewModel.onSubjectChange(reSubject)
                selectedMessage = null
                viewModel.openFullCompose()
            },
            onReplyAll = { msg ->
                viewModel.replyAll(msg)
                viewModel.toggleQuote()
                val subj = msg.subject
                val reSubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
                selectedSubject = reSubject
                viewModel.onSubjectChange(reSubject)
                selectedMessage = null
                viewModel.openFullCompose()
            },
            onForward = { msg ->
                forwardMessage = msg
            },
            isSpamNow = liveMessage.isSpam,
            spamCategory = liveMessage.spamCategory,
            onShowSpamCategory = {
                spamDialogSingleMessage = liveMessage
                showSpamCategoryDialog = true
            },
            customTag = liveMessage.customTag,
            isStarredNow = liveMessage.isStarred,
            onToggleStarred = { viewModel.toggleStarred(liveMessage) },
            onDeleteMessage = { msg ->
                selectedMessage = null
                messageToDelete = msg
            },
            isTodoNow = liveMessage.isTodo,
            onToggleTodo = { viewModel.toggleTodo(liveMessage) },
            onShowRawSource = { viewModel.loadRawSource(liveMessage, toAddress) },
            onSaveDecryptedToSent = { msg -> viewModel.saveDecryptedToSent(msg) },
            onOpenAttachment = openAttachmentSmart,
            onPrevious = {
                if (msgIndex > 0) selectedMessage = uiState.messages[msgIndex - 1]
            },
            onNext = {
                if (msgIndex in uiState.messages.indices && msgIndex < uiState.messages.size - 1) {
                    selectedMessage = uiState.messages[msgIndex + 1]
                }
            }
        )
    }

    if (uiState.fullComposeOpen && selectedMessage == null) {
        FullComposeOverlay(
            fromAddress = uiState.accountEmail ?: "",
            toValue = uiState.selectedRecipient ?: "",
            subject = selectedSubject,
            ccValue = uiState.composeCc,
            bccValue = uiState.composeBcc,
            body = uiState.composeText,
            quote = uiState.pendingQuote,
            isSending = uiState.isSending,
            attachments = uiState.composeAttachments,
            isRecording = isRecording,
            recordingSeconds = recordingSeconds,
            isGeneratingSuggestion = uiState.isGeneratingSuggestion,
            aiSuggestion = uiState.aiSuggestion,
            onClose = { viewModel.closeFullCompose() },
            onSend = {
                viewModel.send(to = uiState.selectedRecipient?.takeIf { it.isNotBlank() }, subject = selectedSubject.takeIf { it.isNotBlank() })
            },
            onToChange = { viewModel.setSelectedRecipient(it) },
            onSubjectChange = {
                selectedSubject = it
                viewModel.onSubjectChange(it)
            },
            onCcChange = { viewModel.setComposeCc(it) },
            onBccChange = { viewModel.setComposeBcc(it) },
            onBodyChange = { viewModel.onComposeTextChange(it) },
            onRemoveAttachment = { viewModel.removeComposeAttachment(it) },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onCompress = { viewModel.createCompressedAttachment(it) },
            onRecordStart = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (VoiceRecorder.start(context) != null) isRecording = true
                } else {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onRecordStop = {
                if (isRecording) {
                    val file = VoiceRecorder.stop()
                    isRecording = false
                    if (file != null) viewModel.addComposeAttachment(file)
                }
            },
            onGenerateAi = { showComposeAiTone = true },
            onApplyAi = {
                // The compose text may already be HTML (rich-text editor) — in that case the
                // plain-text AI suggestion is appended as an HTML paragraph.
                val current = uiState.composeText
                val merged = when {
                    current.isBlank() -> uiState.aiSuggestion
                    com.mailchat.network.SmtpService.looksLikeHtml(current) ->
                        current + "<p>" +
                            com.mailchat.network.SmtpService.escapeHtml(uiState.aiSuggestion).replace("\n", "<br>") +
                            "</p>"
                    else -> "$current\n\n${uiState.aiSuggestion}"
                }
                viewModel.onComposeTextChange(merged)
                viewModel.clearAiSuggestion()
            },
            onClearAi = { viewModel.clearAiSuggestion() },
            contacts = uiState.contacts,
            smtpAccounts = uiState.smtpAccounts,
            fromAccountId = uiState.composeFromAccountId,
            onFromChange = { viewModel.setComposeFrom(it) }
        )
    }

    if (uiState.rawSourceLoading || uiState.rawSource != null) {
        RawSourceDialog(
            loading = uiState.rawSourceLoading,
            rawSource = uiState.rawSource,
            onDismiss = { viewModel.closeRawSource() }
        )
    }

    replyToneMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { replyToneMessage = null },
            title = { Text(stringResource(R.string.ai_reply)) },
            text = { Text(stringResource(R.string.ai_reply_tone_question)) },
            confirmButton = {
                TextButton(onClick = {
                    replyToneMessage = null
                    viewModel.generateMessageReply(msg, "formal")
                }) {
                    Text(stringResource(R.string.ai_reply_formal))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    replyToneMessage = null
                    viewModel.generateMessageReply(msg, "informal")
                }) {
                    Text(stringResource(R.string.ai_reply_informal))
                }
            }
        )
    }

    if (showComposeAiTone) {
        AlertDialog(
            onDismissRequest = { showComposeAiTone = false },
            title = { Text(stringResource(R.string.ai_reply)) },
            text = { Text(stringResource(R.string.ai_reply_tone_question)) },
            confirmButton = {
                TextButton(onClick = {
                    showComposeAiTone = false
                    viewModel.generateReplySuggestion(selectedSubject, "formal")
                }) {
                    Text(stringResource(R.string.ai_reply_formal))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showComposeAiTone = false
                    viewModel.generateReplySuggestion(selectedSubject, "informal")
                }) {
                    Text(stringResource(R.string.ai_reply_informal))
                }
            }
        )
    }

    forwardMessage?.let { msg ->
        var forwardTo by remember { mutableStateOf("") }
        var forwardContactName by remember { mutableStateOf("") }
        var forwardText by remember { mutableStateOf("") }
        var showForwardContactPicker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { forwardMessage = null },
            title = { Text(stringResource(R.string.forward)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = forwardTo,
                        onValueChange = { forwardTo = it },
                        label = { Text(stringResource(R.string.to_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        trailingIcon = {
                            // Contact picker (unified with new email / share): pick a recipient.
                            IconButton(onClick = { showForwardContactPicker = true }) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = stringResource(R.string.select_contact),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Rich-text editor for the forward note (same as reply / reply-all).
                    com.mailchat.ui.component.RichTextEditor(
                        initialHtml = forwardText,
                        onHtmlChange = { forwardText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.message)
                    )
                    val fwdAttachments = uiState.attachmentsByMessage[msg.id].orEmpty()
                    if (fwdAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.forward_attachments_hint, fwdAttachments.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (forwardTo.isNotBlank()) {
                            viewModel.forwardTo(forwardTo, msg, forwardText, forwardContactName)
                            forwardMessage = null
                        }
                    },
                    enabled = forwardTo.isNotBlank()
                ) {
                    Text(stringResource(R.string.forward))
                }
            },
            dismissButton = {
                TextButton(onClick = { forwardMessage = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        // New contact picker (same as new email / share): full-screen dialog with photos.
        if (showForwardContactPicker) {
            Dialog(
                onDismissRequest = { showForwardContactPicker = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text(stringResource(R.string.select_contact), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(uiState.contacts) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (contact.email.isNotBlank()) {
                                                forwardTo = contact.email
                                                forwardContactName = contact.name
                                            }
                                            showForwardContactPicker = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!contact.photoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = contact.photoUri,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(contact.name.ifBlank { contact.email }, style = MaterialTheme.typography.bodyMedium)
                                        Text(contact.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    archiveFile?.let { af ->
        ArchiveViewerDialog(file = af, onDismiss = { archiveFile = null })
    }

    if (showMembersDialog) {
        val isGroup = EmailGrouping.isGroupKey(uiState.chat?.participantEmail ?: "")
        val currentMembers = uiState.chat?.participantEmail
            ?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val myEmail = uiState.accountEmail?.lowercase()
        val allParticipants = if (isGroup) currentMembers else viewModel.getChatParticipants()
        val participants = allParticipants.filter { it.lowercase() != myEmail }
        val selectedRecipient = uiState.selectedRecipient
        var addText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf(uiState.chat?.participantName ?: "") }
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isGroup) stringResource(R.string.group_members)
                        else stringResource(R.string.select_recipient),
                        modifier = Modifier.weight(1f)
                    )
                    com.mailchat.ui.component.HelpIcon(
                        title = stringResource(R.string.help_recipient_title),
                        text = stringResource(R.string.help_recipient_text)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text(stringResource(if (isGroup) R.string.group_name else R.string.contact_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Auto-reply "Wo bist Du?" options (for the chat's main participant, 1:1 chats only).
                    if (!isGroup) {
                        val mainParticipant = (uiState.chat?.participantEmail ?: "").trim().lowercase()
                        if (mainParticipant.isNotBlank()) {
                            var autoLocation by remember(mainParticipant) {
                                mutableStateOf(com.mailchat.util.SettingsUtils.getAutoLocationReply(context, mainParticipant))
                            }
                            var autoWeather by remember(mainParticipant) {
                                mutableStateOf(com.mailchat.util.SettingsUtils.getAutoWeather(context, mainParticipant))
                            }
                            var showWeatherWarning by remember { mutableStateOf(false) }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.auto_reply_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoLocation = !autoLocation
                                        com.mailchat.util.SettingsUtils.setAutoLocationReply(context, mainParticipant, autoLocation)
                                        if (!autoLocation) {
                                            autoWeather = false
                                            com.mailchat.util.SettingsUtils.setAutoWeather(context, mainParticipant, false)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (autoLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.auto_reply_location),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = autoLocation, onCheckedChange = {
                                    autoLocation = it
                                    com.mailchat.util.SettingsUtils.setAutoLocationReply(context, mainParticipant, it)
                                    if (!it) {
                                        autoWeather = false
                                        com.mailchat.util.SettingsUtils.setAutoWeather(context, mainParticipant, false)
                                    }
                                })
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = autoLocation) {
                                        if (autoWeather) {
                                            autoWeather = false
                                            com.mailchat.util.SettingsUtils.setAutoWeather(context, mainParticipant, false)
                                        } else {
                                            // Enabling requires a warning: the location is sent to open-meteo.com.
                                            showWeatherWarning = true
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    tint = if (autoWeather && autoLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.auto_reply_weather),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = autoWeather,
                                    onCheckedChange = {
                                        if (it) {
                                            // Enabling requires a warning: the location is sent to open-meteo.com.
                                            showWeatherWarning = true
                                        } else {
                                            autoWeather = false
                                            com.mailchat.util.SettingsUtils.setAutoWeather(context, mainParticipant, false)
                                        }
                                    },
                                    enabled = autoLocation
                                )
                            }
                            // Warning shown before the weather option is activated: the location
                            // coordinates are sent to open-meteo.com to fetch the weather.
                            if (showWeatherWarning) {
                                AlertDialog(
                                    onDismissRequest = { showWeatherWarning = false },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Cloud,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    title = { Text(stringResource(R.string.weather_warning_title)) },
                                    text = { Text(stringResource(R.string.weather_warning_text)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showWeatherWarning = false
                                            autoWeather = true
                                            com.mailchat.util.SettingsUtils.setAutoWeather(context, mainParticipant, true)
                                        }) {
                                            Text(stringResource(R.string.weather_warning_ok))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showWeatherWarning = false }) {
                                            Text(stringResource(R.string.cancel))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isGroup) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onRecipientSelected(null) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedRecipient == null) Icons.Default.Check else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (selectedRecipient == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.send_to_all),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    participants.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onRecipientSelected(
                                        if (selectedRecipient == p) null else p
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedRecipient == p) Icons.Default.Check else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (selectedRecipient == p) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = p,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    photoPickEmail = p
                                    photoPicker.launch("image/*")
                                }
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Split a merged chat back apart: move this address' incoming messages
                            // into their own chat. Offered for every address except the chat's own
                            // (main) participant, which stays as the chat's identity.
                            if (!isGroup && p.trim().lowercase() != (uiState.chat?.participantEmail ?: "").trim().lowercase()) {
                                IconButton(
                                    onClick = {
                                        viewModel.splitParticipantFromChat(p)
                                        showMembersDialog = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CallSplit,
                                        contentDescription = stringResource(R.string.split_hint),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (isGroup && currentMembers.contains(p)) {
                                IconButton(
                                    onClick = {
                                        viewModel.updateGroupMembers(
                                            currentMembers.filter { it != p }
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (isGroup) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.add_member),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = addText,
                            onValueChange = { addText = it },
                            label = { Text(stringResource(R.string.add_recipient)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            trailingIcon = {
                                if (addText.isNotBlank()) {
                                    IconButton(onClick = {
                                        val addr = addText.trim().lowercase()
                                        if (addr.contains("@") && addr !in currentMembers) {
                                            viewModel.updateGroupMembers(currentMembers + addr)
                                        }
                                        addText = ""
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.contacts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.contacts.forEach { contact ->
                                if (contact.email.lowercase() in currentMembers) return@forEach
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateGroupMembers(
                                                currentMembers + contact.email.lowercase()
                                            )
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (contact.photoUri != null) {
                                        AsyncImage(
                                            model = contact.photoUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${contact.name} (${contact.email})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameText.isNotBlank() && nameText != uiState.chat?.participantName) {
                        viewModel.renameChat(nameText)
                    }
                    showMembersDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text(stringResource(R.string.delete_message_confirm_title)) },
            text = { Text(stringResource(R.string.delete_message_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete = null
                    viewModel.deleteMessage(message)
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEmailAttachDialog) {
        AlertDialog(
            onDismissRequest = { showEmailAttachDialog = false },
            title = { Text(stringResource(R.string.select_emails_to_attach)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(300.dp)
                ) {
                    items(uiState.messages) { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEmailIds = if (message.id in selectedEmailIds) {
                                        selectedEmailIds - message.id
                                    } else {
                                        selectedEmailIds + message.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (message.id in selectedEmailIds) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (message.id in selectedEmailIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.subject.ifBlank { message.from },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = message.body.take(60),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedEmailIds.isNotEmpty()) {
                        val selectedMessages = uiState.messages.filter { it.id in selectedEmailIds }
                        val emailText = selectedMessages.joinToString("\n\n---\n\n") { msg ->
                            buildString {
                                appendLine(msg.subject.ifBlank { msg.from })
                                appendLine("From: ${msg.from}")
                                appendLine()
                                append(msg.bodyFull.ifBlank { msg.body })
                            }
                        }
                        val currentText = uiState.composeText
                        viewModel.onComposeTextChange(
                            if (currentText.isBlank()) emailText else "$currentText\n\n---\n\n$emailText"
                        )
                    }
                    showEmailAttachDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailAttachDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCompressDialog) {
        CompressPasswordDialog(
            onDismiss = { showCompressDialog = false },
            onConfirm = { password ->
                showCompressDialog = false
                viewModel.createCompressedAttachment(password)
            }
        )
    }

    if (showSizeWarning) {
        val hasImages = uiState.composeAttachments.any { f ->
            val n = f.name.lowercase()
            n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")
        }
        AlertDialog(
            onDismissRequest = { showSizeWarning = false },
            title = { Text(stringResource(R.string.size_limit_title)) },
            text = { Text(stringResource(R.string.size_limit_message)) },
            confirmButton = {
                if (hasImages) {
                    TextButton(onClick = {
                        showSizeWarning = false
                        viewModel.compressImageAttachments()
                    }) {
                        Text(stringResource(R.string.compress_images))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSizeWarning = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun CompressPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compress)) },
        text = {
            Column {
                Text(stringResource(R.string.compress_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_optional)) },
                    supportingText = {
                        if (password.isNotEmpty() && password.length < 12) {
                            Text(stringResource(R.string.password_min_length), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.compress_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isEmpty() || password.length >= 12
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullComposeOverlay(
    fromAddress: String,
    toValue: String,
    subject: String,
    ccValue: String,
    bccValue: String,
    body: String,
    quote: String?,
    isSending: Boolean,
    attachments: List<File>,
    isRecording: Boolean,
    recordingSeconds: Long,
    isGeneratingSuggestion: Boolean,
    aiSuggestion: String,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onToChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onCcChange: (String) -> Unit,
    onBccChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onRemoveAttachment: (File) -> Unit,
    onPickFile: () -> Unit,
    onCompress: (String) -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onGenerateAi: () -> Unit,
    onApplyAi: () -> Unit,
    onClearAi: () -> Unit,
    contacts: List<com.mailchat.service.Contact>,
    smtpAccounts: List<com.mailchat.data.model.EmailAccount>,
    fromAccountId: Long,
    onFromChange: (Long) -> Unit
) {
    var showCcBcc by remember { mutableStateOf(ccValue.isNotBlank() || bccValue.isNotBlank()) }
    var showQuote by remember { mutableStateOf(true) }
    var contactPickerFor by remember { mutableStateOf<String?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun appendContact(field: String, email: String) {
        when (field) {
            "to" -> onToChange(if (toValue.isBlank()) email else "$toValue, $email")
            "cc" -> onCcChange(if (ccValue.isBlank()) email else "$ccValue, $email")
            "bcc" -> onBccChange(if (bccValue.isBlank()) email else "$bccValue, $email")
        }
    }

    if (contactPickerFor != null) {
        val field = contactPickerFor!!
        Dialog(
            onDismissRequest = { contactPickerFor = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(stringResource(R.string.select_contact), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(contacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        appendContact(field, contact.email)
                                        contactPickerFor = null
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!contact.photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = contact.photoUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(contact.name.ifBlank { contact.email }, style = MaterialTheme.typography.bodyMedium)
                                    Text(contact.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCompressDialog) {
        CompressPasswordDialog(
            onDismiss = { showCompressDialog = false },
            onConfirm = { password ->
                showCompressDialog = false
                onCompress(password)
            }
        )
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onSend, enabled = !isSending && body.isNotBlank()) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                            }
                        }
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                var showFromDropdown by remember { mutableStateOf(false) }
                val selectedFrom = smtpAccounts.firstOrNull { it.id == fromAccountId }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .then(if (smtpAccounts.isNotEmpty()) Modifier.clickable { showFromDropdown = true } else Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            selectedFrom?.emailAddress ?: fromAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (smtpAccounts.isNotEmpty()) {
                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                if (smtpAccounts.isNotEmpty()) {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showFromDropdown,
                        onDismissRequest = { showFromDropdown = false }
                    ) {
                        smtpAccounts.forEach { acc ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${acc.emailAddress} · ${acc.smtpHost}") },
                                onClick = {
                                    showFromDropdown = false
                                    onFromChange(acc.id)
                                }
                            )
                        }
                    }
                }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = toValue,
                        onValueChange = onToChange,
                        label = { Text(stringResource(R.string.to_label)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { contactPickerFor = "to" }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.select_contact), modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    if (showCcBcc) {
                        OutlinedTextField(
                            value = ccValue,
                            onValueChange = onCcChange,
                            label = { Text("CC") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { contactPickerFor = "cc" }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.select_contact), modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        OutlinedTextField(
                            value = bccValue,
                            onValueChange = onBccChange,
                            label = { Text("BCC") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { contactPickerFor = "bcc" }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.select_contact), modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showCcBcc = !showCcBcc }) {
                            Text(if (showCcBcc) "CC/BCC ▲" else "CC/BCC ▼")
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = subject,
                        onValueChange = onSubjectChange,
                        label = { Text(stringResource(R.string.subject)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Rich-text editor (bold, italic, underline, alignment, font size, font
                    // family, lists) — used for the detail-email reply / reply-all / forward.
                    com.mailchat.ui.component.RichTextEditor(
                        initialHtml = body,
                        onHtmlChange = onBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.type_message)
                    )
                    // Attachment picker (consistent with new email / share): paperclip + label.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPickFile) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = stringResource(R.string.attachment),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(R.string.compose_add_attachment),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!quote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showQuote = !showQuote }) {
                            Icon(
                                if (showQuote) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showQuote) stringResource(R.string.hide_quote) else stringResource(R.string.show_quote))
                        }
                        if (showQuote) {
                            Text(
                                text = quote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.voice_recording) + " " + String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachments.forEach { file ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = file.name.takeLast(12),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.fillMaxWidth().clickable { onRemoveAttachment(file) }.padding(4.dp)
                                )
                            }
                        }
                    }
                }
                if (aiSuggestion.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(stringResource(R.string.ai_reply), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(aiSuggestion, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onApplyAi) { Text(stringResource(R.string.ai_apply)) }
                                TextButton(onClick = onClearAi) { Text(stringResource(R.string.cancel)) }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Attachment picking is via the "Add attachment" row above (consistent
                    // with new email / share). Compress stays here for quick access.
                    IconButton(onClick = { showCompressDialog = true }) {
                        Icon(Icons.Default.Compress, contentDescription = stringResource(R.string.compress))
                    }
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onRecordStart()
                                        tryAwaitRelease()
                                        onRecordStop()
                                    }
                                )
                            }
                            .size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = stringResource(R.string.voice_record),
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onGenerateAi, enabled = !isGeneratingSuggestion) {
                        if (isGeneratingSuggestion) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_reply))
                        }
                    }
                }
            }
        }
    }
}

/** A URL found in a plaintext email body: its character range and the URL itself. */
private data class UrlSpan(val start: Int, val end: Int, val url: String)

/**
 * Builds an [AnnotatedString] from a plaintext email body, marking all http/https URLs with a
 * link-like [SpanStyle] (primary color + underline), and returns the URL ranges separately so a
 * tap gesture can be resolved to a URL via the [TextLayoutResult].
 */
private fun buildClickableText(plain: String, linkColor: androidx.compose.ui.graphics.Color): Pair<AnnotatedString, List<UrlSpan>> {
    val spans = mutableListOf<UrlSpan>()
    val builder = AnnotatedString.Builder()
    val urlRegex = Regex("https?://[^\\s<>\"']+")
    var lastEnd = 0
    for (match in urlRegex.findAll(plain)) {
        val start = match.range.first
        var end = match.range.last + 1
        // Trim trailing punctuation that is unlikely to be part of the URL.
        while (end > start && plain[end - 1] in ".,;:!?)'\">]") {
            end--
        }
        if (end > start) {
            builder.append(plain.substring(lastEnd, start))
            spans.add(UrlSpan(start, end, plain.substring(start, end)))
            builder.pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            builder.append(plain.substring(start, end))
            builder.pop()
            lastEnd = end
        }
    }
    builder.append(plain.substring(lastEnd))
    return builder.toAnnotatedString() to spans
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullEmailView(
    message: Message,
    toAddress: String,
    attachments: List<Attachment>,
    noReply: Boolean,
    onBack: () -> Unit,
    onReplyWithQuote: (Message) -> Unit,
    onReply: (Message) -> Unit,
    onReplyAll: (Message) -> Unit,
    onForward: (Message) -> Unit,
    isSpamNow: Boolean,
    spamCategory: String = "",
    onShowSpamCategory: () -> Unit,
    customTag: String = "",
    isStarredNow: Boolean,
    onToggleStarred: () -> Unit,
    onDeleteMessage: (Message) -> Unit,
    isTodoNow: Boolean,
    onToggleTodo: () -> Unit,
    onShowRawSource: () -> Unit,
    onSaveDecryptedToSent: (Message) -> Unit = {},
    onOpenAttachment: (Attachment) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val isHtml = message.bodyFull.contains("<", ignoreCase = true)
    val isPhishingEmail = spamCategory == "phishing"
    val blockImagesForSpam = isSpamNow && spamCategory != "newsletter"
    var loadImages by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isCreatingEvent by remember { mutableStateOf(false) }
    val htmlForView = remember(message.bodyFull, attachments, loadImages) {
        val selectionCss = "<style>*{-webkit-user-select:text;user-select:text;-webkit-touch-callout:default;} body{margin:0;padding:8px;}</style>"
        var h = message.bodyFull
        // Inline-CID-Bilder liegen lokal vor (mit der E-Mail empfangen) -> immer sofort anzeigen
        val hasCidRefs = h.contains("cid:", ignoreCase = true)
        com.mailchat.ai.AppLogger.log(context, "CID debug: msg=${message.id} totalAtt=${attachments.size} imgAtt=${attachments.count { it.isImage }} hasCid=$hasCidRefs subject=${message.subject.take(40)}")
        val imageAttachments = attachments.filter { it.isImage }
        for (img in imageAttachments) {
            val refs = listOfNotNull(
                img.contentId.takeIf { it.isNotBlank() },
                img.fileName.takeIf { it.isNotBlank() }
            )
            if (refs.isEmpty()) continue
            var file = File(img.uri)
            if (!file.exists()) {
                val attDir = File(context.filesDir, "attachments")
                if (attDir.exists()) {
                    val searchName = img.fileName.ifBlank { img.contentId }
                    file = attDir.listFiles()?.firstOrNull { it.name.endsWith(searchName) } ?: file
                }
            }
            if (!file.exists()) {
                com.mailchat.ai.AppLogger.log(context, "CID NOT FOUND: uri=${img.uri} cid=${img.contentId} name=${img.fileName}")
                continue
            }
            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            val dataUri = "data:${img.mimeType};base64,$b64"
            var replaced = false
            for (ref in refs) {
                val cid = Regex.escape(ref)
                val before = h.length
                h = h.replace(Regex("cid:<$cid>", RegexOption.IGNORE_CASE), dataUri)
                h = h.replace(Regex("cid:$cid", RegexOption.IGNORE_CASE), dataUri)
                if (h.length != before) replaced = true
            }
            com.mailchat.ai.AppLogger.log(context, "CID replace: cid=${img.contentId} file=${file.name}(${file.length()}b) replaced=$replaced")
        }
        // Noch verbleibende cid:-Referenzen als broken-link-Platzhalter ersetzen
        h = h.replace(Regex("<img[^>]*src\\s*=\\s*[\"']cid:[^\"']*[\"'][^>]*>", RegexOption.IGNORE_CASE), "")
        if (!loadImages) {
            // Nur externe Bild-Referenzen (Download nötig) entfernen, cid-/data-Bilder bleiben
            h = h.replace(Regex("<img[^>]*src\\s*=\\s*[\"'](?!cid:|data:)[^\"']*['\"][^>]*>", RegexOption.IGNORE_CASE), "")
        }
        if (h.contains("<")) {
            selectionCss + h
        } else {
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$selectionCss</head><body><pre style=\"white-space:pre-wrap;font-family:sans-serif;\">${android.text.Html.escapeHtml(h)}</pre></body></html>"
        }
    }
    val emailText = buildString {
        if (isHtml) {
            val body = message.bodyFull.cleanInvisible()
            val bodyWithoutWrapper = body
                .replaceFirst(Regex("(?i)<!DOCTYPE[^>]*>\\s*"), "")
                .replaceFirst(Regex("(?i)<html[^>]*>\\s*"), "")
                .replaceFirst(Regex("(?i)</html>\\s*$"), "")
                .replaceFirst(Regex("(?is)<head>.*?</head>\\s*"), "")
                .replaceFirst(Regex("(?i)<body[^>]*>\\s*"), "")
                .replaceFirst(Regex("(?i)\\s*</body>\\s*$"), "")
            append("<html><head><meta charset=\"utf-8\"></head><body>")
            appendLine("<div style=\"margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #ccc;\">")
            appendLine("<p style=\"margin:4px 0;\"><b>${message.subject.ifBlank { stringResource(R.string.full_email) }}</b></p>")
            appendLine("<p style=\"margin:4px 0;\">${stringResource(R.string.from)}: ${message.from}</p>")
            appendLine("<p style=\"margin:4px 0;\">${stringResource(R.string.to_label)}: $toAddress</p>")
            appendLine("<p style=\"margin:4px 0;\">${stringResource(R.string.date)}: ${DateUtils.formatFull(message.timestamp)}</p>")
            appendLine("</div>")
            append(bodyWithoutWrapper)
            append("</body></html>")
        } else {
            appendLine(message.subject.ifBlank { stringResource(R.string.full_email) })
            appendLine(stringResource(R.string.from) + ": " + message.from)
            appendLine(stringResource(R.string.to_label) + ": " + toAddress)
            appendLine(stringResource(R.string.date) + ": " + DateUtils.formatFull(message.timestamp))
            appendLine()
            append(message.bodyFull.cleanInvisible())
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.full_email)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    val shareLabel = stringResource(R.string.share)
                    if (isHtml) {
                        IconButton(
                            onClick = { if (!blockImagesForSpam) loadImages = !loadImages },
                            enabled = !blockImagesForSpam
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = if (loadImages) stringResource(R.string.block_images) else stringResource(R.string.load_images),
                                tint = if (loadImages) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!message.isIncoming && message.isEncrypted) {
                        IconButton(onClick = { onSaveDecryptedToSent(message) }) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(R.string.secure_writeback_button)
                            )
                        }
                    }
                    IconButton(onClick = onShowRawSource) {
                        Icon(Icons.Default.Subject, contentDescription = stringResource(R.string.show_raw_source))
                    }
                    IconButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = if (isHtml) "text/html" else "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, message.subject)
                            putExtra(android.content.Intent.EXTRA_TEXT, emailText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, shareLabel))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = shareLabel)
                    }
                    // Opens the spam classification menu (ham / newsletter / unwanted ad /
                    // phishing) for this message — same menu as in the chat header.
                    IconButton(onClick = onShowSpamCategory) {
                        Icon(
                            Icons.Default.Report,
                            contentDescription = stringResource(R.string.spam_category),
                            tint = if (isSpamNow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val createEventLabel = stringResource(R.string.create_event)
                    val aiEnabledForEvent = com.mailchat.util.SettingsUtils.getAiEnabled(context)
                    val isPhishingEmail = spamCategory == "phishing"
                    if (aiEnabledForEvent) {
                    IconButton(
                        onClick = {
                            isCreatingEvent = true
                            val scope = kotlinx.coroutines.MainScope()
                            scope.launch {
                                try {
                                    val wv = webViewRef
                                    val selectedText = if (wv != null) {
                                        val deferred = CompletableDeferred<String>()
                                        wv.evaluateJavascript("window.getSelection().toString()") { value ->
                                            val text = value?.removeSurrounding("\"")?.replace("\\n", "\n")?.trim() ?: ""
                                            deferred.complete(text)
                                        }
                                        withTimeoutOrNull(3000) { deferred.await() } ?: ""
                                    } else ""
                                    val textToUse = if (selectedText.isNotBlank()) selectedText
                                    else message.bodyFull.stripHtml().normalizeWhitespace()
                                    com.mailchat.ai.AppLogger.log(context, "Termin: textLen=${textToUse.length} selected=${selectedText.isNotBlank()} preview=${textToUse.take(100)}")
                                    val initOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.mailchat.util.AiEngine.configureAndInitialize(context)
                                    }
                                    if (!initOk) {
                                        com.mailchat.ai.AppLogger.log(context, "Termin: AI init failed")
                                        isCreatingEvent = false
                                        return@launch
                                    }
                                    val aiResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.mailchat.ai.GeminiService.extractAppointment(textToUse)
                                    }
                                    com.mailchat.ai.AppLogger.log(context, "Termin: AI result=$aiResult")
                                    if (aiResult != null) {
                                        val (title, dateStr, timeStr, durationMin) = aiResult
                                        val calendarIntent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                            data = android.net.Uri.parse("content://com.android.calendar/events")
                                            putExtra("title", title)
                                            putExtra("description", message.subject)
                                            if (dateStr.isNotBlank()) {
                                                try {
                                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                                    val dateTime = sdf.parse("$dateStr $timeStr")
                                                    if (dateTime != null) {
                                                        putExtra("beginTime", dateTime.time)
                                                        putExtra("endTime", dateTime.time + (durationMin * 60L * 1000L))
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        }
                                        context.startActivity(android.content.Intent.createChooser(calendarIntent, createEventLabel))
                                    } else {
                                        com.mailchat.ai.AppLogger.log(context, "Termin: AI could not extract appointment")
                                    }
                                } catch (e: Exception) {
                                    com.mailchat.ai.AppLogger.log(context, "Termin error: ${e.message}")
                                }
                                isCreatingEvent = false
                            }
                        },
                        enabled = !isCreatingEvent && !isPhishingEmail
                    ) {
                        if (isCreatingEvent) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = createEventLabel,
                                tint = if (isPhishingEmail) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isHtml && !loadImages) {
                Text(
                    text = stringResource(R.string.images_blocked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = message.subject.ifBlank { stringResource(R.string.full_email) },
                    style = MaterialTheme.typography.titleLarge
                )
                // Top row: show the spam category and the custom tag (if set) as chips.
                // Tapping a chip opens the combined spam/tag dialog (same as the header button).
                val showSpamChip = spamCategory.isNotBlank() && spamCategory != "ham"
                val showTagChip = customTag.isNotBlank()
                if (showSpamChip || showTagChip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showSpamChip) {
                            val (spamLabel, spamColor) = when (spamCategory) {
                                "phishing" -> stringResource(R.string.spam_phishing) to Color(0xFFD32F2F)
                                "unwanted_ad" -> stringResource(R.string.spam_unwanted) to Color(0xFFF9A825)
                                "newsletter" -> stringResource(R.string.spam_newsletter) to Color(0xFF388E3C)
                                else -> spamCategory to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = spamColor.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onShowSpamCategory() }
                            ) {
                                Text(
                                    text = spamLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = spamColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (showTagChip) Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (showTagChip) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1565C0).copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onShowSpamCategory() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Label,
                                        contentDescription = null,
                                        tint = Color(0xFF1565C0),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = customTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HeaderRow(stringResource(R.string.from), message.from)
                if (message.cc.isNotBlank()) {
                    Text(
                        text = "CC: ${message.cc}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HeaderRow(stringResource(R.string.to_label), toAddress)
                HeaderRow(stringResource(R.string.date), DateUtils.formatFull(message.timestamp))
            }
            if (isHtml) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isHapticFeedbackEnabled = true
                            settings.apply {
                                javaScriptEnabled = true
                                blockNetworkImage = !loadImages
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                textZoom = 100
                            }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    if (isPhishingEmail) {
                                        // Links in Phishing-E-mails sind gesperrt
                                        android.widget.Toast.makeText(
                                            ctx,
                                            "Links in Phishing-E-mails sind gesperrt",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return true
                                    }
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        request.url
                                    )
                                    ctx.startActivity(intent)
                                    return true
                                }
                            }
                        }
                    },
                    update = { wv ->
                        wv.settings.blockNetworkImage = !loadImages
                        if (wv.tag != htmlForView) {
                            wv.tag = htmlForView
                            wv.loadDataWithBaseURL(null, htmlForView, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SelectionContainer {
                        val plainText = message.bodyFull.cleanInvisible()
                        if (isPhishingEmail) {
                            // Phishing: keine klickbaren Links
                            Text(
                                text = plainText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            val linkColor = MaterialTheme.colorScheme.primary
                            val (clickable, urlSpans) = remember(plainText, linkColor) {
                                buildClickableText(plainText, linkColor)
                            }
                            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            Text(
                                text = clickable,
                                style = MaterialTheme.typography.bodyMedium,
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier.pointerInput(urlSpans) {
                                    if (urlSpans.isEmpty()) return@pointerInput
                                    detectTapGestures { offset ->
                                        val layout = layoutResult ?: return@detectTapGestures
                                        val pos = layout.getOffsetForPosition(offset)
                                        val span = urlSpans.firstOrNull { pos in it.start until it.end }
                                        if (span != null) {
                                            try {
                                                context.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(span.url)
                                                    )
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (attachments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.attachments),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    attachments.forEach { attachment ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onOpenAttachment(attachment)
                                    }
                            )
                            if (attachment.sizeBytes > 0) {
                                Text(
                                    text = formatFileSize(attachment.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(onClick = {
                                shareAttachmentFile(context, File(attachment.uri), attachment.mimeType)
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!noReply && spamCategory != "phishing") {
                    IconButton(onClick = { onReply(message) }) {
                        Icon(
                            Icons.Default.Reply,
                            contentDescription = stringResource(R.string.reply),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = { onReplyAll(message) }) {
                        ReplyAllIcon(
                            contentDescription = stringResource(R.string.reply_all),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                IconButton(onClick = { onForward(message) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Forward,
                        contentDescription = stringResource(R.string.forward),
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = { onDeleteMessage(message) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}



@Composable
private fun RawSourceDialog(loading: Boolean, rawSource: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.raw_source_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!rawSource.isNullOrBlank()) {
                        IconButton(onClick = {
                            val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clip.setPrimaryClip(android.content.ClipData.newPlainText("mailchat", rawSource))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                    }
                }
                when {
                    loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    rawSource != null -> Text(
                        text = rawSource,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    )
                    else -> Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReplyAllIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Reply,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
        )
        Icon(
            imageVector = Icons.Default.Reply,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(22.dp)
        )
    }
}

private fun shareAttachmentFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        if (!file.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "application/octet-stream" }
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Teilen"))
    } catch (e: Exception) {
        android.util.Log.w("MailchatShare", "share attachment failed", e)
    }
}

@Composable
private fun HeaderRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun calculateEmailSize(body: String, attachments: List<File>): Long {
    val bodySize = body.toByteArray().size.toLong()
    val attachmentSize = attachments.sumOf { it.length() }
    return bodySize + attachmentSize
}

private fun copyUriToFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val dir = File(context.filesDir, "compose_attachments").apply { mkdirs() }
        var name = (uri.lastPathSegment ?: "attachment_${System.currentTimeMillis()}")
            .substringAfterLast('/')
            .sanitizeFileName()
        val dotIndex = name.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < name.length - 1
        if (!hasExtension) {
            val mime = context.contentResolver.getType(uri)
            val ext = mimeToExtension(mime)
            if (ext.isNotBlank()) name = "$name.$ext"
        }
        val file = File(dir, "${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    } catch (e: Exception) {
        null
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun compressImage(source: File, maxWidth: Int): File? {
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxWidth) maxWidth.toFloat() / width else 1f
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val dir = File(source.parentFile, "compressed").apply { mkdirs() }
        val out = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { os -> scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, os) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        out
    } catch (_: Exception) {
        source
    }
}

private fun mimeToExtension(mime: String?): String = when {
    mime == null -> ""
    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
    mime.contains("png") -> "png"
    mime.contains("gif") -> "gif"
    mime.contains("webp") -> "webp"
    mime.contains("pdf") -> "pdf"
    mime.contains("msword") -> "doc"
    mime.contains("ms-excel") -> "xls"
    mime.contains("mspowerpoint") -> "ppt"
    mime.contains("zip") -> "zip"
    mime.contains("plain") -> "txt"
    mime.contains("html") -> "html"
    else -> ""
}

@Composable
private fun ChatHeaderAvatar(name: String, photoUri: String?, accountColor: Int) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(2.dp, Color(accountColor), CircleShape)
        )
    } else {
        AccountAvatar(name = name, color = accountColor, size = 36)
    }
}

private val memberAvatarPalette = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFFF8A65)
)

@Composable
private fun GroupMemberAvatars(
    emails: List<String>,
    contactPhotos: Map<String, String?>,
    contactNames: Map<String, String>
) {
    val shown = if (emails.size > 4) emails.take(3) else emails.take(4)
    val extra = emails.size - shown.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        shown.forEachIndexed { index, email ->
            if (index > 0) Spacer(modifier = Modifier.width(4.dp))
            SmallMemberAvatar(
                email = email,
                photoUri = contactPhotos[email.lowercase()],
                name = contactNames[email.lowercase()] ?: email
            )
        }
        if (extra > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmallMemberAvatar(email: String, photoUri: String?, name: String) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
        )
    } else {
        val color = memberAvatarPalette[Math.floorMod(email.hashCode(), memberAvatarPalette.size)]
        val initial = (name.firstOrNull() ?: email.firstOrNull())?.uppercase() ?: "?"
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ComposeBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<File>,
    onRemoveAttachment: (File) -> Unit,
    onRemoveAllAttachments: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    sendError: String?,
    isRecording: Boolean,
    recordingSeconds: Long,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onRecordCancel: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (sendError != null) {
            Text(
                text = sendError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.voice_recording) + " " +
                        String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onRecordCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachments.forEach { file ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = file.name.takeLast(12),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRemoveAttachment(file) }
                                .padding(4.dp)
                        )
                    }
                }
                IconButton(onClick = onRemoveAllAttachments) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove all",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment picking is available via the "+" menu (upload media / camera),
            // so no separate paperclip here (kept consistent with the full compose).
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    onRecordStart()
                                    tryAwaitRelease()
                                    onRecordStop()
                                }
                            }
                        )
                    }
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                val scale by animateFloatAsState(
                    targetValue = if (isRecording) 1.3f else 1f,
                    animationSpec = spring()
                )
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.voice_record),
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.scale(scale)
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.compose_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 8,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { onTextChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_text),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.FilledIconButton(
                onClick = onSend,
                enabled = !isSending && (text.isNotBlank() || attachments.isNotEmpty())
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send)
                    )
                }
            }
        }
    }
}

