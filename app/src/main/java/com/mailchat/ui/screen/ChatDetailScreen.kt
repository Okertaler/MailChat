package com.mailchat.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
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
import com.mailchat.ui.component.ZoomableImage
import com.mailchat.ui.component.AccountAvatar
import com.mailchat.ui.component.GroupAvatar
import com.mailchat.ui.component.SpinningRefreshButton
import com.mailchat.ui.viewmodel.ChatDetailViewModel
import com.mailchat.util.AppointmentFormat
import com.mailchat.util.DateUtils
import com.mailchat.util.EmailGrouping
import com.mailchat.util.PollFormat
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiEnabled = SettingsUtils.getAiEnabled(context)
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var messageMenuFor by remember { mutableStateOf<Message?>(null) }
    var customTagMessage by remember { mutableStateOf<Message?>(null) }
    var replyToneMessage by remember { mutableStateOf<Message?>(null) }
    var showComposeAiTone by remember { mutableStateOf(false) }
    var showPollEditor by remember { mutableStateOf(false) }
    var showAppointmentEditor by remember { mutableStateOf(false) }
    // When set (an AI-detected appointment), the appointment editor is pre-filled with it.
    var appointmentEditorInitial by remember { mutableStateOf<com.mailchat.ai.GeminiService.Appointment?>(null) }
    // An AI appointment was detected: (appointment, subject) pending the user's choice
    // ("send as appointment poll" vs. "add to my calendar"). Null = none.
    var pendingApptAction by remember { mutableStateOf<Pair<com.mailchat.ai.GeminiService.Appointment, String>?>(null) }
    // When starting an appointment poll from an AI-detected appointment, the user first picks the
    // recipient/group (chat). Null = use the current chat.
    var apptTargetChat by remember { mutableStateOf<com.mailchat.data.model.Chat?>(null) }
    var showApptChatPicker by remember { mutableStateOf(false) }
    var apptPickerChats by remember { mutableStateOf<List<com.mailchat.data.model.Chat>>(emptyList()) }
    var apptPickerQuery by remember { mutableStateOf("") }
    // Load the chat list for the recipient/group picker when the dialog opens.
    LaunchedEffect(showApptChatPicker) {
        if (showApptChatPicker) {
            apptPickerQuery = ""
            apptPickerChats = viewModel.chatsForPicker()
        }
    }
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
    // Full-screen (zoomable) image viewer for images tapped in the chat.
    var fullScreenImage by remember { mutableStateOf<java.io.File?>(null) }
    val openImageFullScreen: (Attachment) -> Unit = { att ->
        val file = com.mailchat.util.AttachmentUtils.resolveFile(context, att)
        if (file == null) {
            android.widget.Toast.makeText(
                context,
                context.getString(com.mailchat.R.string.attachment_not_found),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            fullScreenImage = file
        }
    }

    var selectedSubject by remember { mutableStateOf("") }
    var showSubjectDropdown by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    // "Wo bist Du?" confirmation dialog (hint + Senden/Abbrechen) before the location request
    // is sent.
    var showWhereAreYouConfirm by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    // Drawing (Zeichnung/Bild): a full-screen finger-drawing canvas; the result is attached.
    var showDrawingScreen by remember { mutableStateOf(false) }
    // Sticky note (Notizzettel): a local note written in the chat, never sent as an email.
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var stickyNoteText by remember { mutableStateOf("") }
    // The selected color for the note (one of StickyNoteColors keys).
    var stickyNoteColor by remember { mutableStateOf(com.mailchat.util.StickyNoteColors.YELLOW) }
    // Non-null when the dialog edits an existing note (opened by clicking the note in the chat).
    var editingStickyNote by remember { mutableStateOf<Message?>(null) }
    // Non-empty when the note should be pinned under EACH of these selected messages
    // (opened from the "Bearbeiten" menu with a selection).
    var stickyNoteTargetMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
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

    // Requests the location permission (for "Mein Standort" / "Wo bist Du?"). The background
    // location permission is requested together with the foreground one — it is needed for the
    // "Wo bist Du?" auto-reply to read a fresh location fix while the phone is locked (the app
    // is in the background). On Android 12+ the system shows a combined prompt ("Allow all the
    // time" grants both).
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

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
        // Poll answers and result emails are suppressed in the chat: the poll shows its own
        // result, so there is only ONE entry per poll.
        var visible = uiState.messages.filter { msg ->
            !msg.isPollAnswer &&
                PollFormat.parseResultMarker(msg.bodyFull.ifBlank { msg.body }) == null &&
                AppointmentFormat.parseApptResultMarker(msg.bodyFull.ifBlank { msg.body }) == null
        }
        // The creator sends one poll email per recipient (each with its own poll-ID, all linked by
        // a shared groupId). Show only ONE poll message per group (the first one) so the chat
        // doesn't show the same poll N times.
        val seenGroups = mutableSetOf<String>()
        visible = visible.filter { msg ->
            val isPollMsg = msg.pollId.isNotBlank() && !msg.isPollAnswer
            if (!isPollMsg) return@filter true
            if (msg.pollId in seenGroups) return@filter false
            seenGroups.add(msg.pollId)
            true
        }
        if (searchQuery.isBlank()) {
            visible
        } else {
            visible.filter {
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
                        // The edit menu is shown immediately when multi-select mode is enabled
                        // (not only after the first message is checked).
                        run {
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
                                                    Icon(
                                                        Icons.Outlined.Summarize,
                                                        null,
                                                        tint = if (selectedMsgs.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            },
                                            enabled = !uiState.isAiProcessing && selectedMsgs.isNotEmpty(),
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
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Label,
                                                null,
                                                tint = if (isSingle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                        },
                                        enabled = isSingle,
                                        onClick = {
                                            customTagMessage = singleMsg
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.to_processed)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Flag,
                                                null,
                                                tint = if (selectedMsgs.isEmpty()) Color(0xFFF44336).copy(alpha = 0.3f) else Color(0xFFF44336)
                                            )
                                        },
                                        enabled = selectedMsgs.isNotEmpty(),
                                        onClick = {
                                            viewModel.setTodoForMessages(selectedMsgs, true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.mark_processed)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                null,
                                                tint = if (selectedMsgs.isEmpty()) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF4CAF50)
                                            )
                                        },
                                        enabled = selectedMsgs.isNotEmpty(),
                                        onClick = {
                                            viewModel.setProcessedForMessages(selectedMsgs, true)
                                        }
                                    )
                                    // Create a sticky note (Notizzettel) under the selected message(s):
                                    // with several selected, the SAME note is pinned under each one.
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sticky_note)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.StickyNote2,
                                                null,
                                                tint = if (selectedMsgs.isEmpty()) Color(0xFFFFC107).copy(alpha = 0.3f) else Color(0xFFFFC107)
                                            )
                                        },
                                        enabled = selectedMsgs.isNotEmpty(),
                                        onClick = {
                                            stickyNoteText = ""
                                            stickyNoteColor = com.mailchat.util.StickyNoteColors.YELLOW
                                            editingStickyNote = null
                                            stickyNoteTargetMessages = selectedMsgs.toList()
                                            showStickyNoteDialog = true
                                        }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                null,
                                                tint = if (selectedMsgs.isEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error
                                            )
                                        },
                                        enabled = selectedMsgs.isNotEmpty(),
                                        onClick = {
                                            viewModel.deleteMessages(selectedMessageIds.toList())
                                            selectedMessageIds = emptySet()
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Manual sync for this chat (spins while running).
                        SpinningRefreshButton(isSpinning = uiState.isSyncing, onClick = { viewModel.syncChat() })
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
                        // Sticky note (Notizzettel): write a local note into the chat,
                        // never sent as an email. The help icon lives inside the editor dialog.
                        IconButton(onClick = {
                            stickyNoteText = ""
                            stickyNoteColor = com.mailchat.util.StickyNoteColors.YELLOW
                            editingStickyNote = null
                            stickyNoteTargetMessages = emptyList()
                            showStickyNoteDialog = true
                        }) {
                            Icon(
                                Icons.Default.StickyNote2,
                                contentDescription = stringResource(R.string.sticky_note),
                                tint = Color(0xFFFFC107)
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
                            // Drawing (Zeichnung/Bild): draw with the finger (optionally on top of
                            // a loaded image) and attach the result.
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Create, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.drawing)) },
                                onClick = {
                                    showAddMenu = false
                                    showDrawingScreen = true
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
                            // Create a poll (Umfrage) in this chat.
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Poll, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.poll_create)) },
                                onClick = {
                                    showAddMenu = false
                                    showPollEditor = true
                                }
                            )
                            // Create a TERMIN (appointment) in this chat: Ja/Nein/Vielleicht + comment.
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.appt_create)) },
                                onClick = {
                                    showAddMenu = false
                                    appointmentEditorInitial = null
                                    showAppointmentEditor = true
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.add_my_location)) },
                                trailingIcon = {
                                    com.mailchat.ui.component.HelpIcon(
                                        title = stringResource(R.string.help_my_location_title),
                                        text = stringResource(R.string.help_my_location_text)
                                    )
                                },
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
                                        com.mailchat.ai.AppLogger.log(context, "Mein Standort: skipped — location permission missing")
                                        android.widget.Toast.makeText(context, R.string.location_permission_needed, android.widget.Toast.LENGTH_LONG).show()
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                            )
                                        )
                                        return@DropdownMenuItem
                                    }
                                    scope.launch {
                                        try {
                                            // 1) Fast path: last known location.
                                            var location = try {
                                                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                                    ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                                    ?: lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
                                            } catch (e: Exception) {
                                                null
                                            }
                                            // 2) No last known location: request a fresh fix.
                                            if (location == null) location = requestSingleLocationFix(lm)
                                            if (location != null) {
                                                val lat = location.latitude
                                                val lon = location.longitude
                                                val german = com.mailchat.util.SettingsUtils.isGermanUi(context)
                                                val locationText = (if (german) "\n📍 Mein Standort:\n" else "\n📍 My Location:\n") +
                                                    "OpenStreetMap: https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon\n" +
                                                    "Google Maps: https://www.google.com/maps?q=$lat,$lon\n" +
                                                    "Apple Maps: https://maps.apple.com/?ll=$lat,$lon"
                                                viewModel.onComposeTextChange(uiState.composeText + locationText)
                                                // Generate the OSM map image (red marker) and attach it as a PNG.
                                                // The map is optional — the text links remain even if it fails.
                                                val mapOk = viewModel.attachLocationMap(lat, lon)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (mapOk) R.string.location_map_attached else R.string.location_map_failed,
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(context, R.string.location_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, R.string.location_error, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            // "Wo bist Du?" — sends a location request (subject "Standortanfrage",
                            // body "Wo bist du?" + "Anfrage von <Kontakt>"). If the contact has the
                            // auto-location-reply enabled (and granted the location permission in
                            // their MailChat app), their app replies with their location (and
                            // optionally the local weather).
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                text = { Text(stringResource(R.string.where_are_you)) },
                                onClick = {
                                    showAddMenu = false
                                    val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (!hasLocationPermission) {
                                        com.mailchat.ai.AppLogger.log(context, "Wo bist Du?: skipped — location permission missing")
                                        android.widget.Toast.makeText(context, R.string.location_permission_needed, android.widget.Toast.LENGTH_LONG).show()
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                            )
                                        )
                                        return@DropdownMenuItem
                                    }
                                    showWhereAreYouConfirm = true
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
                                            } else if (message.isStickyNote) {
                                                // Clicking a sticky note opens the editor so the note can be modified.
                                                editingStickyNote = message
                                                stickyNoteText = message.body
                                                stickyNoteColor = message.stickyNoteColor
                                                stickyNoteTargetMessages = emptyList()
                                                showStickyNoteDialog = true
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
                                        onImageClick = { attachment ->
                                            if (message.spamCategory == "phishing") {
                                                android.widget.Toast.makeText(context, "Anhänge aus Phishing-E-mails können nicht geöffnet werden", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                openImageFullScreen(attachment)
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
                val subj = msg.subject
                val reSubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
                selectedSubject = reSubject
                viewModel.onSubjectChange(reSubject)
                selectedMessage = null
                viewModel.openFullCompose()
            },
            onReplyAll = { msg ->
                viewModel.replyAll(msg)
                val subj = msg.subject
                val reSubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
                selectedSubject = reSubject
                viewModel.onSubjectChange(reSubject)
                selectedMessage = null
                viewModel.openFullCompose()
            },
            onForward = { msg ->
                // Unified forward: open the full compose overlay (same as reply / reply-all)
                // with the complete text and the original attachments pre-selected.
                viewModel.prepareForward(msg)
                val subj = msg.subject
                val fwdSubject = if (subj.startsWith("Fwd:", ignoreCase = true) || subj.startsWith("Fw:", ignoreCase = true)) subj else "Fwd: ${subj.ifBlank { msg.from }}"
                selectedSubject = fwdSubject
                viewModel.onSubjectChange(fwdSubject)
                selectedMessage = null
            },
            isSpamNow = liveMessage.isSpam,
            spamCategory = liveMessage.spamCategory,
            onShowSpamCategory = {
                spamDialogSingleMessage = liveMessage
                showSpamCategoryDialog = true
            },
            onMarkAsPhishing = { msg ->
                viewModel.setSpamCategoryForMessages(listOf(msg), "phishing")
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
            },
            viewModel = viewModel,
            onApptAction = { appt, subject ->
                pendingApptAction = appt to subject
            }
        )
    }

    // Full-screen zoomable image viewer (tapped image in the chat).
    fullScreenImage?.let { file ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ZoomableImage(file.absolutePath, Modifier.fillMaxSize())
            IconButton(
                onClick = { fullScreenImage = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
        }
    }

    if (uiState.fullComposeOpen && selectedMessage == null) {
        FullComposeOverlay(
            fromAddress = uiState.accountEmail ?: "",
            toValue = uiState.selectedRecipient ?: "",
            subject = selectedSubject,
            ccValue = uiState.composeCc,
            bccValue = uiState.composeBcc,
            body = uiState.composeText,
            hasQuote = uiState.replyQuote != null,
            quoteEnabled = uiState.quoteEnabled,
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
            onToggleQuote = { viewModel.toggleQuote() },
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

    // "Wo bist Du?" confirmation: explains that the auto-reply only works when the recipient
    // granted the location permission AND enabled auto-replies for this contact in their app.
    if (showWhereAreYouConfirm) {
        val whereAreYouContactName = uiState.chat?.participantName?.takeIf { it.isNotBlank() }
            ?: uiState.contactName?.takeIf { it.isNotBlank() }
            ?: (uiState.chat?.participantEmail ?: "")
        AlertDialog(
            onDismissRequest = { showWhereAreYouConfirm = false },
            title = { Text(stringResource(R.string.where_are_you)) },
            text = { Text(stringResource(R.string.where_are_you_confirm_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    showWhereAreYouConfirm = false
                    viewModel.sendLocationRequest(whereAreYouContactName)
                }) {
                    Text(stringResource(R.string.send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhereAreYouConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Sticky note (Notizzettel): a local note written into the chat, never sent as an email.
    // The dialog background follows the selected note color. The help icon lives here (moved
    // out of the chat header). In "pin under" mode (opened from "Bearbeiten" with a selection)
    // the SAME note is pinned under each selected message.
    if (showStickyNoteDialog) {
        val noteColor = com.mailchat.util.StickyNoteColors.byKey(stickyNoteColor)
        val isPinMode = stickyNoteTargetMessages.isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                showStickyNoteDialog = false
                editingStickyNote = null
                stickyNoteTargetMessages = emptyList()
            },
            containerColor = noteColor.bubbleLight,
            titleContentColor = noteColor.textLight,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.StickyNote2,
                        contentDescription = null,
                        tint = noteColor.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when {
                                editingStickyNote != null -> R.string.sticky_note_edit
                                isPinMode -> R.string.sticky_note_pin
                                else -> R.string.sticky_note
                            }
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    // Help icon moved here from the chat header.
                    com.mailchat.ui.component.HelpIcon(
                        title = stringResource(R.string.help_sticky_note_title),
                        text = stringResource(R.string.help_sticky_note_text)
                    )
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = stickyNoteText,
                        onValueChange = { stickyNoteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.sticky_note_hint)) },
                        minLines = 3,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = noteColor.bubbleLight,
                            unfocusedContainerColor = noteColor.bubbleLight
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    // Color picker: yellow (default), pink, orange, purple.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        com.mailchat.util.StickyNoteColors.ALL.forEach { color ->
                            val isSelected = color.key == stickyNoteColor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color.accent)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) noteColor.textLight else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { stickyNoteColor = color.key },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = color.labelDe,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            if (isPinMode) R.string.sticky_note_pin_hint else R.string.sticky_note_not_sent
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = noteColor.textLight.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (stickyNoteText.isNotBlank()) {
                            val editing = editingStickyNote
                            if (editing != null) {
                                viewModel.updateStickyNote(editing, stickyNoteText.trim(), stickyNoteColor)
                            } else if (isPinMode) {
                                viewModel.saveStickyNoteUnderMessages(
                                    stickyNoteTargetMessages,
                                    stickyNoteText.trim(),
                                    stickyNoteColor
                                )
                            } else {
                                viewModel.saveStickyNote(stickyNoteText.trim(), stickyNoteColor)
                            }
                            showStickyNoteDialog = false
                            editingStickyNote = null
                            stickyNoteTargetMessages = emptyList()
                        }
                    },
                    enabled = stickyNoteText.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStickyNoteDialog = false
                    editingStickyNote = null
                    stickyNoteTargetMessages = emptyList()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPollEditor) {
        PollEditor(
            isSending = uiState.isSending,
            onDismiss = { showPollEditor = false },
            onCreate = { title, question, options, multi, numberMode, personMode ->
                showPollEditor = false
                viewModel.createPoll(title, question, options, multi, numberMode, personMode)
                // The poll is a standalone email — close the compose overlay for a clean flow.
                viewModel.closeFullCompose()
            }
        )
    }

    if (showAppointmentEditor) {
        AppointmentEditor(
            isSending = uiState.isSending,
            initial = appointmentEditorInitial,
            onDismiss = {
                showAppointmentEditor = false
                appointmentEditorInitial = null
            },
            onCreate = { title, question, startDate, startTime, endDate, endTime, allDay, location ->
                showAppointmentEditor = false
                appointmentEditorInitial = null
                // apptTargetChat is set only in the AI flow (recipient/group picked first);
                // null in the "+"-menu flow means "use the current chat". A contact that is not
                // in the chats has id = 0: pass its email so the poll is sent to that contact.
                val targetChatId = apptTargetChat?.takeIf { it.id != 0L }?.id
                val targetEmail = apptTargetChat?.takeIf { it.id == 0L }?.participantEmail
                apptTargetChat = null
                viewModel.createAppointment(title, question, startDate, startTime, endDate, endTime, allDay, location, targetChatId, targetEmail)
                // The appointment is a standalone email — close the compose overlay for a clean flow.
                viewModel.closeFullCompose()
            }
        )
    }

    // Drawing (Zeichnung/Bild): full-screen finger-drawing canvas. The saved PNG is attached
    // to the email being composed.
    if (showDrawingScreen) {
        DrawingScreen(
            onDismiss = { showDrawingScreen = false },
            onConfirm = { file ->
                showDrawingScreen = false
                viewModel.addComposeAttachment(file)
            }
        )
    }

    // AI appointment detected: let the user choose between sending it as an appointment poll
    // (Termin-Umfrage) or adding it to their own calendar.
    val pendingApptActionValue = pendingApptAction
    if (pendingApptActionValue != null) {
        val (appt, subject) = pendingApptActionValue
        // Captured here (composable context) because the onClick lambdas below are not composable.
        val apptContext = LocalContext.current
        val apptCreateEventLabel = stringResource(R.string.create_event)
        AlertDialog(
            onDismissRequest = { pendingApptAction = null },
            title = { Text(stringResource(R.string.appt_ai_title)) },
            text = {
                Column {
                    Text(appt.title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${appt.date} · ${appt.time} (${appt.durationMin} min)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (appt.location.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "📍 ${appt.location}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingApptAction = null
                    // Remember the appointment for the editor, then let the user choose the
                    // recipient/group before the editor opens.
                    appointmentEditorInitial = appt
                    apptTargetChat = null
                    showApptChatPicker = true
                }) {
                    Text(stringResource(R.string.appt_ai_send))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingApptAction = null
                        launchCalendarEventInsert(apptContext, appt, subject, apptCreateEventLabel)
                    }) {
                        Text(stringResource(R.string.appt_ai_calendar))
                    }
                    TextButton(onClick = { pendingApptAction = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Recipient/group picker shown before starting an appointment poll from an AI-detected
    // appointment. The chosen chat is stored in apptTargetChat and used by createAppointment.
    if (showApptChatPicker) {
        AlertDialog(
            onDismissRequest = { showApptChatPicker = false },
            title = { Text(stringResource(R.string.appt_pick_recipient)) },
            text = {
                val filteredApptChats = apptPickerChats
                    .filter { chat ->
                        apptPickerQuery.isBlank() ||
                            chat.participantName.contains(apptPickerQuery, ignoreCase = true) ||
                            chat.participantEmail.contains(apptPickerQuery, ignoreCase = true)
                    }
                    .sortedBy { apptPickerDisplayName(it).lowercase() }
                Column {
                    OutlinedTextField(
                        value = apptPickerQuery,
                        onValueChange = { apptPickerQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filteredApptChats.isEmpty()) {
                        Text(stringResource(R.string.appt_pick_recipient_empty))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(filteredApptChats) { chat ->
                                val isGroup = com.mailchat.util.EmailGrouping.isGroupKey(chat.participantEmail)
                                val name = apptPickerDisplayName(chat)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            apptTargetChat = chat
                                            showApptChatPicker = false
                                            showAppointmentEditor = true
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isGroup) Icons.Default.Group else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            chat.participantEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showApptChatPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
                            var autoMap by remember(mainParticipant) {
                                mutableStateOf(com.mailchat.util.SettingsUtils.getAutoMap(context, mainParticipant))
                            }
                            var showWeatherWarning by remember { mutableStateOf(false) }
                            var showMapWarning by remember { mutableStateOf(false) }
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
                                            autoMap = false
                                            com.mailchat.util.SettingsUtils.setAutoMap(context, mainParticipant, false)
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
                                        autoMap = false
                                        com.mailchat.util.SettingsUtils.setAutoMap(context, mainParticipant, false)
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = autoLocation) {
                                        if (autoMap) {
                                            autoMap = false
                                            com.mailchat.util.SettingsUtils.setAutoMap(context, mainParticipant, false)
                                        } else {
                                            // Enabling requires a warning: the exact coordinates are
                                            // sent to OpenStreetMap and the map is unencrypted.
                                            showMapWarning = true
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = if (autoMap && autoLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.auto_reply_map),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = autoMap,
                                    onCheckedChange = {
                                        if (it) {
                                            // Enabling requires a warning: the exact coordinates are
                                            // sent to OpenStreetMap and the map is unencrypted.
                                            showMapWarning = true
                                        } else {
                                            autoMap = false
                                            com.mailchat.util.SettingsUtils.setAutoMap(context, mainParticipant, false)
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
                            // Warning shown before the map option is activated: the exact
                            // coordinates are sent to OpenStreetMap to fetch the map tiles, and
                            // the map image is attached unencrypted.
                            if (showMapWarning) {
                                AlertDialog(
                                    onDismissRequest = { showMapWarning = false },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    title = { Text(stringResource(R.string.map_warning_title)) },
                                    text = { Text(stringResource(R.string.map_warning_text)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showMapWarning = false
                                            autoMap = true
                                            com.mailchat.util.SettingsUtils.setAutoMap(context, mainParticipant, true)
                                        }) {
                                            Text(stringResource(R.string.map_warning_ok))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showMapWarning = false }) {
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
    hasQuote: Boolean,
    quoteEnabled: Boolean,
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
    onToggleQuote: () -> Unit,
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
    var contactPickerFor by remember { mutableStateOf<String?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showToolbarHelp by remember { mutableStateOf(false) }
    // Shared controller so the formatting toolbar (rendered above the keyboard, next to the
    // compress / mic / AI buttons) can drive the rich-text editor.
    val editorController = remember { com.mailchat.ui.component.RichTextEditorController() }

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
    if (showToolbarHelp) {
        AlertDialog(
            onDismissRequest = { showToolbarHelp = false },
            title = { Text(stringResource(R.string.format_help_title)) },
            text = { Text(stringResource(R.string.format_help_text)) },
            confirmButton = {
                TextButton(onClick = { showToolbarHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
    // Full-screen overlay (NOT a separate Dialog window). A separate dialog window is not
    // edge-to-edge, so when the stretchy editor grows on each keystroke the window re-lays out
    // and briefly hides the status bar (shifting the content). As a full-screen composable the
    // content is part of the activity's edge-to-edge window, so the status bar stays put.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        BackHandler { onClose() }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.compose)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        // Undo/Redo for the message field (rich-text editor). Uses the WebView's
                        // native editing history via the shared editor controller.
                        IconButton(onClick = { editorController.exec("undo") }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                        }
                        IconButton(onClick = { editorController.exec("redo") }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
                        }
                        IconButton(onClick = { showToolbarHelp = true }) {
                            Icon(Icons.Default.Help, contentDescription = stringResource(R.string.help))
                        }
                        IconButton(onClick = onSend, enabled = !isSending && body.isNotBlank()) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
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
                    // Formatting toolbar, placed at the top (second row) so it is always
                    // visible. The compress / mic / AI-answer actions are built in as icons
                    // at the start of the toolbar.
                    com.mailchat.ui.component.RichTextToolbar(
                        controller = editorController,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        leadingContent = {
                            // Compress (Zip)
                            IconButton(onClick = { showCompressDialog = true }) {
                                Icon(Icons.Default.Compress, contentDescription = stringResource(R.string.compress), modifier = Modifier.size(20.dp))
                            }
                            // Mic (long-press to record)
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
                                    .size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.voice_record),
                                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp).offset(y = 7.dp)
                                )
                            }
                            // AI answer
                            IconButton(onClick = onGenerateAi, enabled = !isGeneratingSuggestion) {
                                if (isGeneratingSuggestion) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.ai_reply), modifier = Modifier.size(20.dp))
                                }
                            }
                            // Separator between the action icons and the formatting buttons.
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    )
                    HorizontalDivider()
                    // AI suggestion (fixed position, below the toolbar) so it is always
                    // visible when generated — the bottom of the dialog is off-screen.
                    if (aiSuggestion.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.ai_reply), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                // Scrollable so the full suggestion can be read (max height 160dp).
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(aiSuggestion, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = onApplyAi) { Text(stringResource(R.string.ai_apply)) }
                                    TextButton(onClick = onClearAi) { Text(stringResource(R.string.cancel)) }
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .imePadding()
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
                    // Rich-text editor — used for the detail-email reply / reply-all / forward.
                    // The formatting toolbar is rendered separately above the keyboard (see
                    // RichTextToolbar below), so it is not shown on top of the editor here.
                    com.mailchat.ui.component.RichTextEditor(
                        initialHtml = body,
                        onHtmlChange = onBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.type_message),
                        controller = editorController,
                        showToolbar = false
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
                        // Quote toggle (reply / reply-all only): the quote is part of the
                        // message field (editable); toggling removes / re-inserts it.
                        if (hasQuote) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = onToggleQuote) {
                                Icon(
                                    if (quoteEnabled) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (quoteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.quote_toggle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (quoteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

/**
 * Removes the email address header (From/To/Cc/Date/Subject lines) from the beginning of [text]
 * so the appointment search starts after it. The header contains the email's received date, which
 * must not be extracted as an appointment. Only leading header lines are removed; the body is kept
 * intact. Works for the German and English labels used by the full-email view.
 */
private fun stripEmailHeader(text: String): String {
    val headerPrefixes = listOf(
        "from:", "to:", "cc:", "bcc:", "date:", "subject:",
        "von:", "an:", "datum:", "betreff:"
    )
    val lines = text.lines()
    var i = 0
    // Skip a leading subject line (the first non-header line, if the next line is a header line).
    if (lines.isNotEmpty() && !lines[0].trim().isBlank()) {
        val firstLower = lines[0].trim().lowercase()
        val nextIsHeader = lines.getOrNull(1)?.trim()?.lowercase()?.let { nl ->
            headerPrefixes.any { nl.startsWith(it) }
        } == true
        if (!headerPrefixes.any { firstLower.startsWith(it) } && nextIsHeader) i = 1
    }
    // Skip the consecutive header lines.
    while (i < lines.size) {
        val lower = lines[i].trim().lowercase()
        if (lower.isBlank()) break
        if (headerPrefixes.any { lower.startsWith(it) }) i++ else break
    }
    return lines.drop(i).joinToString("\n").trim()
}

/**
 * The display name for a chat in the appointment recipient/group picker: the group display name
 * for groups, otherwise the participant name (or the email local-part as a fallback).
 */
private fun apptPickerDisplayName(chat: com.mailchat.data.model.Chat): String {
    val isGroup = com.mailchat.util.EmailGrouping.isGroupKey(chat.participantEmail)
    return if (isGroup) {
        chat.participantName.ifBlank {
            com.mailchat.util.EmailGrouping.groupDisplayName(
                chat.participantEmail.split(";").map { it.trim() }
            )
        }
    } else {
        chat.participantName.ifBlank { chat.participantEmail.substringBefore('@') }
    }
}

/**
 * Opens the system calendar's "insert event" screen pre-filled with [appointment]. Used both for
 * a single detected appointment and after the user picked one from the multi-appointment dialog.
 */
private fun launchCalendarEventInsert(
    context: android.content.Context,
    appointment: com.mailchat.ai.GeminiService.Appointment,
    description: String,
    chooserLabel: String
) {
    val (title, dateStr, timeStr, durationMin, location) = appointment
    val calendarIntent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
        data = android.net.Uri.parse("content://com.android.calendar/events")
        putExtra("title", title)
        putExtra("description", description)
        // The official extra is "eventLocation" (CalendarContract.Events.EVENT_LOCATION);
        // "location" is set too for third-party calendar apps that read it.
        if (location.isNotBlank()) {
            putExtra("eventLocation", location)
            putExtra("location", location)
        }
        // The context may be the Application context (not an Activity), so starting an activity
        // from it requires FLAG_ACTIVITY_NEW_TASK.
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
    context.startActivity(android.content.Intent.createChooser(calendarIntent, chooserLabel))
}

/**
 * Poll (Umfrage) editor: title, question, a dynamic list of options and a multiple-choice toggle.
 * Shown as a full-screen overlay; [onCreate] is called with the validated poll data.
 */
@Composable
private fun PollEditor(
    isSending: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, question: String, options: List<String>, multi: Boolean, numberMode: Boolean, personMode: Boolean) -> Unit
) {
    val context = LocalContext.current
    // Pre-fill the title with "Umfrage: " / "Poll: " (editable) so the poll start email
    // contains the word "Umfrage:" by default.
    val isGermanUi = com.mailchat.util.SettingsUtils.isGermanUi(context)
    var title by remember { mutableStateOf(if (isGermanUi) "Umfrage: " else "Poll: ") }
    var question by remember { mutableStateOf("") }
    // IMPORTANT: always replace the list with a NEW instance (never mutate in place) so Compose
    // detects the change and recomposes (in-place mutation of a list in mutableStateOf is not
    // observed → text fields wouldn't update and deleting an option would crash).
    var options by remember { mutableStateOf(listOf("", "")) }
    var multi by remember { mutableStateOf(false) }
    // Numeric poll: respondents enter a number per option (summed up). Outside person mode it is
    // mutually exclusive with the multiple-choice toggle (a poll is either a checkbox poll or a
    // numeric poll). In person mode, numbers FORCE multiple choice (a person can fill in several
    // options), so the multi toggle is locked on while numbers is on.
    var numberMode by remember { mutableStateOf(false) }
    // Person-related poll: the result is a table (persons × options, bottom row = column sums).
    var personMode by remember { mutableStateOf(false) }
    val validOptions = options.map { it.trim() }.filter { it.isNotBlank() }
    val canSend = title.isNotBlank() && validOptions.size >= 2 && !isSending

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.poll_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.poll_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text(stringResource(R.string.poll_question_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    for (i in options.indices) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = options[i],
                                onValueChange = { newValue ->
                                    val updated = options.toMutableList()
                                    updated[i] = newValue
                                    options = updated
                                },
                                label = { Text(stringResource(R.string.poll_option_label, i + 1)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (options.size > 2) {
                                IconButton(onClick = {
                                    val updated = options.toMutableList()
                                    updated.removeAt(i)
                                    options = updated
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    TextButton(onClick = {
                        if (options.size < 10) options = options + ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.poll_add_option))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = multi,
                            // Locked on while a person poll uses numbers (numbers force multiple choice).
                            enabled = !(personMode && numberMode),
                            onCheckedChange = { newMulti ->
                                multi = newMulti
                                // Outside person mode, multi and numbers are mutually exclusive.
                                if (newMulti && !personMode) numberMode = false
                            }
                        )
                        Text(stringResource(R.string.poll_multi_choice), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = personMode,
                            onCheckedChange = { newPerson ->
                                personMode = newPerson
                                if (newPerson) {
                                    if (numberMode) multi = true  // numbers force multi in person mode
                                } else {
                                    // Turning off person mode: restore mutual exclusivity (multi XOR numbers).
                                    if (multi && numberMode) numberMode = false
                                }
                            }
                        )
                        Text(stringResource(R.string.poll_person_mode), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = numberMode,
                            onCheckedChange = { newNumber ->
                                numberMode = newNumber
                                if (newNumber) {
                                    if (personMode) multi = true   // numbers force multi in person mode
                                    else multi = false             // mutual exclusivity outside person mode
                                }
                            }
                        )
                        Text(stringResource(R.string.poll_number_mode), style = MaterialTheme.typography.bodyMedium)
                    }
                    // The send button lives INSIDE the scrollable area so it is always fully
                    // visible (a fixed button at the bottom edge gets clipped when the keyboard
                    // or the screen height is small).
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onCreate(title.trim(), question.trim(), validOptions, multi, numberMode, personMode) },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.poll_send))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * TERMIN (appointment) editor: title, question, start/end date+time, all-day toggle and location.
 * Shown as a full-screen overlay; [onCreate] is called with the validated appointment data. When
 * [initial] is set (an AI-detected appointment), the fields are pre-filled with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentEditor(
    isSending: Boolean,
    initial: com.mailchat.ai.GeminiService.Appointment?,
    onDismiss: () -> Unit,
    onCreate: (title: String, question: String, startDate: String, startTime: String, endDate: String, endTime: String, allDay: Boolean, location: String) -> Unit
) {
    val context = LocalContext.current
    val isGermanUi = com.mailchat.util.SettingsUtils.isGermanUi(context)
    val today = java.time.LocalDate.now()
    fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = h * 60 + m + minutes
        return "%02d:%02d".format((total / 60) % 24, total % 60)
    }
    var title by remember { mutableStateOf(initial?.title ?: (if (isGermanUi) "Termin: " else "Appointment: ")) }
    var question by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(initial?.date ?: today.toString()) }
    var startTime by remember { mutableStateOf(initial?.time ?: "09:00") }
    var endDate by remember { mutableStateOf(initial?.date ?: today.toString()) }
    var endTime by remember { mutableStateOf(initial?.let { addMinutes(it.time, it.durationMin) } ?: "10:00") }
    var allDay by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val validDates = startDate.isNotBlank() && endDate.isNotBlank() &&
        (allDay || (startTime.isNotBlank() && endTime.isNotBlank())) &&
        (startDate < endDate || (startDate == endDate && (allDay || startTime <= endTime)))
    val canSend = title.isNotBlank() && validDates && !isSending

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.appt_editor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.appt_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text(stringResource(R.string.appt_question_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                        Text(stringResource(R.string.appt_all_day))
                    }
                    Spacer(Modifier.height(8.dp))
                    ApptDateField(stringResource(R.string.appt_start_date), startDate, { showStartDatePicker = true })
                    Spacer(Modifier.height(8.dp))
                    if (!allDay) {
                        ApptTimeField(stringResource(R.string.appt_start_time), startTime, { showStartTimePicker = true })
                        Spacer(Modifier.height(8.dp))
                    }
                    ApptDateField(stringResource(R.string.appt_end_date), endDate, { showEndDatePicker = true })
                    Spacer(Modifier.height(8.dp))
                    if (!allDay) {
                        ApptTimeField(stringResource(R.string.appt_end_time), endTime, { showEndTimePicker = true })
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.appt_location_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!validDates) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.appt_date_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onCreate(title.trim(), question.trim(), startDate, startTime, endDate, endTime, allDay, location.trim()) },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.appt_send))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Date pickers (Material3). The state is created when the picker branch enters composition
    // (i.e. when the picker opens) and discarded when it closes, so it always starts from the
    // current value.
    if (showStartDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = apptDateToMillis(startDate)
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDate = apptMillisToDate(it) }
                    showStartDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }
    if (showEndDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = apptDateToMillis(endDate)
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = apptMillisToDate(it) }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }
    if (showStartTimePicker) {
        ApptTimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { startTime = it; showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        ApptTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { endTime = it; showEndTimePicker = false }
        )
    }
}

/**
 * A time picker dialog (Material3 TimePicker in a plain Dialog with OK/Cancel buttons).
 * [onConfirm] receives the chosen time as "HH:mm".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApptTimePickerDialog(initialTime: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val (h, m) = apptTimeParts(initialTime)
    val state = androidx.compose.material3.rememberTimePickerState(initialHour = h, initialMinute = m)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/** Converts a "YYYY-MM-DD" date to epoch millis (UTC midnight), or null when invalid. */
private fun apptDateToMillis(date: String): Long? = try {
    java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}

/** Converts epoch millis (UTC) back to a "YYYY-MM-DD" date. */
private fun apptMillisToDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

/** Splits an "HH:mm" time into (hour, minute). */
private fun apptTimeParts(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** A read-only date field that opens a date picker when tapped (trailing icon). */
@Composable
private fun ApptDateField(label: String, value: String, onPick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(Icons.Default.Event, contentDescription = label)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** A read-only time field that opens a time picker when tapped (trailing icon). */
@Composable
private fun ApptTimeField(label: String, value: String, onPick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(Icons.Default.AccessTime, contentDescription = label)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Interactive poll (Umfrage) view: shows the question, the options as checkboxes (when open),
 * an answer button, the results as a bar chart and (for the creator) a close button.
 */
@Composable
private fun PollView(message: Message, viewModel: ChatDetailViewModel) {
    val pollId = message.pollId
    var results by remember { mutableStateOf<ChatDetailViewModel.PollResults?>(null) }
    // Fetch incoming emails ONCE when the poll view is opened (while the poll is still open),
    // so new votes and the result email are processed immediately (results show up right away).
    var pollSyncTriggered by remember(pollId) { mutableStateOf(false) }
    LaunchedEffect(pollId) {
        viewModel.observePollResults(pollId).collect { res ->
            results = res
            if (!pollSyncTriggered && res != null &&
                res.poll.status == com.mailchat.data.model.Poll.STATUS_OPEN
            ) {
                pollSyncTriggered = true
                // Start the fetch window at the poll message (minus 1h for clock skew), so all
                // poll answers — which are replies after the poll email — are covered.
                val periodStart = if (message.timestamp > 0) message.timestamp - 3_600_000 else null
                viewModel.syncChat(periodStartOverride = periodStart)
            }
        }
    }
    val accountEmail = viewModel.uiState.value.accountEmail?.let { it.trim().lowercase() } ?: ""
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Numeric polls: the number entered per option (aligned to the options, "" = not entered).
    var numberValues by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }

    val r = results
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val poll = r.poll
    val options = PollFormat.optionsFromJson(poll.options)
    // Pre-select the user's existing vote (once, so live updates don't clobber the selection).
    if (!initialized) {
        val myVote = r.votes.firstOrNull { it.voterEmail.trim().lowercase() == accountEmail }
        selected = myVote?.let { PollFormat.indicesFromJson(it.optionIndices).toSet() } ?: emptySet()
        numberValues = if (myVote != null) {
            val vals = PollFormat.valuesFromJson(myVote.values)
            options.indices.map { i ->
                val v = vals.getOrElse(i) { 0.0 }
                if (v == 0.0) "" else fmtPollNum(v)
            }
        } else {
            options.indices.map { "" }
        }
        initialized = true
    }
    val isOpen = poll.status == com.mailchat.data.model.Poll.STATUS_OPEN
    // The poll message is outgoing in the creator's account and incoming in a recipient's.
    val isCreator = !message.isIncoming

    // Who has answered: the creator sees it live (per-recipient polls); a recipient sees the
    // same list from the structured result in the result email (once the poll is closed).
    val displayRecipients: List<Pair<String, Boolean>> = when {
        r.resultData?.recipients?.isNotEmpty() == true -> r.resultData.recipients
        r.recipients.isNotEmpty() -> r.recipients.map { p ->
            p.recipientEmail.trim().lowercase() to r.votes.any {
                it.voterEmail.trim().lowercase() == p.recipientEmail.trim().lowercase()
            }
        }
        else -> emptyList()
    }
    // Contact names for the participant list and the person table (fallback: the email).
    val nameEmails = remember(displayRecipients, r.votes) {
        (displayRecipients.map { it.first } + r.votes.map { it.voterEmail })
            .filter { it.isNotBlank() }.distinct()
    }
    var contactNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(nameEmails) {
        contactNames = viewModel.resolveContactNames(nameEmails)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("📊 ${poll.title}", style = MaterialTheme.typography.titleLarge)
        // Always show the question (also in the result view, so the result stays understandable).
        if (poll.question.isNotBlank()) {
            Text(
                poll.question,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            if (isOpen) {
                when {
                    poll.personMode -> stringResource(R.string.poll_person_mode)
                    poll.numberMode -> stringResource(R.string.poll_number_mode)
                    poll.multi -> stringResource(R.string.poll_multi)
                    else -> stringResource(R.string.poll_single)
                }
            } else {
                stringResource(R.string.poll_result)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        if (isOpen) {
            if (poll.numberMode) {
                // Numeric poll: a number input per option (the numbers are summed up in the result).
                for (i in options.indices) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            options[i],
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = numberValues.getOrElse(i) { "" },
                            onValueChange = { newValue ->
                                // Digits, an optional leading minus and at most one decimal point.
                                if (newValue.isEmpty() || newValue.matches(NUMBER_INPUT_REGEX)) {
                                    val updated = numberValues.toMutableList()
                                    while (updated.size < options.size) updated.add("")
                                    updated[i] = newValue
                                    numberValues = updated
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val parsedValues = numberValues.map { it.toDoubleOrNull() ?: 0.0 }
                Button(
                    onClick = { viewModel.answerPoll(pollId, emptyList(), parsedValues) },
                    enabled = numberValues.any { it.isNotBlank() }
                ) {
                    Text(stringResource(R.string.poll_answer))
                }
            } else {
                for (i in options.indices) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = i in selected,
                            onCheckedChange = {
                                if (poll.multi) {
                                    val mutable = selected.toMutableSet()
                                    if (i in mutable) mutable.remove(i) else mutable.add(i)
                                    selected = mutable
                                } else {
                                    selected = if (i in selected) emptySet() else setOf(i)
                                }
                            }
                        )
                        Text(options[i], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.answerPoll(pollId, selected.toList()) },
                    enabled = selected.isNotEmpty()
                ) {
                    Text(stringResource(R.string.poll_answer))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // The result: a recipient uses the structured result from the result email (the same
        // aggregated data the creator sees); the creator computes it live from the votes.
        val rd = r.resultData
        if (poll.personMode) {
            // Person-related poll: a table (persons × options, bottom row = column sums), shown
            // live while open and after closing.
            val rows = rd?.rows?.takeIf { it.isNotEmpty() }
                ?: PollFormat.personTableRows(options, r.votes, poll.numberMode)
            val sums = rd?.sums?.takeIf { it.isNotEmpty() }
                ?: PollFormat.personColumnSums(options, r.votes, poll.numberMode)
            PollPersonTable(options = options, rows = rows, sums = sums, contactNames = contactNames)
        } else {
            PollBarChart(
                options = options,
                counts = rd?.counts?.takeIf { it.isNotEmpty() } ?: r.counts,
                totalVotes = rd?.total ?: r.totalVotes,
                numberMode = poll.numberMode
            )
        }

        // Who has answered (creator: live per recipient; recipient: from the result email).
        if (displayRecipients.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.poll_recipients), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            for ((email, hasVoted) in displayRecipients) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasVoted) "✓" else "○",
                        color = if (hasVoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(contactNames[email.lowercase()] ?: email, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (isOpen) {
            if (isCreator) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.closePoll(pollId) }) {
                    Text(stringResource(R.string.poll_close))
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.poll_closed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dispatches a poll message to the right interactive view: an appointment (Termin) poll renders
 * [AppointmentView], everything else renders [PollView]. The mode is read from the poll record
 * (observed once), so a message that is an appointment is never shown as a regular poll.
 */
@Composable
private fun PollOrApptView(message: Message, viewModel: ChatDetailViewModel) {
    var isAppt by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(message.pollId) {
        viewModel.observePollResults(message.pollId).collect { res ->
            isAppt = res?.poll?.appointmentMode
        }
    }
    when (isAppt) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        true -> AppointmentView(message = message, viewModel = viewModel)
        false -> PollView(message = message, viewModel = viewModel)
    }
}

/**
 * Interactive TERMIN (appointment) view: shows the date/time/location, the Ja/Nein/Vielleicht
 * answer (with an optional comment), the responses (creator: live; recipient: the ASCII result
 * once closed), an "Add to calendar" button (ICS share) and (for the creator) a close button.
 */
@Composable
private fun AppointmentView(message: Message, viewModel: ChatDetailViewModel) {
    val context = LocalContext.current
    val pollId = message.pollId
    var results by remember { mutableStateOf<ChatDetailViewModel.PollResults?>(null) }
    var apptSyncTriggered by remember(pollId) { mutableStateOf(false) }
    LaunchedEffect(pollId) {
        viewModel.observePollResults(pollId).collect { res ->
            results = res
            if (!apptSyncTriggered && res != null &&
                res.poll.status == com.mailchat.data.model.Poll.STATUS_OPEN
            ) {
                apptSyncTriggered = true
                val periodStart = if (message.timestamp > 0) message.timestamp - 3_600_000 else null
                viewModel.syncChat(periodStartOverride = periodStart)
            }
        }
    }
    val accountEmail = viewModel.uiState.value.accountEmail?.let { it.trim().lowercase() } ?: ""
    var selectedChoice by remember { mutableStateOf<Int?>(null) }
    var comment by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    val isGerman = com.mailchat.util.SettingsUtils.isGermanUi(context)

    val r = results
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val poll = r.poll
    // Pre-select the user's existing answer (once, so live updates don't clobber the selection).
    if (!initialized) {
        val myVote = r.votes.firstOrNull { it.voterEmail.trim().lowercase() == accountEmail }
        selectedChoice = myVote?.let { PollFormat.indicesFromJson(it.optionIndices).firstOrNull() }
        comment = myVote?.comment ?: ""
        initialized = true
    }
    val isOpen = poll.status == com.mailchat.data.model.Poll.STATUS_OPEN
    val isCreator = !message.isIncoming
    val choiceLabels = listOf(
        stringResource(R.string.appt_answer_yes),
        stringResource(R.string.appt_answer_no),
        stringResource(R.string.appt_answer_maybe)
    )
    val dateLabel = AppointmentFormat.dateLabel(
        poll.startDate, poll.startTime, poll.endDate, poll.endTime, poll.allDay, isGerman
    )
    // Contact names for the response list (fallback: the email). For a closed appointment the
    // recipient sees the structured responses from the result email (not the local votes).
    val responseEmails = remember(r.votes, r.apptResultData) {
        (r.votes.map { it.voterEmail } + (r.apptResultData?.responses?.map { it.email } ?: emptyList()))
            .filter { it.isNotBlank() }.distinct()
    }
    var contactNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(responseEmails) {
        contactNames = viewModel.resolveContactNames(responseEmails)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("📅 ${poll.title}", style = MaterialTheme.typography.titleLarge)
        if (poll.question.isNotBlank()) {
            Text(poll.question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        // Date + location.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(dateLabel, style = MaterialTheme.typography.bodyLarge)
        }
        if (poll.location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(poll.location, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (isOpen) {
            Spacer(Modifier.height(16.dp))
            // Ja / Nein / Vielleicht (single choice).
            for (i in choiceLabels.indices) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedChoice == i,
                        onClick = { selectedChoice = i }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(choiceLabels[i], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.appt_comment_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val choice = selectedChoice ?: return@Button
                    viewModel.answerAppointment(pollId, choice, comment.trim())
                },
                enabled = selectedChoice != null && !uiStateIsSending(viewModel)
            ) {
                Text(stringResource(R.string.appt_send_answer))
            }
        }

        Spacer(Modifier.height(16.dp))
        // Responses. Creator: live (from the votes). Recipient (closed): the ASCII result body.
        if (isCreator) {
            val deduped = r.votes
            if (deduped.isNotEmpty()) {
                Text(stringResource(R.string.appt_recipients), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                for (vote in deduped) {
                    val choice = PollFormat.indicesFromJson(vote.optionIndices).firstOrNull()
                    val label = choice?.let { choiceLabels.getOrNull(it) } ?: "—"
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            if (choice != null) "✓" else "○",
                            color = if (choice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contactNames[vote.voterEmail.lowercase()] ?: vote.voterEmail, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(label)
                                    if (vote.comment.isNotBlank()) append(" — ").append(vote.comment)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                Text(
                    stringResource(R.string.appt_no_response),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!isOpen) {
            // Recipient, closed: show the structured result (the same responses the creator
            // sees), rendered like the creator's list. Fallback: the ASCII result body (legacy
            // result emails without the structured payload).
            val responses = r.apptResultData?.responses
            if (!responses.isNullOrEmpty()) {
                Text(stringResource(R.string.appt_recipients), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                for (resp in responses) {
                    val label = choiceLabels.getOrElse(resp.choice) { "—" }
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "✓",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contactNames[resp.email.lowercase()] ?: resp.email, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(label)
                                    if (resp.comment.isNotBlank()) append(" — ").append(resp.comment)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                // Summary line (Ja/Nein/Vielleicht counts).
                val counts = IntArray(3)
                for (resp in responses) if (resp.choice in 0..2) counts[resp.choice]++
                Text(
                    buildString {
                        append(choiceLabels.getOrElse(0) { "Ja" }).append(": ").append(counts[0]).append(" · ")
                        append(choiceLabels.getOrElse(1) { "Nein" }).append(": ").append(counts[1]).append(" · ")
                        append(choiceLabels.getOrElse(2) { "Vielleicht" }).append(": ").append(counts[2])
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                val body = message.bodyFull.ifBlank { message.body }
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // "Add to calendar" (ICS share) — available to everyone, open or closed.
        OutlinedButton(onClick = { viewModel.shareAppointmentToCalendar(pollId) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.appt_calendar))
        }

        if (isOpen) {
            if (isCreator) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.closePoll(pollId) }) {
                    Text(stringResource(R.string.appt_close))
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.poll_closed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Reads the current isSending flag from the ViewModel (for the answer button's enabled state). */
private fun uiStateIsSending(viewModel: ChatDetailViewModel): Boolean = viewModel.uiState.value.isSending

/** A simple horizontal bar chart for the poll results. */
@Composable
private fun PollBarChart(options: List<String>, counts: List<Int>, totalVotes: Int, numberMode: Boolean = false) {
    val maxCount = counts.maxOrNull() ?: 0
    // Numeric polls can have negative values: scale by the largest absolute value and draw a
    // diverging bar (zero line in the middle, positive → right, negative → left in red).
    val maxAbs = counts.maxOfOrNull { abs(it) } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in options.indices) {
            val count = counts.getOrElse(i) { 0 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    options[i],
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.widthIn(max = 140.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                if (numberMode) {
                    val fraction = if (maxAbs > 0) abs(count).toFloat() / maxAbs else 0f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        // Zero line in the middle of the track.
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                        if (count > 0) {
                            // Positive: bar grows to the right from the zero line.
                            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        } else if (count < 0) {
                            // Negative: red bar grows to the left from the zero line.
                            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.5f)) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                } else {
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(if (numberMode) 44.dp else 28.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (numberMode) R.string.poll_total else R.string.poll_votes, totalVotes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A table for a person-related poll: rows = persons (voters, already deduped — one row per
 * person), columns = options, and a bottom row with the column sums. For a checkbox poll a cell
 * is "✓" when the option was selected; for a numeric poll it is the entered number. The person
 * column shows the contact name when known (else the email).
 */
@Composable
private fun PollPersonTable(
    options: List<String>,
    rows: List<List<String>>,
    sums: List<Int>,
    contactNames: Map<String, String>
) {
    val personWidth = 140.dp
    val optWidth = 84.dp

    Column(Modifier.horizontalScroll(rememberScrollState())) {
        // Header row
        Row {
            PollTableCell(stringResource(R.string.poll_person), personWidth, bold = true)
            for (opt in options) PollTableCell(opt, optWidth, bold = true, centered = true)
        }
        // One row per person (contact name when known, else the email)
        for (row in rows) {
            Row {
                PollTableCell(contactNames[row.first().lowercase()] ?: row.first(), personWidth)
                for (i in options.indices) PollTableCell(row.getOrElse(i + 1) { "" }, optWidth, centered = true)
            }
        }
        // Bottom row: the sum of each column
        Row {
            PollTableCell(stringResource(R.string.poll_sum), personWidth, bold = true)
            for (i in options.indices) PollTableCell(sums.getOrElse(i) { 0 }.toString(), optWidth, bold = true, centered = true)
        }
    }
}

/** A single bordered cell of the person-poll table. */
@Composable
private fun PollTableCell(text: String, width: Dp, bold: Boolean = false, centered: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 34.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

// Numeric poll input: an optional leading minus, digits and at most one decimal point.
private val NUMBER_INPUT_REGEX = Regex("^-?\\d*\\.?\\d*$")

/** Formats a number without a trailing ".0" for whole values (5.0 → "5", 3.5 → "3.5"). */
private fun fmtPollNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

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
    // Marks this email as phishing (from the link-confirmation dialog).
    onMarkAsPhishing: (Message) -> Unit = {},
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
    onNext: () -> Unit,
    viewModel: ChatDetailViewModel,
    // An AI appointment was detected (single result, or one picked from several): the parent
    // shows the choice "send as appointment poll" vs. "add to my calendar".
    onApptAction: (com.mailchat.ai.GeminiService.Appointment, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val isHtml = message.bodyFull.contains("<", ignoreCase = true)
    // A poll message (not a poll answer, not a result email) is rendered as an interactive poll
    // instead of the raw body. Result emails show the ASCII bars as a regular email.
    val isPoll = message.pollId.isNotBlank() && !message.isPollAnswer &&
        PollFormat.parseResultMarker(message.bodyFull.ifBlank { message.body }) == null &&
        AppointmentFormat.parseApptResultMarker(message.bodyFull.ifBlank { message.body }) == null
    val isPhishingEmail = spamCategory == "phishing"
    // The WebView's client is created once (in the AndroidView factory), so it would capture a
    // stale phishing state. rememberUpdatedState keeps a reference that always reads the current
    // value, so marking/unmarking phishing takes effect immediately (no need to reopen the email).
    val isPhishingEmailRef = rememberUpdatedState(isPhishingEmail)
    val blockImagesForSpam = isSpamNow && spamCategory != "newsletter"
    var loadImages by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isCreatingEvent by remember { mutableStateOf(false) }
    // Multiple appointments detected by the AI: shown as a selection dialog before anything is
    // created in the calendar. Null = no selection pending.
    var pendingAppointments by remember { mutableStateOf<List<com.mailchat.ai.GeminiService.Appointment>?>(null) }
    // A link in the email was tapped: shown as a confirmation dialog (full, unmasked URL) before
    // it is opened. The user can also mark the email as phishing from this dialog. Null = none.
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    // Help dialog for the link-confirmation dialog (explains the phishing option).
    var showLinkHelp by remember { mutableStateOf(false) }
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
                                    val rawText = if (selectedText.isNotBlank()) selectedText
                                    else message.bodyFull.stripHtml().normalizeWhitespace()
                                    // Start the appointment search AFTER the address header: the
                                    // header (From/To/Date/Subject) contains the email's received
                                    // date, which must not be extracted as an appointment.
                                    val textToUse = stripEmailHeader(rawText)
                                    com.mailchat.ai.AppLogger.log(context, "Termin: textLen=${textToUse.length} selected=${selectedText.isNotBlank()} preview=${textToUse.take(100)}")
                                    val initOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.mailchat.util.AiEngine.configureAndInitialize(context)
                                    }
                                    if (!initOk) {
                                        com.mailchat.ai.AppLogger.log(context, "Termin: AI init failed")
                                        isCreatingEvent = false
                                        return@launch
                                    }
                                    val aiResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.mailchat.ai.GeminiService.extractAppointments(textToUse)
                                    }
                                    com.mailchat.ai.AppLogger.log(context, "Termin: AI results=$aiResults")
                                    when {
                                        aiResults.isEmpty() -> {
                                            com.mailchat.ai.AppLogger.log(context, "Termin: AI could not extract appointment")
                                        }
                                        aiResults.size == 1 -> {
                                            // Let the user choose: send as appointment poll or add to own calendar.
                                            onApptAction(aiResults[0], message.subject)
                                        }
                                        else -> {
                                            // Multiple appointments: let the user choose before anything
                                            // is created in the calendar.
                                            pendingAppointments = aiResults
                                        }
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
                // For phishing emails the hint also mentions the blocked links (they cannot be
                // loaded anyway); otherwise it only mentions the images.
                Text(
                    text = if (isPhishingEmail) {
                        stringResource(R.string.images_links_blocked_phishing)
                    } else {
                        stringResource(R.string.images_blocked_hint)
                    },
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
            if (isPoll) {
                PollOrApptView(message = message, viewModel = viewModel)
            } else if (isHtml) {
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
                                    // Read the CURRENT phishing state (the client is created once,
                                    // so a plain capture would be stale after marking/unmarking).
                                    if (isPhishingEmailRef.value) {
                                        // Links in Phishing-E-mails sind gesperrt
                                        android.widget.Toast.makeText(
                                            ctx,
                                            "Links in Phishing-E-mails sind gesperrt",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return true
                                    }
                                    // Show the full (unmasked) URL in a confirmation dialog before
                                    // opening it (phishing protection). The user can also mark the
                                    // email as phishing from that dialog.
                                    pendingUrl = request.url.toString()
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
                                            // Show the full (unmasked) URL in a confirmation dialog
                                            // before opening it (phishing protection).
                                            pendingUrl = span.url
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAttachments = !showAttachments }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showAttachments) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.attachments_count, attachments.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showAttachments) {
                        // The list has a solid background (so file names stay readable) and is
                        // scrollable with a max height (so many attachments don't push the
                        // reply buttons off-screen).
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            attachments.forEach { attachment ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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

    // Multiple appointments were detected: let the user pick one before it is created in the
    // calendar. Tapping an entry opens the calendar insert screen for that appointment.
    // Multiple appointments were detected: let the user pick one before it is created in the
    // calendar. Tapping an entry opens the calendar insert screen for that appointment.
    // (An `if` + `for` loop is used instead of `let`/`forEach` because those are regular
    // lambdas and would leave the composable context.)
    val pendingAppointmentsList = pendingAppointments
    if (pendingAppointmentsList != null) {
        AlertDialog(
            onDismissRequest = { pendingAppointments = null },
            title = { Text(stringResource(R.string.select_appointment)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    for (appt in pendingAppointmentsList) {
                        val dateLabel = try {
                            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(appt.date)
                            if (parsed != null) {
                                java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.getDefault()).format(parsed)
                            } else appt.date
                        } catch (_: Exception) { appt.date }
                        ListItem(
                            headlineContent = { Text(appt.title) },
                            supportingContent = {
                                val loc = if (appt.location.isNotBlank()) " · 📍 ${appt.location}" else ""
                                Text("$dateLabel · ${appt.time}$loc")
                            },
                            modifier = Modifier.clickable {
                                pendingAppointments = null
                                // Same choice as for a single detection: send as poll or own calendar.
                                onApptAction(appt, message.subject)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingAppointments = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Link confirmation: before a link is opened, show the full (unmasked) URL and ask for
    // confirmation. The user can also mark the email as phishing (which blocks all links in
    // this email). The help icon explains the options.
    val pendingUrlValue = pendingUrl
    if (pendingUrlValue != null) {
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.link_open_confirm_title), modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLinkHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = stringResource(R.string.help), modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                Column {
                    Text(stringResource(R.string.link_open_confirm_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    // Full, unmasked URL (selectable so it can be inspected / copied).
                    SelectionContainer {
                        Text(
                            text = pendingUrlValue,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = pendingUrlValue
                    pendingUrl = null
                    try {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (_: Exception) { }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        pendingUrl = null
                        onMarkAsPhishing(message)
                    }) {
                        Text(stringResource(R.string.spam_phishing), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { pendingUrl = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
    // Help for the link-confirmation dialog (explains the phishing option).
    if (showLinkHelp) {
        AlertDialog(
            onDismissRequest = { showLinkHelp = false },
            title = { Text(stringResource(R.string.link_help_title)) },
            text = { Text(stringResource(R.string.link_help_text)) },
            confirmButton = {
                TextButton(onClick = { showLinkHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
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

/**
 * Requests a single fresh location fix (GPS first, then network). Each attempt has its own
 * timeout; returns null if all attempts fail or time out. Used by "Mein Standort" when there
 * is no last known location.
 */
private suspend fun requestSingleLocationFix(lm: android.location.LocationManager): android.location.Location? {
    val attempts = listOf(
        android.location.LocationManager.GPS_PROVIDER to 20_000L,
        android.location.LocationManager.NETWORK_PROVIDER to 15_000L
    )
    for ((provider, timeoutMs) in attempts) {
        val available = try {
            lm.getProviders(false).contains(provider)
        } catch (e: Exception) {
            false
        }
        if (!available) continue
        val deferred = kotlinx.coroutines.CompletableDeferred<android.location.Location>()
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                deferred.complete(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }
        try {
            lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
        } catch (e: Exception) {
            continue
        }
        // requestSingleUpdate delivers at most one fix and then removes the listener
        // automatically; if the timeout fires first, a late fix is simply ignored.
        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (result != null) return result
    }
    return null
}

