# ScreenGuard server v0.10.18 — Android wire protocol

The Android child agent implements the existing ScreenGuard [upstream v0.10.18](https://github.com/adambie/screenguard/tree/v0.10.18) WebSocket protocol. No custom server endpoint, schema, database migration or Linux-agent change is required. **Server v0.10.17 does not support `app_usage_sync`.**

## Transport, pairing and identity

Configure an HTTPS/TLS-trusted `wss://<server>/ws` URL. Pair using `pairing_request`; store the accepted bearer token encrypted using Android Keystore. On an authenticated connection:

- `agent_hello` contains `machine_id`, `hostname`, `timezone`, numeric `agent_version: "0.10.18"`, `last_config_version`, and `capabilities`.
- `user_list_update` maps one Android user to protocol `local_uid: 1000`; the supplied display name is not hard-coded.
- `capabilities` includes `app_usage` when the app collector is configured. It includes `web_filter` **only when the optional DNS VPN is running and the parent opted in**. The VPN's limited DNS coverage must not be confused with robust per-app web enforcement.
- The server stores `web_filter_available` from the initial hello; the agent reconnects when the DNS service changes state to update this badge. The protocol has no dedicated live `dns_active` field.

## Daily whole-device time

`usage_sync` provides daily cumulative values:

```json
{"type":"usage_sync","payload":{"usage":[{"local_uid":1000,"date":"2026-11-07","used_seconds":900}]}}
```

The agent sends `heartbeat.users[].active_seconds_since_last = 0`. The server **adds** heartbeat deltas, whereas `usage_sync` reconciles cumulative totals using a maximum. Sending both would double-count time. Screen time is locally measured from interactive, unlocked Android `UsageStatsManager.queryEvents`; retained events are reconstructed after limited interruptions, subject to Android availability.

## Per-app accounting and limits

On ScreenGuard v0.10.18, `app_usage_sync` carries cumulative daily application and group totals (simplified):

```json
{
  "type":"app_usage_sync",
  "payload":{
    "entries":[{"local_uid":1000,"date":"2026-11-07","app_id":"desktop:android.org.example.reader","display_name":"Reader","categories":[],"seconds_total":300}],
    "groups":[{"local_uid":1000,"date":"2026-11-07","group_id":"example-group","seconds_total":300}]
  }
}
```

Android app IDs use the accepted upstream `desktop:android.<package>` form. Only time in the foreground is tracked; Android cannot measure simultaneous desktop-style processes. Totals are cumulative and sent again on reconnect, rather than additive increments. An app may match a configured group via explicit ID, prefix or category rule.

- `config_push` includes `app_groups`, weekday `daily_limits`, `adjustment_today`, `app_approval` and `blocked_domains`. The agent stores matching local-user policy for offline enforcement.
- `remaining_update.users[].group_usage_elsewhere` supplies usage from other devices; it is combined with local totals to enforce shared limits.
- `app_request` asks the server to approve a newly used application. Requests are persisted locally to survive leaving the blocked app, and delivered when online.
- Android uses an Accessibility overlay for blocked/group-expired apps, rather than a Linux process kill.

## Incoming commands

| Server message | Android handling |
| --- | --- |
| `remaining_update` | Quota decision and shared group usage for the matching local UID |
| `config_push` | Thresholds, app groups, approvals and blocked domains |
| `config_reload` | Reconnect/reload |
| `lock_now` | Immediate local restrictions |
| `notify_user` | One-way parent notification, only for the matching local UID while online |
| `fetch_logs` | Reply with `log_response` containing up to 50 bounded, sanitized diagnostic lines |
| `update_agent` | Notify parent with a link to Android Releases; **never run a Linux installer** |
| `unpair` | Remove token and cached policy |
| `pong` | Accept a null JSON payload |

The normal connection also sends zero-additive `heartbeat`, cumulative `usage_sync` and `app_usage_sync` while online. The agent has no server-side acknowledgement for an individual app report: `sync_app_entries` in diagnostics indicates a **queued WebSocket send**, not proof that the server stored it.

## Safety constraints

Never log bearer tokens, pairing codes or child names; never associate events with another UID; do not send additive time in heartbeat; do not execute arbitrary server-side commands; preserve local/remote quota idempotency. The server controls policy and retention. Accessibility/legacy Device Administrator are not an OS security boundary.

See [App usage](ANDROID_APP_USAGE.md), [DNS](ANDROID_DNS.md) and [Remote logs](ANDROID_REMOTE_LOGS.md).
