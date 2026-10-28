package fr.sevenskies.screenguard.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import fr.sevenskies.screenguard.storage.TrackingStatus
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n

/**
 * Optional legacy Android Device Administrator. We request NO device policy.
 * Android requires deactivation of active administrator before app uninstall.
 * No password reset, device wipe or screen lock is invoked by this receiver.
 */
class ScreenGuardDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        ScreenGuardI18n.text(context,
            "Désactiver l'administration de ScreenGuard réduit sa protection. ") +
        ScreenGuardI18n.text(context,
            "Pour modifier ce statut, utilisez les actions parentales dans ScreenGuard.")

    override fun onDisabled(context: Context, intent: Intent) {
        val status = TrackingStatus(context)
        status.settingsAuthorizedUntilMillis = 0L
        status.message = "Statut administrateur désactivé : désinstallation possible."
        super.onDisabled(context, intent)
    }
}
