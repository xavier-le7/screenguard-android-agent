package fr.sevenskies.screenguard.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import fr.sevenskies.screenguard.i18n.ScreenGuardI18n
import fr.sevenskies.screenguard.MainActivity
import fr.sevenskies.screenguard.network.ScreenGuardClient
import fr.sevenskies.screenguard.storage.AgentStore
import fr.sevenskies.screenguard.storage.TrackingStatus
import fr.sevenskies.screenguard.usage.UsageLedger

/**
 * Explicitly user-started foreground screen-time monitor.
 * Uses Android's specialUse FGS rather than dataSync (Android 15 dataSync
 * has an execution-time cap). Samsung background restrictions may still
 * require manual "Unrestricted" battery settings.
 */
class TrackingService : Service(), ScreenGuardClient.Events {
    private fun loc(s: String): String = ScreenGuardI18n.text(this, s)
    private lateinit var store: AgentStore
    private lateinit var state: TrackingStatus
    private lateinit var ledger: UsageLedger
    private var client: ScreenGuardClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var receiverRegistered = false
    private var networkRegistered = false
    private lateinit var connectivity: ConnectivityManager
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!running) return
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    runCatching {
                        if (ledger.hasPermission() && ledger.collect()) {
                            state.lastCollectionMillis = System.currentTimeMillis()
                        }
                    }.onFailure { state.message = "Reprise écran : collecte indisponible" }
                    updateWarnings()
                    client?.refreshConnection()
                }
            }
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (running && capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                // Handle via main looper, not a binder/network callback thread.
                handler.post { if (running) client?.refreshConnection() }
            }
        }
    }
    private val sample = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                if (ledger.collect()) {
                    state.lastCollectionMillis = System.currentTimeMillis()
                    updateWarnings()
                } else {
                    state.message = "Collecte indisponible : vérifier l'autorisation d'utilisation."
                }
            } catch (t: Exception) {
                state.message = "Collecte locale indisponible : " + t.javaClass.simpleName
            }
            if (running) {
                ProtectionHealthReporter.check(this@TrackingService, state, ledger.hasPermission())
                handler.postDelayed(this, SAMPLE_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = AgentStore(this)
        state = TrackingStatus(this)
        ledger = UsageLedger(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            state.trackingEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (store.loadToken() == null || !ledger.hasPermission()) {
            state.message = "Appairage et accès aux statistiques requis."
            state.trackingEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            createNotificationChannel()
            startForeground(
                NOTIFICATION_ID, createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            running = true
            state.trackingEnabled = true
            state.serviceRunning = true
            state.message = "Suivi démarré. Connexion au serveur..."
            runCatching {
                if (ledger.collect()) {
                    state.lastCollectionMillis = System.currentTimeMillis()
                }
            }.onFailure {
                state.message = "Statistiques indisponibles : " + it.javaClass.simpleName
            }
            client = ScreenGuardClient(store, this) { ledger.usageSnapshot() }
            client?.connect()
            // Screen-unlock triggers a refresh even after prolonged Doze.
            registerReceiver(wakeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            connectivity = getSystemService(ConnectivityManager::class.java)
            runCatching {
                connectivity.registerDefaultNetworkCallback(networkCallback, handler)
                networkRegistered = true
            }.onFailure { state.message = "Réseau : rappel indisponible (" +
                it.javaClass.simpleName + ")" }
            handler.postDelayed(sample, SAMPLE_INTERVAL_MILLIS)
        }
        return START_STICKY
    }

    override fun status(text: String) {
        state.message = text
    }

    override fun remaining(minutes: Int, enforce: String) {
        state.remainingMinutes = minutes
        state.enforce = enforce
        state.lastServerUpdateMillis = System.currentTimeMillis()
        state.message = "Serveur actif — quota reçu."
    }

    override fun policy(decision: fr.sevenskies.screenguard.protocol.ScreenGuardProtocol.RemainingDecision) {
        state.updateFromServer(decision, ledger.todaySeconds())
        Log.i("ScreenGuardSync", "server policy=" + decision.enforce +
            " remaining_min=" + decision.minutes + " local_seconds=" + ledger.todaySeconds())
        state.message = "Serveur actif — décision : " + decision.enforce
        updateWarnings()
    }

    override fun warningThresholds(values: List<Int>) {
        state.setWarningThresholds(values)
    }

    override fun immediateLock() {
        state.updateFromServer(
            fr.sevenskies.screenguard.protocol.ScreenGuardProtocol.RemainingDecision(
                0, "lock", null
            ), ledger.todaySeconds()
        )
        state.message = "Verrouillage immédiat demandé par le serveur."
        updateWarnings()
    }

    override fun parentMessage(
        message: fr.sevenskies.screenguard.protocol.ScreenGuardProtocol.ParentMessage
    ) {
        handler.post {
            if (!running) return@post
            runCatching {
                val delivered = ParentMessageNotifier.show(this, message)
                state.message = if (delivered)
                    "Message parental reçu : notification affichée."
                else "Message parental reçu, mais les notifications Android sont désactivées."
                // Deliberately do not log, upload or save the actual message body.
            }.onFailure {
                state.message = "Message parental reçu : notifications indisponibles."
                Log.w("ScreenGuardMessage", "Notification unavailable", it)
            }
        }
    }

    override fun unpaired() {
        state.restrictionsEnabled = false
        state.trackingEnabled = false
        stopSelf()
    }

    override fun paired() { /* Pairing happens in the parent bootstrap UI. */ }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(sample)
        if (receiverRegistered) {
            unregisterReceiver(wakeReceiver)
            receiverRegistered = false
        }
        if (networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback)
            networkRegistered = false
        }
        client?.close()
        client = null
        state.serviceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Warn once per crossed threshold, not on every 30-second heartbeat. */
    private fun updateWarnings() {
        if (!state.restrictionsEnabled) return
        val result = state.policyAt(ledger.todaySeconds())
        if (result.mode != fr.sevenskies.screenguard.enforcement.EnforcementPolicy.Mode.WARN &&
            result.mode != fr.sevenskies.screenguard.enforcement.EnforcementPolicy.Mode.LOCK) return

        val crossed = when (result.mode) {
            fr.sevenskies.screenguard.enforcement.EnforcementPolicy.Mode.LOCK -> 0
            else -> state.warningThresholds()
                .filter { it > 0 && (result.secondsRemaining ?: Long.MAX_VALUE) <= it * 60L }
                .minOrNull() ?: 15
        }
        if (crossed >= state.lastWarnedThreshold) return
        state.lastWarnedThreshold = crossed

        val manager = getSystemService(NotificationManager::class.java)
        val warningChannel = "screen_guard_alerts"
        manager.createNotificationChannel(NotificationChannel(
            warningChannel, loc("Avertissements ScreenGuard"), NotificationManager.IMPORTANCE_HIGH
        ))
        val text = if (crossed == 0) {
            loc("Le temps autorisé est terminé : applications limitées.")
        } else {
            resources.getQuantityString(fr.sevenskies.screenguard.R.plurals.screen_time_remaining, crossed, crossed)
        }
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(2202, Notification.Builder(this, warningChannel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ScreenGuard — " + store.child)
            .setContentText(text)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build())
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, loc("Surveillance ScreenGuard"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = loc("Comptabilisation du temps d'écran et synchronisation des quotas.")
        })
    }

    private fun createNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(loc("ScreenGuard — comptage en cours"))
            .setContentText(loc("Le temps d'utilisation est suivi et synchronisé."))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "fr.sevenskies.screenguard.START_TRACKING"
        const val ACTION_STOP = "fr.sevenskies.screenguard.STOP_TRACKING"
        private const val CHANNEL_ID = "screen_guard_tracking"
        private const val NOTIFICATION_ID = 2201
        private const val SAMPLE_INTERVAL_MILLIS = 10_000L
    }
}
