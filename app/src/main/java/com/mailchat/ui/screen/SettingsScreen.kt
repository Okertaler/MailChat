package com.mailchat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailchat.BuildConfig
import com.mailchat.R
import com.mailchat.data.model.EmailAccount
import com.mailchat.ui.viewmodel.ChatListViewModel
import com.mailchat.util.LanguageUtils
import com.mailchat.util.SettingsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * A single-line, tappable section header with a help icon and an expand/collapse chevron.
 * The whole row toggles the section; the help icon shows its own dialog without toggling.
 */
@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    helpTitle: String? = null,
    helpText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (helpTitle != null && helpText != null) {
            com.mailchat.ui.component.HelpIcon(
                title = helpTitle,
                text = helpText
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onVersionHistory: () -> Unit,
    onHelp: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onCustomTags: () -> Unit = {},
    onComposeEmail: (recipient: String, subject: String, body: String) -> Unit = { _, _, _ -> },
    onLegal: (String) -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    var accountToDelete by remember { mutableStateOf<EmailAccount?>(null) }
    var accountToRenew by remember { mutableStateOf<EmailAccount?>(null) }
    var accountForColor by remember { mutableStateOf<EmailAccount?>(null) }
    var selectedLanguage by remember { mutableStateOf(LanguageUtils.getLanguage(context)) }
    var selectedDarkMode by remember { mutableStateOf(SettingsUtils.getDarkMode(context)) }
    var selectedMaxSize by remember { mutableStateOf(SettingsUtils.getMaxEmailSizeMb(context)) }
    var selectedSyncPeriod by remember { mutableStateOf(SettingsUtils.getSyncPeriodDays(context)) }
    var selectedSyncInterval by remember { mutableStateOf(SettingsUtils.getSyncIntervalMinutes(context)) }
    var signatureText by remember { mutableStateOf(SettingsUtils.getSignature(context)) }
    var aiEnabled by remember { mutableStateOf(SettingsUtils.getAiEnabled(context)) }
    var aiAutoProcess by remember { mutableStateOf(SettingsUtils.getAiAutoProcess(context)) }
    var showAiWarning by remember { mutableStateOf(false) }
    var showAiLog by remember { mutableStateOf(false) }
    var showOpenAiWarning by remember { mutableStateOf(false) }
    var providerVersion by remember { mutableStateOf(0) }
    var showSecureKeyDialog by remember { mutableStateOf(false) }
    var secureKeyDialogKey by remember { mutableStateOf(0) }
    var showSecureConnectionDialog by remember { mutableStateOf(false) }
    var pgpKeyVersion by remember { mutableStateOf(0) }
    var autoLookupEnabled by remember { mutableStateOf(com.mailchat.crypto.OpenPgpKeyService.isAutoLookupEnabled(context)) }
    var isScanningContacts by remember { mutableStateOf(false) }

    // Private key export/import (.asc)
    var showImportPassphraseDialog by remember { mutableStateOf(false) }
    var importKeyContent by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var importKeyError by remember { mutableStateOf("") }
    var isImportingKey by remember { mutableStateOf(false) }

    // Export: the user chooses the passphrase of the exported file (or none).
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportKeyContent by remember { mutableStateOf("") }
    var isExportingKey by remember { mutableStateOf(false) }

    val exportKeyLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pgp-keys")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(exportKeyContent.toByteArray()) }
                android.widget.Toast.makeText(context, R.string.key_export_success, android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, R.string.key_export_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importKeyLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                if (content.contains("BEGIN PGP PRIVATE KEY BLOCK") ||
                    content.contains("BEGIN PGP SECRET KEY BLOCK")
                ) {
                    importKeyContent = content
                    importPassphrase = ""
                    importKeyError = ""
                } else {
                    importKeyError = context.getString(R.string.key_import_invalid)
                }
                showImportPassphraseDialog = true
            } catch (e: Exception) {
                importKeyError = context.getString(R.string.key_import_failed)
                showImportPassphraseDialog = true
            }
        }
    }

    // Collapsible sections: all collapsed by default, except Info.
    var accountsExpanded by remember { mutableStateOf(false) }
    var mailSettingsExpanded by remember { mutableStateOf(false) }
    var signatureExpanded by remember { mutableStateOf(false) }
    var aiSettingsExpanded by remember { mutableStateOf(false) }
    var secureSettingsExpanded by remember { mutableStateOf(false) }
    var generalExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var customTagsExpanded by remember { mutableStateOf(false) }
    var infoExpanded by remember { mutableStateOf(true) }
    var widgetExpanded by remember { mutableStateOf(false) }
    var widgetOpenMode by remember { mutableStateOf(SettingsUtils.getWidgetOpenMode(context)) }
    var showWidgetModeDropdown by remember { mutableStateOf(false) }
    var widgetContentMode by remember { mutableStateOf(SettingsUtils.getWidgetContentMode(context)) }
    var showWidgetContentDropdown by remember { mutableStateOf(false) }
    var widgetTransparency by remember { mutableIntStateOf(SettingsUtils.getWidgetTransparency(context)) }

    // Key signature (second signature on encrypted emails)
    var keySigValue by remember { mutableStateOf(SettingsUtils.getEncryptedKeySignature(context)) }
    var showKeySigDropdown by remember { mutableStateOf(false) }

    // Inline custom tags editing
    var tags by remember { mutableStateOf(SettingsUtils.getCustomTags(context)) }
    var showTagEditDialog by remember { mutableStateOf(false) }
    var tagEditIndex by remember { mutableStateOf(-1) }
    var tagEditName by remember { mutableStateOf("") }
    var tagEditDesc by remember { mutableStateOf("") }
    var showTagDeleteDialog by remember { mutableStateOf(false) }
    var tagDeleteIndex by remember { mutableStateOf(-1) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            CollapsibleSectionHeader(
                title = "📧 " + stringResource(R.string.accounts),
                expanded = accountsExpanded,
                onToggle = { accountsExpanded = !accountsExpanded },
                helpTitle = stringResource(R.string.help_accounts_title),
                helpText = stringResource(R.string.help_accounts_text)
            )

            if (accountsExpanded) {
            Button(
                onClick = onAddAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_account_button))
            }

            if (uiState.accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            uiState.accounts.forEach { account ->
                ListItem(
                    headlineContent = { Text(account.emailAddress) },
                    supportingContent = { Text(account.imapHost) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(account.avatarColor))
                                .clickable { accountForColor = account }
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { accountToDelete = account }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_account),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onEditAccount(account.id) })
                )
            }
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "⚙️ " + stringResource(R.string.mail_settings),
                expanded = mailSettingsExpanded,
                onToggle = { mailSettingsExpanded = !mailSettingsExpanded },
                helpTitle = stringResource(R.string.help_smtp_title),
                helpText = stringResource(R.string.help_smtp_text)
            )

            if (mailSettingsExpanded) {
            val smtpAccounts = uiState.accounts.filter { it.smtpHost.isNotBlank() }
            if (smtpAccounts.isNotEmpty()) {
                var showSenderDropdown by remember { mutableStateOf(false) }
                var senderId by remember { mutableLongStateOf(SettingsUtils.getSenderAccountId(context)) }
                LaunchedEffect(smtpAccounts, senderId) {
                    if (senderId != 0L && smtpAccounts.none { it.id == senderId }) {
                        SettingsUtils.setSenderAccountId(context, 0L)
                        senderId = 0L
                    }
                }
                val senderAccount = smtpAccounts.firstOrNull { it.id == senderId }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.smtp_sender_account)) },
                    supportingContent = {
                        Text(
                            senderAccount?.let { "${it.emailAddress} (${it.smtpHost})" }
                                ?: stringResource(R.string.smtp_sender_auto)
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.DropdownMenu(
                            expanded = showSenderDropdown,
                            onDismissRequest = { showSenderDropdown = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.smtp_sender_auto)) },
                                onClick = {
                                    showSenderDropdown = false
                                    SettingsUtils.setSenderAccountId(context, 0L)
                                    senderId = 0L
                                }
                            )
                            smtpAccounts.forEach { acc ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("${acc.emailAddress} · ${acc.smtpHost}") },
                                    onClick = {
                                        showSenderDropdown = false
                                        SettingsUtils.setSenderAccountId(context, acc.id)
                                        senderId = acc.id
                                    }
                                )
                            }
                        }
                        TextButton(onClick = { showSenderDropdown = true }) {
                            Text(
                                senderAccount?.emailAddress
                                    ?: stringResource(R.string.smtp_sender_auto_short),
                                maxLines = 1
                            )
                        }
                    },
                     modifier = Modifier.fillMaxWidth()
                 )
            }
            SectionDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.max_email_size)) },
                supportingContent = { Text(stringResource(R.string.max_email_size_value, selectedMaxSize)) },
                trailingContent = {
                    Row {
                        TextButton(
                            onClick = {
                                val newSize = (selectedMaxSize - 1).coerceAtLeast(1)
                                selectedMaxSize = newSize
                                SettingsUtils.setMaxEmailSizeMb(context, newSize)
                            },
                            enabled = selectedMaxSize > 1
                        ) { Text("−") }
                        Text(
                            text = "$selectedMaxSize MB",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                val newSize = (selectedMaxSize + 1).coerceAtMost(50)
                                selectedMaxSize = newSize
                                SettingsUtils.setMaxEmailSizeMb(context, newSize)
                            }
                        ) { Text("+") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.sync_period)) },
                supportingContent = { Text(stringResource(R.string.sync_period_value, selectedSyncPeriod)) },
                trailingContent = {
                    Row {
                        TextButton(
                            onClick = {
                                val newDays = (selectedSyncPeriod - 1).coerceAtLeast(1)
                                selectedSyncPeriod = newDays
                                SettingsUtils.setSyncPeriodDays(context, newDays)
                                com.mailchat.service.SyncWorker.runImmediateSync(context)
                            },
                            enabled = selectedSyncPeriod > 1
                        ) { Text("−") }
                        Text(
                            text = stringResource(R.string.sync_period_value, selectedSyncPeriod),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                val newDays = (selectedSyncPeriod + 1).coerceAtMost(3650)
                                selectedSyncPeriod = newDays
                                SettingsUtils.setSyncPeriodDays(context, newDays)
                                com.mailchat.service.SyncWorker.runImmediateSync(context)
                            }
                        ) { Text("+") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.reset_sync)) },
                supportingContent = { Text(stringResource(R.string.reset_sync_desc)) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            com.mailchat.service.SyncWorker.resetSync(context)
                        }
                    ) {
                        Text(stringResource(R.string.reset_sync))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()
            var showSyncDropdown by remember { mutableStateOf(false) }
            // Note: no sub-15-minute option — WorkManager periodic work cannot run more
            // frequently than every 15 minutes, so a "5 minutes" entry would be misleading.
            val syncIntervals = listOf(0, 15, 30, 60)
            val syncLabels = listOf(
                stringResource(R.string.sync_off),
                stringResource(R.string.sync_15min),
                stringResource(R.string.sync_30min),
                stringResource(R.string.sync_1hour)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.auto_sync)) },
                supportingContent = { Text(stringResource(R.string.auto_sync_desc)) },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSyncDropdown,
                        onDismissRequest = { showSyncDropdown = false }
                    ) {
                        syncIntervals.forEachIndexed { index, minutes ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(syncLabels[index]) },
                                onClick = {
                                    showSyncDropdown = false
                                    selectedSyncInterval = minutes
                                    SettingsUtils.setSyncIntervalMinutes(context, minutes)
                                    com.mailchat.service.SyncWorker.schedule(context, minutes)
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showSyncDropdown = true }) {
                        // A legacy stored value (e.g. 5) is not in the list; it is coerced to
                        // 15 minutes by SyncWorker, so show that instead of falling back to "Off".
                        val idx = syncIntervals.indexOf(selectedSyncInterval)
                        Text(if (idx >= 0) syncLabels[idx] else stringResource(R.string.sync_15min))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()
            var imapPushEnabled by remember { mutableStateOf(SettingsUtils.isImapPushEnabled(context)) }
            var showBatteryDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.imap_push)) },
                supportingContent = { Text(stringResource(R.string.imap_push_desc)) },
                trailingContent = {
                    Switch(
                        checked = imapPushEnabled,
                        onCheckedChange = { checked ->
                            imapPushEnabled = checked
                            SettingsUtils.setImapPushEnabled(context, checked)
                            if (checked) {
                                com.mailchat.service.ImapPushService.start(context)
                                if (!com.mailchat.service.ImapPushService.isIgnoringBatteryOptimizations(context)) {
                                    showBatteryDialog = true
                                }
                            } else {
                                com.mailchat.service.ImapPushService.stop(context)
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()
            val ignoringBatteryOptimization =
                com.mailchat.service.ImapPushService.isIgnoringBatteryOptimizations(context)
            ListItem(
                headlineContent = { Text(stringResource(R.string.battery_optimization_item)) },
                supportingContent = {
                    Text(
                        if (ignoringBatteryOptimization) stringResource(R.string.battery_optimization_active)
                        else stringResource(R.string.battery_optimization_desc)
                    )
                },
                trailingContent = {
                    if (!ignoringBatteryOptimization) {
                        TextButton(onClick = { showBatteryDialog = true }) {
                            Text(stringResource(R.string.battery_optimization_action))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (showBatteryDialog) {
                AlertDialog(
                    onDismissRequest = { showBatteryDialog = false },
                    title = { Text(stringResource(R.string.battery_optimization_title)) },
                    text = { Text(stringResource(R.string.battery_optimization_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showBatteryDialog = false
                            com.mailchat.service.ImapPushService.requestIgnoreBatteryOptimizations(context)
                        }) {
                            Text(stringResource(R.string.battery_optimization_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatteryDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "✍️ " + stringResource(R.string.signature),
                expanded = signatureExpanded,
                onToggle = { signatureExpanded = !signatureExpanded },
                helpTitle = stringResource(R.string.help_signature_title),
                helpText = stringResource(R.string.help_signature_text)
            )

            if (signatureExpanded) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.text_signature)) },
                supportingContent = { Text(stringResource(R.string.signature_desc)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = signatureText,
                onValueChange = {
                    signatureText = it
                    SettingsUtils.setSignature(context, it)
                },
                label = { Text(stringResource(R.string.text_signature)) },
                minLines = 1,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val keySigOptions = listOf(
                SettingsUtils.KEY_SIG_OFF to stringResource(R.string.key_sig_off),
                SettingsUtils.KEY_SIG_KEY to stringResource(R.string.key_sig_key),
                SettingsUtils.KEY_SIG_LINK to stringResource(R.string.key_sig_link)
            )
            val currentKeySigLabel = keySigOptions.firstOrNull { it.first == keySigValue }?.second
                ?: stringResource(R.string.key_sig_off)
            ListItem(
                headlineContent = { Text(stringResource(R.string.key_signature)) },
                supportingContent = { Text(stringResource(R.string.key_signature_desc)) },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showKeySigDropdown,
                        onDismissRequest = { showKeySigDropdown = false }
                    ) {
                        keySigOptions.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showKeySigDropdown = false
                                    keySigValue = value
                                    SettingsUtils.setEncryptedKeySignature(context, value)
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showKeySigDropdown = true }) {
                        Text(currentKeySigLabel)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "🤖 " + stringResource(R.string.ai_settings),
                expanded = aiSettingsExpanded,
                onToggle = { aiSettingsExpanded = !aiSettingsExpanded },
                helpTitle = stringResource(R.string.help_ai_settings_title),
                helpText = stringResource(R.string.help_ai_settings_text)
            )

            if (aiSettingsExpanded) {

            ListItem(
                headlineContent = { Text(stringResource(R.string.ai_enabled)) },
                supportingContent = {
                    Text(
                        if (aiEnabled) stringResource(R.string.ai_on) else stringResource(R.string.ai_off)
                    )
                },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = aiEnabled,
                        onCheckedChange = {
                            if (it) {
                                showAiWarning = true
                            } else {
                                aiEnabled = false
                                SettingsUtils.setAiEnabled(context, false)
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (aiEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_auto_process)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.ai_auto_process_desc))
                            Text(
                                text = stringResource(R.string.ai_auto_process_warning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = aiAutoProcess,
                            onCheckedChange = {
                                aiAutoProcess = it
                                SettingsUtils.setAiAutoProcess(context, it)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            var showModelDropdown by remember { mutableStateOf(false) }
            var selectedModelId by remember { mutableStateOf(SettingsUtils.getAiModel(context)) }
            val modelOptions = com.mailchat.ai.GeminiService.getAvailableModels()
            val currentModel = com.mailchat.ai.GeminiService.getModelById(selectedModelId)
            val currentModelLabel = currentModel?.label ?: selectedModelId
            val isDownloaded = com.mailchat.ai.GeminiService.isModelDownloaded(context, selectedModelId)
            val downloadState by com.mailchat.ai.GeminiService.downloadState.collectAsStateWithLifecycle()
            val isDownloading = downloadState is com.mailchat.ai.GeminiService.DownloadState.Downloading

            ListItem(
                headlineContent = { Text(stringResource(R.string.ai_model)) },
                supportingContent = {
                    Text(
                        if (isDownloaded) {
                            "${currentModelLabel} · ${stringResource(R.string.ai_downloaded)}"
                        } else {
                            "${currentModelLabel} · ${currentModel?.sizeGb?.let { "%.1f GB".format(it) } ?: ""}"
                        }
                    )
                },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        modelOptions.forEach { model ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${model.label} (${model.sizeGb} GB, ${stringResource(R.string.ai_local)})") },
                                onClick = {
                                    showModelDropdown = false
                                    selectedModelId = model.id
                                    SettingsUtils.setAiModel(context, model.id)
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showModelDropdown = true }) {
                        Text("$currentModelLabel (${stringResource(R.string.ai_local)})")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isDownloaded && !isDownloading) {
                Button(
                    onClick = {
                        val model = com.mailchat.ai.GeminiService.getModelById(selectedModelId)
                        if (model != null) {
                            MainScope().launch {
                                val ok = com.mailchat.ai.GeminiService.downloadModel(context, model) { _ ->
                                    // Progress wird ueber GeminiService.downloadState getrackt
                                }
                                if (ok) {
                                    SettingsUtils.setAiModel(context, model.id)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.ai_download_button))
                }
            }

            val ds = downloadState
            if (isDownloading && ds is com.mailchat.ai.GeminiService.DownloadState.Downloading) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_downloading)) },
                    supportingContent = {
                        Text("${(ds.progress * 100).toInt()}%")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (ds is com.mailchat.ai.GeminiService.DownloadState.Error) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_download_error)) },
                    supportingContent = { Text(ds.message) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val embeddingModel = com.mailchat.ai.GeminiService.getEmbeddingModel()
            val embeddingDownloaded = com.mailchat.ai.GeminiService.isModelDownloaded(context, embeddingModel.id)
            ListItem(
                headlineContent = { Text(stringResource(R.string.ai_embedding_model)) },
                supportingContent = {
                    Text(
                        if (embeddingDownloaded) {
                            "${embeddingModel.label} · ${stringResource(R.string.ai_downloaded)}"
                        } else {
                            "${embeddingModel.label} · ${"%.1f GB".format(embeddingModel.sizeGb)} · ${stringResource(R.string.ai_embedding_desc)}"
                        }
                    )
                },
                trailingContent = {
                    if (!embeddingDownloaded) {
                        TextButton(
                            onClick = {
                                MainScope().launch {
                                    com.mailchat.ai.GeminiService.downloadModel(context, embeddingModel)
                                }
                            },
                            enabled = !isDownloading
                        ) {
                            Text(stringResource(R.string.ai_download_button))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()

            var showProviderDropdown by remember { mutableStateOf(false) }
            val currentProvider = remember(providerVersion) { SettingsUtils.getAiProvider(context) }
            val providerOptions = listOf(
                SettingsUtils.AI_PROVIDER_ON_DEVICE to stringResource(R.string.ai_provider_on_device),
                SettingsUtils.AI_PROVIDER_OPENAI to stringResource(R.string.ai_provider_openai)
            )
            val currentProviderLabel = providerOptions.first { it.first == currentProvider }.second
            ListItem(
                headlineContent = { Text(stringResource(R.string.ai_provider)) },
                supportingContent = { Text(currentProviderLabel) },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false }
                    ) {
                        providerOptions.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showProviderDropdown = false
                                    if (value == SettingsUtils.AI_PROVIDER_OPENAI) {
                                        showOpenAiWarning = true
                                    } else {
                                        SettingsUtils.setAiProvider(context, value)
                                        providerVersion++
                                    }
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showProviderDropdown = true }) {
                        Text(currentProviderLabel)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (currentProvider == SettingsUtils.AI_PROVIDER_OPENAI) {
                var apiKey by remember { mutableStateOf(SettingsUtils.getOpenAiApiKey(context)) }
                var endpoint by remember { mutableStateOf(SettingsUtils.getOpenAiEndpoint(context)) }
                var apiModel by remember { mutableStateOf(SettingsUtils.getOpenAiModel(context)) }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        SettingsUtils.setOpenAiApiKey(context, it)
                    },
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    singleLine = true,

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        SettingsUtils.setOpenAiEndpoint(context, it)
                    },
                    label = { Text(stringResource(R.string.ai_api_endpoint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = apiModel,
                    onValueChange = {
                        apiModel = it
                        SettingsUtils.setOpenAiModel(context, it)
                    },
                    label = { Text(stringResource(R.string.ai_api_model)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                var contextSize by remember { mutableStateOf(SettingsUtils.getOpenAiContextSize(context).toString()) }
                OutlinedTextField(
                    value = contextSize,
                    onValueChange = {
                        contextSize = it.filter { c -> c.isDigit() }
                        contextSize.toIntOrNull()?.let { SettingsUtils.setOpenAiContextSize(context, it) }
                    },
                    label = { Text(stringResource(R.string.ai_context_size)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "📱 " + stringResource(R.string.widget_title),
                expanded = widgetExpanded,
                onToggle = { widgetExpanded = !widgetExpanded },
                helpTitle = stringResource(R.string.help_widget_title),
                helpText = stringResource(R.string.help_widget_text)
            )

            if (widgetExpanded) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.widget_open_mode)) },
                    trailingContent = {
                        androidx.compose.material3.DropdownMenu(
                            expanded = showWidgetModeDropdown,
                            onDismissRequest = { showWidgetModeDropdown = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.widget_open_chat)) },
                                onClick = {
                                    showWidgetModeDropdown = false
                                    widgetOpenMode = SettingsUtils.WIDGET_MODE_CHAT
                                    SettingsUtils.setWidgetOpenMode(context, SettingsUtils.WIDGET_MODE_CHAT)
                                    // Rebuild the widget so its PendingIntents pick up the new mode
                                    // (detail mode adds a message_id extra to each item).
                                    com.mailchat.widget.MailWidgetProvider.refreshAll(context)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.widget_open_detail)) },
                                onClick = {
                                    showWidgetModeDropdown = false
                                    widgetOpenMode = SettingsUtils.WIDGET_MODE_DETAIL
                                    SettingsUtils.setWidgetOpenMode(context, SettingsUtils.WIDGET_MODE_DETAIL)
                                    // Rebuild the widget so its PendingIntents pick up the new mode
                                    // (detail mode adds a message_id extra to each item).
                                    com.mailchat.widget.MailWidgetProvider.refreshAll(context)
                                }
                            )
                        }
                        TextButton(onClick = { showWidgetModeDropdown = true }) {
                            Text(
                                if (widgetOpenMode == SettingsUtils.WIDGET_MODE_DETAIL)
                                    stringResource(R.string.widget_open_detail)
                                else
                                    stringResource(R.string.widget_open_chat)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Content mode: latest or unread emails.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.widget_content_mode)) },
                    trailingContent = {
                        androidx.compose.material3.DropdownMenu(
                            expanded = showWidgetContentDropdown,
                            onDismissRequest = { showWidgetContentDropdown = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.widget_content_latest)) },
                                onClick = {
                                    showWidgetContentDropdown = false
                                    widgetContentMode = SettingsUtils.WIDGET_CONTENT_LATEST
                                    SettingsUtils.setWidgetContentMode(context, SettingsUtils.WIDGET_CONTENT_LATEST)
                                    com.mailchat.widget.MailWidgetProvider.refreshAll(context)
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.widget_content_unread)) },
                                onClick = {
                                    showWidgetContentDropdown = false
                                    widgetContentMode = SettingsUtils.WIDGET_CONTENT_UNREAD
                                    SettingsUtils.setWidgetContentMode(context, SettingsUtils.WIDGET_CONTENT_UNREAD)
                                    com.mailchat.widget.MailWidgetProvider.refreshAll(context)
                                }
                            )
                        }
                        TextButton(onClick = { showWidgetContentDropdown = true }) {
                            Text(
                                if (widgetContentMode == SettingsUtils.WIDGET_CONTENT_UNREAD)
                                    stringResource(R.string.widget_content_unread)
                                else
                                    stringResource(R.string.widget_content_latest)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Background transparency in percent (scrollable slider).
                ListItem(
                    headlineContent = { Text(stringResource(R.string.widget_transparency)) },
                    trailingContent = { Text("${widgetTransparency} %") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.Slider(
                    value = widgetTransparency.toFloat(),
                    onValueChange = { widgetTransparency = it.toInt() },
                    onValueChangeFinished = {
                        SettingsUtils.setWidgetTransparency(context, widgetTransparency)
                        com.mailchat.widget.MailWidgetProvider.refreshAll(context)
                    },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "🔒 " + stringResource(R.string.secure_settings_title),
                expanded = secureSettingsExpanded,
                onToggle = { secureSettingsExpanded = !secureSettingsExpanded },
                helpTitle = stringResource(R.string.help_secure_title),
                helpText = stringResource(R.string.help_secure_text)
            )

            if (secureSettingsExpanded) {

            val hasPgpKey = remember(pgpKeyVersion) { com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context) }
            val generatedKeyEmail = remember(pgpKeyVersion) {
                if (hasPgpKey) com.mailchat.crypto.OpenPgpKeyService.getKeyEmailFromKey(context) else ""
            }
            val keyEmail = run {
                val senderId = SettingsUtils.getSenderAccountId(context)
                (uiState.accounts.firstOrNull { it.id == senderId } ?: uiState.accounts.firstOrNull())?.emailAddress ?: ""
            }
            var showDeleteKeyDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("🔑 " + stringResource(R.string.secure_generate_key)) },
                supportingContent = {
                    Text(
                        if (hasPgpKey) {
                            if (generatedKeyEmail.isNotBlank()) stringResource(R.string.secure_key_generated_for, generatedKeyEmail)
                            else stringResource(R.string.secure_key_generated)
                        } else if (keyEmail.isNotBlank()) stringResource(R.string.secure_generate_key_for, keyEmail)
                        else ""
                    )
                },
                trailingContent = {
                    if (hasPgpKey) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !hasPgpKey) {
                        secureKeyDialogKey++
                        showSecureKeyDialog = true
                    }
            )
            if (hasPgpKey) {
                ListItem(
                    headlineContent = { Text("🗑️ " + stringResource(R.string.secure_delete_key)) },
                    supportingContent = { Text(stringResource(R.string.secure_delete_key_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteKeyDialog = true }
                )
            }
            if (showDeleteKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteKeyDialog = false },
                    title = { Text(stringResource(R.string.secure_delete_key)) },
                    text = { Text(stringResource(R.string.secure_delete_key_warning)) },
                    confirmButton = {
                        TextButton(onClick = {
                            com.mailchat.crypto.OpenPgpKeyService.deleteIdentity(context)
                            pgpKeyVersion++
                            showDeleteKeyDialog = false
                        }) {
                            Text(stringResource(R.string.secure_delete_key_confirm), color = Color(0xFFD32F2F))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteKeyDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            ListItem(
                headlineContent = { Text("🌐 " + stringResource(R.string.secure_auto_lookup)) },
                supportingContent = { Text(stringResource(R.string.secure_auto_lookup_hint)) },
                trailingContent = {
                    Switch(
                        checked = autoLookupEnabled,
                        onCheckedChange = {
                            autoLookupEnabled = it
                            com.mailchat.crypto.OpenPgpKeyService.setAutoLookupEnabled(context, it)
                        }
                    )
                }
            )

            var writeBackEnabled by remember { mutableStateOf(com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackEnabled(context)) }
            ListItem(
                headlineContent = { Text("🔓 " + stringResource(R.string.secure_writeback_title)) },
                supportingContent = { Text(stringResource(R.string.secure_writeback_hint)) },
                trailingContent = {
                    Switch(
                        checked = writeBackEnabled,
                        onCheckedChange = {
                            writeBackEnabled = it
                            com.mailchat.crypto.OpenPgpKeyService.setAutoWriteBackEnabled(context, it)
                        }
                    )
                }
            )

            var writeBackInboxEnabled by remember { mutableStateOf(com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackInboxEnabled(context)) }
            ListItem(
                headlineContent = { Text("📥 " + stringResource(R.string.secure_writeback_inbox_title)) },
                supportingContent = { Text(stringResource(R.string.secure_writeback_inbox_hint)) },
                trailingContent = {
                    Switch(
                        checked = writeBackInboxEnabled,
                        onCheckedChange = {
                            writeBackInboxEnabled = it
                            com.mailchat.crypto.OpenPgpKeyService.setAutoWriteBackInboxEnabled(context, it)
                        }
                    )
                }
            )

            var showPublishKeyDialog by remember { mutableStateOf(false) }
            var isPublishing by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("📤 " + stringResource(R.string.secure_publish_key)) },
                supportingContent = { Text(stringResource(R.string.secure_publish_key_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasPgpKey) { showPublishKeyDialog = true }
            )
            if (showPublishKeyDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isPublishing) showPublishKeyDialog = false },
                    title = { Text(stringResource(R.string.secure_publish_key)) },
                    text = { Text(stringResource(R.string.secure_publish_key_warning)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isPublishing = true
                                MainScope().launch {
                                    val result = withContext(Dispatchers.IO) {
                                        com.mailchat.crypto.OpenPgpKeyService.publishKey(context)
                                    }
                                    isPublishing = false
                                    showPublishKeyDialog = false
                                    val message = when (result) {
                                        com.mailchat.crypto.OpenPgpKeyService.PublishResult.VERIFICATION_SENT ->
                                            context.getString(R.string.secure_publish_key_success)
                                        com.mailchat.crypto.OpenPgpKeyService.PublishResult.UPLOADED ->
                                            context.getString(R.string.secure_publish_key_uploaded)
                                        com.mailchat.crypto.OpenPgpKeyService.PublishResult.FAILED ->
                                            context.getString(R.string.secure_publish_key_failed)
                                    }
                                    android.widget.Toast.makeText(
                                        context, message, android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !isPublishing
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.secure_publish_key_confirm))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!isPublishing) showPublishKeyDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Export the private key as a .asc file (only if a key exists).
            // The user chooses the passphrase of the exported file in a dialog first,
            // because the key stored in the app is protected by an internal passphrase
            // the user never saw (auto-generated at key creation).
            if (hasPgpKey) {
                ListItem(
                    headlineContent = { Text("📤 " + stringResource(R.string.key_export_title)) },
                    supportingContent = { Text(stringResource(R.string.key_export_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            exportPassphrase = ""
                            showExportPassphraseDialog = true
                        }
                )
            }
            if (showExportPassphraseDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isExportingKey) showExportPassphraseDialog = false },
                    title = { Text(stringResource(R.string.key_export_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.key_export_passphrase_hint))
                            OutlinedTextField(
                                value = exportPassphrase,
                                onValueChange = { exportPassphrase = it },
                                label = { Text(stringResource(R.string.key_export_passphrase)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (exportPassphrase.isBlank()) {
                                Text(
                                    stringResource(R.string.key_export_unprotected_warning),
                                    color = Color(0xFFF57C00),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isExportingKey = true
                                MainScope().launch {
                                    // Re-armor the stored key with the user-chosen passphrase.
                                    // Fallback: if the internal passphrase is unavailable
                                    // (e.g. after a backup restore on another device), export
                                    // the stored key as-is (previous behavior).
                                    val ok = withContext(Dispatchers.IO) {
                                        try {
                                            val stored = com.mailchat.crypto.OpenPgpKeyService.getSecretKeyArmored(context)
                                            val internalPassphrase = com.mailchat.crypto.OpenPgpKeyService.getPassphrase(context)
                                            exportKeyContent = if (internalPassphrase.isNotEmpty()) {
                                                com.mailchat.crypto.OpenPgpKeyService.reArmorSecretKey(
                                                    stored, internalPassphrase, exportPassphrase
                                                )
                                            } else {
                                                stored
                                            }
                                            true
                                        } catch (e: Exception) {
                                            com.mailchat.ai.AppLogger.log(context, "Key export re-armor failed: ${e.message}")
                                            exportKeyContent = com.mailchat.crypto.OpenPgpKeyService.getSecretKeyArmored(context)
                                            true
                                        }
                                    }
                                    isExportingKey = false
                                    showExportPassphraseDialog = false
                                    if (ok) {
                                        exportKeyLauncher.launch("mailchat_private_key.asc")
                                    } else {
                                        android.widget.Toast.makeText(context, R.string.key_export_failed, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isExportingKey
                        ) {
                            if (isExportingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!isExportingKey) showExportPassphraseDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            // Import a private key from a .asc file (replaces the current key).
            ListItem(
                headlineContent = { Text("📥 " + stringResource(R.string.key_import_title)) },
                supportingContent = { Text(stringResource(R.string.key_import_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { importKeyLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "text/plain")) }
            )
            if (showImportPassphraseDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isImportingKey) showImportPassphraseDialog = false },
                    title = { Text(stringResource(R.string.key_import_title)) },
                    text = {
                        Column {
                            if (importKeyError.isNotBlank()) {
                                Text(importKeyError, color = Color(0xFFD32F2F))
                            } else {
                                Text(stringResource(R.string.key_import_passphrase_hint))
                                OutlinedTextField(
                                    value = importPassphrase,
                                    onValueChange = { importPassphrase = it },
                                    label = { Text(stringResource(R.string.key_import_passphrase)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (importKeyError.isNotBlank()) {
                                    showImportPassphraseDialog = false
                                    return@TextButton
                                }
                                isImportingKey = true
                                MainScope().launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        com.mailchat.crypto.OpenPgpKeyService.importSecretKey(
                                            context, importKeyContent, importPassphrase
                                        )
                                    }
                                    isImportingKey = false
                                    showImportPassphraseDialog = false
                                    pgpKeyVersion++
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) R.string.key_import_success else R.string.key_import_failed,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !isImportingKey && importKeyError.isBlank()
                        ) {
                            if (isImportingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.key_import_confirm))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!isImportingKey) showImportPassphraseDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            var showDeleteDirKeyDialog by remember { mutableStateOf(false) }
            var isDeletingDirKey by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("🗑️ " + stringResource(R.string.key_delete_title)) },
                supportingContent = { Text(stringResource(R.string.key_delete_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasPgpKey) { showDeleteDirKeyDialog = true }
            )
            if (showDeleteDirKeyDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isDeletingDirKey) showDeleteDirKeyDialog = false },
                    title = { Text(stringResource(R.string.key_delete_title)) },
                    text = { Text(stringResource(R.string.key_delete_warning)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isDeletingDirKey = true
                                MainScope().launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        com.mailchat.crypto.OpenPgpKeyService.deleteKeyFromDirectory(context)
                                    }
                                    isDeletingDirKey = false
                                    showDeleteDirKeyDialog = false
                                    val message = if (ok) context.getString(R.string.key_delete_success)
                                    else context.getString(R.string.key_delete_failed)
                                    android.widget.Toast.makeText(
                                        context, message, android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !isDeletingDirKey
                        ) {
                            if (isDeletingDirKey) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.key_delete_confirm))
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!isDeletingDirKey) showDeleteDirKeyDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            var showPeersDialog by remember { mutableStateOf(false) }
            val peersCount = com.mailchat.crypto.OpenPgpKeyService.getPeers(context).size
            ListItem(
                headlineContent = { Text("👥 " + stringResource(R.string.secure_peers_title)) },
                supportingContent = {
                    if (peersCount > 0) Text("$peersCount")
                    else Text("")
                },
                trailingContent = {
                    Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPeersDialog = true }
            )

            if (showPeersDialog) {
                var peersVersion by remember { mutableIntStateOf(0) }
                val peers = remember(peersVersion) { com.mailchat.crypto.OpenPgpKeyService.getPeers(context) }
                var showPeersHelp by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showPeersDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.secure_peers_title), modifier = Modifier.weight(1f))
                            IconButton(onClick = { showPeersHelp = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Help, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (isScanningContacts) return@TextButton
                                    isScanningContacts = true
                                    MainScope().launch {
                                        val contacts = uiState.contacts.filter { it.email.isNotBlank() }
                                        val ownEmails = uiState.accounts.map { it.emailAddress.lowercase() }
                                        var found = 0
                                        withContext(Dispatchers.IO) {
                                            for (contact in contacts) {
                                                val email = contact.email.lowercase()
                                                if (ownEmails.contains(email)) continue
                                                if (com.mailchat.crypto.OpenPgpKeyService.getPeer(context, email) != null) continue
                                                val pubKey = com.mailchat.crypto.OpenPgpKeyService.fetchKeyByEmail(email, 8000, 12000)
                                                if (!pubKey.isNullOrBlank()) {
                                                    try {
                                                        val ring = com.mailchat.crypto.OpenPgpKeyService.parsePublicKeyRing(pubKey)
                                                        val fp = ring.publicKey.getFingerprint().joinToString("") { "%02X".format(it) }
                                                        com.mailchat.crypto.OpenPgpKeyService.addPeer(
                                                            context,
                                                            com.mailchat.crypto.OpenPgpKeyService.OpenPgpPeer(email, pubKey, fp)
                                                        )
                                                        found++
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        }
                                        isScanningContacts = false
                                        peersVersion++
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.secure_scan_result, found),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = !isScanningContacts
                            ) {
                                if (isScanningContacts) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.secure_scan_in_progress))
                                } else {
                                    Text(stringResource(R.string.secure_scan_contacts))
                                }
                            }
                            if (peers.isEmpty()) {
                                Text(stringResource(R.string.secure_no_peers))
                            } else {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                peers.forEach { peer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(peer.email, style = MaterialTheme.typography.bodyMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    peer.fingerprint.take(4),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = " (i)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.clickable {
                                                        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Fingerprint", peer.fingerprint))
                                                        android.widget.Toast.makeText(context, peer.fingerprint, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            text = "✕",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .clickable {
                                                    com.mailchat.crypto.OpenPgpKeyService.removePeer(context, peer.email)
                                                    showPeersDialog = false
                                                }
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPeersDialog = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                )
                if (showPeersHelp) {
                    AlertDialog(
                        onDismissRequest = { showPeersHelp = false },
                        title = { Text(stringResource(R.string.help_secure_title)) },
                        text = { Text(stringResource(R.string.help_peers_text)) },
                        confirmButton = {
                            TextButton(onClick = { showPeersHelp = false }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    )
                }
            }

            ListItem(
                headlineContent = { Text("🔄 " + stringResource(R.string.secure_exchange_setting)) },
                supportingContent = { Text(stringResource(R.string.secure_exchange_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasPgpKey) {
                        showSecureConnectionDialog = true
                    }
            )
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "📋 " + stringResource(R.string.general),
                expanded = generalExpanded,
                onToggle = { generalExpanded = !generalExpanded },
                helpTitle = stringResource(R.string.help_contact_title),
                helpText = stringResource(R.string.help_contact_text)
            )

            if (generalExpanded) {

            ListItem(
                headlineContent = { Text("👤 " + stringResource(R.string.manage_contacts)) },
                supportingContent = { Text(stringResource(R.string.manage_contacts_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.provider.ContactsContract.Contacts.CONTENT_URI
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    })
            )

            SectionDivider()

            var showLangDropdown by remember { mutableStateOf(false) }
            val langOptions = listOf(
                LanguageUtils.SYSTEM to stringResource(R.string.language_system),
                LanguageUtils.GERMAN to stringResource(R.string.language_german),
                LanguageUtils.ENGLISH to stringResource(R.string.language_english)
            )
            val currentLangLabel = langOptions.first { it.first == selectedLanguage }.second
            ListItem(
                headlineContent = { Text("🌐 " + stringResource(R.string.language)) },
                supportingContent = { Text(currentLangLabel) },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showLangDropdown,
                        onDismissRequest = { showLangDropdown = false }
                    ) {
                        langOptions.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showLangDropdown = false
                                    selectedLanguage = value
                                    LanguageUtils.setLanguage(context, value)
                                    activity?.recreate()
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showLangDropdown = true }) {
                        Text(currentLangLabel)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()

            var showDisplayDropdown by remember { mutableStateOf(false) }
            val displayOptions = listOf(
                SettingsUtils.DARK_MODE_SYSTEM to stringResource(R.string.dark_mode_system),
                SettingsUtils.DARK_MODE_LIGHT to stringResource(R.string.dark_mode_light),
                SettingsUtils.DARK_MODE_DARK to stringResource(R.string.dark_mode_dark)
            )
            val currentDisplayLabel = displayOptions.first { it.first == selectedDarkMode }.second
            ListItem(
                headlineContent = { Text("🌙 " + stringResource(R.string.display)) },
                supportingContent = { Text(currentDisplayLabel) },
                trailingContent = {
                    androidx.compose.material3.DropdownMenu(
                        expanded = showDisplayDropdown,
                        onDismissRequest = { showDisplayDropdown = false }
                    ) {
                        displayOptions.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showDisplayDropdown = false
                                    selectedDarkMode = value
                                    SettingsUtils.setDarkMode(context, value)
                                    activity?.recreate()
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showDisplayDropdown = true }) {
                        Text(currentDisplayLabel)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "💾 " + stringResource(R.string.backup_restore),
                expanded = backupExpanded,
                onToggle = { backupExpanded = !backupExpanded },
                helpTitle = stringResource(R.string.help_backup_title),
                helpText = stringResource(R.string.help_backup_text)
            )

            if (backupExpanded) {

            var showExportDialog by remember { mutableStateOf(false) }
            var showImportDialog by remember { mutableStateOf(false) }
            var backupMessage by remember { mutableStateOf<String?>(null) }

            ListItem(
                headlineContent = { Text("📤 " + stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExportDialog = true }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("📥 " + stringResource(R.string.backup_import)) },
                supportingContent = { Text(stringResource(R.string.backup_import_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showImportDialog = true }
            )

            backupMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.startsWith("OK")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            var pendingExportPassword by remember { mutableStateOf<String?>(null) }
            var pendingIncludeAttachments by remember { mutableStateOf(false) }
            val savePicker = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                val pw = pendingExportPassword
                val includeAtt = pendingIncludeAttachments
                if (uri != null && pw != null) {
                    MainScope().launch {
                        try {
                            val file = com.mailchat.util.ConfigBackupUtils.exportConfig(context, pw, includeAtt)
                            val bytes = file.readBytes()
                            android.util.Log.i("MailchatExport", "exportConfig produced ${bytes.size} bytes, uri=$uri")
                            if (bytes.isEmpty()) {
                                backupMessage = "Fehler: Export ist leer (0 Bytes)"
                                return@launch
                            }
                            val out = context.contentResolver.openOutputStream(uri)
                            if (out == null) {
                                android.util.Log.e("MailchatExport", "openOutputStream returned null for uri=$uri")
                                backupMessage = "Fehler: Ziel-Datei konnte nicht geöffnet werden"
                                return@launch
                            }
                            out.use { it.write(bytes) }
                            file.delete()
                            android.util.Log.i("MailchatExport", "Export written: ${bytes.size} bytes to $uri")
                            backupMessage = "OK: Export erstellt (${bytes.size} Bytes)"
                        } catch (e: Exception) {
                            android.util.Log.e("MailchatExport", "Export failed", e)
                            backupMessage = "Fehler: ${e.message}"
                        }
                    }
                }
                pendingExportPassword = null
            }

            if (showExportDialog) {
                var exportPassword by remember { mutableStateOf("") }
                var includeAttachments by remember { mutableStateOf(false) }
                Dialog(onDismissRequest = { showExportDialog = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.backup_export), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = exportPassword,
                                onValueChange = { exportPassword = it },
                                label = { Text(stringResource(R.string.backup_password)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = includeAttachments,
                                    onCheckedChange = { includeAttachments = it }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.backup_include_attachments), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.backup_include_attachments_desc), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showExportDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        if (exportPassword.length < 12) {
                                            backupMessage = "Min. 12 Zeichen"
                                            return@TextButton
                                        }
                                        showExportDialog = false
                                        pendingExportPassword = exportPassword
                                        pendingIncludeAttachments = includeAttachments
                                        savePicker.launch("mailchat_config.enc")
                                    }
                                ) {
                                    Text(stringResource(R.string.ok))
                                }
                            }
                        }
                    }
                }
            }

            if (showImportDialog) {
                var importPassword by remember { mutableStateOf("") }
                var importFile by remember { mutableStateOf<File?>(null) }
                var importReadError by remember { mutableStateOf(false) }
                val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val tmpFile = File(context.cacheDir, "import_config.enc")
                                tmpFile.writeBytes(bytes)
                                importFile = tmpFile
                            } else {
                                // The selected file could not be read (or is empty). Show an error
                                // instead of silently proceeding with an empty file.
                                importFile = null
                                importReadError = true
                            }
                        } catch (e: Exception) {
                            importFile = null
                            importReadError = true
                            android.util.Log.e("MailchatImport", "Failed to read selected file: ${uri}", e)
                        }
                    }
                }
                LaunchedEffect(Unit) { filePicker.launch(arrayOf("*/*")) }
                Dialog(onDismissRequest = { showImportDialog = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.backup_import), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            when {
                                importFile != null ->
                                    Text(stringResource(R.string.backup_file_selected), style = MaterialTheme.typography.bodyMedium)
                                importReadError ->
                                    Text(stringResource(R.string.backup_file_read_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                else ->
                                    Text(stringResource(R.string.backup_no_file), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importPassword,
                                onValueChange = { importPassword = it },
                                label = { Text(stringResource(R.string.backup_password)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        val file = importFile ?: return@TextButton
                                        if (importPassword.isBlank()) return@TextButton
                                        showImportDialog = false
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            val result = com.mailchat.util.ConfigBackupUtils.importConfig(context, file, importPassword)
                                            backupMessage = if (result.isSuccess) "OK: Import erfolgreich" else "Fehler: ${result.exceptionOrNull()?.message}"
                                            file.delete()
                                            if (result.isSuccess) {
                                                if (result.getOrThrow().needsRestart) {
                                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                    context.startActivity(intent)
                                                    android.os.Process.killProcess(android.os.Process.myPid())
                                                } else {
                                                    viewModel.syncAll()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.ok))
                                }
                            }
                        }
                    }
                }
            }
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "🏷️ " + stringResource(R.string.ai_custom_tags),
                expanded = customTagsExpanded,
                onToggle = { customTagsExpanded = !customTagsExpanded },
                helpTitle = stringResource(R.string.help_custom_tags_title),
                helpText = stringResource(R.string.help_custom_tags_text)
            )

            if (customTagsExpanded) {
            tags.forEachIndexed { index, tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = tag.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        tagDeleteIndex = index
                        showTagDeleteDialog = true
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = {
                        tagEditIndex = index
                        tagEditName = tag.name
                        tagEditDesc = tag.description
                        showTagEditDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                }
            }
            Button(
                onClick = {
                    tagEditIndex = -1
                    tagEditName = ""
                    tagEditDesc = ""
                    showTagEditDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_tag))
            }
            }

            SectionDivider()

            CollapsibleSectionHeader(
                title = "ℹ️ " + stringResource(R.string.info),
                expanded = infoExpanded,
                onToggle = { infoExpanded = !infoExpanded }
            )

            if (infoExpanded) {

            ListItem(
                headlineContent = { Text("📝 " + stringResource(R.string.app_log)) },
                supportingContent = { Text(stringResource(R.string.app_log_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAiLog = true }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("☕ " + stringResource(R.string.buy_me_coffee)) },
                supportingContent = { Text("PayPal · florescu@web.de") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=florescu@web.de")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    })
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("✉️ " + stringResource(R.string.contact_developer)) },
                supportingContent = { Text("florescu@web.de") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.openDeveloperChat { chatId ->
                            onOpenChat(chatId)
                        }
                    }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("❓ " + stringResource(R.string.help)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHelp)
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("🔒 " + stringResource(R.string.legal_privacy)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLegal("privacy") }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("⚠️ " + stringResource(R.string.legal_disclaimer)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLegal("disclaimer") }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("📋 " + stringResource(R.string.legal_imprint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLegal("imprint") }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("⚖️ " + stringResource(R.string.legal_licenses)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLegal("licenses") }
            )
            SectionDivider()
            ListItem(
                headlineContent = { Text("📱 " + stringResource(R.string.app_name)) },
                supportingContent = {
                    Text("${BuildConfig.VERSION_NAME} · ${stringResource(R.string.app_description)}")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onVersionHistory)
            )
            }
        }
    }

    accountToDelete?.let { account ->
        Dialog(
            onDismissRequest = { accountToDelete = null }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_account_confirm_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.delete_account_confirm_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { accountToDelete = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            viewModel.deleteAccount(account)
                            accountToDelete = null
                        }) {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    accountToRenew?.let { account ->
        Dialog(
            onDismissRequest = { accountToRenew = null }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.renew_emails_confirm_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.renew_emails_confirm_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { accountToRenew = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            viewModel.renewAccountEmails(account)
                            accountToRenew = null
                        }) {
                            Text(stringResource(R.string.renew_emails))
                        }
                    }
                }
            }
        }
    }

    accountForColor?.let { account ->
        AccountColorDialog(
            account = account,
            onDismiss = { accountForColor = null },
            onColorSelected = { color ->
                viewModel.updateAccountColor(account, color)
                accountForColor = null
            }
        )
    }

    if (showTagEditDialog) {
        AlertDialog(
            onDismissRequest = { showTagEditDialog = false },
            title = { Text(if (tagEditIndex >= 0) stringResource(R.string.edit_tag) else stringResource(R.string.add_tag)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tagEditName,
                        onValueChange = { tagEditName = it },
                        label = { Text(stringResource(R.string.tag_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagEditDesc,
                        onValueChange = { tagEditDesc = it },
                        label = { Text(stringResource(R.string.tag_description)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tagEditName.isNotBlank()) {
                            val newTags = tags.toMutableList()
                            if (tagEditIndex >= 0) {
                                newTags[tagEditIndex] = SettingsUtils.CustomTag(tagEditName.trim(), tagEditDesc.trim())
                            } else {
                                newTags.add(SettingsUtils.CustomTag(tagEditName.trim(), tagEditDesc.trim()))
                            }
                            tags = newTags
                            SettingsUtils.saveCustomTags(context, tags)
                        }
                        showTagEditDialog = false
                    },
                    enabled = tagEditName.isNotBlank()
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTagEditDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTagDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showTagDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_tag)) },
            text = { Text(stringResource(R.string.delete_tag_confirm, tags.getOrNull(tagDeleteIndex)?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    val newTags = tags.toMutableList()
                    if (tagDeleteIndex in newTags.indices) {
                        newTags.removeAt(tagDeleteIndex)
                        tags = newTags
                        SettingsUtils.saveCustomTags(context, tags)
                    }
                    showTagDeleteDialog = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showTagDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showAiWarning) {
        Dialog(onDismissRequest = { showAiWarning = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.ai_warning_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ai_warning_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAiWarning = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            showAiWarning = false
                            aiEnabled = true
                            SettingsUtils.setAiEnabled(context, true)
                        }) {
                            Text(stringResource(R.string.ai_enable_confirm))
                        }
                    }
                }
            }
        }
    }

    if (showOpenAiWarning) {
        Dialog(onDismissRequest = { showOpenAiWarning = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.ai_openai_warning_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ai_openai_warning_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showOpenAiWarning = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            showOpenAiWarning = false
                            SettingsUtils.setAiProvider(context, SettingsUtils.AI_PROVIDER_OPENAI)
                            providerVersion++
                        }) {
                            Text(stringResource(R.string.ai_openai_warning_confirm))
                        }
                    }
                }
            }
        }
    }

    if (showAiLog) {
        var logContent by remember { mutableStateOf(com.mailchat.ai.AppLogger.getLog(context)) }
        var copied by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        LaunchedEffect(logContent) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        Dialog(onDismissRequest = { showAiLog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.app_log),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = logContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Log", logContent))
                            copied = true
                        }) {
                            Text(if (copied) "Copied!" else "Copy")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            com.mailchat.ai.AppLogger.clear(context)
                            logContent = "(empty)"
                        }) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showAiLog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    if (showSecureKeyDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf("") }
        val senderAccountId = SettingsUtils.getSenderAccountId(context)
        val defaultAccount = uiState.accounts.firstOrNull { it.id == senderAccountId } ?: uiState.accounts.firstOrNull()
        var selectedAccountEmail by remember(secureKeyDialogKey) { mutableStateOf(defaultAccount?.emailAddress ?: "") }
        var showAccountDropdown by remember { mutableStateOf(false) }
        var isGenerating by remember { mutableStateOf(false) }
        var genError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isGenerating) showSecureKeyDialog = false },
            title = { Text(stringResource(R.string.secure_generate_key)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.accounts.size > 1) {
                        OutlinedTextField(
                            value = selectedAccountEmail,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.secure_select_account)) },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = { showAccountDropdown = true }) {
                                    Text("▼")
                                }
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.secure_key_gen_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (genError.isNotEmpty()) {
                        Text(
                            genError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val accountEmail = selectedAccountEmail.ifBlank { uiState.accounts.firstOrNull()?.emailAddress ?: "mailchat@local" }
                        val passphrase = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
                        isGenerating = true
                        genError = ""
                        MainScope().launch {
                            var failed = false
                            withContext(Dispatchers.IO) {
                                try {
                                    com.mailchat.crypto.OpenPgpKeyService.generateIdentity(context, accountEmail, passphrase)
                                } catch (e: Exception) {
                                    failed = true
                                    genError = e.message ?: "Key generation failed"
                                }
                            }
                            isGenerating = false
                            if (failed) {
                                // keep dialog open so the user sees the error
                            } else {
                                pgpKeyVersion++
                                showSecureKeyDialog = false
                            }
                        }
                    },
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(stringResource(R.string.secure_generate_key))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isGenerating) showSecureKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        if (showAccountDropdown) {
            AlertDialog(
                onDismissRequest = { showAccountDropdown = false },
                title = { Text(stringResource(R.string.secure_select_account)) },
                text = {
                    Column {
                        uiState.accounts.forEach { account ->
                            Text(
                                text = account.emailAddress,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAccountEmail = account.emailAddress
                                        showAccountDropdown = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }

    if (showSecureConnectionDialog) {
        val contactItems = uiState.contacts
            .filter { it.email.isNotBlank() }
            .map { contact ->
                com.mailchat.ui.component.ContactItem(
                    name = contact.name,
                    email = contact.email,
                    photoUri = contact.photoUri
                )
            }
        com.mailchat.ui.component.SecureConnectionDialog(
            ownEmails = uiState.accounts.map { it.emailAddress.lowercase() },
            contacts = contactItems,
            onDismiss = { showSecureConnectionDialog = false },
            onConnected = { },
            onSendKeyByEmail = { recipient, keyData ->
                showSecureConnectionDialog = false
                onComposeEmail(recipient, "MailChat PGP Key", keyData)
            }
        )
    }
}

private val ACCOUNT_COLORS = listOf(
    0xFF128C7E.toInt(),
    0xFFE53935.toInt(),
    0xFF1E88E5.toInt(),
    0xFFFB8C00.toInt(),
    0xFF8E24AA.toInt(),
    0xFF43A047.toInt(),
    0xFF000000.toInt(),
    0xFFFF00FF.toInt(),
    0xFF795548.toInt(),
    0xFF1A237E.toInt(),
    0xFFFFEB3B.toInt(),
    0xFFE91E63.toInt()
)



@Composable
private fun AccountColorDialog(
    account: EmailAccount,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.account_color),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ACCOUNT_COLORS.chunked(6).forEach { rowColors ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .clickable { onColorSelected(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (account.avatarColor == color) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(20.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
