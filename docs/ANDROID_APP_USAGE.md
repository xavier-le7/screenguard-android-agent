# App runtime compatibility

Android reports foreground activity (not simultaneous background processes), while
the upstream Linux tracker reports concurrently running desktop applications.
Cumulative app and group totals are sent using the existing `app_usage_sync` wire
messages. The Android app ID is `desktop:android.<package>`, which is understood
by ScreenGuard 0.10.18's existing AppId parser.

Data stays device-local until encrypted WebSocket synchronization. App names and
categories are intentionally part of the standard ScreenGuard v0.10.18 per-app
usage reporting protocol. ScreenGuard doesn't receive browsing URLs or screen
contents. Existing screen-time accounting stays separate from per-app tracking.

The `app_usage` capability must only be advertised with active Android
Usage Access. No compatibility claim for `web_filter` is made until Android
VPN filtering is functional and tested on a device.
