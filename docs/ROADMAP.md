# ScreenGuard Android Agent — roadmap and current status

## Released in v0.10.18 (Android versionCode 21)

- [x] Public AGPL-3.0-or-later source, maintainer-signed Android APK and reproducible CI workflow.
- [x] Encrypted WSS pairing with a user-supplied server URL and a single logical user (`local_uid 1000`).
- [x] Android 14+ Usage Access accounting for interactive/unlocked screen time and locally persisted daily totals.
- [x] Cumulative `usage_sync` and zero-additive heartbeat for shared quota accounting.
- [x] Foreground per-app reporting and per-group cumulative usage using ScreenGuard 0.10.18 `app_usage_sync`.
- [x] App group rules, weekday limits, adjustments, shared usage from other agents and Accessibility overlay restrictions.
- [x] Persisted application approval requests (`app_request`) and parent decisions from `config_push`.
- [x] Optional parent PIN, 60-second block test, 15-minute sensitive Settings authorization and legacy Device Administrator.
- [x] Parent-to-child one-way notifications, when online.
- [x] Server-initiated sanitized `fetch_logs` / `log_response` health and network/app counters.
- [x] Notification for `update_agent` (links to official Android APK; no silent installer).
- [x] Optional parent-enabled DNS-only `VpnService` applying `blocked_domains`.
- [x] Separate parent-protected DNS ON and OFF controls; actual local activation display and server badge refresh.
- [x] Best-effort recovery after network interruption, device boot and APK update if DNS was left enabled.
- [x] Official v0.10.18 GitHub Release and GHCR archive containing its signed APK.

## Work still needed

- [ ] Bypass-resistant web filtering across DoH/DoT, browser-specific DNS, IPv6 DNS and conflicting VPNs; do not conflate today's DNS-only VPN with full content filtering.
- [ ] Exhaustive multi-OEM testing of VPN start/stop, battery restrictions, reboot persistence, blocked-domain resolution and network failures.
- [ ] Validate usage accuracy across long Doze, offline periods, reboot, clock changes, midnight and DST transitions.
- [ ] Regression testing for app group daily limits, priority/exclusion rules, all-day block, other-device consumption and approval state changes.
- [ ] Improve Android diagnostics and provide a reliable, timestamped server-side DNS status in cooperation with upstream (the present server has only `web_filter_available`).
- [ ] Verify Android settings overlays/15-minute grace and parental recovery across additional vendors; Accessibility can be revoked.
- [ ] Verify the update path from all previously signed builds, including user-data preservation and Accessibility reauthorization.
- [ ] Move all newer inline UI strings to Android resources and complete/verify the six translations.
- [ ] Review Accessibility disclosure, `QUERY_ALL_PACKAGES`, VPN permission and foreground service declarations for app-store policies.
- [ ] Independent security/privacy assessment and exact dependency/third-party license audit.

## Architecture and non-goals

This is an **independent** Android companion, not a fork of ScreenGuard's Linux service. It uses upstream's existing v0.10.18 WebSocket messages; it does not patch the server or database. Android cannot terminate arbitrary packages or guarantee that a determined device user cannot disable Accessibility/VPN. Neither root nor mandatory Device Owner is required.

Keep all public documentation free of real child names, local/private URLs, identifiers and credentials. Contributions are welcome via [CONTRIBUTING.md](../CONTRIBUTING.md).
