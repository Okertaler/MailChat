package com.mailchat.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mailchat.service.SyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            SyncWorker.schedule(context, SyncWorker.getPreferredInterval(context))
            SyncWorker.runImmediateSync(context)
            com.mailchat.service.ImapPushService.startIfEnabled(context)
        }
    }
}
