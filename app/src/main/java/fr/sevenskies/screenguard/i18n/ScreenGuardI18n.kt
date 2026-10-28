package fr.sevenskies.screenguard.i18n

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Android resource localization of legacy French UI strings.
 * The exact French source is the stable key; translated values reside in
 * res/values-{en,de,es,pt,pl}. No settings, child names or protocol payloads
 * are translated. A missing translation falls back to the French original.
 */
object ScreenGuardI18n {
    private val cachedIds = ConcurrentHashMap<String, Int>()
    private val prefixes = listOf(
        "Tablette de ", "✓ Appareil lié au profil ",
        "Profil : ", "Appairage : ", "Serveur : ", "Réponse quota : ",
        "Service suivi : ", "Dernier relevé : ", "Accès statistiques : ",
        "Accessibilité : ", "Service accessibilité : ",
        "Dernière évaluation : ", "Restrictions : ",
        "Administrateur Android : ", "Filtre réglages sensibles : ",
        "Dernier contrôle Paramètres : ", "Contrôle Paramètres effectué : ",
        "Accès temporaire aux réglages : ", "Décision : ",
        "Test local : ", "Collecte de secours : ", "Application : ",
        "Bloqueur : ", "Santé : ", "Message : ",
        "il y a ", "Hors ligne · ", "Quota reçu : ",
        "Impossible de modifier le code : ", "Code parent : ",
        "Action impossible : ", "Application protégée : ",
        "TEST : blocage demandé pour ", "TEST : application ignorée",
        "Quota épuisé : blocage demandé pour ",
        "Quota autorisé pour ",
        "Le quota ou la plage horaire du profil ",
        "Réglages sensibles autorisés pendant ",
        "Il reste environ ", "Appairage demandé. Saisir dans ScreenGuard : ",
        "Code à saisir dans ScreenGuard : ", "Erreur d'appairage : ",
        "Appairage impossible : ", "Synchronisation : ",
        "Connecté : quota indisponible pour UID ", "Message du parent : ",
        "Message non reconnu : ", "Statistiques indisponibles : ",
        "Collecte locale indisponible : ", "Réseau : rappel indisponible (",
        "Serveur actif — décision : "
    ).sortedByDescending { it.length }
    private val suffixes = listOf(
        ". Aucun nouvel appairage nécessaire.", " sont épuisés. "
    )

    fun text(ctx: Context, original: String): String {
        if (original.isEmpty() || ctx.resources.configuration.locales[0].language == "fr") {
            return original
        }
        // Dynamic multi-line diagnostics change continuously. Split first
        // rather than caching one resource lookup for every new full log.
        if (original.contains('\n')) {
            return original.split('\n').joinToString("\n") { text(ctx, it) }
        }
        val exact = resolve(ctx, original)
        if (exact != null) return exact
        for (prefix in prefixes) {
            if (original.startsWith(prefix) && original.length > prefix.length) {
                val translated = resolve(ctx, prefix)
                if (translated != null) {
                    return translated + text(ctx, original.substring(prefix.length))
                }
            }
        }
        for (suffix in suffixes) {
            if (original.endsWith(suffix) && original.length > suffix.length) {
                val translated = resolve(ctx, suffix)
                if (translated != null) {
                    return text(ctx, original.dropLast(suffix.length)) + translated
                }
            }
        }
        return original
    }

    private fun resolve(ctx: Context, original: String): String? {
        val name = "sg_" + java.lang.Integer.toHexString(original.hashCode()).padStart(8, '0')
        if (cachedIds.size > 512 && !cachedIds.containsKey(name)) cachedIds.clear()
        val id = cachedIds.getOrPut(name) {
            ctx.resources.getIdentifier(name, "string", ctx.packageName)
        }
        if (id == 0) return null
        return ctx.getString(id)
    }
}
