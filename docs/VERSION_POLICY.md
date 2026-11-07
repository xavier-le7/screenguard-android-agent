# Version policy

Upstream ScreenGuard v0.10.18 compares the three numeric semver components
sent in `agent_hello.agent_version` against the latest server-agent release.
The earlier Android prefix `android-0.5.3` never parsed as semver and triggered
an inaccurate "Update available" badge.

Android now sends `BuildConfig.VERSION_NAME` as an unprefixed numeric semver.
This feature branch targets **0.10.18 / Android versionCode 19**. The signed
production release remains v0.5.3 until real-device validation; do **not**
publish the new version or Git tag until the acceptance tests pass.

The `app_usage` capability indicates real Android per-app accounting.
`web_filter` is **not** advertised: the experimental DNS VPN does not block
all DoH/DoT, IPv6 DNS or VPN bypasses. Feature-specific capabilities should
describe actual functionality, not be faked to suppress a warning.
