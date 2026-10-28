package fr.sevenskies.screenguard

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.net.Uri
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n
import fr.sevenskies.screenguard.enforcement.EnforcementPolicy
import fr.sevenskies.screenguard.enforcement.SensitiveSettingsPolicy
import fr.sevenskies.screenguard.enforcement.GuardAccessibilityService
import fr.sevenskies.screenguard.network.ScreenGuardClient
import fr.sevenskies.screenguard.security.ParentPin
import fr.sevenskies.screenguard.security.ScreenGuardDeviceAdminReceiver
import fr.sevenskies.screenguard.storage.AgentStore
import fr.sevenskies.screenguard.storage.TrackingStatus
import fr.sevenskies.screenguard.tracking.TrackingService
import fr.sevenskies.screenguard.usage.UsageLedger
import java.util.concurrent.TimeUnit

/** Parent dashboard; the ScreenGuard protocol and counters are untouched. */
class MainActivity : Activity(), ScreenGuardClient.Events {
    private lateinit var store: AgentStore
    private lateinit var pairingClient: ScreenGuardClient
    private lateinit var state: TrackingStatus
    private lateinit var ledger: UsageLedger
    private lateinit var parentPin: ParentPin

    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var connectionChip: TextView
    private lateinit var protectionChip: TextView
    private lateinit var quotaFigure: TextView
    private lateinit var quotaNote: TextView
    private lateinit var tabletFigure: TextView
    private lateinit var syncFigure: TextView
    private lateinit var serverHealth: TextView
    private lateinit var trackingHealth: TextView
    private lateinit var guardHealth: TextView
    private lateinit var settingsHealth: TextView
    private lateinit var infoNotice: TextView
    private lateinit var setupPanel: LinearLayout
    private lateinit var controlPanel: LinearLayout
    private lateinit var diagnosticPanel: LinearLayout
    private lateinit var setupToggle: TextView
    private lateinit var controlToggle: TextView
    private lateinit var diagnosticToggle: TextView
    private lateinit var pairingPanel: LinearLayout
    private lateinit var serverAddress: EditText
    private lateinit var profileNameInput: EditText
    private lateinit var setupText: TextView
    private lateinit var statsAction: TextView
    private lateinit var accessAction: TextView
    private lateinit var trackingAction: TextView
    private lateinit var restrictAction: TextView
    private lateinit var sensitiveAction: TextView
    private lateinit var notificationAction: TextView
    private var pendingAdminRequest = false
    private lateinit var diagnosticText: TextView

