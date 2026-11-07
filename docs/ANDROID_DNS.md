# Experimental DNS filtering on Android — v0.10.18

**Important:** this feature is an **opt-in DNS-only Android VPN**, not a network-wide or bypass-resistant website filter. It does **not** protect against all browser-specific DNS-over-HTTPS (DoH), DNS-over-TLS (DoT), private DNS, IPv6 DNS, other VPNs, split-DNS configurations or caches. Do not present it as equivalent to the Linux ScreenGuard network filter.

## Architecture

The app uses Android `VpnService`, after the parent authorizes the VPN through Android's consent dialog. It creates a synthetic local DNS endpoint and routes requests for that address through a VPN TUN interface. **It does not route general Internet traffic through the VPN.**

The requested domain is compared against the server's `blocked_domains` list from `config_push`:
- Exact blocked domains and subdomains return DNS **NXDOMAIN**.
- Other supported DNS queries are forwarded over protected UDP/53 to a resolver on the physical (non-VPN) network; failure can produce **SERVFAIL**.
- DNS queries, resolved domains and full URLs are **not persisted or sent to the ScreenGuard server**.

DNS features depend on Android's resolver/VPN behavior. The current packet handling is intentionally limited to supported DNS packets: DoT, DoH, some IPv6 queries, DNS-over-TCP/EDNS and vendor-specific behavior are incomplete. An unreachable upstream DNS server can disrupt normal name resolution while VPN mode is active.

## Explicit parent controls

In Android's parent settings, select **Activer le filtrage DNS** (Enable DNS filtering) or **Désactiver le filtrage DNS** (Disable DNS filtering). Both actions use the ScreenGuard six-digit parent PIN. Activation also requires Android VPN approval.

The app's main health panel and setup area show:
- **ACTIVE** — parent opted in and the VPN service reports running.
- **STOPPING** — VPN still running after the parent selected OFF.
- **PENDING** — opted in, but the VPN service is not currently running.
- **DISABLED** — parent opted out and service stopped.

An explicit OFF action saves opt-in=false **before** requesting service shutdown, closes the TUN descriptor and stops the service. The user should check the VPN status icon disappears. It must not restart merely because the tablet reboots.

If the parent leaves filtering **enabled**, the boot and APK-replacement receiver attempts to restart it when Android still grants VPN permission. This restart is best-effort: OEM process restrictions and loss of authorization can prevent it. On permission revocation, the service stops and clears opt-in.

## Server reporting

ScreenGuard server **v0.10.18 does not expose a native `dns_active` field**. It exposes `web_filter_available` from `agent_hello.capabilities`.

Android reports `web_filter` only while the parent opted in **and** the DNS VPN service says it is running. On start, stop or revocation, the Android tracking agent reconnects so the server re-evaluates that badge. This badge is an **availability/status approximation**, not a promise that encrypted DNS bypasses are blocked; it may remain stale while an agent is disconnected.

The server can request [remote diagnostics](ANDROID_REMOTE_LOGS.md) to see the latest application-reported `dns_running`, `opt_in`, `private_dns`, `packets`, `dns_queries`, `blocked`, `forwarded`, `replied`, `upstream_fail`, `unhandled`, `io_errors` and `last_error`. These are aggregate counters/statuses; they do **not** reveal DNS query strings.

## Parent test checklist

1. Enable filtering, approve Android's VPN consent, and confirm **ACTIVE** locally plus an Android VPN notification/icon.
2. Block a test domain through the ScreenGuard server; on the tablet, verify ordinary DNS resolution for it fails and an unrelated domain still resolves.
3. From the server, fetch logs and compare `dns_queries`, `blocked` and `forwarded` before/after the test.
4. Disable filtering with the parent PIN; confirm **DISABLED**, loss of the VPN icon and restored resolution for the test domain.
5. While the device is online, refresh the server and verify its web-filter badge reflects the stop.
6. Reboot once with DNS OFF (it must stay off), then re-enable and reboot with DNS ON (best-effort auto-restart).
7. Test Android Private DNS and at least one browser with DoH: confirm limitations are disclosed rather than describing the filter as bypass-resistant.

Use Android VPN settings to revoke consent if app-level recovery fails. A VPN that cannot resolve allowed domains can temporarily impair connectivity.
