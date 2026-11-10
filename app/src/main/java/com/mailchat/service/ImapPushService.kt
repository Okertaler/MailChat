package com.mailchat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.mailchat.MainActivity
import com.mailchat.R
import com.mailchat.data.database.MailchatDatabase
import com.mailchat.data.model.EmailAccount
import com.mailchat.util.Secrets
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import com.sun.mail.imap.IMAPFolder

@AndroidEntryPoint
class ImapPushService : Service() {

    @Inject lateinit var syncEngine: SyncEngine

    private var serviceScope: CoroutineScope? = null
    @Volatile private var running = false
    @Volatile private var isSyncing = false
    @Volatile private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.push_active)))
            foregroundStarted = true
        } catch (e: Exception) {
            // On Android 12+, starting a foreground service from the background can fail with
            // ForegroundServiceStartNotAllowedException ("time limit already exhausted"). Stop the
            // service gracefully instead of crashing the whole app.
            android.util.Log.e("MailchatPush", "startForeground failed: ${e.message} — stopping service")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) {
            // The foreground service could not be started (background start restriction).
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        acquireWakeLock()
        serviceScope?.cancel()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startIdleLoops()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        serviceScope?.cancel()
        serviceScope = null
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * Hold a partial wake lock while push is active so the CPU stays awake and the IMAP IDLE
     * loop can re-issue IDLE on time (the server caps IDLE at ~29 minutes). Without it the CPU
     * may sleep and the connection goes stale, delaying incoming mail. Combined with the
     * battery-optimization exemption this keeps push reliable in the background.
     */
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mailchat:ImapPush")
            wl.acquire()
            wakeLock = wl
            com.mailchat.ai.AppLogger.log(this, "IMAP Push: wake lock acquired")
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(this, "IMAP Push: failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun startIdleLoops() {
        val scope = serviceScope ?: return
        scope.launch {
            val accounts = try {
                val db = MailchatDatabase.getInstance(this@ImapPushService)
                db.accountDao().getAllAccounts().first()
            } catch (e: Exception) {
                emptyList()
            }
            accounts.forEach { account ->
                scope.launch { idleLoop(account) }
            }
        }
        // Fallback: poll periodically in case IDLE misses mail (stale connection, server not
        // sending EXISTS updates). This guarantees mail is picked up even when the IDLE loop is
        // not detecting it, so emails load in every screen (chat, settings) and in the background.
        scope.launch {
            while (running) {
                delay(PERIODIC_SYNC_INTERVAL_MS)
                triggerSync()
            }
        }
    }

    private suspend fun idleLoop(account: EmailAccount) {
        var retryDelay = 5000L
        while (running) {
            try {
                val password = Secrets.getSecret(this@ImapPushService, Secrets.accountPasswordKey(account.id))
                    ?: account.passwordEncrypted
                if (password.isBlank()) {
                    delay(60000)
                    continue
                }
                val props = Properties().apply {
                    put("mail.store.protocol", "imaps")
                    put("mail.imaps.ssl.enable", "true")
                    put("mail.imaps.connectiontimeout", "15000")
                    put("mail.imaps.timeout", "3600000")
                    put("mail.imaps.writetimeout", "30000")
                    put("mail.imaps.idle", "true")
                }
                val session = Session.getInstance(props)
                val store: Store = session.getStore("imaps")
                store.connect(account.imapHost, account.imapPort, account.username, password)
                com.mailchat.ai.AppLogger.log(this@ImapPushService, "IMAP Push: connected ${account.emailAddress}")

                val folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)
                val imapFolder = folder as? IMAPFolder

                if (imapFolder != null) {
                    while (running) {
                        imapFolder.idle()
                        com.mailchat.ai.AppLogger.log(this@ImapPushService, "IMAP Push: idle ended for ${account.emailAddress}, triggering sync")
                        triggerSync()
                        delay(1000)
                    }
                }

                try { folder.close() } catch (_: Exception) {}
                try { store.close() } catch (_: Exception) {}
                retryDelay = 5000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!running) return
                com.mailchat.ai.AppLogger.log(this@ImapPushService, "IMAP Push error for ${account.emailAddress}: ${e.message}; retry in ${retryDelay}ms")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(300000L)
            }
        }
    }

    /**
     * Run the sync directly in this (foreground) service instead of via WorkManager. WorkManager
     * jobs are not reliably executed in the background (Doze / battery optimizations can defer
     * them), which is why mail was only picked up on the overview page (whose sync runs in the
     * foreground). The service holds a wake lock, so the sync runs reliably regardless of which
     * screen is visible or whether the app is in the background.
     */
    private fun triggerSync() {
        if (isSyncing) return
        if (!::syncEngine.isInitialized) return
        val scope = serviceScope ?: return
        isSyncing = true
        scope.launch {
            try {
                com.mailchat.ai.AppLogger.log(this@ImapPushService, "IMAP Push: running direct sync")
                val result = syncEngine.syncAll()
                if (result.errorDetails.isNotEmpty()) {
                    com.mailchat.ai.AppLogger.log(
                        this@ImapPushService,
                        "IMAP Push: sync errors: ${result.errorDetails.joinToString("; ").take(200)}"
                    )
                }
            } catch (e: Exception) {
                com.mailchat.ai.AppLogger.log(this@ImapPushService, "IMAP Push: direct sync failed: ${e.message}")
            } finally {
                isSyncing = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.push_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ImapPushService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .addAction(0, getString(R.string.push_stop), stopPi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "imap_push"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.mailchat.service.IMAP_PUSH_STOP"
        /** Fallback poll interval in case IDLE misses incoming mail. */
        private const val PERIODIC_SYNC_INTERVAL_MS = 2 * 60 * 1000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, ImapPushService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Startet den Push-Loop nur, wenn die Funktion aktiviert ist; Fehler (Background-Limits) werden ignoriert. */
        fun startIfEnabled(context: android.content.Context) {
            if (!com.mailchat.util.SettingsUtils.isImapPushEnabled(context)) return
            try {
                start(context)
            } catch (_: Exception) {
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, ImapPushService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        /** Whether the app is exempt from battery optimizations (Doze). */
        fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
            return try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Open the system dialog that lets the user exempt the app from battery optimizations.
         * Falls back to the app's details settings if the direct intent is unavailable.
         */
        fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
            val pkg = context.packageName
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                } catch (_: Exception) {
                }
            }
        }
    }
}