    private var setupExpanded = false
    private var controlsExpanded = false
    private var diagnosticsExpanded = false
    private var manuallyOpenedSetup = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (::statusTitle.isInitialized) updateDashboard()
            handler.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AgentStore(this)
        state = TrackingStatus(this)
        ledger = UsageLedger(this)
        parentPin = ParentPin(this)
        pairingClient = ScreenGuardClient(store, this)
        window.statusBarColor = NAVY
        window.navigationBarColor = BACKGROUND
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
        }
        val frame = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(36))
        }
        frame.addView(column, FrameLayout.LayoutParams(
            if (resources.configuration.smallestScreenWidthDp >= 600) dp(620)
            else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        scroll.addView(frame)
        setContentView(scroll)

        buildHeader(column)
        space(column, 16)
        buildStatus(column)
        space(column, 13)
        buildQuota(column)
        space(column, 13)
        buildHealth(column)
        space(column, 16)

        infoNotice = label("", 13f, NAVY).apply {
            background = rounded(Color.rgb(218, 243, 241), 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            visibility = View.GONE
        }
        column.addView(infoNotice, full(-2))
        space(column, 12)
        buildSetup(column)
        space(column, 10)
        buildControls(column)
        space(column, 10)
        buildDiagnostics(column)
        space(column, 18)
        column.addView(label("ScreenGuard 0.5.3",
            12f, MUTED).apply { gravity = Gravity.CENTER })

        setupExpanded = store.loadToken() == null || !ledger.hasPermission() ||
            !state.trackingEnabled || !state.restrictionsEnabled || !accessibilityGranted()
        panels()
        updateDashboard()
        showParentNotificationIfPresent(intent)
    }

    private fun buildHeader(root: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(NAVY, 15)
            contentDescription = "ScreenGuard"
        }, LinearLayout.LayoutParams(dp(50), dp(50)))
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        names.addView(label("ScreenGuard", 24f, NAVY, true))
        names.addView(label(
            if (store.child.isBlank()) Build.MODEL
            else "Tablette de ${store.child}  ·  ${Build.MODEL}",
            12f, MUTED
        ))
        header.addView(names)
        root.addView(header)
    }

    private fun buildStatus(root: LinearLayout) {
        val c = card(NAVY)
        c.addView(label("ÉTAT DE LA PROTECTION", 11f, Color.rgb(166, 219, 215), true))
        space(c, 10)
        statusTitle = label("Analyse…", 25f, Color.WHITE, true)
        statusDescription = label("", 14f, Color.rgb(214, 228, 239))
        c.addView(statusTitle)
        space(c, 5)
        c.addView(statusDescription)
        space(c, 15)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        connectionChip = label("", 11f, Color.WHITE, true)
        protectionChip = label("", 11f, Color.WHITE, true)
        chips.addView(connectionChip)
        chips.addView(protectionChip, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(8)
        })
        c.addView(chips)
        root.addView(c, full(-2))
    }

    private fun buildQuota(root: LinearLayout) {
        val c = card(Color.WHITE)
        c.addView(label("QUOTA PARTAGÉ RESTANT", 12f, MUTED, true))
        space(c, 4)
        quotaFigure = label("—", 44f, NAVY, true)
        c.addView(quotaFigure)
        quotaNote = label("Même quota sur PC et tablette", 13f, MUTED)
        c.addView(quotaNote)
        space(c, 17)
        c.addView(View(this).apply { setBackgroundColor(DIVIDER) },
            LinearLayout.LayoutParams(-1, dp(1)))
        space(c, 15)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(label("TABLETTE AUJOURD'HUI", 10f, MUTED, true))
        space(left, 5)
        tabletFigure = label("—", 18f, NAVY, true)
        left.addView(tabletFigure)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(label("DERNIÈRE SYNCHRO", 10f, MUTED, true))
        space(right, 5)
        syncFigure = label("—", 16f, NAVY, true)
        right.addView(syncFigure)
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(row)
        root.addView(c, full(-2))
    }

    private fun buildHealth(root: LinearLayout) {
        val c = card(Color.WHITE)
        c.addView(label("État du système", 18f, NAVY, true))
        space(c, 12)
        serverHealth = healthRow(c, "Serveur")
        trackingHealth = healthRow(c, "Suivi du temps")
        guardHealth = healthRow(c, "Blocage des applications")
        settingsHealth = healthRow(c, "Réglages sensibles")
        root.addView(c, full(-2))
    }

    private fun healthRow(parent: LinearLayout, name: String): TextView {
        val line = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        line.addView(label(name, 14f, TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        val status = label("…", 12f, MUTED, true).apply {
            gravity = Gravity.END
            maxLines = 2
        }
        line.addView(status, LinearLayout.LayoutParams(dp(174), -2))
        parent.addView(line)
        return status
    }

    private fun loc(message: String): String = ScreenGuardI18n.text(this, message)

    private fun openLanguageSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }.onFailure { inform("Réglages de langue indisponibles.") }
    }

    private fun buildSetup(root: LinearLayout) {
        setupToggle = section(root, "Configuration", "Association et autorisations") {
            setupExpanded = !setupExpanded
            manuallyOpenedSetup = true
            panels()
        }
        setupPanel = card(Color.WHITE)
        setupPanel.addView(action("Langue de l'application", false) {
            openLanguageSettings()
        }, full(48))
        space(setupPanel, 12)
        setupText = label("", 13f, MUTED)
        setupPanel.addView(setupText)
        pairingPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        space(pairingPanel, 12)
        pairingPanel.addView(label("SERVEUR", 11f, MUTED, true))
        serverAddress = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(NAVY)
            setText(store.serverUrl)
            hint = "wss://example.org/ws"
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(BACKGROUND, 12)
        }
        pairingPanel.addView(serverAddress, full(-2))
        space(pairingPanel, 9)
        pairingPanel.addView(label("PROFIL", 11f, MUTED, true))
        profileNameInput = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(NAVY)
            setText(store.child)
            hint = loc("Nom du profil enfant")
            filters = arrayOf(InputFilter.LengthFilter(64))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(BACKGROUND, 12)
        }
        pairingPanel.addView(profileNameInput, full(-2))
        space(pairingPanel, 9)
        pairingPanel.addView(action("Demander un code d'appairage", false) {
            if (store.loadToken() != null) {
                inform("Tablette déjà associée.")
            } else {
                val name = profileNameInput.text.toString().trim()
                if (name.isBlank()) {
                    profileNameInput.error = loc("Saisis un nom de profil.")
                } else {
                    pairingClient.pair(serverAddress.text.toString().trim(), name)
                }
            }
        }, full(48))
        setupPanel.addView(pairingPanel)
        space(setupPanel, 13)
        statsAction = action("Autoriser les statistiques", false) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        setupPanel.addView(statsAction, full(48))
        space(setupPanel, 8)
        accessAction = action("Autoriser l'accessibilité", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setupPanel.addView(accessAction, full(48))
        space(setupPanel, 8)
        trackingAction = action("Démarrer / reprendre le suivi", false) { startTracking() }
        setupPanel.addView(trackingAction, full(48))
        space(setupPanel, 8)
        notificationAction = action("Autoriser les notifications", false) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
            } else {
                inform("✓ Notifications autorisées")
            }
        }
        setupPanel.addView(notificationAction, full(48))
        space(setupPanel, 8)
        restrictAction = action("Activer les restrictions", false) {
            requireParent {
                when {
                    store.loadToken() == null -> inform("Associe d'abord cette tablette.")
                    !ledger.hasPermission() || !state.trackingEnabled ->
                        inform("Autorise les statistiques et démarre le suivi.")
                    !accessibilityGranted() -> inform("Active ScreenGuard dans Accessibilité.")
                    else -> {
                        state.restrictionsEnabled = true
                        inform("Protection activée : les limites ScreenGuard s'appliquent.")
                    }
                }
            }
        }
        setupPanel.addView(restrictAction, full(48))
        space(setupPanel, 8)
        setupPanel.addView(label("PROTECTION DES RÉGLAGES", 11f, MUTED, true))
        space(setupPanel, 6)
        setupPanel.addView(label(
            "Wi-Fi, Bluetooth, son et affichage restent accessibles. " +
                "L'installation et la suppression des autres applications restent libres. " +
                "Les réglages permettant de désactiver ScreenGuard demandent le code parent.",
            12f, MUTED
        ))
        space(setupPanel, 9)
        sensitiveAction = action("Protéger les réglages sensibles", false) {
            requestSensitiveSettingsProtection()
        }
        setupPanel.addView(sensitiveAction, full(48))
        root.addView(setupPanel, full(-2))
    }

    private fun buildControls(root: LinearLayout) {
        controlToggle = section(root, "Actions parentales", "Test et commandes protégées") {
            controlsExpanded = !controlsExpanded
            panels()
        }
        controlPanel = card(Color.WHITE)
        controlPanel.addView(label(
            "Le code parent protège ces actions. Le test local n'affecte pas le quota.",
            13f, MUTED
        ))
        space(controlPanel, 12)
        controlPanel.addView(action("Tester le blocage 60 secondes", false) {
            beginTest()
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Définir / modifier le code parent", false) {
            if (parentPin.isConfigured()) changeParentPin()
            else requireParent { inform("Code parent créé : protection facultative disponible.") }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Autoriser les réglages sensibles (15 min)", false) {
            if (!parentPin.isConfigured()) {
                inform("Crée d'abord un code parent.")
            } else {
                requireParent {
                    state.settingsAuthorizedUntilMillis = System.currentTimeMillis() + SensitiveSettingsPolicy.PARENT_ACCESS_MILLIS
                    inform("Réglages sensibles autorisés pendant 15 minutes.")
                }
            }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Désactiver le filtre des réglages", false, true) {
            if (!parentPin.isConfigured()) inform("Code parent non configuré.")
            else requireParent {
                state.settingsGuardEnabled = false
                state.settingsAuthorizedUntilMillis = 0L
                inform("Filtrage des réglages désactivé. Le statut administrateur reste actif.")
            }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Retirer le statut administrateur", false, true) {
            if (!parentPin.isConfigured()) inform("Code parent non configuré.")
            else requireParent {
                AlertDialog.Builder(this)
                    .setTitle(loc("Retirer la protection de désinstallation ?"))
                    .setMessage(loc("ScreenGuard pourra alors être désinstallé depuis Android."))
                    .setNegativeButton(loc("Annuler"), null)
                    .setPositiveButton(loc("Retirer")) { _, _ ->
                        val admin = deviceAdmin()
                        if (adminIsActive()) {
                            getSystemService(DevicePolicyManager::class.java)
                                .removeActiveAdmin(admin)
                        }
                        state.settingsGuardEnabled = false
                        state.settingsAuthorizedUntilMillis = 0L
                        inform("Statut administrateur retiré par le parent.")
                    }.show()
            }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Annuler le test", false) {
            requireParent {
                state.testLockUntilMillis = 0L
                inform("Test annulé.")
            }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Désactiver les restrictions", false, true) {
            requireParent {
                state.restrictionsEnabled = false
                state.testLockUntilMillis = 0L
                inform("Restrictions désactivées ; le suivi continue.")
            }
        }, full(48))
        space(controlPanel, 8)
        controlPanel.addView(action("Arrêter le suivi", false, true) {
            requireParent {
                AlertDialog.Builder(this)
                    .setTitle(loc("Arrêter le suivi ?"))
                    .setMessage(loc("Le comptage et le blocage seront interrompus."))
                    .setNegativeButton(loc("Annuler"), null)
                    .setPositiveButton(loc("Arrêter")) { _, _ ->
                        state.restrictionsEnabled = false
                        state.testLockUntilMillis = 0L
                        state.trackingEnabled = false
                        stopService(Intent(this, TrackingService::class.java))
                        inform("Suivi arrêté par le parent.")
                    }.show()
            }
        }, full(48))
        root.addView(controlPanel, full(-2))
    }

    private fun buildDiagnostics(root: LinearLayout) {
        diagnosticToggle = section(root, "Diagnostic avancé", "Services et horodatages") {
            diagnosticsExpanded = !diagnosticsExpanded
            panels()
        }
        diagnosticPanel = card(Color.WHITE)
        diagnosticText = label("", 12f, TEXT).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        diagnosticPanel.addView(diagnosticText)
        space(diagnosticPanel, 12)
        diagnosticPanel.addView(action("Copier le diagnostic", false) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("ScreenGuard", diagnosticText.text)
            )
            inform("Diagnostic copié sans jeton ni code parent.")
        }, full(48))
        root.addView(diagnosticPanel, full(-2))
    }

    private fun section(parent: LinearLayout, title: String, help: String,
                        onclick: () -> Unit): TextView {
        val toggle = action(loc(title) + "  ⌄", false) { onclick() }
        parent.addView(toggle, full(46))
        parent.addView(label(help, 12f, MUTED).apply {
            setPadding(dp(9), 0, 0, 0)
        })
        space(parent, 7)
        return toggle
    }

    private fun panels() {
        setupPanel.visibility = if (setupExpanded) View.VISIBLE else View.GONE
        controlPanel.visibility = if (controlsExpanded) View.VISIBLE else View.GONE
        diagnosticPanel.visibility = if (diagnosticsExpanded) View.VISIBLE else View.GONE
        setupToggle.text = loc("Configuration") + "  " + if (setupExpanded) "⌃" else "⌄"
        controlToggle.text = loc("Actions parentales") + "  " + if (controlsExpanded) "⌃" else "⌄"
        diagnosticToggle.text = loc("Diagnostic avancé") + "  " + if (diagnosticsExpanded) "⌃" else "⌄"
    }

    private fun updateDashboard() {
        val now = System.currentTimeMillis()
        val paired = store.loadToken() != null
        val statsAllowed = ledger.hasPermission()
        val usedSeconds = ledger.todaySeconds()
        val policy = state.policyAt(usedSeconds)
        val syncAge = age(now, state.lastServerUpdateMillis)
        val sampleAge = age(now, state.lastCollectionMillis)
        val guardAge = age(now, state.lastGuardEvaluatedAt)
        val online = paired && syncAge < 90L
        val trackingOk = statsAllowed && sampleAge < 50L
        val accessAllowed = accessibilityGranted()
        val guardOk = accessAllowed && guardAge < 60L
        val restrictOn = state.restrictionsEnabled
        val ready = paired && trackingOk && guardOk && restrictOn
        val locked = policy.mode == EnforcementPolicy.Mode.LOCK
        val adminMissing = state.settingsGuardEnabled && !adminIsActive()

        val headline: String
        val subtitle: String
        when {
            adminMissing -> {
                headline = "Protection incomplète"
                subtitle = "Statut administrateur à réactiver dans ScreenGuard."
            }
            !paired -> {
                headline = "Configuration nécessaire"
                subtitle = "Associe la tablette à un profil ScreenGuard."
            }
            locked && guardOk && restrictOn -> {
                headline = "Temps d'écran terminé"
                subtitle = "Les applications de loisirs sont bloquées."
            }
            ready && !state.settingsGuardEnabled -> {
                headline = "Quota actif"
                subtitle = "Le filtre des réglages sensibles n'est pas activé."
            }
            !ready -> {
                headline = "Protection à vérifier"
                subtitle = "Une autorisation ou un service doit être réactivé."
            }
            !online -> {
                headline = "Protection locale active"
                subtitle = "Hors ligne : dernière limite connue appliquée."
            }
            policy.mode == EnforcementPolicy.Mode.UNKNOWN -> {
                headline = "En attente du quota"
                subtitle = "Le serveur n'a pas encore confirmé la limite du jour."
            }
            else -> {
                headline = "Protection active"
                subtitle = "Quota partagé et restrictions opérationnels."
            }
        }
        statusTitle.text = loc(headline)
        statusDescription.text = loc(subtitle)
        chip(connectionChip, if (online) "● Serveur actif" else "● Hors ligne", online)
        chip(protectionChip, when {
            adminMissing -> "● Admin inactif"
            locked && guardOk && restrictOn -> "● Bloqué"
            ready -> "● Protégé"
            else -> "● À vérifier"
        }, ready && !locked && !adminMissing)

        quotaFigure.text = policy.secondsRemaining?.let { minutes(it / 60L) } ?: "—"
        quotaFigure.setTextColor(if (locked) RED else NAVY)
        quotaNote.text = loc(when (policy.mode) {
            EnforcementPolicy.Mode.LOCK -> "Quota épuisé ou horaire terminé"
            EnforcementPolicy.Mode.WARN -> "Le temps autorisé touche à sa fin"
            EnforcementPolicy.Mode.ALLOW -> "Temps restant sur PC et tablette"
            EnforcementPolicy.Mode.UNKNOWN -> "Décision du jour indisponible"
        })
        tabletFigure.text = minutes(usedSeconds / 60L)
        syncFigure.text = if (online) ago(syncAge) else
            if (syncAge == Long.MAX_VALUE) "Jamais" else "Hors ligne · " + ago(syncAge)

        health(serverHealth, if (online) "● Connecté" else "● Hors ligne",
            online, if (online) GREEN else AMBER)
        health(trackingHealth, when {
            !statsAllowed -> "● Permission manquante"
            trackingOk -> "● Actif"
            else -> "● Suspendu"
        }, trackingOk, if (trackingOk) GREEN else RED)
        health(guardHealth, when {
            !restrictOn -> "● Désactivé"
            !accessAllowed -> "● Permission manquante"
            !guardOk -> "● Service inactif"
            locked -> "● Blocage actif"
            else -> "● Activé"
        }, guardOk && restrictOn, if (guardOk && restrictOn) GREEN else AMBER)
        val parentalSettingsOpen = now < state.settingsAuthorizedUntilMillis
        val settingsSummary = when {
            !parentPin.isConfigured() -> "● Code non défini"
            !state.settingsGuardEnabled -> "● Non activé"
            !adminIsActive() -> "● Admin retiré"
            !accessAllowed || !guardOk -> "● Accessibilité inactive"
            parentalSettingsOpen -> "● Parent autorisé"
            else -> "● Filtre activé"
        }
        health(settingsHealth, settingsSummary,
            state.settingsGuardEnabled && adminIsActive() && guardOk &&
                !parentalSettingsOpen,
            if (state.settingsGuardEnabled && adminIsActive() && guardOk &&
                !parentalSettingsOpen) GREEN else AMBER)

        setupText.text = loc(if (paired)
            "✓ Appareil lié au profil ${store.child}. Aucun nouvel appairage nécessaire."
        else "Associe la tablette au profil de l'enfant.")
        pairingPanel.visibility = if (paired) View.GONE else View.VISIBLE
        statsAction.text = loc(if (statsAllowed) "✓ Statistiques autorisées" else
            "1 · Autoriser les statistiques")
        accessAction.text = loc(if (accessAllowed) "✓ Accessibilité autorisée" else
            "2 · Autoriser l'accessibilité")
        trackingAction.text = loc(if (trackingOk) "✓ Suivi actif" else
            "3 · Démarrer / reprendre le suivi")
        restrictAction.text = loc(if (restrictOn && guardOk) "✓ Restrictions activées" else
            "4 · Activer les restrictions")
        notificationAction.text = if (checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            loc("✓ Notifications autorisées") else loc("Autoriser les notifications")
        sensitiveAction.text = loc(when {
            adminMissing -> "⚠ Réactiver administrateur Android"
            !parentPin.isConfigured() -> "Code parent nécessaire avant activation"
            state.settingsGuardEnabled && adminIsActive() ->
                "✓ Réglages sensibles protégés"
            adminIsActive() -> "Activer le filtre des réglages sensibles"
            else -> "Activer la protection contre la désinstallation"
        })

        val testSeconds = ((state.testLockUntilMillis - now).coerceAtLeast(0L) + 999L) / 1000L
        diagnosticText.text = loc(buildString {
            appendLine("Profil : ${store.child}")
            appendLine("Appairage : ${if (paired) "OK" else "ABSENT"}")
            appendLine("Serveur : ${if (online) "CONNECTÉ" else "NON CONFIRMÉ"}")
            appendLine("Réponse quota : ${ago(syncAge)}")
            appendLine("Service suivi : ${state.serviceRunning}")
            appendLine("Dernier relevé : ${ago(sampleAge)}")
            appendLine("Accès statistiques : $statsAllowed")
            appendLine("Accessibilité : $accessAllowed")
            appendLine("Service accessibilité : ${state.accessibilityServiceConnected}")
            appendLine("Dernière évaluation : ${ago(guardAge)}")
            appendLine("Restrictions : $restrictOn")
            appendLine("Administrateur Android : ${adminIsActive()}")
            appendLine("Filtre réglages sensibles : ${state.settingsGuardEnabled}")
            appendLine("Dernier contrôle Paramètres : ${state.lastSettingsGuardDiagnostic}")
            appendLine("Contrôle Paramètres effectué : ${ago(age(now, state.lastSettingsGuardCheckedAt))}")
            appendLine("Accès temporaire aux réglages : ${if (now <
                state.settingsAuthorizedUntilMillis) "accordé" else "inactif"}")
            appendLine("Décision : ${policy.mode}")
            appendLine("Test local : ${if (testSeconds > 0L) "$testSeconds s" else "inactif"}")
            appendLine("Collecte de secours : ${ago(age(now, state.lastFallbackCollectionMillis))}")
            appendLine("Application : ${state.lastGuardPackage}")
            appendLine("Bloqueur : ${state.lastGuardReason}")
            appendLine("Santé : ${state.lastHealthIssue.ifEmpty { "OK" }}")
            append("Message : ${state.message}")
        })
        if (ready && setupExpanded && !manuallyOpenedSetup) {
            setupExpanded = false
            panels()
        }
    }

    private fun health(view: TextView, value: String, good: Boolean, color: Int) {
        view.text = loc(value)
        view.setTextColor(color)
    }

    private fun deviceAdmin(): ComponentName =
        ComponentName(this, ScreenGuardDeviceAdminReceiver::class.java)

    private fun adminIsActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(deviceAdmin())

    /**
     * Security invariant: administrative protection is impossible to enable
     * before a PIN already exists AND is verified. No automatic PIN creation
     * is triggered by this button.
     */
    private fun requestSensitiveSettingsProtection() {
        if (!parentPin.isConfigured()) {
            inform("Définis d'abord ton code dans Actions parentales.")
            return
        }
        if (!accessibilityGranted() || !state.accessibilityServiceConnected) {
            inform("Le service d'accessibilité doit être activé avant cette protection.")
            return
        }
        requireParent {
            if (adminIsActive()) {
                state.settingsGuardEnabled = true
                state.settingsAuthorizedUntilMillis = 0L
                inform("Réglages ScreenGuard protégés. Wi-Fi et autres applications restent libres.")
            } else {
                pendingAdminRequest = true
                // Only lifecycle Device Administrator is requested, no wipe
                // or password policies. Android obtains explicit consent.
                state.settingsAuthorizedUntilMillis = System.currentTimeMillis() + SensitiveSettingsPolicy.PARENT_ACCESS_MILLIS
                val request = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin())
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.device_admin_explanation))
                }
                @Suppress("DEPRECATION")
                startActivityForResult(request, 41)
            }
        }
    }

    @Deprecated("Android framework activity result, no AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || !pendingAdminRequest) return
        pendingAdminRequest = false
        val activated = parentPin.isConfigured() && adminIsActive() &&
            accessibilityGranted()
        state.settingsGuardEnabled = activated
        state.settingsAuthorizedUntilMillis = 0L
        inform(if (activated)
            "Protection activée. Wi-Fi et gestion des autres applications restent libres."
        else "Statut administrateur non activé : aucun filtrage ajouté.")
        updateDashboard()
    }

    private fun startTracking() {
        when {
            store.loadToken() == null -> inform("Associe d'abord cette tablette.")
            !ledger.hasPermission() -> inform("Autorise les statistiques d'utilisation.")
            else -> {
                startForegroundService(
                    Intent(this, TrackingService::class.java)
                        .setAction(TrackingService.ACTION_START)
                )
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
                }
                inform("Suivi demandé, synchronisation automatique activée.")
            }
        }
    }

    private fun beginTest() {
        requireParent {
            val issue = when {
                !state.restrictionsEnabled -> "Active d'abord les restrictions."
                !accessibilityGranted() -> "Autorise le service d'accessibilité."
                !state.accessibilityServiceConnected -> "L'accessibilité ne répond pas."
                !state.trackingEnabled || !state.serviceRunning ->
                    "Démarre d'abord le suivi permanent."
                !ledger.hasPermission() -> "Autorise les statistiques d'utilisation."
                age(System.currentTimeMillis(), state.lastCollectionMillis) > 120L ->
                    "Le suivi est suspendu."
                else -> null
            }
            if (issue != null) {
                inform(issue)
                AlertDialog.Builder(this).setTitle(loc("Test indisponible"))
                    .setMessage(issue).setPositiveButton(loc("OK"), null).show()
            } else {
                state.testLockUntilMillis = System.currentTimeMillis() + 60_000L
                AlertDialog.Builder(this)
                    .setTitle(loc("Blocage activé pour 60 secondes"))
                    .setMessage(loc("Ouvre un jeu pour afficher le blocage. ") +
                        loc("Les applications essentielles restent accessibles. ") +
                        loc("Le quota du serveur ne change pas."))
                    .setNegativeButton(loc("Rester ici"), null)
                    .setPositiveButton(loc("Aller à l'accueil")) { _, _ ->
                        startActivity(Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.show()
                inform("Test local activé pour 60 secondes.")
            }
        }
    }

    private fun accessibilityGranted(): Boolean =
        getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name ==
                        GuardAccessibilityService::class.java.name
            }

    private fun pinField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(6))
        hint = loc("Code parent · 6 chiffres")
    }

    /**
     * Change the parent credential only after verifying the existing PIN.
     * A new random salt is generated; neither PIN is stored or logged.
     */
    private fun changeParentPin() {
        if (!parentPin.isConfigured()) {
            inform("Aucun code parent configuré. Active d'abord les restrictions.")
            return
        }
        val current = pinField().apply { hint = loc("Code parent actuel") }
        val replacement = pinField().apply { hint = loc("Nouveau code (6 chiffres)") }
        val confirmation = pinField().apply { hint = loc("Confirmer le nouveau code") }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(4))
            addView(current)
            addView(replacement)
            addView(confirmation)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(loc("Modifier le code parent"))
            .setMessage(loc("Le code actuel est obligatoire. Les restrictions restent actives."))
            .setView(fields)
            .setNegativeButton(loc("Annuler"), null)
            .setPositiveButton(loc("Enregistrer"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val old = current.text.toString()
                val next = replacement.text.toString()
                val confirm = confirmation.text.toString()
                when {
                    !ParentPin.isValid(next) -> {
                        replacement.error = loc("Le nouveau code doit contenir 6 chiffres.")
                    }
                    next != confirm -> {
                        confirmation.error = loc("Les codes ne correspondent pas.")
                    }
                    next == old -> {
                        replacement.error = loc("Choisis un nouveau code différent.")
                    }
                    else -> runCatching {
                        parentPin.changePin(old, next)
                    }.onSuccess { changed ->
                        if (changed) {
                            dialog.dismiss()
                            inform("Code parent modifié. Le nouveau code est déjà actif.")
                        } else {
                            current.error = loc("Code actuel incorrect ou temporairement bloqué.")
                            current.text.clear()
                        }
                    }.onFailure { error ->
                        inform("Impossible de modifier le code : ${error.message}")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun requireParent(action: () -> Unit) {
        if (!parentPin.isConfigured()) {
            val first = pinField()
            val confirm = pinField().apply { hint = loc("Confirmer le code") }
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(8))
                addView(first)
                addView(confirm)
            }
            AlertDialog.Builder(this)
                .setTitle(loc("Créer le code parent"))
                .setMessage(loc("Choisis six chiffres distincts du code Android de l'enfant. ") +
                    loc("Ce code sera demandé pour arrêter la protection."))
                .setView(group)
                .setNegativeButton(loc("Annuler"), null)
                .setPositiveButton(loc("Enregistrer")) { _, _ ->
                    val p1 = first.text.toString()
                    if (!ParentPin.isValid(p1) || p1 != confirm.text.toString()) {
                        inform("Les deux codes doivent être identiques (six chiffres).")
                    } else {
                        runCatching {
                            parentPin.setNew(p1)
                            action()
                        }.onFailure { inform("Code parent : ${it.message}") }
                    }
                }.show()
        } else {
            val field = pinField()
            AlertDialog.Builder(this).setTitle(loc("Autorisation parentale"))
                .setMessage(loc("Saisis le code ScreenGuard."))
                .setView(field)
                .setNegativeButton(loc("Annuler"), null)
                .setPositiveButton(loc("Valider")) { _, _ ->
                    if (parentPin.verify(field.text.toString())) action()
                    else inform("Code incorrect ou temporairement bloqué.")
                }.show()
        }
    }

    override fun status(text: String) {
        runOnUiThread { if (::infoNotice.isInitialized) inform(text) }
    }
    private fun inform(message: String) {
        if (!::infoNotice.isInitialized) return
        infoNotice.text = loc(message)
        infoNotice.visibility = View.VISIBLE
    }
    override fun paired() {
        runOnUiThread {
            inform("Appairage accepté. Configure maintenant les autorisations.")
            updateDashboard()
        }
    }
    override fun remaining(minutes: Int, enforce: String) {
        status("Quota reçu : $minutes min · $enforce")
    }
    private fun showParentNotificationIfPresent(openIntent: Intent?) {
        if (openIntent == null ||
            !openIntent.hasExtra(EXTRA_PARENT_MESSAGE_BODY)) return
        val title = openIntent.getStringExtra(EXTRA_PARENT_MESSAGE_TITLE)
            ?.take(160)?.ifBlank { loc("Message du parent") }
            ?: loc("Message du parent")
        val body = openIntent.getStringExtra(EXTRA_PARENT_MESSAGE_BODY)
            ?.take(4096).orEmpty()
        // Consumed once; rotation must not re-open the same message.
        openIntent.removeExtra(EXTRA_PARENT_MESSAGE_TITLE)
        openIntent.removeExtra(EXTRA_PARENT_MESSAGE_BODY)
        if (body.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(loc("Fermer"), null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showParentNotificationIfPresent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 12 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            inform("Les notifications sont nécessaires pour afficher les messages parentaux.")
        }
        if (::notificationAction.isInitialized) updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        pairingClient.close()
        super.onDestroy()
    }

    private fun card(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = rounded(color, 20)
        elevation = dp(2).toFloat()
    }

    private fun action(name: String, primary: Boolean, destructive: Boolean = false,
                       onClick: () -> Unit): TextView {
        val backgroundColor = when {
            primary -> TEAL
            destructive -> Color.rgb(255, 239, 239)
            else -> Color.rgb(234, 244, 247)
        }
        val foreground = when {
            primary -> Color.WHITE
            destructive -> RED
            else -> NAVY
        }
        return label(name, 14f, foreground, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(if (primary) Color.WHITE else TEAL),
                rounded(backgroundColor, 13), null
            )
            setOnClickListener {
                runCatching { onClick() }.onFailure {
                    inform("Action impossible : ${it.message ?: it.javaClass.simpleName}")
                }
            }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: android.view.accessibility.AccessibilityNodeInfo
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
        }
    }

    private fun chip(view: TextView, name: String, good: Boolean) {
        view.text = loc(name)
        view.setTextColor(if (good) Color.rgb(183, 255, 229)
            else Color.rgb(255, 226, 180))
        view.background = rounded(if (good) Color.rgb(26, 83, 88)
            else Color.rgb(87, 75, 73), 20)
        view.setPadding(dp(10), dp(7), dp(10), dp(7))
    }

    private fun label(name: String, size: Float, color: Int,
                      bold: Boolean = false) = TextView(this).apply {
        text = loc(name)
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun full(height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        if (height == -2) ViewGroup.LayoutParams.WRAP_CONTENT else dp(height)
    )
    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun age(now: Long, epoch: Long): Long =
        if (epoch <= 0L) Long.MAX_VALUE
        else TimeUnit.MILLISECONDS.toSeconds((now - epoch).coerceAtLeast(0L))
    private fun ago(seconds: Long): String = when {
        seconds == Long.MAX_VALUE -> loc("jamais")
        seconds < 5L -> loc("à l'instant")
        seconds < 60L -> when (resources.configuration.locales[0].language) {
            "en" -> "${seconds}s ago"
            "pl" -> "${seconds} s temu"
            else -> loc("il y a ") + "${seconds} s"
        }
        seconds < 3_600L -> when (resources.configuration.locales[0].language) {
            "en" -> "${seconds / 60L}m ago"
            "pl" -> "${seconds / 60L} min temu"
            else -> loc("il y a ") + "${seconds / 60L} min"
        }
        else -> when (resources.configuration.locales[0].language) {
            "en" -> "${seconds / 3_600L}h ago"
            "pl" -> "${seconds / 3_600L} godz. temu"
            else -> loc("il y a ") + "${seconds / 3_600L} h"
        }
    }
    private fun minutes(value: Long): String =
        if (value < 60L) "$value min"
        else "${value / 60L} h ${(value % 60L).toString().padStart(2, '0')}"

    companion object {
        const val EXTRA_PARENT_MESSAGE_TITLE =
            "fr.sevenskies.screenguard.parent_message_title"
        const val EXTRA_PARENT_MESSAGE_BODY =
            "fr.sevenskies.screenguard.parent_message_body"

        private val NAVY = Color.rgb(24, 43, 65)
        private val TEAL = Color.rgb(9, 126, 122)
        private val BACKGROUND = Color.rgb(244, 247, 250)
        private val TEXT = Color.rgb(42, 55, 69)
        private val MUTED = Color.rgb(104, 120, 138)
        private val DIVIDER = Color.rgb(231, 236, 242)
        private val GREEN = Color.rgb(14, 121, 90)
        private val AMBER = Color.rgb(157, 103, 34)
        private val RED = Color.rgb(184, 47, 62)
    }
}
