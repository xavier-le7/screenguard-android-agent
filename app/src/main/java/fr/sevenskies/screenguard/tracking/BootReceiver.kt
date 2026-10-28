package fr.sevenskies.screenguard.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.sevenskies.screenguard.storage.AgentStore
import fr.sevenskies.screenguard.storage.TrackingStatus
import fr.sevenskies.screenguard.usage.UsageLedger

/**
 * Best-effort restart after user unlock and BOOT_COMPLETED.
 * A manufacturer may suppress autostart: the UI shows the last sample age.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // PACKAGE_REPLACED is explicitly allowed to restart the opted-in
        // foreground service following an APK upgrade, without re-pairing.
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!TrackingStatus(context).trackingEnabled) return
        if (AgentStore(context).loadToken() == null) return
        if (!UsageLedger(context).hasPermission()) return
        runCatching {
            context.startForegroundService(
                Intent(context, TrackingService::class.java)
                    .setAction(TrackingService.ACTION_START)
            )
        }.onFailure {
            TrackingStatus(context).message =
                "Reprise automatique bloquée par Android ; ouvrir ScreenGuard Agent."
        }
    }
}
