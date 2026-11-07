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
settings dialog; the ScreenGuard parent PIN protects the enable/disable
action. The opt-in preference is persisted for reboot/upgrade recovery.

**Known limitations:** private DNS (DoT), browser-specific DoH, other VPNs,
IPv6 DNS, split DNS, EDNS/TCP fallback and OEM battery restrictions require
device-level validation. DNS-only protection is not equivalent to Linux's
per-UID DNS enforcement. The `web_filter` capability remains **disabled**.
