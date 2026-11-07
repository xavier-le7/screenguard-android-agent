# Version numbers and server capability reporting

**Current official Android release: v0.10.18 (versionCode 21).** Target upstream ScreenGuard **server v0.10.18**. The application ID and original maintainer signing certificate are unchanged.

## Version check

Upstream ScreenGuard compares numeric semantic version components sent in `agent_hello.agent_version` with the latest version it knows. The historical `android-0.5.3` string caused an inaccurate "update available" indication. Android now reports its numeric `BuildConfig.VERSION_NAME` (currently `0.10.18`).

The **Android `versionCode`** is a separate, monotonically increasing integer required for APK upgrades. It was 18 in the official v0.5.3 release and is **21 in v0.10.18**, accommodating the earlier TEST builds. Rebuilding the same `versionCode` is not a valid upgrade path from a differently signed APK.

Align the numeric release with the supported upstream server protocol only when the implementation has been reviewed and tested. **Equal version numbers do not imply identical OS enforcement features.** Check the server image actually running, not only its configured `latest` tag or WebUI image.

## Capabilities vs. live state

- `app_usage` is advertised when the Android app usage reporting integration is configured. Usage Access still needs to be granted, and the UI/server must be checked for actual data.
- `web_filter` is advertised **only when** the parent opted in and the DNS VPN reports itself running. Android sends an updated hello after reconnecting on VPN start/stop.
- Server **`web_filter_available` is a capability/availability badge**, not a distinct, timestamped DNS-active status. Offline indicators can be stale.
- An active DNS VPN provides only partial DNS filtering; DoH, DoT, IPv6 DNS and other bypasses remain documented limitations.

For a new release, update `versionName` and increment `versionCode`, document the capabilities actually present, run Android CI and device tests, then create the corresponding `vX.Y.Z` tag **on `main`**. See [Releasing](RELEASING.md).
