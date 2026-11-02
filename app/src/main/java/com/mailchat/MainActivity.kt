package com.mailchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mailchat.network.SmtpService
import com.mailchat.ui.navigation.ComposeDataStore
import com.mailchat.ui.navigation.MailchatNavHost
import com.mailchat.ui.navigation.PendingCompose
import com.mailchat.ui.theme.MailchatTheme
import com.mailchat.util.LanguageUtils
import com.mailchat.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var smtpService: SmtpService
    private val pendingChatId = mutableStateOf<Long?>(null)
    private val pendingShare = mutableStateOf<com.mailchat.ui.navigation.ShareData?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge so the status bar is transparent and its icon
        // appearance adapts to the theme. This fixes Samsung devices where the
        // fixed theme status-bar color was being overridden.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        smtpService.setContext(applicationContext)
        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        intent?.getLongExtra("chat_id", -1L)?.let { if (it > 0) pendingChatId.value = it }
        handleShareIntent(intent)
        com.mailchat.service.ImapPushService.startIfEnabled(applicationContext)
        var showSplash by mutableStateOf(true)
        setContent {
            MailchatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        val splashScale = remember { mutableStateOf(0.3f) }
                        LaunchedEffect(Unit) {
                            val start = System.currentTimeMillis()
                            while (System.currentTimeMillis() - start < 1200) {
                                val progress = ((System.currentTimeMillis() - start).toFloat() / 1200f).coerceIn(0f, 1f)
                                splashScale.value = 0.3f + (2.7f) * progress
                                kotlinx.coroutines.delay(16)
                            }
                            showSplash = false
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(160.dp)
                                    .scale(splashScale.value),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        MailchatNavHost(
                            pendingChatId = pendingChatId,
                            pendingShare = pendingShare
                        )
                    }
                }
            }
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val mimeType = intent.type
                if (uri != null || !text.isNullOrBlank()) {
                    pendingShare.value = com.mailchat.ui.navigation.ShareData(uri, text, mimeType)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayExtra(Intent.EXTRA_STREAM) as? Array<Uri>
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val mimeType = intent.type
                if ((uris != null && uris.isNotEmpty()) || !text.isNullOrBlank()) {
                    pendingShare.value = com.mailchat.ui.navigation.ShareData(
                        uri = uris?.firstOrNull(),
                        text = text,
                        mimeType = mimeType
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        val chatId = intent.getLongExtra("chat_id", -1L)
        if (chatId > 0) pendingChatId.value = chatId
    }
}
