# Application usage, groups and approvals — Android v0.10.18

**Requires ScreenGuard server v0.10.18.** Server v0.10.17 does **not** implement the incoming `app_usage_sync` protocol, so an agent announcing 0.10.18 will still appear to report no applications to an older server.

## What is measured

Android `UsageStatsManager.queryEvents` reconstructs the **foreground application** while the display is interactive and unlocked. Only one foreground app accrues time in this model; it does not measure multiple simultaneously active desktop processes or app background work.

Each locally stored day has:
- Per-app cumulative time keyed to the Android package name.
- Per-group cumulative time for the matching ScreenGuard group.
- Independent whole-device usage from the pre-existing ScreenGuard usage ledger.

App IDs are mapped to the accepted upstream format `desktop:android.<package-name>`, for example `desktop:android.org.example.reader`. The app label and basic category such as Game or WebBrowser are included in server reports. The code does **not** upload a complete inventory of installed applications, only applications with collected usage.

To resolve foreground app labels and game categories on Android 11+, this sideloaded agent requests `QUERY_ALL_PACKAGES`, in addition to Android Usage Access. This is a sensitive Android/Google Play permission and would require a separate store-policy review before any Play distribution.

The app collector replays today's retained Android usage events on first use and after an interrupted session/boot where possible. Android may omit events or remove historical data: long gaps and manufacturer-specific behavior can reduce accuracy. Time in background or with screen locked is not counted.

## Wire protocol and idempotency

The agent sends `app_usage_sync.entries` with `local_uid`, `date`, `app_id`, `display_name`, `categories`, and cumulative `seconds_total`, together with cumulative `groups` totals. Resending a cumulative value is intended to be safe on reconnect: the server uses a maximum/upsert model rather than additive deltas. A normal WebSocket `send` only confirms the message was **queued** locally; it is not server acknowledgement.

Android still sends `usage_sync` separately for device screen-time quotas. It does not add that time again through `heartbeat`.

## App groups and enforcement

The agent reads `config_push` for the linked user (`local_uid 1000`), including:
- Rules for explicit app ID, prefix, category and exclusions.
- Weekday `daily_limits` and `adjustment_today`.
- The built-in Blocked group and new-app approval state.
- The server's blocked domains (used by the optional DNS VPN).

Group assignment prefers an explicit app-ID rule, then other included rules, while respecting exclusion rules. Local group time plus `remaining_update.group_usage_elsewhere` determines time remaining. A zero-minute group day and explicitly blocked/denied app are restricted even without prior usage. New applications can require approval; `app_request` is queued locally until transmission.

If an app is denied or its group quota is exhausted, ScreenGuard's Accessibility service presents an overlay. An exhausted group limit has a 10-second warning/grace; denial or approval-required state is immediate. Android cannot force-stop arbitrary apps the way a Linux agent can. This requires Accessibility permission and active restrictions, which Android users may revoke.

## Diagnostics and troubleshooting

1. Confirm the server **container** really runs v0.10.18; changing a `latest` tag does not automatically restart/recreate an existing container. The server and WebUI may be deployed from separate images.
2. Ensure **Usage Access** is granted and ScreenGuard tracking is running.
3. Open a regular application for a minute, then another; check server app usage after the normal synchronization interval.
4. From the server, select **View logs** on the agent. Inspect:
   - `apps_scan`: result of last collection scan.
   - `events`, `resumed`, `bootstrap`: bounded aggregate event counters.
   - `app_count`, `today_seconds`: local per-app accounting.
   - `sync_app_entries`, `sync_group_entries`, `sync_queued_at_ms`: messages queued by the agent, **not proof of server acceptance**.
5. If local app counts are nonzero and the server UI remains empty, check backend version, profile association and server-side reception rather than assuming Android failed to collect.

### Suggested regression tests

Check app totals against a stopwatch; verify offline recovery, midnight and reboot, grouped apps, daily zero-minute/other-device quotas, unknown-app approval after leaving the app, and that essential system functions remain reachable. For secure/diagnostic handling, see [Remote logs](ANDROID_REMOTE_LOGS.md).

**Known limits:** Usage Access can be revoked, Android may suppress events, split-screen/multi-window behavior is not guaranteed, and shared usage accrued elsewhere while offline is unknown until the next server update.
