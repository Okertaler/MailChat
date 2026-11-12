package com.mailchat.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
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
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mailchat.R
import com.mailchat.ui.screen.AccountSetupScreen
import com.mailchat.ui.screen.ChatAssistantScreen
import com.mailchat.ui.screen.ChatDetailScreen
import com.mailchat.ui.screen.ChatListScreen
import com.mailchat.ui.screen.CustomTagsScreen
import com.mailchat.ui.screen.HelpScreen
import com.mailchat.ui.screen.LegalScreen
import com.mailchat.ui.screen.HiddenChatsScreen
import com.mailchat.ui.screen.ComposeEmailScreen
import com.mailchat.ui.screen.SettingsScreen
import com.mailchat.ui.screen.VersionHistoryScreen

object Routes {
    const val CHAT_LIST = "chat_list"
    const val CHAT_DETAIL = "chat_detail/{chatId}"
    const val DETAIL_PLACEHOLDER = "detail_placeholder"
    const val ACCOUNT_SETUP = "account_setup/{accountId}"
    const val SETTINGS = "settings"
    const val HIDDEN_CHATS = "hidden_chats/{type}"
    const val VERSION_HISTORY = "version_history"
    const val HELP = "help"
    const val ASSISTANT = "assistant"
    const val COMPOSE = "compose"
    const val CUSTOM_TAGS = "custom_tags"
    const val LEGAL = "legal/{doc}"

    fun chatDetail(chatId: Long) = "chat_detail/$chatId"
    fun accountSetup(accountId: Long = -1L) = "account_setup/$accountId"
    fun hiddenChats(type: String = "hidden") = "hidden_chats/$type"
    fun legal(doc: String) = "legal/$doc"
}

data class PendingCompose(
    val recipient: String,
    val subject: String,
    val body: String
)

object ComposeDataStore {
    var pending: PendingCompose? = null
    var pendingShareUri: android.net.Uri? = null
    var pendingShareMimeType: String? = null
}

/**
 * Holds the id of a message that should be opened in full view once its chat is shown.
 * Set by the home-screen widget (when configured to open the email detail) and consumed by
 * [com.mailchat.ui.screen.ChatDetailScreen].
 */
object PendingMessageStore {
    // Backed by a Compose MutableState so screens can observe changes with snapshotFlow
    // (works both on cold start and when the chat is already open).
    private val _messageId = mutableStateOf<Long?>(null)
    var messageId: Long?
        get() = _messageId.value
        set(value) { _messageId.value = value }
}

/**
 * Set when the user taps the three-dot menu in the home-screen widget: the app should open the
 * settings screen with the widget section expanded. Consumed by [com.mailchat.ui.screen.SettingsScreen].
 */
object PendingSettingsStore {
    private val _openWidgetSection = mutableStateOf(false)
    var openWidgetSection: Boolean
        get() = _openWidgetSection.value
        set(value) { _openWidgetSection.value = value }
}

data class ShareData(
    val uri: android.net.Uri?,
    val text: String?,
    val mimeType: String?
)

