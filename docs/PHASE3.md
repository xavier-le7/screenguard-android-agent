# Android setup, enforcement and field testing

This document covers optional app restrictions and device permissions. It is intended for any supported Android 14+ device running ScreenGuard Android Agent with an existing ScreenGuard v0.10.17 server.

## Setup

1. Install a trusted APK or build from source.
2. Configure your own WSS endpoint and a profile display name, then complete pairing in the upstream ScreenGuard parent interface.
3. Enable Android **Usage Access**, allow notifications for warnings and parent messages, and start screen-time tracking.
4. Enable the ScreenGuard Accessibility service if app blocking is needed. Android may require a separate acknowledgment of restricted settings for sideloaded apps.
5. Set a six-digit parent PIN and explicitly enable restrictions. The PIN must not be the same as the child's device unlock code.
6. Optionally enable protection of sensitive Settings screens and Device Administrator through their separate opt-in actions.
7. Verify that network configuration, emergency features, unrelated app installation/removal and parental recovery remain available.

Do not forcibly enable these permissions. Device Owner, root and factory reset are **not** required.

## Enforcement behavior

The app uses server `remaining_update` and `lock_now` decisions. A running Accessibility service draws a `TYPE_ACCESSIBILITY_OVERLAY` over restricted leisure apps during `lock`, with access to the home screen and essential system functions.

An offline agent retains recent per-day policy and subtracts locally observed usage. A newly started day without a fresh server decision is treated conservatively as unknown rather than using yesterday's policy. Concurrent usage on other devices is not known offline.

The **60-second blocking test** is available under Parent actions. It requires the parent PIN and does not change any server quota. It tests local overlay enforcement rather than server-side expiry.

## Selective Settings protection

An optional Accessibility overlay requests the parent PIN on recognized sensitive Settings pages. Successful authorization lasts **15 minutes**. Wi-Fi, Bluetooth and management of unrelated installed apps should remain available.

Settings navigation differs by Android version and manufacturer. Unknown screens may bypass the Accessibility overlay; this is **not** a security boundary guaranteed by the OS. Legacy Device Administrator adds an Android deactivation step before uninstalling but the user can deactivate it.

## Android background restrictions

A foreground `specialUse` service performs usage sampling and quota synchronization. Screen unlock, network recovery and app update/boot receivers attempt to resume tracking. Android vendors can still stop background work despite a foreground service.

An Accessibility fallback may continue writing to the same local usage ledger while the primary service is interrupted, but **cannot synchronize until a network service is running**.

If the permission for Usage Access is revoked, the app cannot accurately measure the missing time. It must not invent usage.

## Suggested verification matrix

| Scenario | Expected result |
| --- | --- |
| Device online and in use | Local used time grows; latest server quota updates |
| Display off or locked | Local active-time counter stays unchanged |
| Brief network loss, reconnect | Missing local totals are merged once without double charging |
| Quota exhausted on another linked device | Server restriction is reflected after synchronization |
| Parent grants extra allowance | Restriction is removed after the server update |
| Parent sends a message | Notification contains the server-supplied title and body |
| Sensitive Settings page opened | Optional PIN overlay appears when enabled and no grace period is active |
| Wi-Fi changed or unrelated app removed | No Settings PIN prompt |
| Permissions revoked, app force-stopped or rebooted | Diagnostics report lost coverage; automatic recovery is best-effort |
| Local blocking test | Overlay appears for 60 seconds without changing the server allowance |

Use the on-device Advanced diagnostics and, when needed, Android `adb logcat`. Never publish bearer tokens, real user names, private server addresses or children's messages in public diagnostic reports.

This client does not modify upstream server data structures, endpoints, Linux agents or quota rules.
