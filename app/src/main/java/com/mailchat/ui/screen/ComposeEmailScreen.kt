package com.mailchat.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mailchat.R
import com.mailchat.ui.viewmodel.ChatListViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(
    recipient: String,
    subject: String,
    body: String,
    onBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var to by remember { mutableStateOf(recipient) }
    var subj by remember { mutableStateOf(subject) }
    var msgBody by remember { mutableStateOf(body) }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var showCcBcc by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf(0L) }
    val selectedAccount = uiState.accounts.firstOrNull { it.id == selectedAccountId }
        ?: uiState.accounts.firstOrNull { it.smtpHost.isNotBlank() }
        ?: uiState.accounts.firstOrNull()
    val shareUri = com.mailchat.ui.navigation.ComposeDataStore.pendingShareUri
    val shareMimeType = com.mailchat.ui.navigation.ComposeDataStore.pendingShareMimeType

    // Contact picker + attachment picker (unified with the reply/forward compose).
    var contactPickerOpen by remember { mutableStateOf(false) }
    var pickedFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            try {
                val name = picked.lastPathSegment ?: "attachment"
                val file = java.io.File(context.cacheDir, name)
                context.contentResolver.openInputStream(picked)?.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                if (file.exists()) {
                    pickedFiles = pickedFiles + file
                }
            } catch (_: Exception) {
            }
        }
    }

    // Success detection: sendNewEmail() sets isSendingNewEmail=false and leaves
    // newEmailError null on success -> go back to the chat list.
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isSendingNewEmail) {
        if (uiState.isSendingNewEmail) {
            wasSending = true
        } else if (wasSending) {
            wasSending = false
            if (uiState.newEmailError == null) {
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compose_new_email)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // From (account) selection, same as the new-email dialog in the chat list.
            if (uiState.accounts.size > 1) {
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
                        text = stringResource(R.string.from_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp)
                    )
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
            } else if (selectedAccount != null) {
                Text(
                    text = "${stringResource(R.string.from_label)}: ${selectedAccount.emailAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            var toInput by remember { mutableStateOf("") }
            val recipientList = remember(to) {
                to.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            }
            if (recipientList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recipientList.forEach { email ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    to = recipientList.filter { it != email }.joinToString(", ")
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = email,
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
            }
            OutlinedTextField(
                value = toInput,
                onValueChange = { toInput = it },
                label = { Text(stringResource(R.string.compose_add_recipient)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                trailingIcon = {
                    Row {
                        if (toInput.isNotBlank()) {
                            IconButton(onClick = {
                                val addr = toInput.trim().lowercase()
                                if (addr.contains("@") && addr !in recipientList) {
                                    to = if (to.isBlank()) addr else "$to, $addr"
                                }
                                toInput = ""
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                        // Contact picker: select recipients from the contact list
                        // (add several to create a group chat).
                        IconButton(onClick = { contactPickerOpen = true }) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = stringResource(R.string.select_contact),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (contactPickerOpen) {
                Dialog(
                    onDismissRequest = { contactPickerOpen = false },
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
                                                if (contact.email.isNotBlank() && contact.email !in recipientList) {
                                                    to = if (to.isBlank()) contact.email else "$to, ${contact.email}"
                                                }
                                                contactPickerOpen = false
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
            // CC/BCC (optional, collapsed by default).
            TextButton(onClick = { showCcBcc = !showCcBcc }) {
                Text(
                    text = stringResource(R.string.compose_cc_bcc),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (showCcBcc) {
                OutlinedTextField(
                    value = cc,
                    onValueChange = { cc = it },
                    label = { Text(stringResource(R.string.compose_cc)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bcc,
                    onValueChange = { bcc = it },
                    label = { Text(stringResource(R.string.compose_bcc)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = subj,
                onValueChange = { subj = it },
                label = { Text(stringResource(R.string.compose_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = msgBody,
                onValueChange = { msgBody = it },
                label = { Text(stringResource(R.string.compose_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
            if (shareUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shareUri.lastPathSegment ?: "Attachment",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // Attachment picker (unified with reply/forward): pick files to attach.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
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
            if (pickedFiles.isNotEmpty()) {
                pickedFiles.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { pickedFiles = pickedFiles.filter { it != file } }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            uiState.newEmailError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (to.isBlank()) return@Button
                    // Copy the shared file (if any) to a temp file and add the picked files.
                    val attachmentFiles = mutableListOf<java.io.File>()
                    if (shareUri != null) {
                        try {
                            val fileName = shareUri.lastPathSegment ?: "attachment"
                            val inputStream = context.contentResolver.openInputStream(shareUri)
                            if (inputStream != null) {
                                val tempFile = java.io.File(context.cacheDir, fileName)
                                tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                                inputStream.close()
                                attachmentFiles.add(tempFile)
                            }
                        } catch (_: Exception) {
                        }
                    }
                    attachmentFiles.addAll(pickedFiles)
                    // Unified send path: encryption (OpenPGP/legacy), signature, chat
                    // creation and attachment storage are handled by the ViewModel,
                    // exactly like the reply/forward compose.
                    viewModel.sendNewEmail(
                        to = to,
                        subject = subj,
                        body = msgBody,
                        accountId = selectedAccount?.id ?: 0L,
                        attachments = attachmentFiles,
                        cc = cc,
                        bcc = bcc
                    )
                },
                enabled = !uiState.isSendingNewEmail && to.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSendingNewEmail) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Text(" " + stringResource(R.string.compose_send))
                }
            }
        }
    }

    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text(stringResource(R.string.select_account)) },
            text = {
                Column {
                    uiState.accounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAccountId = account.id
                                    showAccountPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (account.id == (selectedAccount?.id ?: 0L)) Icons.Default.Check else Icons.Default.Email,
                                contentDescription = null,
                                tint = if (account.id == (selectedAccount?.id ?: 0L)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

    // Encryption failed: same dialog as the chat list (send unencrypted or abort).
    uiState.encryptionErrorDialog?.let {
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
