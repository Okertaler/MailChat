package com.mailchat.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailchat.R
import com.mailchat.ui.viewmodel.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenChatsScreen(
    chatType: String = "hidden",
    onBack: () -> Unit,
    onChatClick: (Long) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImportant = chatType == "important"
    val title = if (isImportant) stringResource(R.string.important_chats) else stringResource(R.string.hidden_chats)
    val emptyText = if (isImportant) stringResource(R.string.no_important_chats) else stringResource(R.string.no_hidden_chats)
    val chats = if (isImportant) uiState.importantChats else uiState.hiddenChats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(
                    items = chats,
                    key = { "${if (isImportant) "imp" else "hid"}_${it.id}" }
                ) { chat ->
                    ListItem(
                        headlineContent = { Text(chat.participantEmail) },
                        supportingContent = { Text(chat.lastMessageSubject.ifBlank { chat.lastMessageBody.take(60) }) },
                        trailingContent = {
                            IconButton(onClick = {
                                if (isImportant) viewModel.unimportantChat(chat)
                                else viewModel.showHiddenChat(chat)
                            }) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = stringResource(R.string.show)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat.id) }
                    )
                }
            }
        }
    }
}
