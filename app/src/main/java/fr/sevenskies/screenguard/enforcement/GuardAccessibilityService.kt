package fr.sevenskies.screenguard.enforcement

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.app.KeyguardManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n
import fr.sevenskies.screenguard.storage.AgentStore
import fr.sevenskies.screenguard.storage.TrackingStatus
import fr.sevenskies.screenguard.security.ParentPin
import fr.sevenskies.screenguard.tracking.ProtectionHealthReporter
import fr.sevenskies.screenguard.usage.UsageLedger

/**
 * Blocks nonessential foreground applications via TYPE_ACCESSIBILITY_OVERLAY.
 * Deliberately leaves system navigation, launcher, dialer, emergency and
 * settings reachable: this is app blocking, never a device/kiosk lock.
 */
class GuardAccessibilityService : AccessibilityService() {
    private fun loc(s: String): String = ScreenGuardI18n.text(this, s)
    private lateinit var state: TrackingStatus
    private lateinit var ledger: UsageLedger
    private lateinit var windows: WindowManager
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var currentPackage: String? = null
    private var lastWasLock = false
    private var lastDiagnostic: String? = null
    private var lastFallbackElapsed = 0L
    private var overlayKind: String? = null
    private var settingsActivity: String? = null
    private var lastSettingsEventElapsed = 0L
    private lateinit var parentPin: ParentPin

