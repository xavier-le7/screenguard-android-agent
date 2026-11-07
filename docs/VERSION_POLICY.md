# Version policy: Android agent and upstream ScreenGuard

## Why `android-0.5.3` caused a permanent update badge

ScreenGuard server v0.10.18 implements `release_check::is_older(agent_version, latest_version)`: it compares numeric `major.minor.patch` tuples; for non-numeric versions it falls back to **string inequality**. The older Android hello `android-0.5.3` therefore always showed *Update available*, even for an equal version.

## Policy

- The Android `agent_hello.agent_version` is **exactly the numeric Android `versionName`** (from generated `BuildConfig.VERSION_NAME`). No `android-` prefix is allowed.
- When the Android child agent offers **equivalent ScreenGuard v0.10.18 capabilities**, adopt Android `versionName = "0.10.18"`, increment Android `versionCode` above 18, and publish Git tag `v0.10.18`.
- Until required capabilities are implemented and tested, keep the actual version (currently v0.5.3) and accept the accurate update badge rather than misreporting parity.
- The server's feature-specific `capabilities` (e.g. `app_usage` and `web_filter`) must only be announced when the corresponding Android implementation is working.
- The upstream `update_agent` message executes a Linux installer and **must not be executed on Android**. Independent Android upgrade installation stays under user/parent control.

This preserves installed apps: keep `applicationId` and the existing signing certificate, and increment `versionCode` for every APK upgrade. Source/documentation changes can be merged without creating a new release.
