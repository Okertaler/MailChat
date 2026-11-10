package com.mailchat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailchat.R
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.repository.AccountRepository
import com.mailchat.network.ImapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MailProvider(
    val name: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int
)

data class AccountSetupUiState(
    val displayName: String = "",
    val emailAddress: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val smtpHost: String = "",
    val smtpPort: String = "587",
    val username: String = "",
    val password: String = "",
    val isTesting: Boolean = false,
    val testResultRes: Int? = null,
    val testResultDetail: String? = null,
    val isSaving: Boolean = false,
    val saveErrorRes: Int? = null,
    val saveErrorDetail: String? = null,
    val isEditing: Boolean = false,
    val editingAccountId: Long = -1L,
    val availableFolders: List<String> = emptyList(),
    val selectedFolders: Set<String> = emptySet(),
    val isLoadingFolders: Boolean = false,
    val showFolderPicker: Boolean = false
)

@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val imapService: ImapService,
    private val smtpService: com.mailchat.network.SmtpService
) : ViewModel() {

    private val editingAccountId: Long = savedStateHandle.get<Long>("accountId") ?: -1L

    private val _uiState = MutableStateFlow(AccountSetupUiState(editingAccountId = editingAccountId))
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()

    init {
        if (editingAccountId != -1L) {
            viewModelScope.launch {
                accountRepository.getAccountById(editingAccountId)?.let { account ->
                    val selected = account.syncFolders
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toSet()
                    _uiState.update {
                        it.copy(
                            isEditing = true,
                            displayName = account.displayName,
                            emailAddress = account.emailAddress,
                            imapHost = account.imapHost,
                            imapPort = account.imapPort.toString(),
                            smtpHost = account.smtpHost,
                            smtpPort = account.smtpPort.toString(),
                            username = account.username,
                            password = account.passwordEncrypted,
                            selectedFolders = selected
                        )
                    }
                }
            }
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFolders = true, showFolderPicker = true) }
            val state = _uiState.value
            val account = EmailAccount(
                displayName = state.displayName,
                emailAddress = state.emailAddress,
                imapHost = state.imapHost,
                imapPort = state.imapPort.toIntOrNull() ?: 993,
                imapSsl = true,
                username = state.username,
                passwordEncrypted = state.password
            )
            try {
                val folders = imapService.listFolders(account)
                _uiState.update { state ->
                    // For new accounts (no folders selected yet) pre-select the sensible defaults:
                    // INBOX (Posteingang) plus the first outgoing folder (e.g. Sent/Postausgang).
                    val defaults = if (state.selectedFolders.isEmpty()) {
                        buildSet {
                            folders.firstOrNull { it.equals("INBOX", ignoreCase = true) }?.let { add(it) }
                            folders.firstOrNull { imapService.isOutgoingFolder(it) }?.let { add(it) }
                        }
                    } else state.selectedFolders
                    state.copy(availableFolders = folders, isLoadingFolders = false, selectedFolders = defaults)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFolders = false, testResultRes = R.string.error_with_detail, testResultDetail = e.message) }
            }
        }
    }

    fun toggleFolder(folder: String) {
        _uiState.update { state ->
            val selected = state.selectedFolders.toMutableSet()
            if (selected.contains(folder)) selected.remove(folder) else selected.add(folder)
            state.copy(selectedFolders = selected)
        }
    }

    fun dismissFolderPicker() = _uiState.update { it.copy(showFolderPicker = false) }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(emailAddress = value) }
    fun onHostChange(value: String) = _uiState.update { it.copy(imapHost = value) }
    fun onPortChange(value: String) = _uiState.update { it.copy(imapPort = value) }
    fun onSmtpHostChange(value: String) = _uiState.update { it.copy(smtpHost = value) }
    fun onSmtpPortChange(value: String) = _uiState.update { it.copy(smtpPort = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun onProviderSelected(providerName: String) {
        val provider = PROVIDERS.find { it.name == providerName } ?: return
        _uiState.update {
            it.copy(
                imapHost = provider.imapHost,
                imapPort = provider.imapPort.toString(),
                smtpHost = provider.smtpHost,
                smtpPort = provider.smtpPort.toString()
            )
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResultRes = null, testResultDetail = null) }
            val state = _uiState.value
            val account = EmailAccount(
                displayName = state.displayName,
                emailAddress = state.emailAddress,
                imapHost = state.imapHost,
                imapPort = state.imapPort.toIntOrNull() ?: 993,
                imapSsl = true,
                username = state.username,
                passwordEncrypted = state.password,
                smtpHost = state.smtpHost,
                smtpPort = state.smtpPort.toIntOrNull() ?: 587
            )
            try {
                val result = imapService.fetchNewMessages(account, 0, maxMessages = 1)
                if (result.messages.isEmpty() && result.errors.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResultRes = R.string.error_with_detail,
                            testResultDetail = result.errors.first()
                        )
                    }
                    return@launch
                }
                // Test SMTP if host is filled
                if (state.smtpHost.isNotBlank()) {
                    try {
                        smtpService.sendEmail(
                            account = account,
                            to = state.emailAddress,
                            subject = "Mailchat SMTP Test",
                            body = "SMTP connection test - please ignore."
                        )
                        _uiState.update { it.copy(isTesting = false, testResultRes = R.string.connection_success, testResultDetail = null) }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isTesting = false,
                                testResultRes = R.string.error_with_detail,
                                testResultDetail = "SMTP: ${e.message}"
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isTesting = false, testResultRes = R.string.connection_success, testResultDetail = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTesting = false, testResultRes = R.string.error_with_detail, testResultDetail = e.message) }
            }
        }
    }

    fun saveAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveErrorRes = null, saveErrorDetail = null) }
            val state = _uiState.value
            if (state.emailAddress.isBlank() || state.imapHost.isBlank() || state.password.isBlank()) {
                _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.fill_required_fields, saveErrorDetail = null) }
                return@launch
            }
            try {
                val existing = if (state.isEditing) {
                    accountRepository.getAccountById(state.editingAccountId)
                } else null
                val account = EmailAccount(
                    id = existing?.id ?: 0,
                    displayName = state.displayName.ifBlank { state.emailAddress },
                    emailAddress = state.emailAddress,
                    imapHost = state.imapHost,
                    imapPort = state.imapPort.toIntOrNull() ?: 993,
                    imapSsl = true,
                    username = state.username.ifBlank { state.emailAddress },
                    passwordEncrypted = state.password,
                    smtpHost = state.smtpHost,
                    smtpPort = state.smtpPort.toIntOrNull() ?: 587,
                    avatarColor = existing?.avatarColor ?: generateColor(state.emailAddress),
                    lastSyncTimestamp = existing?.lastSyncTimestamp ?: 0,
                    syncFolders = state.selectedFolders.joinToString(",")
                )
                accountRepository.insertAccount(account)
                _uiState.update { it.copy(isSaving = false) }
                onDone()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveErrorRes = R.string.error_with_detail, saveErrorDetail = e.message) }
            }
        }
    }

    private fun generateColor(email: String): Int {
        val hash = email.hashCode()
        val hue = (hash % 360 + 360) % 360
        return android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.5f, 0.8f))
    }

    companion object {
        val PROVIDERS = listOf(
            MailProvider("Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 587),
            MailProvider("Outlook / Hotmail", "outlook.office365.com", 993, "smtp-mail.outlook.com", 587),
            MailProvider("Web.de", "imap.web.de", 993, "smtp.web.de", 587),
            MailProvider("GMX", "imap.gmx.net", 993, "mail.gmx.net", 465),
            MailProvider("T-Online", "imap.t-online.de", 993, "smtp.t-online.de", 587),
            MailProvider("Freenet", "imap.freenet.de", 993, "smtp.freenet.de", 587),
            MailProvider("Yahoo", "imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 587)
        )
    }
}