@Composable
fun MailchatNavHost(
    navController: NavHostController = rememberNavController(),
    pendingChatId: MutableState<Long?> = mutableStateOf(null),
    pendingCompose: MutableState<PendingCompose?> = mutableStateOf(null),
    pendingShare: MutableState<ShareData?> = mutableStateOf(null),
    pendingSettings: MutableState<Boolean> = mutableStateOf(false)
) {
    LaunchedEffect(pendingChatId.value) {
        pendingChatId.value?.let { chatId ->
            android.util.Log.i("MailchatNav", "Navigating to chat detail: $chatId")
            navController.navigate(Routes.chatDetail(chatId))
            pendingChatId.value = null
        }
    }
    LaunchedEffect(pendingSettings.value) {
        if (pendingSettings.value) {
            android.util.Log.i("MailchatNav", "Navigating to settings (widget section)")
            navController.navigate(Routes.SETTINGS)
            pendingSettings.value = false
        }
    }
    LaunchedEffect(pendingCompose.value) {
        pendingCompose.value?.let { compose ->
            ComposeDataStore.pending = compose
            navController.navigate(Routes.COMPOSE)
            pendingCompose.value = null
        }
    }
    LaunchedEffect(pendingShare.value) {
        pendingShare.value?.let { share ->
            ComposeDataStore.pending = PendingCompose(
                recipient = "",
                subject = "",
                body = share.text ?: ""
            )
            ComposeDataStore.pendingShareUri = share.uri
            ComposeDataStore.pendingShareMimeType = share.mimeType
            navController.navigate(Routes.COMPOSE)
            pendingShare.value = null
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT_LIST
    ) {
        composable(Routes.CHAT_LIST) {
            val configuration = LocalConfiguration.current
            val isWide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                configuration.screenWidthDp >= 600
            if (isWide) {
                val detailNavController = rememberNavController()
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatListScreen(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                        onChatClick = { chatId ->
                            detailNavController.navigate(Routes.chatDetail(chatId))
                        },
                        onAccountSetup = { navController.navigate(Routes.SETTINGS) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onHelp = { navController.navigate(Routes.HELP) },
                        onAssistant = { navController.navigate(Routes.ASSISTANT) },
                        onComposeNewEmail = {
                            ComposeDataStore.pending = PendingCompose(recipient = "", subject = "", body = "")
                            navController.navigate(Routes.COMPOSE)
                        }
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight()
                    ) {
                        NavHost(
                            navController = detailNavController,
                            startDestination = Routes.DETAIL_PLACEHOLDER
                        ) {
                            composable(Routes.DETAIL_PLACEHOLDER) {
                                DetailPlaceholder()
                            }
                            composable(
                                route = Routes.CHAT_DETAIL,
                                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
                                ChatDetailScreen(
                                    chatId = chatId,
                                    onBack = { detailNavController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            } else {
                ChatListScreen(
                    onChatClick = { chatId ->
                        navController.navigate(Routes.chatDetail(chatId))
                    },
                    onAccountSetup = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onHelp = { navController.navigate(Routes.HELP) },
                    onAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onComposeNewEmail = {
                        ComposeDataStore.pending = PendingCompose(recipient = "", subject = "", body = "")
                        navController.navigate(Routes.COMPOSE)
                    }
                )
            }
        }
        composable(
            route = Routes.CHAT_DETAIL,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
            ChatDetailScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ACCOUNT_SETUP,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType; defaultValue = -1L })
        ) {
            AccountSetupScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Routes.accountSetup(-1L)) },
                onEditAccount = { accountId -> navController.navigate(Routes.accountSetup(accountId)) },
                onVersionHistory = { navController.navigate(Routes.VERSION_HISTORY) },
                onHelp = { navController.navigate(Routes.HELP) },
                onOpenChat = { chatId -> navController.navigate(Routes.chatDetail(chatId)) },
                onCustomTags = { navController.navigate(Routes.CUSTOM_TAGS) },
                onComposeEmail = { recipient, subject, body ->
                    pendingCompose.value = PendingCompose(recipient, subject, body)
                },
                onLegal = { doc -> navController.navigate(Routes.legal(doc)) }
            )
        }
        composable(Routes.CUSTOM_TAGS) {
            CustomTagsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.HIDDEN_CHATS,
            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "hidden" })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "hidden"
            HiddenChatsScreen(
                chatType = type,
                onBack = { navController.popBackStack() },
                onChatClick = { chatId -> navController.navigate(Routes.chatDetail(chatId)) }
            )
        }
        composable(Routes.VERSION_HISTORY) {
            VersionHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HELP) {
            HelpScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.LEGAL,
            arguments = listOf(navArgument("doc") { type = NavType.StringType })
        ) { backStackEntry ->
            val doc = backStackEntry.arguments?.getString("doc") ?: "privacy"
            LegalScreen(
                doc = doc,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ASSISTANT) {
            ChatAssistantScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.COMPOSE) {
            val composeData = ComposeDataStore.pending
            ComposeEmailScreen(
                recipient = composeData?.recipient ?: "",
                subject = composeData?.subject ?: "",
                body = composeData?.body ?: "",
                onBack = {
                    ComposeDataStore.pending = null
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun DetailPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
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
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = null,
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.select_chat_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}
