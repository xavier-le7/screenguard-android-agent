package fr.sevenskies.screenguard.enforcement

import java.text.Normalizer
import java.util.Locale

/**
 * Opt-in guard for paths that can disable ScreenGuard.
 * Installing or uninstalling OTHER apps, package management, and enabling
 * installation from other sources must remain unrestricted.
 * "ScreenGuard app info" is protected only when its own details are shown.
 */
object SensitiveSettingsPolicy {
    const val PARENT_ACCESS_MILLIS = 15L * 60_000L

    fun shouldRequireParent(
        pkg: String?,
        activity: String?,
        title: String?,
        screenGuardDetailsVisible: Boolean = false
    ): Boolean {
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.settings") return false
        val activityName = activity.orEmpty().lowercase(Locale.ROOT)
        val normalizedTitle = normalize(title.orEmpty())

        if (NETWORK_ACTIVITY.any { activityName.contains(it) } ||
            normalizedTitle in SAFE_TITLES) return false

        // Other applications must remain installable and uninstallable.
        // General "Applications" and all non-ScreenGuard AppInfo screens are free.
        if (isAppDetails(activityName, normalizedTitle)) {
            return screenGuardDetailsVisible
        }
        if (isDeviceAdminTitle(title)) return true
        if (normalizedTitle in GENERAL_APP_PAGES ||
            GENERAL_APP_ACTIVITIES.any { activityName.contains(it) }) return false

        return SENSITIVE_ACTIVITIES.any { activityName.contains(it) } ||
            normalizedTitle in SENSITIVE_TITLES
    }

    fun isDeviceAdminTitle(title: String?): Boolean {
        val normalized = normalize(title.orEmpty())
        return normalized in setOf(
            "applications admin. appareil",
            "applications admin appareil",
            "applications admin. de l'appareil",
            "applications d'administration de l'appareil",
            "device admin apps",
            "device administrators"
        )
    }

    fun isAppDetails(activity: String?, title: String?): Boolean {
        val name = activity.orEmpty().lowercase(Locale.ROOT)
        val label = normalize(title.orEmpty())
        return APP_DETAIL_ACTIVITIES.any { name.contains(it) } ||
            label in APP_DETAIL_TITLES
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value, Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private val NETWORK_ACTIVITY = listOf(
        "wifi", "wlan", "networkdashboard", "tether", "hotspot",
        "bluetooth", "mobiledata", "mobile", "internet", "nfc"
    )

    private val SAFE_TITLES = setOf(
        "wi-fi", "wifi", "internet", "connexion", "connexions",
        "reseaux mobiles", "donnees mobiles", "bluetooth",
        "point d'acces mobile", "point d'acces et modem",
        "luminosite", "sons et vibrations", "affichage",
        "installation d'applications", "installer des applications"
    )

    private val APP_DETAIL_ACTIVITIES = listOf(
        "appinfodashboard", "installedappdetails", "applicationdetails",
        "appdetails", "appinfosettings"
    )
    private val APP_DETAIL_TITLES = setOf(
        "infos sur l'application", "informations sur l'application",
        "informations sur l'appli", "informations sur l'app",
        "infos sur l'appli"
    )
    private val GENERAL_APP_ACTIVITIES = listOf(
        "manageapplications", "applicationssettings", "appsettings",
        "installedapplications", "installexternal", "unknownapp"
    )
    private val GENERAL_APP_PAGES = setOf(
        "applications", "gestion des applications", "applications installees",
        "gerer les applications", "mes applications",
        "installer applis inconnues", "installation d'applis inconnues",
        "installer des applications inconnues",
        "autorisation d'installation des applis inconnues",
        "gestionnaire d'autorisations", "autorisations des applications"
    )
    private val SENSITIVE_ACTIVITIES = listOf(
        "accessibilitysettings", "accessibilityservice",
        "deviceadmin", "deviceadministrators",
        "usageaccess", "specialaccess",
        "developmentsettings", "developmentpreferences",
        "factoryreset", "masterclear", "resetdashboard"
    )
    private val SENSITIVE_TITLES = setOf(
        "accessibilite", "services installes", "services telecharges",
        "applications d'accessibilite", "administrateurs de l'appareil",
        "applications d'administration de l'appareil",
        "applications d'admin. de l'appareil",
        "applications d'administration", "applications de gestion de l'appareil",
        "acces special", "acces special aux applications",
        "acces aux donnees d'utilisation", "acces aux statistiques d'utilisation",
        "options de developpement", "options pour les developpeurs",
        "reinitialisation", "retablir les parametres par defaut",
        "retablir la configuration d'usine", "retablir valeurs d'usine"
    )
}
