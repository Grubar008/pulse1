package com.pulse.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulse.app.MainActivity

import com.pulse.app.data.prefs.PulseSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PulseSettings
) {

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECOVERY, "Recovery ready", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BEDTIME, "Bedtime reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    suspend fun maybeNotifyRecovery(recovery: Int?) {
        val enabled = settings.settings.first().notificationsEnabled
        if (!enabled || recovery == null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            recovery <= 33 -> "Recovery $recovery — red zone. Consider lower strain today."
            recovery <= 66 -> "Recovery $recovery — yellow zone. Moderate effort recommended."
            else -> "Recovery $recovery — green zone. Ready for a productive day."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RECOVERY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Your morning recovery")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RECOVERY_ID, notification) }
    }

    suspend fun notifyBedtime() {
        val enabled = settings.settings.first().notificationsEnabled
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_BEDTIME)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to wind down")
            .setContentText("Targeted bedtime approaching. Check your sleep planner.")
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(BEDTIME_ID, notification) }
    }

    companion object {
        const val CHANNEL_RECOVERY = "recovery"
        const val CHANNEL_BEDTIME = "bedtime"
        const val RECOVERY_ID = 1001
        const val BEDTIME_ID = 1002
    }
}
