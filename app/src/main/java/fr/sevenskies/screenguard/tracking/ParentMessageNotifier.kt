package fr.sevenskies.screenguard.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import fr.sevenskies.screenguard.MainActivity
import fr.sevenskies.screenguard.R
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n
import fr.sevenskies.screenguard.protocol.ScreenGuardProtocol
import java.util.concurrent.atomic.AtomicInteger

/**
 * Existing ScreenGuard server command notify_user, Android notification only.
 * No server changes, no replies, no extra WebSocket traffic.
 */
object ParentMessageNotifier {
    private const val CHANNEL_ID = "screen_guard_parent_messages"
    private val nextId = AtomicInteger(3200)

    /**
     * Called by the foreground tracking service. It never starts an Activity
     * automatically from the background: the child opens the notification.
     * Private message content is hidden on the lock screen.
     */
    fun show(context: Context, message: ScreenGuardProtocol.ParentMessage): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            ScreenGuardI18n.text(context, "Messages des parents"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ScreenGuardI18n.text(
                context, "Messages envoyés depuis l'application parent ScreenGuard.")
            enableVibration(true)
            setShowBadge(true)
        })

        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        val id = nextId.getAndIncrement()
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_PARENT_MESSAGE_TITLE, message.summary)
            putExtra(MainActivity.EXTRA_PARENT_MESSAGE_BODY, message.body)
        }
        val pending = PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicNotification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("ScreenGuard")
            .setContentText(ScreenGuardI18n.text(context, "Nouveau message des parents"))
            .build()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(message.summary)
            .setContentText(message.body)
            .setStyle(Notification.BigTextStyle().bigText(message.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
        manager.notify(id, notification)
        return true
    }
}
