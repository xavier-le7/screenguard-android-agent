# Android installation, permissions and enforcement — v0.10.18

For the **official ScreenGuard Android Agent v0.10.18**, Android 14+ and a self-hosted **ScreenGuard server v0.10.18**. The Android package is `fr.sevenskies.screenguard.agent`; the signed v0.10.18 release has `versionCode 21`.

## First installation

1. Download the maintainer-signed APK from [Releases](https://github.com/xavier-le7/screenguard-android-agent/releases/tag/v0.10.18). Install it without deleting a previous maintainer-signed installation.
2. Configure a trusted `wss://<your-server>/ws` endpoint and choose a profile display name. Pair through the existing ScreenGuard interface, then associate local user `1000` with the appropriate server profile.
3. Grant **Usage Access** and, for parental notifications, **notification permission**. Start the foreground tracking service.
4. Wait for the agent to connect; check whole-device time and **per-app usage** on the ScreenGuard v0.10.18 server. A v0.10.17 backend does not understand `app_usage_sync`.
5. If app restrictions are desired, enable Android's **ScreenGuard Accessibility** service, enable restrictions in the app, and configure a separate six-digit **parent PIN**. Sideloaded Android apps may require the parent to allow restricted settings explicitly.
6. Optionally enable Settings protection (15-minute parent authorization window), legacy Device Administrator and/or the 60-second local overlay test.
7. Optionally enable **DNS filtering** as described below.

For uninterrupted background tracking, vendor-specific battery optimization exemptions may be necessary. Android can still stop or disable services. Never depend on this app for emergency calling, network recovery or access to essential system functions.

## Screen-time and app limits

Screen time uses Android Usage Access events while the display is interactive and unlocked. Its daily cumulative ledger is **independent** of foreground per-app/group counters; heartbeat's additive active seconds stay zero. Per-app reporting uses the server v0.10.18 `app_usage_sync` protocol.

When policies are configured on the server, the Android agent caches `app_groups`, weekday limits and approval state. It computes current group time using its local totals plus the latest `group_usage_elsewhere` totals. Accessibility uses an overlay to block restricted apps, rather than terminating processes. A group quota reaching zero gives the child a **10-second warning/grace** before the overlay; a directly denied/new-unapproved app can be restricted immediately.

The initial app discovery and approvals can require the agent to synchronize while online; unknown apps can queue approval requests for later transmission. An offline agent cannot know usage newly accrued on another device. The local group/category mapping is an approximation of the desktop agent model.

**Quick acceptance test:** open a regular app for a minute and check that it appears in the server per-app usage list; create a test group, allow a short interval, and confirm the overlay. Test a previously unapproved app, approve it on the server and verify that its restrictions update after synchronization. Do not test with essential Settings, dialer or emergency apps.

## DNS filtering: explicit parent controls

The DNS filter is **optional and experimental**, not a full-browser content filter.

1. Open the ScreenGuard Android parent settings.
2. Select **Activer le filtrage DNS** / **Enable DNS filtering**, enter the parent PIN and grant Android's **VPN consent**.
3. Verify the main health indicator and settings show **ACTIVE**. The VPN status icon/notification should be visible.
4. Configure a test domain in the ScreenGuard server's blocked-domains policy. Verify that a normal DNS lookup for that domain fails; verify an unrelated domain still resolves.
5. Select **Désactiver le filtrage DNS** / **Disable DNS filtering** and enter the PIN. The service records the parent's explicit OFF choice and closes the VPN interface; confirm the status becomes **DISABLED**, the VPN icon disappears and the filtered domain resolves normally again.
6. If the parent leaves DNS **ON**, the app attempts to restore it after boot or APK upgrade (if Android retains VPN consent and permits service start). If the parent selects **OFF**, the preference is persisted and the app should **not** reactivate it on reboot.

Possible displayed states: **ACTIVE** (parent opted in and VPN running), **STOPPING** (VPN still running although parent opted out), **PENDING** (opted in but VPN not currently running), **DISABLED** (off). A parent should be able to request stop even if the service's last in-memory state is stale.

**Limitations:** DNS-over-HTTPS, DNS-over-TLS, private DNS, some IPv6 paths, third-party VPNs, DNS caches and certain browser resolvers can bypass this DNS-only solution. If the underlying resolver fails, ordinary Internet access may be disrupted while the tunnel is active. See [DNS architecture and status](ANDROID_DNS.md).

## What the server displays

The server v0.10.18 exposes `web_filter_available` from the agent's capabilities, **not** a dedicated, continuously updated `dns_active` field. Android advertises `web_filter` on its authenticated WebSocket hello only while the opted-in DNS VPN is running, and reconnects to refresh the badge at start/stop/revocation. The server badge can therefore be stale if the device is offline; it also does **not** indicate protection from all encrypted DNS bypasses.

For the actual local state and counters, ask the ScreenGuard server to **View logs / fetch_logs** while the agent is connected. Look for `dns_running`, `opt_in`, `dns_queries`, `blocked`, `forwarded` and `upstream_fail`. Per-app collection diagnostics include `apps_scan`, `app_count` and `sync_app_entries`. See [Remote logs](ANDROID_REMOTE_LOGS.md).

## Updating or recovering the agent

- Install a **higher-`versionCode`** APK with the **same signing certificate** over the existing app. Never uninstall before an intended upgrade: pairing and local data would be deleted.
- A server `update_agent` command triggers an Android notification/link to official Releases; it does **not** silently install an APK or execute Linux scripts.
- After updating, check tracking, notifications, Usage Access, Accessibility, VPN authorization and background-start behavior. Android/OEMs may require accessibility reauthorization.
- If the VPN prevents DNS resolution, use the parent PIN to **disable DNS**, verify the Android VPN is stopped, then inspect logs. If necessary, Android's own VPN settings can revoke the VPN authorization.

## Manual regression checklist

| Scenario | Expected behavior |
| --- | --- |
| Online screen-time use | Whole-device totals increase, no additive heartbeat double count |
| Foreground application | App appears in server 0.10.18 usage with cumulative seconds |
| App group exhausted | App overlay appears; other essential apps remain usable |
| New-app approval | Request reaches server, parent decision applied on refresh |
| Network loss/reconnect | Stored totals resynchronize without duplicating time |
| Parent message | One-way local notification when connected |
| Settings protection | Optional PIN gate with a 15-minute authorization window |
| DNS ON | ACTIVE locally; server badge refreshes while online |
| DNS OFF | VPN stops; DISABLED persists after reboot |
| DNS still opted in after reboot | Best-effort restart; Android/OEM restrictions may interfere |
| Server requests logs | Short, sanitized diagnostics; no raw logcat or identifying values |
| Server requests update | Notification to parent; no silent install |
| Existing signed install upgraded | Application data, pairing and signature remain consistent |

No Device Owner, root, server modification or uncontrolled Settings lockout is required.
