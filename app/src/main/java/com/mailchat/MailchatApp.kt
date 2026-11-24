package com.mailchat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MailchatApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        com.mailchat.service.SyncWorker.runImmediateSync(this)
    }

    /**
     * Logs uncaught exceptions (crashes) to the app log file (Settings -> App log) so the next
     * crash can be diagnosed. Delegates to the default handler afterwards, so the normal crash
     * behaviour is unchanged.
     */
    private fun installCrashLogger() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.append("CRASH on thread ").append(thread.name).append(": ").append(throwable)
                for (element in throwable.stackTrace) {
                    sb.append("\n    at ").append(element)
                }
                var cause = throwable.cause
                var depth = 0
                while (cause != null && depth < 5) {
                    sb.append("\nCaused by: ").append(cause)
                    for (element in cause.stackTrace) {
                        sb.append("\n    at ").append(element)
                    }
                    cause = cause.cause
                    depth++
                }
                com.mailchat.ai.AppLogger.log(this, sb.toString())
            } catch (_: Throwable) {
                // Never crash while logging a crash.
            }
            if (default != null) {
                default.uncaughtException(thread, throwable)
            }
        }
    }
}
