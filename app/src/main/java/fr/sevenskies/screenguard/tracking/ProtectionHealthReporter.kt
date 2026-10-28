package fr.sevenskies.screenguard.tracking

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import fr.sevenskies.screenguard.security.ScreenGuardDeviceAdminReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n
import fr.sevenskies.screenguard.MainActivity
import fr.sevenskies.screenguard.storage.TrackingStatus

/**
 * Local health diagnostic. Never uses a secret token or sends data off-device.
 * Both services may call it; SharedPreferences throttle one notification per
 * issue (then one every 30 minutes). Only alert when the tablet is in use.
 */
object ProtectionHealthReporter {
    private const val CHANNEL = "screen_guard_health"
    private const val ALERT_ID = 2203
    private const val INTERVAL_MILLIS = 30L * 60 * 1000

    fun check(context: Context, state: TrackingStatus, usageAllowed: Boolean) {
        if (!state.trackingEnabled) return
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) return
        val now = System.currentTimeMillis()
        val adminMissing = state.settingsGuardEnabled &&
            !context.getSystemService(DevicePolicyManager::class.java).isAdminActive(
                ComponentName(context, ScreenGuardDeviceAdminReceiver::class.java)
            )
        val issue = when {
            adminMissing -> "Administrateur ScreenGuard inactif : désinstallation possible"
            !usageAllowed -> "Accès aux statistiques révoqué : comptage impossible"
            state.restrictionsEnabled &&
                (!state.accessibilityServiceConnected ||
                    state.lastGuardEvaluatedAt <= 0 ||
                    now - state.lastGuardEvaluatedAt > 90_000L) ->
                "Accessibilité ScreenGuard inactive : blocage non garanti"
            !state.serviceRunning ||
                state.lastCollectionMillis <= 0 ||
                now - state.lastCollectionMillis > 90_000L ->
                "Service de suivi inactif : vérifier ScreenGuard Agent"
            else -> null
        }
        if (issue == null) {
            if (state.lastHealthIssue.isNotEmpty()) state.lastHealthIssue = ""
            return
        }
        if (issue == state.lastHealthIssue &&
            now - state.lastHealthAlertMillis < INTERVAL_MILLIS) return
        state.lastHealthIssue = issue
        state.lastHealthAlertMillis = now
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, ScreenGuardI18n.text(context, "Santé de la protection ScreenGuard"),
                NotificationManager.IMPORTANCE_HIGH
            ))
            val open = PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(ALERT_ID, Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(ScreenGuardI18n.text(context, "ScreenGuard — attention"))
                .setContentText(ScreenGuardI18n.text(context, issue))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        }.onFailure {
            state.message = "$issue (notifications Android indisponibles)"
        }
    }
}
