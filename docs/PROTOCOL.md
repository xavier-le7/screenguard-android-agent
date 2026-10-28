# ScreenGuard v0.10.17 protocol compatibility

The Android agent implements the **existing** WebSocket contract in [upstream ScreenGuard](https://github.com/adambie/screenguard/tree/v0.10.17). No new message types, server APIs, database changes or Linux agent changes are required.

## Pairing and logical identity

- Connect only to the user-configured secure WebSocket `wss://<server>/ws`, with standard TLS verification.
- Submit `pairing_request` and store the returned bearer token in Android Keystore-encrypted private preferences.
- Once authenticated, send `agent_hello` and `user_list_update`.
- A single Android user is represented by the protocol's stable logical `local_uid: 1000`. The profile display name is entered at pairing, not hard-coded.
- Administrators link that agent user to a profile in the existing ScreenGuard administration interface.

## Screen-time accounting

A `heartbeat` request contains the local UID with `active_seconds_since_last: 0`. This is intentional: upstream server accounting **adds** heartbeat deltas, whereas `usage_sync` reconciles cumulative daily totals using **MAX(existing, reported)**. Sending both active heartbeat time and usage snapshots would double-charge usage.

The agent sends `usage_sync.usage` records of the form:

```json
{"local_uid": 1000, "date": "2026-01-15", "used_seconds": 900}
```

The normal connection sequence is `agent_hello`, `user_list_update`, `usage_sync`, then zero-active-time `heartbeat`, repeating while online. Identical daily cumulative snapshots can be resent safely after reconnection. The server calculates the shared quota across agents mapped to the same profile.

Android measurement uses `UsageStatsManager.queryEvents`, counting only intervals when the display is interactive and unlocked. Daily totals are stored locally and split on the device's local calendar day, including time-zone transitions. Short disconnections can be reconstructed from retained events; Android process restarts and long gaps may lose precision. The device cannot know what other linked agents consume while it is offline.

## Incoming server commands

- `remaining_update`: latest allow/warn/lock decision and remaining time for the matching `local_uid`.
- `config_push`: warning thresholds and policy configuration.
- `lock_now`: immediate restriction for the matching local user.
- `config_reload`: refresh the connection/policy.
- `notify_user`: parent message containing `local_uid`, `summary`, and `body`; display as a local Android notification only when online. No reply message is sent.
- `unpair`: discard the local token.
- `pong`: transport response; a null payload is valid.

These names and fields are defined upstream; the Android implementation must not invent equivalent endpoints.

## Safety invariants

1. Never send additive active seconds in a heartbeat.
2. Never associate a received user event with another UID.
3. Keep encrypted tokens out of logs, notifications and diagnostics.
4. Keep fallback offline counters idempotent across reconnection.
5. Preserve the server's authority over quotas and schedules.
6. Do not assert that Android Accessibility/Device Administrator enforces absolute tamper resistance.
