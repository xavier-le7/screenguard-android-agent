# ScreenGuard Android Agent

**Independent, open-source Android child agent for the self-hosted [ScreenGuard server](https://github.com/adambie/screenguard).** This repository is not the official upstream Android application and is not endorsed by the upstream maintainers.

[![Android CI](https://github.com/xavier-le7/screenguard-android-agent/actions/workflows/android-ci.yml/badge.svg)](https://github.com/xavier-le7/screenguard-android-agent/actions/workflows/android-ci.yml)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue.svg)](LICENSE)

**Current stable release:** **[v0.10.18](https://github.com/xavier-le7/screenguard-android-agent/releases/tag/v0.10.18)** (Android `versionCode 21`), for ScreenGuard **server v0.10.18**. Requires **Android 14 or later** (minSdk 34).

> **Important:** Parental controls are best-effort on Android. This agent is not a certified, tamper-proof product. Its optional DNS filter does **not** prevent DNS-over-HTTPS (DoH), DNS-over-TLS (DoT), IPv6 DNS or every VPN/browser bypass.

## Features in v0.10.18

| Feature | Behavior and limitations |
| --- | --- |
| Secure pairing | Configurable WSS `/ws` endpoint, pairing code, local bearer-token encryption with Android Keystore |
| Screen time | Unlocked, interactive device time, offline daily totals and idempotent `usage_sync` shared-quota synchronization |
| App usage | Foreground time per Android app; app names, categories and cumulative `app_usage_sync` sent to ScreenGuard 0.10.18 |
| App groups | Weekday limits, parent adjustments, usage from other linked devices and Accessibility overlays for blocked/exhausted groups |
| New-app approvals | Request parent approval using `app_request`; persist requests while offline |
| Parental controls | Optional six-digit parent PIN, protected settings, 15-minute settings authorization and 60-second local blocking test |
| Parent messages | One-way `notify_user` notifications when connected |
| Remote logs | Respond to server `fetch_logs` with bounded, application-owned health/events and aggregate app/DNS diagnostics; **no raw logcat** |
| Server update request | `update_agent` prompts a parent to open official Android Releases; no unattended APK installation |
| Website domains | **Experimental, opt-in DNS-only VPN** applying server `blocked_domains` to normal DNS lookups, with separate parent-protected ON/OFF buttons |
| DNS state | Local ACTIVE / STOPPING / PENDING / DISABLED indicators; when online, server's `web_filter_available` badge reflects an actively running, opted-in DNS VPN |
| Recovery | Best-effort background reconnection and boot/APK-upgrade restart of opted-in services |

The project has six locale resource sets (French, English, Spanish, German, Portuguese, Polish). Some recently added interface labels are still implemented as inline text and need full localization; contributions are welcome.

## Install and pair

1. Install the **[maintainer-signed v0.10.18 APK](https://github.com/xavier-le7/screenguard-android-agent/releases/download/v0.10.18/screenguard-android-agent-v0.10.18.apk)**. Optionally verify the adjacent [SHA-256 file](https://github.com/xavier-le7/screenguard-android-agent/releases/download/v0.10.18/screenguard-android-agent-v0.10.18.apk.sha256).
2. Use a reachable, trusted **ScreenGuard server v0.10.18 or later**. Enter your own `wss://your-server.example/ws` URL and the child's profile display name; **no server address is preconfigured**.
3. Complete the pairing flow and associate the agent's logical user with the desired ScreenGuard profile.
4. Grant Android **Usage Access** and notifications, then start tracking. Check the device/agent is online and that daily screen time appears in the server.
5. For app blocking, enable the ScreenGuard **Accessibility** service and the restrictions, and set a six-digit parent PIN. Android may ask you to authorize restricted settings for sideloaded APKs.
6. For optional web domains, go to the parent's settings, choose **Enable DNS filtering**, enter the parent PIN, and approve Android's VPN consent dialog. Verify the DNS status is **ACTIVE**. **Disable DNS filtering** is a separate parent-protected action.
7. Test the app group, approvals and domain rules with a nonessential application or test domain. Verify normal connectivity, calls and access to essential system settings.

Android vendors may require exempting ScreenGuard from battery optimization to keep foreground tracking reliable. The DNS VPN can conflict with other VPNs and may fail to resolve DNS if its upstream network is unavailable.

**After a restart:** if the parent left DNS filtering **enabled**, the agent attempts to restore it (subject to Android VPN authorization and OEM background restrictions). If the parent explicitly **disabled** it, the preference is retained and the filter should not restart.

For permission details, troubleshooting and a test checklist, see [Android setup](docs/PHASE3.md), [App usage](docs/ANDROID_APP_USAGE.md), [DNS filtering](docs/ANDROID_DNS.md), and [Remote diagnostics](docs/ANDROID_REMOTE_LOGS.md).

## Android / server compatibility

The agent reports `agent_version = 0.10.18` rather than the older `android-0.5.3` string; this allows ScreenGuard's numeric version comparison to work. This does **not** mean Android and Linux have identical enforcement capabilities.

The app-usage/group protocol requires server **0.10.18**. If the server is still on 0.10.17, application usage will not be accepted even if the Android agent displays v0.10.18. If using the `latest` image in TrueNAS, **recreate/update the container** to run the new image: the tag by itself does not update a running container. The UI and server images are separate.

The server exposes **`web_filter_available`**, not a separate `dns_active` field. Our agent refreshes this badge at VPN start/stop by reconnecting; an offline agent's last-known badge may be stale. See [Version and capability policy](docs/VERSION_POLICY.md).

## Updates

Install the next **higher-`versionCode`** APK signed by the **same maintainer certificate** over the existing installation. **Do not uninstall first:** removal deletes private pairing and usage state. The official v0.10.18 APK uses `applicationId fr.sevenskies.screenguard.agent` and `versionCode 21`. CI debug builds signed by a different key do not necessarily install as upgrades.

- **[GitHub Releases](https://github.com/xavier-le7/screenguard-android-agent/releases):** normal Android installation, with APK and SHA-256 checksum.
- **[GitHub Packages (GHCR)](https://github.com/xavier-le7/screenguard-android-agent/pkgs/container/screenguard-android-agent-apk):** a *non-runnable* OCI archive holding the same APK, for automated distribution; not an Android installer and not a Linux container.
- Official APKs are built and signed by a trusted **tag-triggered** GitHub Actions workflow after tests. Community pull-request checks receive **no signing secrets**.

See [Signing](docs/SIGNING.md) and [Release procedure](docs/RELEASING.md).

## Build from source

Prerequisites: JDK 17, Gradle 8.9, Android SDK API 35 and the SDK build-tools.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

## Permissions, privacy and safety

| Android capability | Used for |
| --- | --- |
| Usage Access | Whole-screen and foreground-app time accounting |
| `QUERY_ALL_PACKAGES` | Resolve foreground app labels/game categories; **not** upload a full installed-app inventory |
| Accessibility (optional) | Present overlays for restricted apps and recognized sensitive Settings screens |
| VPN consent (optional) | Route queries addressed to the synthetic DNS endpoint through an app-local DNS filter |
| Foreground service, notifications | Long-running tracking/VPN and warnings/messages |
| Device Administrator (optional, legacy) | Add an OS deactivation step before uninstall, **not** Device Owner-grade protection |
| Internet | Communicate with the configured server and forward permitted DNS lookups |

The server receives per-app names, categories and accumulated time when app reporting is enabled. Remote logs contain bounded health/counter data, not browsing contents, child names, credentials or raw Android logs. The optional VPN retains no history of queried domains. See [Privacy](docs/PRIVACY.md) and [Security limitations](docs/SECURITY.md).

No root or mandatory Device Owner is required. Users can still revoke permissions or force-stop the app; protections are not bypass-proof. Android's network settings, parental recovery and emergency functions must remain accessible.

## Documentation and contributing

- [Setup, permissions and enforcement](docs/PHASE3.md)
- [App accounting, groups and approvals](docs/ANDROID_APP_USAGE.md)
- [Optional DNS-only VPN and its limitations](docs/ANDROID_DNS.md)
- [Server-triggered logs and diagnostics](docs/ANDROID_REMOTE_LOGS.md)
- [ScreenGuard 0.10.18 wire protocol](docs/PROTOCOL.md)
- [Version policy and capability reporting](docs/VERSION_POLICY.md)
- [Roadmap and known limitations](docs/ROADMAP.md)
- [Privacy](docs/PRIVACY.md) · [Security](docs/SECURITY.md) · [Dependencies](docs/DEPENDENCIES.md)
- [Contributing](CONTRIBUTING.md) · [Signing](docs/SIGNING.md) · [Releases](docs/RELEASING.md)

This repository is **public open source under [GNU AGPL-3.0-or-later](LICENSE)**. Contributions, issues, forks and coordination with the [original ScreenGuard project](https://github.com/adambie/screenguard) are welcome; no upstream affiliation is implied. Signing keys, private device logs and personal data must not appear in issues, commits or pull requests.
