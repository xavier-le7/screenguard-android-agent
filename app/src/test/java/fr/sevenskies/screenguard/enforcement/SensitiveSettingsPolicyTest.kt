package fr.sevenskies.screenguard.enforcement

import org.junit.Assert.*
import org.junit.Test

class SensitiveSettingsPolicyTest {
    private fun blocked(
        activity: String? = null, title: String? = null,
        targetIsScreenGuard: Boolean = false,
        pkg: String = "com.android.settings"
    ): Boolean = SensitiveSettingsPolicy.shouldRequireParent(
        pkg, activity, title, targetIsScreenGuard
    )

    @Test fun otherAppsCanBeInstalledAndUninstalled() {
        assertFalse(blocked("com.android.settings.Settings\$ManageApplicationsActivity", "Applications"))
        assertFalse(blocked("com.android.settings.applications.appinfo.AppInfoDashboardFragment",
            "Informations sur l'application", targetIsScreenGuard = false))
        assertFalse(blocked("com.android.settings.applications.appinfo.AppInfoDashboardFragment",
            "Minecraft", targetIsScreenGuard = false))
        assertFalse(blocked("com.android.settings.SubSettings", "Installer des applications inconnues"))
        assertFalse(blocked("com.android.settings.applications.InstalledAppDetails",
            "Steam", targetIsScreenGuard = false))
        assertFalse(blocked("com.android.settings.SubSettings", "Gestionnaire d'autorisations"))
    }

    @Test fun screenGuardOwnAppInfoIsProtected() {
        assertTrue(blocked("com.android.settings.applications.appinfo.AppInfoDashboardFragment",
            "Informations sur l'application", targetIsScreenGuard = true))
        assertTrue(blocked("com.android.settings.applications.InstalledAppDetails",
            "Infos sur l'appli", targetIsScreenGuard = true))
    }

    @Test fun samsungAdminSettingsRecognized() {
        assertTrue(blocked("com.android.settings.SubSettings", "Applications admin. appareil"))
        assertTrue(blocked("com.android.settings.SubSettings", "Device admin apps"))
    }

    @Test fun wifiAndEverydaySettingsRemainOpen() {
        assertFalse(blocked("com.android.settings.wifi.WifiSettings", "Wi-Fi"))
        assertFalse(blocked("com.android.settings.Settings", "Connexions",
            pkg = "com.samsung.android.settings"))
        assertFalse(blocked("com.android.settings.Settings", "Bluetooth"))
        assertFalse(blocked("com.android.settings.Settings", "Internet"))
        assertFalse(blocked("com.android.settings.SubSettings", "Luminosité"))
        assertFalse(blocked("com.android.settings.SubSettings", "Sons et vibrations"))
    }

    @Test fun actualSecuritySettingsRequirePin() {
        assertTrue(blocked("com.android.settings.SubSettings", "Accessibilité"))
        assertTrue(blocked("com.android.settings.SubSettings", "Administrateurs de l'appareil"))
        assertTrue(blocked("com.android.settings.SubSettings", "Accès aux statistiques d'utilisation"))
        assertTrue(blocked("com.android.settings.SubSettings", "Options de développement"))
        assertTrue(blocked("com.android.settings.SubSettings", "Réinitialisation"))
    }

    @Test fun noBlockingOutsideSettingsOrOnUnknownPages() {
        assertFalse(blocked("com.example.game.MainActivity", "Accessibilité",
            pkg = "com.example.game"))
        assertFalse(blocked("com.android.settings.SubSettings", "À propos de la tablette"))
        assertFalse(blocked("com.android.settings.SubSettings", null))
    }

    @Test fun gracePeriodIs15Minutes() {
        assertEquals(900_000L, SensitiveSettingsPolicy.PARENT_ACCESS_MILLIS)
    }
}
