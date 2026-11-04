package com.mailchat.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mailchat.R
import com.mailchat.ui.component.ChatListItem
import com.mailchat.ui.viewmodel.ChatListViewModel
import com.mailchat.util.EmailGrouping
import com.mailchat.util.SettingsUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (Long) -> Unit,
    onAccountSetup: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit = {},
    onAssistant: () -> Unit = {},
    onComposeNewEmail: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeChatId by remember { mutableStateOf(SettingsUtils.getActiveChatId(context).takeIf { it != 0L }) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergePhotoOptions by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var mergeSelectedPhoto by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSpamDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<com.mailchat.data.model.Chat?>(null) }
    var showNewEmailDialog by remember { mutableStateOf(false) }
    var newEmailRecipient by remember { mutableStateOf("") }
    var newEmailSubject by remember { mutableStateOf("") }
    var newEmailBody by remember { mutableStateOf("") }
    var newEmailIsGroup by remember { mutableStateOf(false) }
    var newEmailGroupName by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showTodoOnly by remember { mutableStateOf(false) }
    var showProcessedOnly by remember { mutableStateOf(false) }
    var showHiddenOnly by remember { mutableStateOf(false) }
    var filterPhishing by remember { mutableStateOf(false) }
    var filterUnwanted by remember { mutableStateOf(false) }
    var filterNewsletter by remember { mutableStateOf(false) }
    var filterCustomTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showUnreadOnly by remember { mutableStateOf(false) }
    var showGroupOnly by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAiWarningDialog by remember { mutableStateOf(false) }
    var chatToRename by remember { mutableStateOf<com.mailchat.data.model.Chat?>(null) }
    var showEmptyImportPicker by remember { mutableStateOf(false) }
    var emptyImportPassword by remember { mutableStateOf("") }
    var pendingImportFile by remember { mutableStateOf<File?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("mailchat_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_prompted", false)) {
            val contactsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val audioGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!contactsGranted || !audioGranted) {
                showPermissionDialog = true
            }
            prefs.edit().putBoolean("permissions_prompted", true).apply()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Berechtigungen") },
            text = {
                Text("MailChat benötigt Zugriff auf Kontakte (für Namen und Fotos) und Mikrofon (für Sprachnachrichten). Diese Berechtigungen sind optional.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_CONTACTS,
                            android.Manifest.permission.RECORD_AUDIO
                        )
                    )
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Später") }
            }
        )
    }

    val emptyImportPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val tmpFile = File(context.cacheDir, "import_config.enc")
                    tmpFile.writeBytes(bytes)
                    pendingImportFile = tmpFile
                    showEmptyImportPicker = true
                }
            } catch (_: Exception) {}
        }
    }

    val filteredChats = remember(
        uiState.chats,
        uiState.hiddenChats,
        searchQuery,
        showFavoritesOnly,
        showTodoOnly,
        showProcessedOnly,
        showHiddenOnly,
        filterPhishing,
        filterUnwanted,
        filterNewsletter,
        filterCustomTags,
        showUnreadOnly,
        showGroupOnly,
        uiState.spamCategories,
        uiState.chatCustomTags,
        uiState.todoCounts,
        uiState.processedCounts,
        uiState.contactNames
    ) {
        val sourceList = if (showHiddenOnly) uiState.hiddenChats else uiState.chats
        sourceList.filter { chat ->
            val spamCat = uiState.spamCategories[chat.id] ?: ""
            val anySpamFilter = filterPhishing || filterUnwanted || filterNewsletter
            val spamMatch = if (!anySpamFilter) true else when (spamCat) {
                "phishing" -> filterPhishing
                "unwanted_ad" -> filterUnwanted
                "newsletter" -> filterNewsletter
                else -> false
            }
            val customTag = uiState.chatCustomTags[chat.id] ?: ""
            val tagList = customTag.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val customTagMatch = if (filterCustomTags.isEmpty()) true else tagList.any { it in filterCustomTags }
            (!showFavoritesOnly || chat.favorite || chat.important) &&
                (!showTodoOnly || (uiState.todoCounts[chat.id] ?: 0) > 0) &&
                (!showProcessedOnly || (uiState.processedCounts[chat.id] ?: 0) > 0) &&
                (!showUnreadOnly || chat.unreadCount > 0) &&
                (!showGroupOnly || chat.participantEmail.contains(";")) &&
                spamMatch && customTagMatch
        }.let { base ->
            if (searchQuery.isBlank()) base else base.filter { chat ->
                chat.participantName.contains(searchQuery, ignoreCase = true) ||
                    chat.participantEmail.contains(searchQuery, ignoreCase = true) ||
                    (uiState.contactNames[chat.participantEmail.lowercase()] ?: "").contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessageBody.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Source list for selection actions: in the "Hidden" filter only the hidden
    // chats are selectable, so "select all" / "reverse" / "select last" must not
    // reach into the non-hidden chats (would be dangerous for destructive actions).
    val selectionSourceChats = if (showHiddenOnly) uiState.hiddenChats else uiState.chats

    LaunchedEffect(Unit) {
        viewModel.syncAll()
        viewModel.loadAiSummaries()
    }

    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
    }

    BackHandler(enabled = uiState.selectionMode) {
        viewModel.exitSelectionMode()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    LaunchedEffect(uiState.newEmailError) {
        uiState.newEmailError?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!uiState.selectionMode) {
                FloatingActionButton(
                    onClick = {
                        // Unified compose screen (same as "Share with"): From selection,
                        // CC/BCC, contact picker and attachment picker are all available.
                        onComposeNewEmail()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_email))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.selectionMode) {
                        Text(
                            text = stringResource(R.string.select_chats),
                            style = MaterialTheme.typography.titleLarge
                        )
                    } else {
                        val iconBitmap = remember {
                            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                            val bmp = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            drawable?.setBounds(0, 0, 192, 192)
                            drawable?.draw(canvas)
                            bmp.asImageBitmap()
                        }
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = stringResource(R.string.help),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { onHelp() }
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.selectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.selectionMode) {
                        if (uiState.selectedChatIds.isNotEmpty()) {
                            val allSelected = selectionSourceChats.isNotEmpty() && uiState.selectedChatIds.size >= selectionSourceChats.size
                            val aiEnabled = SettingsUtils.getAiEnabled(context)
                            val selectedChats = selectionSourceChats.filter { it.id in uiState.selectedChatIds }
                            val allNoReply = selectedChats.isNotEmpty() &&
                                selectedChats.all { EmailGrouping.isNoReply(it) }
                            Box {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { showEditSheet = true }) {
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
                                    expanded = showEditSheet,
                                    onDismissRequest = { showEditSheet = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)) },
                                        leadingIcon = { Icon(if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll, contentDescription = null) },
                                        onClick = { viewModel.toggleSelectAllChats(selectionSourceChats) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.reverse_selection)) },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                        onClick = { viewModel.reverseChatSelection(selectionSourceChats) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.select_last_chat)) },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = { viewModel.selectLastChat(selectionSourceChats) }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    if (aiEnabled) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.real_names)) },
                                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                            onClick = { viewModel.extractRealNamesForSelected() }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.ai_summarize)) },
                                            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = { showAiWarningDialog = true }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.merge)) },
                                        leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null) },
                                        onClick = { showMergeDialog = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (allNoReply) R.string.no_reply_allow else R.string.no_reply_prohibit)) },
                                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                        onClick = { viewModel.toggleNoReplyOnSelected() }
                                    )
                                    if (showHiddenOnly) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.show)) },
                                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                            onClick = { viewModel.showSelectedChats() }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.hide)) },
                                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                            onClick = { viewModel.hideSelectedChats() }
                                        )
                                    }
                                    androidx.compose.material3.HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.mark_read)) },
                                        leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
                                        onClick = { viewModel.markSelectedAsRead() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.favorite)) },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                        onClick = { viewModel.favoriteSelectedChats() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.important)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.StarHalf, contentDescription = null) },
                                        onClick = { viewModel.importantSelectedChats() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.spam_category)) },
                                        leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                        onClick = { showSpamDialog = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.set_custom_tag)) },
                                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                        onClick = { showCustomTagDialog = true }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_chats)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { showDeleteDialog = true }
                                    )
                                }
                            }
                        }
                    } else {
                        SpinningRefreshButton(isSpinning = uiState.isSyncing || uiState.isAiProcessing, onClick = { viewModel.syncAll() })
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        Box {
                            IconButton(onClick = { showFilterSheet = !showFilterSheet }) {
                            val activeFilterCount = listOf(
                                showFavoritesOnly, showTodoOnly, showProcessedOnly,
                                showHiddenOnly, filterPhishing, filterUnwanted, filterNewsletter,
                                showUnreadOnly, showGroupOnly
                            ).count { it } + filterCustomTags.size
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.filter_title),
                                        tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (activeFilterCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .align(Alignment.TopCenter)
                                                .offset(y = (-1).dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = activeFilterCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                                            )
                                        }
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = showFilterSheet,
                                onDismissRequest = { showFilterSheet = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_favorites)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (showFavoritesOnly) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showFavoritesOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showFavoritesOnly = !showFavoritesOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_unread)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Email,
                                            contentDescription = null,
                                            tint = if (showUnreadOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showUnreadOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showUnreadOnly = !showUnreadOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_group)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            tint = if (showGroupOnly) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showGroupOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showGroupOnly = !showGroupOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_processed)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (showProcessedOnly) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showProcessedOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showProcessedOnly = !showProcessedOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_todo)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = if (showTodoOnly) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showTodoOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showTodoOnly = !showTodoOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_hidden)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = if (showHiddenOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (showHiddenOnly) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { showHiddenOnly = !showHiddenOnly }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.spam_phishing)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Report,
                                            contentDescription = null,
                                            tint = if (filterPhishing) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterPhishing) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { filterPhishing = !filterPhishing }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.spam_unwanted)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Report,
                                            contentDescription = null,
                                            tint = if (filterUnwanted) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterUnwanted) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { filterUnwanted = !filterUnwanted }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.spam_newsletter)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Report,
                                            contentDescription = null,
                                            tint = if (filterNewsletter) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (filterNewsletter) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { filterNewsletter = !filterNewsletter }
                                )
                                val customTags = remember { SettingsUtils.getCustomTags(context).filter { it.name.isNotBlank() } }
                                if (customTags.isNotEmpty()) {
                                    customTags.forEach { tag ->
                                        DropdownMenuItem(
                                            text = { Text(tag.name) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Flag,
                                                    contentDescription = null,
                                                    tint = if (tag.name in filterCustomTags) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            trailingIcon = {
                                                if (tag.name in filterCustomTags) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            onClick = {
                                                filterCustomTags = if (tag.name in filterCustomTags) {
                                                    filterCustomTags - tag.name
                                                } else {
                                                    filterCustomTags + tag.name
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onAssistant) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(R.string.assistant_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    placeholder = { Text(stringResource(R.string.search_chats)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
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
                    // Stop button: cancel the running AI batch processing.
                    TextButton(onClick = { viewModel.stopAiProcessing() }) {
                        Text(
                            text = stringResource(R.string.ai_stop),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.chats.isEmpty() && !uiState.isSyncing) {
                    EmptyState(
                        onAddAccount = onAccountSetup,
                        onImportConfig = { emptyImportPicker.launch(arrayOf("*/*")) }
                    )
                } else if (filteredChats.isEmpty()) {
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
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = filteredChats,
                            key = { it.id }
                        ) { chat ->
                            val account = uiState.accounts.find { it.id == chat.accountId }
                            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                                positionalThreshold = { total -> total * 0.3f },
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            if (uiState.selectionMode) {
                                                viewModel.deselectChat(chat.id)
                                            } else {
                                                chatToDelete = chat
                                                showDeleteDialog = true
                                            }
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            if (uiState.selectionMode) {
                                                viewModel.selectChat(chat.id)
                                            } else {
                                                kotlinx.coroutines.MainScope().launch {
                                                    kotlinx.coroutines.delay(350)
                                                    viewModel.toggleImportantChat(chat.id)
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled ||
                                        dismissState.currentValue != SwipeToDismissBoxValue.Settled
                                    if (active) {
                                        val isRightSwipe = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
                                            (dismissState.targetValue == SwipeToDismissBoxValue.Settled && dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(if (isRightSwipe) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFF9E9E9E).copy(alpha = 0.1f))
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
                                        ) {
                                            if (uiState.selectionMode) {
                                                Icon(
                                                    imageVector = if (isRightSwipe) Icons.Default.Check else Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = if (isRightSwipe) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isRightSwipe) Icons.Default.Star else Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = if (isRightSwipe) Color(0xFFFFC107) else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            ChatListItem(
                                chat = chat,
                                avatarColor = account?.avatarColor ?: 0xFF128C7E.toInt(),
                                onClick = {
                                    activeChatId = chat.id
                                    SettingsUtils.setActiveChatId(context, chat.id)
                                    onChatClick(chat.id)
                                },
                                selectionMode = uiState.selectionMode,
                                selected = chat.id in uiState.selectedChatIds,
                                onLongClick = {
                                    if (uiState.selectionMode) {
                                        viewModel.exitSelectionMode()
                                    } else {
                                        viewModel.enterSelectionMode()
                                        viewModel.toggleChatSelection(chat.id)
                                    }
                                },
                                onSelectionToggle = { viewModel.toggleChatSelection(chat.id) },
                                isActive = activeChatId == chat.id,
                                photoUri = uiState.contactPhotos[chat.participantEmail],
                                messageCount = uiState.messageCounts[chat.id] ?: 0,
                                spamCategory = uiState.spamCategories[chat.id] ?: "",
                                noReply = EmailGrouping.isNoReply(chat),
                                displayName = if (chat.participantName == chat.participantEmail) {
                                    uiState.contactNames[chat.participantEmail.lowercase()]
                                } else {
                                    null
                                },
                                encryptionType = when {
                                    com.mailchat.crypto.OpenPgpKeyService.getPeer(context, chat.participantEmail) != null -> "openpgp"
                                    com.mailchat.crypto.CryptoService.getPeers(context)
                                        .any { it.email.lowercase() == chat.participantEmail.lowercase() && it.pinVerified } -> "legacy"
                                    else -> ""
                                },
                                customTag = chat.customTag.ifBlank { uiState.chatCustomTags[chat.id] ?: "" }
                            )
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyImportPicker && pendingImportFile != null) {
        AlertDialog(
            onDismissRequest = { showEmptyImportPicker = false; pendingImportFile = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = {
                OutlinedTextField(
                    value = emptyImportPassword,
                    onValueChange = { emptyImportPassword = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = pendingImportFile ?: return@TextButton
                        if (emptyImportPassword.isBlank()) return@TextButton
                        showEmptyImportPicker = false
                        MainScope().launch {
                            val result = com.mailchat.util.ConfigBackupUtils.importConfig(context, file, emptyImportPassword)
                            file.delete()
                            emptyImportPassword = ""
                            pendingImportFile = null
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar(context.getString(R.string.import_success))
                                if (result.getOrThrow().needsRestart) {
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(intent)
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                } else {
                                    viewModel.syncAll()
                                }
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.import_error, result.exceptionOrNull()?.message ?: ""))
                            }
                        }
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyImportPicker = false; pendingImportFile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    chatToRename?.let { chat ->
        var renameText by remember { mutableStateOf(chat.participantName) }
        AlertDialog(
            onDismissRequest = { chatToRename = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameChat(chat.id, renameText)
                        }
                        chatToRename = null
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { chatToRename = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMergeDialog) {
        val selectedChats = uiState.chats.filter { it.id in uiState.selectedChatIds }
        val photoOptions = selectedChats.mapNotNull { chat ->
            val photo = uiState.contactPhotos[chat.participantEmail.lowercase()]
            if (photo != null) Triple(chat.participantEmail, chat.participantName, photo) else null
        }.distinct()
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text(stringResource(R.string.merge_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.merge_confirm_message))
                    if (photoOptions.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.merge_select_photo),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        photoOptions.forEachIndexed { index, (email, name, photoUri) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (mergeSelectedPhoto == index)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { mergeSelectedPhoto = index }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                                Column {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMergeDialog = false
                    val selectedEmail = if (photoOptions.size > 1) photoOptions[mergeSelectedPhoto.coerceIn(0, photoOptions.size - 1)].first else null
                    viewModel.mergeSelectedChats(selectedEmail)
                }) {
                    Text(stringResource(R.string.merge))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCustomTagDialog) {
        val customTags = com.mailchat.util.SettingsUtils.getCustomTags(context).filter { it.name.isNotBlank() }
        AlertDialog(
            onDismissRequest = { showCustomTagDialog = false },
            title = { Text(stringResource(R.string.set_custom_tag)) },
            text = {
                Column {
                    customTags.forEach { tag ->
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setCustomTagOnSelected(tag.name)
                                    showCustomTagDialog = false
                                }
                                .padding(12.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.clear_custom_tag),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.setCustomTagOnSelected("")
                                showCustomTagDialog = false
                            }
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCustomTagDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    chatToDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text(stringResource(R.string.delete_chats_confirm_title)) },
            text = { Text(stringResource(R.string.delete_chats_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    chatToDelete = null
                    viewModel.deleteSingleChat(chat)
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAiWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAiWarningDialog = false },
            title = { Text(stringResource(R.string.ai_summarize)) },
            text = { Text(stringResource(R.string.ai_summarize_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showAiWarningDialog = false
                    showEditSheet = false
                    viewModel.processSelectedWithAi()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAiWarningDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; chatToDelete = null },
            title = { Text(stringResource(R.string.delete_chats_confirm_title)) },
            text = { Text(stringResource(R.string.delete_chats_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    chatToDelete?.let { viewModel.deleteSingleChat(it) }
                        ?: viewModel.deleteSelectedChats()
                    chatToDelete = null
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; chatToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSpamDialog) {
        AlertDialog(
            onDismissRequest = { showSpamDialog = false },
            title = { Text(stringResource(R.string.spam_category)) },
            text = {
                Column {
                    Text(stringResource(R.string.spam_category_info), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf(
                            Triple("", stringResource(R.string.spam_none), Color.Gray),
                            Triple("newsletter", stringResource(R.string.spam_newsletter), Color(0xFF388E3C)),
                            Triple("unwanted_ad", stringResource(R.string.spam_unwanted), Color(0xFFF9A825)),
                            Triple("phishing", stringResource(R.string.spam_phishing), Color(0xFFD32F2F))
                        )
                        options.forEach { (cat, label, color) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    showSpamDialog = false
                                    viewModel.setSpamCategoryOnSelected(cat)
                                },
                                label = { Text(label, color = color, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpamDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showNewEmailDialog) {
        var selectedAccountId by remember { mutableStateOf(uiState.accounts.firstOrNull()?.id ?: 0L) }
        NewEmailDialog(
            accounts = uiState.accounts,
            selectedAccountId = selectedAccountId,
            onAccountSelected = { selectedAccountId = it },
            contacts = uiState.contacts,
            recipient = newEmailRecipient,
            onRecipientChange = { newEmailRecipient = it },
            subject = newEmailSubject,
            onSubjectChange = { newEmailSubject = it },
            body = newEmailBody,
            onBodyChange = { newEmailBody = it },
            isGroupChat = newEmailIsGroup,
            onGroupChatChange = { newEmailIsGroup = it },
            groupName = newEmailGroupName,
            onGroupNameChange = { newEmailGroupName = it },
            isSending = uiState.isSendingNewEmail,
            onDismiss = { showNewEmailDialog = false },
            onSend = {
                showNewEmailDialog = false
                val groupName = if (newEmailIsGroup) newEmailGroupName else ""
                viewModel.sendNewEmail(newEmailRecipient, newEmailSubject, newEmailBody, groupName, selectedAccountId)
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
                    viewModel.retrySendNewEmailUnencrypted()
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewEmailDialog(
    accounts: List<com.mailchat.data.model.EmailAccount>,
    selectedAccountId: Long,
    onAccountSelected: (Long) -> Unit,
    contacts: List<com.mailchat.service.Contact>,
    recipient: String,
    onRecipientChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    isGroupChat: Boolean,
    onGroupChatChange: (Boolean) -> Unit,
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    var showAccountPicker by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_email)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (accounts.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showAccountPicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedAccount?.emailAddress ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (selectedAccount != null) {
                    Text(
                        text = "${stringResource(R.string.from_label)}: ${selectedAccount.emailAddress}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val recipientList = remember(recipient) {
                    recipient.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
                }
                if (recipientList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recipientList.forEach { email ->
                            val contactName = contacts.firstOrNull {
                                it.email.equals(email, ignoreCase = true)
                            }?.name
                            Row(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        onRecipientChange(
                                            recipientList.filter { it != email }.joinToString(", ")
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contactName ?: email,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                var inputText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text(stringResource(R.string.add_recipient)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = {
                                val addr = inputText.trim()
                                if (addr.contains("@") && addr !in recipientList) {
                                    onRecipientChange((recipientList + addr).joinToString(", "))
                                }
                                inputText = ""
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.group_chat),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isGroupChat, onCheckedChange = onGroupChatChange)
                }
                if (isGroupChat) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text(stringResource(R.string.group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = onSubjectChange,
                    label = { Text(stringResource(R.string.subject)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    label = { Text(stringResource(R.string.compose_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (contacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.select_recipient),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (contact.email !in recipientList) {
                                        onRecipientChange(
                                            (recipientList + contact.email).joinToString(", ")
                                        )
                                    }
                                }
                                .padding(vertical = 6.dp),
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = contact.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = recipient.isNotBlank() && body.isNotBlank() && !isSending) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(stringResource(R.string.send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text(stringResource(R.string.select_account)) },
            text = {
                Column {
                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAccountSelected(account.id)
                                    showAccountPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (account.id == selectedAccountId) Icons.Default.Check else Icons.Default.Email,
                                contentDescription = null,
                                tint = if (account.id == selectedAccountId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = account.emailAddress,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun EmptyState(
    onAddAccount: () -> Unit,
    onImportConfig: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        androidx.compose.material3.Button(
            onClick = onAddAccount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(stringResource(R.string.add_account))
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onImportConfig,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(stringResource(R.string.backup_import))
        }
    }
}

@Composable
private fun SpinningRefreshButton(isSpinning: Boolean, onClick: () -> Unit) {
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(1000),
                    androidx.compose.animation.core.RepeatMode.Restart
                )
            )
        } else {
            rotation.snapTo(0f)
        }
    }
    IconButton(onClick = onClick, enabled = !isSpinning) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = stringResource(R.string.sync),
            modifier = Modifier.rotate(rotation.value)
        )
    }
}