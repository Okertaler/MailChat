package com.mailchat.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailchat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── About Mailchat ─────────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_about))
            HelpSection(
                title = stringResource(R.string.app_intro_title),
                text = stringResource(R.string.app_intro_text)
            )
            HelpSection(
                title = stringResource(R.string.help_quickstart_title),
                text = stringResource(R.string.help_quickstart_text)
            )

            // ── Email & Accounts ───────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_email))
            HelpSection(
                title = stringResource(R.string.help_add_account_title),
                text = stringResource(R.string.help_add_account_text)
            )
            HelpSection(
                title = stringResource(R.string.help_chats_title),
                text = stringResource(R.string.help_chats_text)
            )
            HelpSection(
                title = stringResource(R.string.help_full_email_title),
                text = stringResource(R.string.help_full_email_text)
            )
            HelpSection(
                title = stringResource(R.string.help_smtp_title),
                text = stringResource(R.string.help_smtp_text)
            )
            HelpSection(
                title = stringResource(R.string.help_smtp_title2),
                text = stringResource(R.string.help_smtp_text2)
            )
            HelpSection(
                title = stringResource(R.string.help_sync_title),
                text = stringResource(R.string.help_sync_text)
            )
            HelpSection(
                title = stringResource(R.string.help_contact_title),
                text = stringResource(R.string.help_contact_text)
            )
            HelpSection(
                title = stringResource(R.string.help_contact_header_title),
                text = stringResource(R.string.help_contact_header_text)
            )
            HelpSection(
                title = stringResource(R.string.help_group_title),
                text = stringResource(R.string.help_group_text)
            )
            HelpSection(
                title = stringResource(R.string.help_new_chat_title),
                text = stringResource(R.string.help_new_chat_text)
            )
            HelpSection(
                title = stringResource(R.string.help_hidden_title),
                text = stringResource(R.string.help_hidden_text)
            )
            HelpSection(
                title = stringResource(R.string.help_delete_title),
                text = stringResource(R.string.help_delete_text)
            )
            HelpSection(
                title = stringResource(R.string.help_message_actions_title),
                text = stringResource(R.string.help_message_actions_text)
            )
            HelpSection(
                title = stringResource(R.string.help_subject_title),
                text = stringResource(R.string.help_subject_text)
            )
            HelpSection(
                title = stringResource(R.string.help_embedded_images_title),
                text = stringResource(R.string.help_embedded_images_text)
            )

            // ── Encryption ─────────────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_encryption))
            HelpSection(
                title = stringResource(R.string.help_secure_title),
                text = stringResource(R.string.help_secure_text)
            )
            HelpSection(
                title = stringResource(R.string.help_digital_signature_title),
                text = stringResource(R.string.help_digital_signature_text)
            )

            // ── AI ─────────────────────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_ai))
            HelpSection(
                title = stringResource(R.string.help_ai_settings_title),
                text = stringResource(R.string.help_ai_settings_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_summary_title),
                text = stringResource(R.string.help_ai_summary_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_provider_title),
                text = stringResource(R.string.help_ai_provider_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_reply_title),
                text = stringResource(R.string.help_ai_reply_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_spam_title),
                text = stringResource(R.string.help_ai_spam_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_phishing_title),
                text = stringResource(R.string.help_ai_phishing_text)
            )
            HelpSection(
                title = stringResource(R.string.help_phishing_blacklist_title),
                text = stringResource(R.string.help_phishing_blacklist_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_tag_title),
                text = stringResource(R.string.help_ai_tag_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_image_block_title),
                text = stringResource(R.string.help_ai_image_block_text)
            )
            HelpSection(
                title = stringResource(R.string.help_ai_summarize_menu_title),
                text = stringResource(R.string.help_ai_summarize_menu_text)
            )

            // ── Handling ───────────────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_handling))
            HelpSection(
                title = stringResource(R.string.help_search_title),
                text = stringResource(R.string.help_search_text)
            )
            HelpSection(
                title = stringResource(R.string.help_swipe_title),
                text = stringResource(R.string.help_swipe_text)
            )
            HelpSection(
                title = stringResource(R.string.help_selection_title),
                text = stringResource(R.string.help_selection_text)
            )
            HelpSection(
                title = stringResource(R.string.help_filter_menu_title),
                text = stringResource(R.string.help_filter_menu_text)
            )
            HelpSection(
                title = stringResource(R.string.help_unread_filter_title),
                text = stringResource(R.string.help_unread_filter_text)
            )
            HelpSection(
                title = stringResource(R.string.help_star_filter_title),
                text = stringResource(R.string.help_star_filter_text)
            )
            HelpSection(
                title = stringResource(R.string.help_pinch_zoom_title),
                text = stringResource(R.string.help_pinch_zoom_text)
            )
            HelpSection(
                title = stringResource(R.string.help_permissions_title),
                text = stringResource(R.string.help_permissions_text)
            )

            // ── Other topics ───────────────────────────────────────────────
            HelpCategory(stringResource(R.string.help_category_other))
            HelpSection(
                title = stringResource(R.string.help_language_title),
                text = stringResource(R.string.help_language_text)
            )
            HelpSection(
                title = stringResource(R.string.help_dark_mode_title),
                text = stringResource(R.string.help_dark_mode_text)
            )
            HelpSection(
                title = stringResource(R.string.help_two_pane_title),
                text = stringResource(R.string.help_two_pane_text)
            )
            HelpSection(
                title = stringResource(R.string.help_widget_title),
                text = stringResource(R.string.help_widget_text)
            )
        }
    }
}

@Composable
private fun HelpCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
    androidx.compose.material3.HorizontalDivider()
}

@Composable
private fun HelpSection(title: String, text: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
