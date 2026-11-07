package fr.sevenskies.screenguard.network

import fr.sevenskies.screenguard.BuildConfig
import fr.sevenskies.screenguard.protocol.ScreenGuardProtocol
import fr.sevenskies.screenguard.storage.AgentStore
import fr.sevenskies.screenguard.storage.AppPolicyStore
import fr.sevenskies.screenguard.enforcement.AppGroupPolicy
import fr.sevenskies.screenguard.usage.AppUsageLedger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.os.SystemClock
import java.net.URI
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Compatible with upstream ScreenGuard v0.10.18 (backward-compatible messages).
 * Usage snapshots are cumulative (server MAX reconciliation); heartbeat
 * ALWAYS reports zero active seconds to prevent a separate additive charge.
 */
class ScreenGuardClient(
    private val store: AgentStore,
    private val events: Events,
    private val usageSnapshot: (() -> Map<String, Long>)? = null,
    private val appUsageSnapshot: (() -> AppUsageLedger.Snapshot)? = null,
    private val currentForeground: (() -> AppGroupPolicy.App?)? = null,
    private val policyStore: AppPolicyStore? = null
) {
    interface Events {
        fun status(text: String)
        fun paired()
        fun remaining(minutes: Int, enforce: String)
        fun policy(decision: ScreenGuardProtocol.RemainingDecision) {
            remaining(decision.minutes, decision.enforce)
        }
        fun warningThresholds(values: List<Int>) {}
        fun immediateLock() {}
        fun unpaired() {}
        /** Redacted agent-owned events for upstream fetch_logs; never Android system logcat. */
        fun diagnosticLogs(): List<String> = emptyList()
        /** A message from a parent, delivered only if this agent is online. */
        fun parentMessage(message: ScreenGuardProtocol.ParentMessage) {}
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Phase 1 no longer needs its workaround: pong payload:null is handled.
        // Transport pings reveal half-open sockets after Samsung Doze.
        .pingInterval(60, TimeUnit.SECONDS)
        .build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    @Volatile private var connected = false
    @Volatile private var lastResponseElapsed = 0L
    @Volatile private var lastAttemptElapsed = 0L
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null
    private var retry = 0
    private val requestedApps = mutableSetOf<String>()

    fun pair(url: String, child: String) {
        check(!closed)
        val endpoint = validateEndpoint(url)
        store.serverUrl = endpoint
        store.child = child
        val code = ScreenGuardProtocol.newPairingCode()
        events.status("Appairage demandé. Saisir dans ScreenGuard : $code (5 min)")
        resetSocket()

        val request = Request.Builder().url(endpoint).build()
        val hostname = hostname()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(ScreenGuardProtocol.pairingRequest(store.machineId, hostname, code))
                events.status("Code à saisir dans ScreenGuard : $code (5 min)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = ScreenGuardProtocol.parse(text)
                    if (message.type == "pairing_accepted") {
                        store.saveToken(message.requirePayload().getString("auth_token"))
                        events.status("Appairage accepté ; activer le suivi sur la tablette.")
                        webSocket.close(1000, "pairing accepted")
                        events.paired()
                    }
                }.onFailure { events.status("Erreur d'appairage : " + it.message) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) events.status("Appairage impossible : " + (response?.code ?: "") + " " + t.message)
            }
        })
        socket = ws
        scheduler.schedule({
            if (!closed && socket === ws && store.loadToken() == null) {
                ws.close(1000, "pairing timed out")
                events.status("Appairage expiré : recommencer pour générer un code.")
            }
        }, 300, TimeUnit.SECONDS)
    }

    fun connect() {
        if (!closed) scheduler.execute {
            if (!closed) {
                reconnectTask?.cancel(false)
                connectSocket()
            }
        }
    }

    /**
     * Called by the active foreground service on validated network recovery
     * or screen unlock. No new socket if we still receive server responses.
     * This is also safe while offline: the persisted usage total is NOT
     * deleted, and retransmitting usage_sync is idempotent.
     */
    fun refreshConnection() {
        if (closed) return
        scheduler.execute {
            if (closed || store.loadToken() == null) return@execute
            val current = socket
            if (connected && current != null) {
                val noReply = SystemClock.elapsedRealtime() - lastResponseElapsed > 90_000L
                if (!noReply) {
                    runCatching { sendQuotaSnapshot(current) }.onSuccess {
                        return@execute
                    }
                }
                // Stale connection: reconnect rather than enqueue indefinitely.
                resetSocket()
            }
            if (SystemClock.elapsedRealtime() - lastAttemptElapsed >= 10_000L) {
                reconnectTask?.cancel(false)
                connectSocket()
            }
        }
    }

    private fun connectSocket() {
        if (closed) return
        val token = store.loadToken()
        if (token.isNullOrBlank()) {
            events.status("Aucun jeton valide : appairer cette tablette.")
            return
        }
        val endpoint = validateEndpoint(store.serverUrl)
        resetSocket()
        lastAttemptElapsed = SystemClock.elapsedRealtime()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed || socket !== webSocket) {
                    webSocket.close(1000, "stale connection")
                    return
                }
                retry = 0
                requestedApps.clear()
                connected = true
                lastResponseElapsed = SystemClock.elapsedRealtime()
                webSocket.send(ScreenGuardProtocol.agentHello(
                    store.machineId, hostname(), TimeZone.getDefault().id,
                    BuildConfig.VERSION_NAME,
                    if (appUsageSnapshot != null) listOf("app_usage") else emptyList()
                ))
                webSocket.send(ScreenGuardProtocol.userListUpdate(store.child))
                sendQuotaSnapshot(webSocket)
                events.status("Serveur connecté — synchronisation active.")
                heartbeatTask?.cancel(false)
                heartbeatTask = scheduler.scheduleAtFixedRate({
                    if (!closed && socket === webSocket) {
                        val elapsed = SystemClock.elapsedRealtime() - lastResponseElapsed
                        if (elapsed > 125_000L) {
                            events.status("Serveur sans réponse : reconnexion en cours.")
                            webSocket.cancel()
                        } else {
                            runCatching { sendQuotaSnapshot(webSocket) }
                                .onFailure {
                                    events.status("Synchronisation : " + it.message)
                                    webSocket.cancel()
                                }
                        }
                    }
                }, 30, 30, TimeUnit.SECONDS)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed || socket !== webSocket) return
                runCatching {
                    val message = ScreenGuardProtocol.parse(text)
                    when (message.type) {
                        "pong" -> Unit // Transport still alive; not proof quota was updated.
                        "remaining_update" -> {
                            policyStore?.applyRemaining(message)
                            val value = ScreenGuardProtocol.remainingForLocalUser(message)
                            if (value != null) {
                                lastResponseElapsed = SystemClock.elapsedRealtime()
                                events.policy(value)
                            } else {
                                events.status("Connecté : quota indisponible pour UID " +
                                    ScreenGuardProtocol.LOCAL_UID)
                            }
                        }
                        "config_push" -> {
                            policyStore?.applyConfig(message)
                            requestedApps.clear()
                            ScreenGuardProtocol.warningThresholds(message)?.let {
                                events.warningThresholds(it)
                            }
                        }
                        "fetch_logs" -> {
                            // This socket is authenticated with the paired agent token.
                            // No arbitrary file reads, system logcat or remote commands.
                            val lines = events.diagnosticLogs()
                            check(webSocket.send(ScreenGuardProtocol.logResponse(lines))) {
                                "Unable to queue log_response"
                            }
                        }
                        "config_reload" -> {
                            webSocket.close(1000, "reload configuration")
                        }
                        "lock_now" -> {
                            if (message.requirePayload()
                                    .optInt("local_uid", -1) == ScreenGuardProtocol.LOCAL_UID) {
                                events.immediateLock()
                            }
                        }
                        "unpair" -> {
                            policyStore?.clear()
                            store.clearToken()
                            events.unpaired()
                            events.status("Agent dissocié par le serveur.")
                            resetSocket()
                        }
                        "notify_user" -> {
                            ScreenGuardProtocol.parentMessageForLocalUser(message)?.let {
                                events.parentMessage(it)
                            }
                        }
                        else -> Unit
                    }
                }.onFailure { events.status("Message non reconnu : " + it.message) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket && !closed) {
                    connected = false
                    events.status("Hors ligne — utilisation enregistrée localement.")
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket && !closed) {
                    connected = false
                    events.status("Connexion interrompue — reconnexion automatique.")
                    scheduleReconnect()
                }
            }
        })
        socket = ws
    }

    private fun sendQuotaSnapshot(ws: WebSocket) {
        // The server MAX-merges these daily totals: resending is idempotent.
        val snapshot = usageSnapshot?.invoke().orEmpty()
        if (snapshot.isNotEmpty()) {
            if (!ws.send(ScreenGuardProtocol.usageSync(snapshot))) {
                throw IllegalStateException("usage_sync was not queued")
            }
        }
        appUsageSnapshot?.invoke()?.let { snapshot ->
            // Upstream uses MAX upserts; re-sending is safe after lost ACK/reconnect.
            // Chunk per 200 entries to avoid oversized WebSocket frames.
            val apps = snapshot.apps.map {
                ScreenGuardProtocol.AppUsage(it.date, it.app.id, it.app.displayName,
                    it.app.categories, it.seconds)
            }
            val groups = snapshot.groups.map {
                ScreenGuardProtocol.GroupUsage(it.date, it.groupId, it.seconds)
            }
            if (apps.isEmpty()) {
                if (groups.isNotEmpty() && !ws.send(ScreenGuardProtocol.appUsageSync(emptyList(), groups)))
                    throw IllegalStateException("group usage sync not queued")
            } else {
                apps.chunked(200).forEachIndexed { index, chunk ->
                    if (!ws.send(ScreenGuardProtocol.appUsageSync(chunk, if (index == 0) groups else emptyList())))
                        throw IllegalStateException("app usage sync not queued")
                }
            }
            val config = policyStore?.config()
            val foreground = currentForeground?.invoke()
            if (config != null && foreground != null && foreground.id.isNotBlank() &&
                !requestedApps.contains(foreground.id)) {
                val result = AppGroupPolicy.decide(config, foreground, emptyMap(), emptyMap())
                if (result.needsApproval &&
                    ws.send(ScreenGuardProtocol.appRequest(
                        foreground.id, foreground.displayName, foreground.categories))) {
                    requestedApps.add(foreground.id)
                }
            }
        }
        // Screen time is sent only through cumulative usage_sync. Never charge twice.
        if (!ws.send(ScreenGuardProtocol.quotaHeartbeat())) {
            throw IllegalStateException("heartbeat was not queued")
        }
    }

    private fun scheduleReconnect() {
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        if (closed || store.loadToken() == null) return
        reconnectTask?.cancel(false)
        val delaySeconds = minOf(300L, 5L shl minOf(retry++, 6))
        reconnectTask = scheduler.schedule({
            if (!closed) connectSocket()
        }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun hostname(): String = "Android-" + store.child

    private fun resetSocket() {
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        val previous = socket
        socket = null
        connected = false
        previous?.close(1000, "restart")
    }

    fun close() {
        if (closed) return
        closed = true
        reconnectTask?.cancel(false)
        resetSocket()
        scheduler.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        fun validateEndpoint(url: String): String {
            val cleaned = url.trim()
            val parsed = URI(cleaned)
            require(parsed.scheme == "wss" && parsed.host != null &&
                parsed.path == "/ws" && parsed.userInfo == null &&
                parsed.fragment == null && parsed.query == null) {
                "Adresse invalide : utiliser wss://hôte/ws."
            }
            return cleaned
        }
    }
}
