# Android agent — remote diagnostics (ScreenGuard 0.10.18)

The upstream server already supports **Agents → View logs** via its REST
`fetch_agent_logs` endpoint, which sends `fetch_logs` to the paired agent over
its existing authenticated WebSocket and waits for `log_response { "lines": [...] }`
for up to five seconds. Android responds using precisely that existing protocol.

## What is sent

A bounded app-private ring of **at most 50 lines**: the current boolean state
(tracking/service/Usage Access/Accessibility/restrictions/settings filter), plus
timestamped, **fixed-vocabulary** lifecycle events (connection, service, optional
accessibility, parent-notification receipt, lock, unpair, request). The ring
survives upgrades and restarts. The app never uploads raw logcat output.

There are deliberately **no** server URLs, connection bearer tokens, pairing
codes, PINs, children or user names, package names, browser history, network
details, Android system logs, notification contents, or arbitrary exception text.

## Safety and functional verification

1. Run tests, build, and install an Android development APK on a test tablet.
2. Pair, grant Usage Access and enable tracking; keep an active connection.
3. From the ScreenGuard server's Agents page, request the Android agent logs.
4. The reply should contain a health line and timestamped events within five
   seconds. Check that no secrets or child-specific data appear in the response.
5. Reboot and repeat: previously recorded events should remain (max 50).
6. Disconnect the agent: upstream returns HTTP 503 for offline agents, or
   HTTP 504 after the five-second response timeout; the Android client does not
   fabricate a successful response.

**Compatibility:** the existing v0.5.3 release is unchanged; this code is a
candidate for the next version. Linux-only commands such as `update_agent` are
**not** implemented on Android: a server-sent remote installer script must not
be executed on a child's device.
