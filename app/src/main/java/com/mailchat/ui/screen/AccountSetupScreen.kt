package com.mailchat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailchat.R
import com.mailchat.ui.viewmodel.AccountSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AccountSetupViewModel = hiltViewModel(),
    chatListViewModel: com.mailchat.ui.viewmodel.ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (uiState.isEditing) {
                                stringResource(R.string.edit_account)
                            } else {
                                stringResource(R.string.add_imap_account)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        com.mailchat.ui.component.HelpIcon(
                            title = stringResource(R.string.help_account_edit_title),
                            text = stringResource(R.string.help_account_edit_text)
                        )
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            var showProviderMenu by remember { mutableStateOf(false) }
            var selectedProvider by remember { mutableStateOf("") }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable { showProviderMenu = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedProvider.isBlank()) {
                            stringResource(R.string.select_provider)
                        } else {
                            selectedProvider
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedProvider.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showProviderMenu,
                    onDismissRequest = { showProviderMenu = false }
                ) {
                    com.mailchat.ui.viewmodel.AccountSetupViewModel.PROVIDERS.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                showProviderMenu = false
                                selectedProvider = provider.name
                                viewModel.onProviderSelected(provider.name)
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringResource(R.string.display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.emailAddress,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.imapHost,
                onValueChange = viewModel::onHostChange,
                label = { Text(stringResource(R.string.imap_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.imapPort,
                onValueChange = viewModel::onPortChange,
                label = { Text(stringResource(R.string.port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.smtpHost,
                onValueChange = viewModel::onSmtpHostChange,
                label = { Text(stringResource(R.string.smtp_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.smtpPort,
                onValueChange = viewModel::onSmtpPortChange,
                label = { Text(stringResource(R.string.smtp_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.password_app_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { viewModel.loadFolders() },
                enabled = !uiState.isLoadingFolders,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (uiState.selectedFolders.isEmpty()) {
                        stringResource(R.string.select_sync_folders)
                    } else {
                        stringResource(R.string.selected_folders_count, uiState.selectedFolders.size)
                    }
                )
            }

            if (uiState.isTesting) {
                CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.testResultRes?.let { res ->
                val detail = uiState.testResultDetail
                val text = if (detail != null) stringResource(res, detail) else stringResource(res)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (res == R.string.connection_success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            OutlinedButton(
                onClick = { viewModel.testConnection() },
                enabled = !uiState.isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.test_connection))
            }

            val editingAccountId = viewModel.uiState.value.editingAccountId
            val chatListState by chatListViewModel.uiState.collectAsStateWithLifecycle()
            if (editingAccountId > 0) {
                OutlinedButton(
                    onClick = { chatListViewModel.renewAccountEmailsForId(editingAccountId) },
                    enabled = !chatListState.isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (chatListState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.renew_emails_in_progress))
                    } else {
                        Text(stringResource(R.string.renew_emails))
                    }
                }
            }

            Button(
                onClick = { viewModel.saveAccount(onDone) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.save_account))
                }
            }

            uiState.saveErrorRes?.let { res ->
                val detail = uiState.saveErrorDetail
                val text = if (detail != null) stringResource(res, detail) else stringResource(res)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (uiState.showFolderPicker) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFolderPicker() },
            title = { Text(stringResource(R.string.select_sync_folders)) },
            text = {
                if (uiState.isLoadingFolders) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.availableFolders.isEmpty()) {
                    Text(stringResource(R.string.no_folders_found))
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .height(400.dp)
                    ) {
                        uiState.availableFolders.forEach { folder ->
                            val isSelected = folder in uiState.selectedFolders
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleFolder(folder) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(20.dp)
                                        .padding(end = 12.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier.background(MaterialTheme.colorScheme.primary)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFolderPicker() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}