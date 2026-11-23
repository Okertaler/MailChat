package com.mailchat.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mailchat.service.SyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            com.mailchat.ai.AppLogger.log(context, "BootReceiver: boot detected — scheduling sync + push")
            // Periodic sync (WorkManager) — the reliable fallback that runs in the background
            // without a foreground service. Guarantees mail is picked up after boot even if the
            // push service cannot start (background start restriction on some Android versions).
            SyncWorker.schedule(context, SyncWorker.getPreferredInterval(context))
            // Immediate sync so mail is picked up right after boot (WorkManager runs it once the
            // network is available).
            SyncWorker.runImmediateSync(context)
            // Real-time push (foreground service). On some Android versions this can fail from
            // the background; the SyncWorker above is the fallback in that case.
            com.mailchat.service.ImapPushService.startIfEnabled(context)
        }
    }
}
