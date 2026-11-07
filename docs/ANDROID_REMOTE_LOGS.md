# Server-initiated remote diagnostics — Android v0.10.18

ScreenGuard server **v0.10.18** supports **Agents → View logs** through `fetch_agent_logs`. The server sends `fetch_logs` over the authenticated agent WebSocket and waits up to five seconds for `log_response {"lines":[...]}`. The Android agent responds using the **existing** upstream protocol; no server patch is required.

## What the response contains

A maximum of **50 short lines**, composed of:
- Current boolean health flags (tracking, Android Usage Access, Accessibility, restrictions and Settings protection).
- Fixed-vocabulary, timestamped application lifecycle events in a bounded app-private ring buffer (start/stop, connection, lock request, parent notification receipt, agent unpairing, accessibility start/stop, log request).
- Aggregate app collection counts (`apps_scan`, `events`, `resumed`, `app_count`, `today_seconds`).
- Aggregate WebSocket queue counts (`sync_app_entries`, `sync_group_entries`, `sync_queued_at_ms`).
- Local DNS VPN state and aggregate counters (`dns_running`, `opt_in`, `dns_queries`, `blocked`, `forwarded`, `upstream_fail`, `private_dns`, `last_error`).

The ring is app-private, bounded and persists through normal restarts/updates. No arbitrary files or Android system `logcat` are collected. Remote logs do **not** include child/profile names, server URLs, app package IDs, individual DNS domains, bearer tokens, pairing codes, parent PINs, parent-message content or raw exception messages.

The `sync_app_entries` count means the agent **queued** a WebSocket write. The upstream protocol has no per-message acknowledgement, so this is not proof that a server stored it.

## How to troubleshoot

1. Confirm the Android agent is **online** in ScreenGuard.
2. Request logs from its server agent page.
3. For app reports, compare `app_count`/`today_seconds` (local collection) with `sync_app_entries` (queued transmission) and the actual server per-app UI. If the server is running v0.10.17, it will not understand `app_usage_sync`.
4. For DNS, compare `dns_running` and `opt_in`. If `dns_queries` stays zero, inspect Android VPN permission and resolver behavior. If `upstream_fail` rises, inspect underlying network DNS reachability.
5. After disabling DNS, expect `dns_running=false`, `opt_in=false` and an updated server badge after an authenticated reconnection.

Offline agents cannot reply; the server may report an unavailable agent or time out after the response window. Counts are diagnostic, **not** a security audit trail or proof of continuous filtering.

**No Linux-style remote update execution:** the `update_agent` message is handled as a safe Android notification linking to the official Releases page. There is no silent APK installer.

If sharing logs publicly, still review them for any unexpected details. See [Privacy](PRIVACY.md), [DNS](ANDROID_DNS.md) and [Protocol](PROTOCOL.md).
