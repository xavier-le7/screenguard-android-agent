# Privacy and data handling — Android v0.10.18

ScreenGuard Android Agent is an independent client for a self-hosted parental-control server. It has **no separate cloud service or embedded third-party analytics**. Communication goes to the parent-configured ScreenGuard server (plus DNS forwarding to an underlying-network resolver if the optional VPN is enabled).

## Data on the device

App-private storage includes:
- Generated agent identity, parent-supplied profile display name and an encrypted bearer token.
- Whole-device daily time, foreground-app and application-group daily cumulative time.
- Cached policies/approvals, other-device group usage, notification/health state and diagnostic event counts.
- Parent's optional DNS opt-in preference and **aggregate** DNS counters.

The bearer token uses Android Keystore-backed AES-GCM encryption. Android application backups are disabled; uninstalling usually deletes private data, including pairing. Android permissions (Usage Access, Accessibility, notifications, VPN, optional legacy Device Administrator) can be reviewed or revoked using Android Settings.

## Data sent to ScreenGuard server

Over the configured WSS connection, the agent sends its identity, paired local user and whole-device cumulative screen time. **In v0.10.18 it also sends per-application information** — Android package-based `app_id`, display name, categories and accumulated foreground seconds — plus cumulative app-group seconds. New-app approval requests can also include app identity/name/category.

The server processes and retains these values according to its own configuration, rules and access controls; this Android project does not administer server-side retention. Parent messages are delivered to an online agent as Android notifications and are not sent back as replies.

When requested by the server, remote diagnostics transmit **bounded app-owned health, fixed-vocabulary events and aggregate counters**; they do not send raw logcat, URLs, typed text, child names, credentials, package names or parent-message text.

## Optional DNS filtering

If the parent explicitly enables the DNS VPN, the device examines requested **domain names locally** to match the server's `blocked_domains` policy. Permitted DNS requests are forwarded to a DNS resolver obtained from the underlying network; they therefore leave the device as network DNS queries. The Android app does **not** retain or transmit a DNS browsing/query history to ScreenGuard.

DNS filtering is **not** complete browsing privacy or bypass resistance: encrypted DNS, some IPv6 traffic, alternate VPNs and browser-specific resolvers can avoid it. The server receives an online availability badge via `web_filter_available`, not detailed domain records.

## User controls and caveats

The parent can enable/disable the optional VPN using a parent PIN; stopping it persists across reboot. Device/Android permission controls remain available and may override the app. Screen-time reports can be incomplete after permission removal or long offline periods.

This privacy description covers the Android **client**. The independently operated ScreenGuard server, device OEM, app distribution platform and network resolver have their own privacy/security properties. A source code repository or successful CI build is not a substitute for an independent privacy review.
