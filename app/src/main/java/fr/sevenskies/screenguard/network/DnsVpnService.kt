package fr.sevenskies.screenguard.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import fr.sevenskies.screenguard.MainActivity
import fr.sevenskies.screenguard.storage.AppPolicyStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Opt-in DNS-only Android VPN. Routes only an app-local synthetic DNS
 * address), not general device traffic. Upstream DNS comes from a physical
 * non-VPN network and uses a protected socket to avoid recursive routing.
 * No queried domain is logged or persisted.
 *
 * Does NOT yet block DoH/DoT, IPv6 DNS or concurrent VPN bypasses; therefore
 * the Android agent does not advertise the upstream web_filter capability.
 */
class DnsVpnService : VpnService() {
    private var descriptor: ParcelFileDescriptor? = null
    private var enabled = false
    private val stats by lazy {
        getSharedPreferences(STATS_PREFS, MODE_PRIVATE)
    }

    private fun increment(key: String) = synchronized(STAT_LOCK) {
        stats.edit().putLong(key, stats.getLong(key, 0L) + 1L).apply()
    }

    private fun recordError(code: String) {
        stats.edit().putString("last_error", code).apply()
    }
    private lateinit var policy: AppPolicyStore

    override fun onCreate() {
        super.onCreate()
        policy = AppPolicyStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setOptedIn(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START && intent != null) return START_NOT_STICKY
        if (intent == null && !isOptedIn(this)) return START_NOT_STICKY
        if (prepare(this) != null) {
            recordError("vpn_consent_missing")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enabled) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "ScreenGuard — DNS", NotificationManager.IMPORTANCE_LOW
        ))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ScreenGuard — filtrage DNS")
            .setContentText("Filtrage des domaines par VPN local")
            .setContentIntent(open).setOngoing(true).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        // TUN descriptors are NON-BLOCKING by default. A blocking read loop
        // would otherwise see an empty buffer/EAGAIN and stop the VPN before
        // the first DNS packet arrived (observed on Samsung Android).
        val vpn = Builder().setSession("ScreenGuard DNS").setMtu(1500)
            .setBlocking(true)
            .addAddress(vpnAddress(2), 32)
            .addDnsServer(vpnAddress(1)).addRoute(vpnAddress(1), 32)
            .setConfigureIntent(open).establish()
        if (vpn == null) {
            recordError("establish_failed")
            Log.w(TAG, "Unable to establish DNS VPN")
            stopSelf()
            return
        }
        descriptor = vpn
        increment("started")
        recordError("none")
        setOptedIn(true)
        enabled = true
        isRunning = true
        Thread({ process(vpn) }, "ScreenGuardDnsTunnel").start()
    }

    private fun process(vpn: ParcelFileDescriptor) {
        try {
            val input = FileInputStream(vpn.fileDescriptor)
            val output = FileOutputStream(vpn.fileDescriptor)
            val bytes = ByteArray(2048)
            while (enabled) {
                val count = input.read(bytes)
                if (count < 0) break
                if (count == 0) {
                    // Defensive fallback in case an OEM ignores setBlocking.
                    Thread.sleep(25)
                    continue
                }
                increment("packets")
                val request = DnsPacket.parse(bytes, count)
                if (request == null) {
                    increment("unhandled")
                    continue
                }
                increment("dns_queries")
                val response = if (DomainFilter.blocked(request.domain, policy.blockedDomains())) {
                    increment("blocked")
                    DnsPacket.errorDns(request.dns, 3) // NXDOMAIN
                } else {
                    val upstream = forward(request.dns)
                    if (upstream == null) {
                        increment("upstream_fail")
                        DnsPacket.errorDns(request.dns, 2) // SERVFAIL
                    } else {
                        increment("forwarded")
                        upstream
                    }
                }
                DnsPacket.response(request, response)?.let {
                    output.write(it)
                    increment("replied")
                }
            }
        } catch (e: Exception) {
            if (enabled) {
                recordError("vpn_io_exception")
                increment("io_errors")
                Log.w(TAG, "DNS tunnel stopped: " + e.javaClass.simpleName)
            }
        } finally {
            enabled = false
            isRunning = false
            runCatching { vpn.close() }
            stopSelf()
        }
    }

    private fun forward(query: ByteArray): ByteArray? {
        val manager = getSystemService(ConnectivityManager::class.java)
        val networkAndDns = manager.allNetworks
            .filter { manager.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }
            .firstNotNullOfOrNull { network ->
                manager.getLinkProperties(network)?.dnsServers?.firstOrNull()
                    ?.let { network to it }
            } ?: run {
                recordError("no_underlying_dns")
                return null
            }
        val privateDnsActive = manager.getLinkProperties(networkAndDns.first)?.isPrivateDnsActive == true
        stats.edit().putBoolean("private_dns", privateDnsActive).apply()
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                networkAndDns.first.bindSocket(socket)
                socket.soTimeout = 1800
                val resolver: InetAddress = networkAndDns.second
                socket.send(DatagramPacket(query, query.size, resolver, 53))
                val buf = ByteArray(1500)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                // Reject mismatched DNS transaction IDs and spoofed replies.
                if (packet.length < 12 ||
                    buf[0] != query[0] || buf[1] != query[1]) {
                    recordError("dns_transaction_mismatch")
                    null
                } else buf.copyOf(packet.length)
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "DNS upstream unavailable: " + e.javaClass.simpleName)
            null
        }
    }

    override fun onRevoke() {
        setOptedIn(false)
        enabled = false
        isRunning = false
        runCatching { descriptor?.close() }
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        enabled = false
        isRunning = false
        runCatching { descriptor?.close() }
        descriptor = null
        super.onDestroy()
    }

    private fun vpnAddress(host: Int): String =
        intArrayOf(10, 44, 0, host).joinToString(".")

    private fun setOptedIn(value: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("enabled", value).commit()
    }

    companion object {
        private val STAT_LOCK = Any()
        private const val STATS_PREFS = "screen_guard_dns_stats"

        fun diagnosticSummary(context: Context): String {
            val p = context.getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
            val keys = listOf("started", "packets", "dns_queries", "blocked",
                "forwarded", "upstream_fail", "unhandled", "replied", "io_errors")
            return "dns_running=" + isRunning +
                " opt_in=" + isOptedIn(context) +
                " private_dns=" + p.getBoolean("private_dns", false) +
                " " + keys.joinToString(" ") { it + "=" + p.getLong(it, 0L) } +
                " last_error=" + (p.getString("last_error", "none") ?: "none")
        }

        private const val PREFS = "screen_guard_dns_optin"
        fun isOptedIn(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        const val ACTION_START = "fr.sevenskies.screenguard.START_DNS_FILTER"
        const val ACTION_STOP = "fr.sevenskies.screenguard.STOP_DNS_FILTER"
        @Volatile var isRunning = false
            private set
        private const val TAG = "ScreenGuardDns"
        private const val CHANNEL_ID = "screen_guard_dns_filter"
        private const val NOTIFICATION_ID = 2204
    }
}
