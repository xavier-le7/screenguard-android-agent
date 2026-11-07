package fr.sevenskies.screenguard.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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
 * Opt-in DNS-only Android VPN. Routes only 10.44.0.1 (a synthetic DNS
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
    private lateinit var policy: AppPolicyStore

    override fun onCreate() {
        super.onCreate()
        policy = AppPolicyStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START && intent != null) return START_NOT_STICKY
        if (prepare(this) != null) {
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

        val vpn = Builder().setSession("ScreenGuard DNS").setMtu(1500)
            .addAddress("10.44.0.2", 32)
            .addDnsServer("10.44.0.1").addRoute("10.44.0.1", 32)
            .setConfigureIntent(open).establish()
        if (vpn == null) {
            Log.w(TAG, "Unable to establish DNS VPN")
            stopSelf()
            return
        }
        descriptor = vpn
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
                if (count <= 0) break
                val request = DnsPacket.parse(bytes, count) ?: continue
                val response = if (DomainFilter.blocked(request.domain, policy.blockedDomains())) {
                    DnsPacket.errorDns(request.dns, 3) // NXDOMAIN
                } else {
                    forward(request.dns) ?: DnsPacket.errorDns(request.dns, 2) // SERVFAIL
                }
                DnsPacket.response(request, response)?.let { output.write(it) }
            }
        } catch (e: Exception) {
            if (enabled) Log.w(TAG, "DNS tunnel stopped: " + e.javaClass.simpleName)
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
            } ?: return null
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
                buf.copyOf(packet.length)
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "DNS upstream unavailable: " + e.javaClass.simpleName)
            null
        }
    }

    override fun onRevoke() {
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

    companion object {
        const val ACTION_START = "fr.sevenskies.screenguard.START_DNS_FILTER"
        const val ACTION_STOP = "fr.sevenskies.screenguard.STOP_DNS_FILTER"
        @Volatile var isRunning = false
            private set
        private const val TAG = "ScreenGuardDns"
        private const val CHANNEL_ID = "screen_guard_dns_filter"
        private const val NOTIFICATION_ID = 2204
    }
}
