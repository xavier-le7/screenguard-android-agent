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

## On-device acceptance tests

1. Compare Android foreground app totals to observed usage and confirm the global screen-time ledger remains unchanged.
2. Verify two apps assigned to a group are not counted concurrently on a single-foreground Android screen, and verify combined quotas with another agent.
3. Start an unknown app, check the server receives an approval request even if the app is closed immediately, then approve it and verify access returns.
4. Verify blocked-group and exhausted per-app limits apply only to the app, never Android Settings, launchers or emergency apps.
5. Reboot, reconnect and verify cumulative totals never double-count.
6. Verify an upgrade from the signed v0.5.3, keeping the same app identity, signing key and paired token.

## App visibility permission

This sideloaded parental-control agent uses `QUERY_ALL_PACKAGES` for accurate
foreground app labels and `ApplicationInfo.CATEGORY_GAME` classification on
Android 11+. It does **not** upload an inventory of installed applications.
Only apps actually active while the unlocked screen is in use are reported,
using the upstream `app_usage_sync` fields. This permission is restricted by
Google Play policies if the app is ever distributed through the Play Store.