    private val poll = object : Runnable {
        override fun run() {
            reevaluate()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        state = TrackingStatus(this)
        ledger = UsageLedger(this)
        parentPin = ParentPin(this)
        state.accessibilityServiceConnected = true
        Log.i("ScreenGuardGuard", "Accessibility service connected")
        windows = getSystemService(WindowManager::class.java)
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Content updates are needed on One UI where a nested Settings page
        // can reuse SubSettings without generating a new window-state event.
        val pkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg != "com.android.settings" &&
                pkg != "com.samsung.android.settings") return
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastSettingsEventElapsed < 350L) return
            lastSettingsEventElapsed = nowElapsed
        }
        if (pkg == "com.android.settings" || pkg == "com.samsung.android.settings") {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val activity = event.className?.toString()
                settingsActivity = if (
                    activity?.startsWith("com.android.settings") == true ||
                    activity?.startsWith("com.samsung.android.settings") == true
                ) activity else null
            }
        } else if (pkg != null && !isKeyboardPackage(pkg)) {
            settingsActivity = null
        }
        // When PIN entry opens the Samsung keyboard, the underlying protected
        // Settings activity is still visible; do not hide our overlay.
        if (overlayKind == "settings" && pkg != null &&
            (isKeyboardPackage(pkg) || pkg == packageName)) return
        if (!pkg.isNullOrEmpty() && pkg != packageName) currentPackage = pkg
        reevaluate()
    }

    private fun reevaluate() {
        if (!::state.isInitialized) return
        val now = System.currentTimeMillis()
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        // An Accessibility overlay may become the active root while the PIN
        // keyboard is displayed. Keep the parent prompt in front until
        // approval or the Home action, rather than clearing it every
        // second because we can no longer read the Settings toolbar.
        if (overlayKind == "settings" &&
            (rootPkg == packageName ||
                (rootPkg != null && isKeyboardPackage(rootPkg)))) {
            return
        }
        val candidatePkg = if (rootPkg == "com.android.settings" ||
            rootPkg == "com.samsung.android.settings") rootPkg else currentPackage
        // The flag and device administrator are independent: owning the admin
        // status alone is not evidence that Settings interception is enabled.
        val settingsPackage = candidatePkg == "com.android.settings" ||
            candidatePkg == "com.samsung.android.settings"
        val settingsGuard = state.settingsGuardEnabled && parentPin.isConfigured()
        val sensitive = settingsPackage && isSensitiveSettingsScreen(candidatePkg)
        if (settingsPackage) {
            val diagnosis = when {
                !state.settingsGuardEnabled -> "Filtre désactivé dans ScreenGuard"
                !parentPin.isConfigured() -> "Code parent absent : filtre non actif"
                now < state.settingsAuthorizedUntilMillis ->
                    "Dérogation parentale de 15 minutes toujours active"
                sensitive -> "Écran sensible reconnu par le filtre"
                else -> "Écran Samsung non reconnu comme sensible"
            }
            if (diagnosis != state.lastSettingsGuardDiagnostic ||
                now - state.lastSettingsGuardCheckedAt >= 5000L) {
                state.lastSettingsGuardDiagnostic = diagnosis
                state.lastSettingsGuardCheckedAt = now
                if (diagnosis == "Écran Samsung non reconnu comme sensible") {
                    Log.w("ScreenGuardGuard", diagnosis)
                }
            }
        }
        if (settingsGuard && power.isInteractive && !keyguard.isKeyguardLocked &&
            now >= state.settingsAuthorizedUntilMillis && sensitive) {
            publishDiagnostic("Réglages sensibles protégés", candidatePkg)
            showSettingsOverlay()
            return
        }
        if (overlayKind == "settings") hideOverlay()
        var age = now - state.lastCollectionMillis
        // The accessibility service remains active on some Samsung devices
        // when the tracking foreground service is stopped. Use one shared
        // ledger lock and checkpoint: a fallback collection is NOT a second
        // independent counter and therefore does not double-report usage.
        val fallbackNeeded = !state.serviceRunning || age > 25_000L
        if (state.restrictionsEnabled && state.trackingEnabled &&
            ledger.hasPermission() && power.isInteractive && !keyguard.isKeyguardLocked &&
            fallbackNeeded && SystemClock.elapsedRealtime() - lastFallbackElapsed >= 10_000L) {
            lastFallbackElapsed = SystemClock.elapsedRealtime()
            val recovered = runCatching { ledger.collect() }.getOrDefault(false)
            if (recovered) {
                state.lastCollectionMillis = System.currentTimeMillis()
                state.lastFallbackCollectionMillis = state.lastCollectionMillis
                age = 0L
            }
        }
        val reason = when {
            !state.restrictionsEnabled -> "Restrictions non activées"
            !state.trackingEnabled -> "Suivi désactivé"
            !ledger.hasPermission() -> "Statistiques d'utilisation non autorisées"
            !power.isInteractive -> "Écran éteint"
            keyguard.isKeyguardLocked -> "Tablette verrouillée"
            state.lastCollectionMillis == 0L || age > 120_000L ->
                "Compteur indisponible, vérifier le suivi Android"
            else -> null
        }
        ProtectionHealthReporter.check(this, state, ledger.hasPermission())
        if (reason != null) {
            publishDiagnostic(reason, currentPackage)
            hideOverlay()
            return
        }

        // When the underlying app changes while the overlay exists, the root
        // window may remain that app; the accessibility overlay is not part
        // of the app's accessible hierarchy.
        val rootPackage = rootInActiveWindow?.packageName?.toString()
        if (!rootPackage.isNullOrBlank() && rootPackage != packageName) {
            currentPackage = rootPackage
        }
        val pkg = currentPackage ?: run {
            publishDiagnostic("Application au premier plan non détectée", null)
            hideOverlay()
            return
        }
        val mode = state.policyAt(ledger.todaySeconds()).mode
        val locallyTestLocked = System.currentTimeMillis() < state.testLockUntilMillis
        val block = (mode == EnforcementPolicy.Mode.LOCK || locallyTestLocked) &&
            !isEssentialPackage(pkg)
        val diagnosis = when {
            isEssentialPackage(pkg) -> "Application protégée : $pkg"
            locallyTestLocked -> if (block) "TEST : blocage demandé pour $pkg"
                else "TEST : application ignorée"
            mode == EnforcementPolicy.Mode.LOCK -> "Quota épuisé : blocage demandé pour $pkg"
            else -> "Quota autorisé pour $pkg"
        }
        publishDiagnostic(diagnosis, pkg)
        if (block != lastWasLock) {
            Log.i("ScreenGuardGuard", if (block) "Application blocked by active quota policy"
                else "Application access restored or essential app opened")
            lastWasLock = block
        }
        if (block) showOverlay() else hideOverlay()
    }

    private fun isKeyboardPackage(pkg: String): Boolean =
        pkg.contains("inputmethod") || pkg.contains("honeyboard") ||
            pkg.contains("keyboard") || pkg == "com.google.android.inputmethod.latin"

    private fun isSensitiveSettingsScreen(pkg: String?): Boolean {
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.settings")
            return false
        val toolbarTitle = readSettingsToolbarTitle(rootInActiveWindow)
        val isAppInfo = SensitiveSettingsPolicy.isAppDetails(settingsActivity, toolbarTitle)
        val ownAppInfo = isAppInfo && hasOwnAppLabelOnDetails(rootInActiveWindow)
        return SensitiveSettingsPolicy.shouldRequireParent(
            pkg, settingsActivity, toolbarTitle, ownAppInfo
        )
    }

    /**
     * Inspect only the header of a per-application details screen, not the
     * installed-applications list. Other app detail screens stay available.
     * An OEM may change its accessibility hierarchy: unknown screens are
     * left accessible rather than preventing ordinary installations.
     */
    private fun hasOwnAppLabelOnDetails(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val displayName = "ScreenGuard"
        while (queue.isNotEmpty() && visited++ < 65) {
            val node = queue.removeFirst()
            val label = node.text?.toString()?.trim()
            val description = node.contentDescription?.toString()?.trim()
            if (label == displayName || description == displayName) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return false
    }

    /**
     * Only read the top toolbar title, never editable fields, lists,
     * notifications, Wi-Fi network names or personal data in Settings.
     * Settings OEM changes may leave the title undetected; this blocker is
     * an additional deterrent, not an enterprise-managed restriction.
     */
    private fun readSettingsToolbarTitle(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 100) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val inHeader = bounds.top >= 0 &&
                bounds.top < resources.displayMetrics.heightPixels * 0.30f
            val isTitle = id.contains("action_bar_title") ||
                id.contains("toolbar_title") ||
                id.contains("collapsing_toolbar_title") ||
                id.contains("expanded_title") ||
                (id.endsWith(":id/title") && inHeader)
            val samsungAdminHeading = inHeader &&
                SensitiveSettingsPolicy.isDeviceAdminTitle(node.text?.toString())
            if (isTitle || samsungAdminHeading) {
                val title = node.text?.toString()
                    ?: node.contentDescription?.toString()
                if (!title.isNullOrBlank()) return title
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    private fun showSettingsOverlay() {
        if (overlayKind == "settings" && overlay != null) return
        hideOverlay()
        val scale = resources.displayMetrics.density
        val padding = (24 * scale).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(18, 30, 49))
        }
        content.addView(TextView(this).apply {
            text = loc("Réglage protégé")
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            text = loc("Ce réglage peut modifier la protection ScreenGuard. ") +
                loc("Le code parent autorise les réglages sensibles pendant 15 minutes.")
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, padding / 2, 0, padding)
        })
        val pin = EditText(this).apply {
            hint = loc("Code parent (6 chiffres)")
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            isSingleLine = true
        }
        content.addView(pin, LinearLayout.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
        ))
        val message = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.rgb(255, 193, 180))
            setPadding(0, padding / 3, 0, padding / 3)
        }
        content.addView(message)
        content.addView(Button(this).apply {
            text = loc("Déverrouiller 15 minutes")
            setOnClickListener {
                val accepted = parentPin.verify(pin.text.toString())
                pin.text?.clear()
                if (accepted) {
                    state.settingsAuthorizedUntilMillis = System.currentTimeMillis() +
                        SensitiveSettingsPolicy.PARENT_ACCESS_MILLIS
                    hideOverlay()
                    reevaluate()
                } else {
                    message.text = loc("Code incorrect ou temporairement bloqué.")
                }
            }
        })
        content.addView(Button(this).apply {
            text = loc("Retour à l'accueil")
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideOverlay()
            }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            title = "ScreenGuard - autorisation parentale"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windows.addView(content, params)
            overlay = content
            overlayKind = "settings"
        }.onFailure {
            state.message = "Protection des paramètres indisponible : " +
                it.javaClass.simpleName
            Log.e("ScreenGuardGuard", "Settings overlay impossible", it)
        }
    }

    private fun publishDiagnostic(reason: String, pkg: String?) {
        // Persist a heartbeat even when the reason is unchanged: parent
        // diagnostics can detect a disabled or dead accessibility service.
        val now = System.currentTimeMillis()
        if (reason == lastDiagnostic && now - state.lastGuardEvaluatedAt < 20_000L) return
        val changed = reason != lastDiagnostic
        lastDiagnostic = reason
        state.lastGuardReason = reason
        state.lastGuardPackage = pkg ?: "aucune"
        state.lastGuardEvaluatedAt = now
        if (changed) Log.i("ScreenGuardGuard", reason)
    }

    private fun isEssentialPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in ESSENTIAL_PACKAGES) return true
        // Resolve any user-selected HOME app, not only Samsung's default.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(
            home, PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        return pkg == resolved
    }

    private fun showOverlay() {
        if (overlayKind == "quota" && overlay != null) return
        hideOverlay()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(18, 26, 42))
        }
        val heading = TextView(this).apply {
            text = loc("Temps d'écran terminé")
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val description = TextView(this).apply {
            text = loc("Le quota ou la plage horaire du profil ") +
                AgentStore(this@GuardAccessibilityService).child +
                loc(" sont épuisés. ") +
                loc("Les applications restent fermées jusqu'à autorisation.")
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 30)
            setTextColor(Color.WHITE)
        }
        val home = Button(this).apply {
            text = loc("Retour à l'accueil")
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideOverlay()
            }
        }
        content.addView(heading)
        content.addView(description)
        content.addView(home)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; title = "ScreenGuard - blocage applications" }
        runCatching {
            windows.addView(content, params)
            overlay = content
            overlayKind = "quota"
        }.onFailure {
            val reason = "Impossible d'afficher le blocage : " + it.javaClass.simpleName
            state.message = reason
            publishDiagnostic(reason, currentPackage)
            Log.e("ScreenGuardGuard", reason, it)
        }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        overlayKind = null
        runCatching { windows.removeView(view) }
    }

    override fun onInterrupt() { hideOverlay() }

    override fun onDestroy() {
        if (::state.isInitialized) {
            state.accessibilityServiceConnected = false
            state.lastGuardReason = "Service d'accessibilité arrêté"
        }
        handler.removeCallbacks(poll)
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        private val ESSENTIAL_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.samsung.android.settings",
            "com.sec.android.app.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.phone",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.emergency",
            "com.samsung.android.emergency",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.app.telephonyui"
        )
    }
}
