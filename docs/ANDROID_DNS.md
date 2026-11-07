# Android DNS filtering — experimental

The native Linux ScreenGuard uses nftables with a DNS proxy; Android needs
an explicitly parent-authorized VpnService. This candidate uses a DNS-only
virtual tunnel, routing only packets to a synthetic DNS address. Ordinary
non-DNS Internet traffic remains outside the tunnel. The upstream
`blocked_domains` list is applied to requested domains and subdomains, which
receive NXDOMAIN. Ordinary queries go to a resolver on the physical network
via a protected UDP socket.

The app retains **no** DNS query history, packet contents, browsing URLs or
network destinations. The parent must authorize the VPN in the Android
settings dialog; the ScreenGuard parent PIN protects two **separate** controls:
"Activer le filtrage DNS" and "Désactiver le filtrage DNS".
Deactivation saves opt-in=false first, closes the VPN's TUN descriptor, and
prevents reboot/update auto-restart. The main health card and configuration
screen display ACTIVE, STOPPING, PENDING, or DISABLED independently of the
Android consent state. The opt-in preference is persisted for reboot/upgrade
recovery.

**Known limitations:** private DNS (DoT), browser-specific DoH, other VPNs,
IPv6 DNS, split DNS, EDNS/TCP fallback and OEM battery restrictions require
device-level validation. DNS-only protection is not equivalent to Linux's
per-UID DNS enforcement.

## Server status reporting (upstream v0.10.18)

Upstream only supports `web_filter_available` (its web-filter **capability**
badge). It has NO separate `web_filter_active`, VPN state, or DNS health
field. The Android client now sends `web_filter` in the initial authenticated
`agent_hello.capabilities` **only while its DNS VPN is running and the parent
has opted in**. On VPN start, stop and Android permission revocation, the
tracking service reconnects so the server re-evaluates the badge.

Therefore while the device is **online**, the server badge approximates the
real DNS VPN activation state, not a guarantee of comprehensive traffic
filtering. An offline device may retain its last capability badge. Accurate
local diagnostic counters remain available from server-initiated `fetch_logs`
(`dns_running`, `opt_in`, `blocked`, `forwarded`, errors). An exact
first-class active/inactive server status requires an upstream protocol/API/UI
change, not merely a new Android message.
