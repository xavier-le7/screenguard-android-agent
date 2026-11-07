# Publishing ScreenGuard Android Agent

The **current stable public release is [v0.10.18](https://github.com/xavier-le7/screenguard-android-agent/releases/tag/v0.10.18)** (`versionCode 21`). Normal Android distribution uses GitHub Releases; GitHub Packages is a separate, non-runnable OCI archive containing the same APK. Do not direct users to short-lived GitHub Actions artifacts for stable installation.

## Versioning and upgrade compatibility

- Android package: `fr.sevenskies.screenguard.agent`. **Do not change it** for official updates.
- Android `versionName` is numeric and matches the release tag, e.g. `0.10.18` → `v0.10.18`; it is also announced to the ScreenGuard server as `agent_version`.
- Android `versionCode` **must increase** for every new official upgrade: v0.5.3 used 18; v0.10.18 uses **21**. Do not reuse 21 for the next update.
- The maintainer must reuse the **same PKCS#12 signing certificate** so installed users can update without uninstalling or losing their pairing and private data.
- Don't claim full Linux feature parity merely because the Android and server release numbers match. Document Android-specific limitations, especially the DNS-only VPN.

## Signing secrets

The signing keystore is **not committed**. The official tag-triggered workflow reads two GitHub Actions repository secrets:

- `SG_ANDROID_KEYSTORE_B64`: base64 encoding of the original maintainer PKCS#12 keystore.
- `SG_ANDROID_KEYSTORE_PASSWORD`: password to that keystore.

Keep an **encrypted offline backup** of the original key and password, outside GitHub. Never print either secret in CI, issues, pull requests, chats or release notes. Forks need separate signing certificates and ideally distinct package IDs.

See [Signing](SIGNING.md) and [Security](SECURITY.md).

## CI and public distributions

The [Android CI workflow](../.github/workflows/android-ci.yml) runs these jobs:

1. **test** — privacy preflight, JVM unit tests and debug compilation for pull requests, ordinary main pushes and tags; no maintainer signing secrets on fork PRs.
2. **signed-release** — on an approved `vX.Y.Z` tag whose commit is included in `main`: restore the stable keystore, build debug/release APKs, verify signing identity, version and release non-debuggability; archive the signed release APK.
3. **publish-ghcr** — publish that signed APK as a non-runnable OCI archive to `ghcr.io/xavier-le7/screenguard-android-agent-apk:vX.Y.Z`.
4. **publish-release** — publish `screenguard-android-agent-vX.Y.Z.apk` and the matching `.apk.sha256` to GitHub Releases. Existing releases are not silently overwritten.

The [distribution smoke test](../.github/workflows/distribution-smoke.yml) validates **unauthenticated** downloads from Releases and GHCR and byte-identical APKs in both channels. The workflow currently checks v0.10.18; update its pinned target for each new stable release.

Protect the `main` branch and `v*` tags with repository rulesets, limit workflow and secret editing, and review changes before allowing production signing. A `latest` Docker tag is **not** itself an Android release/update mechanism.

## Maintainer release procedure

1. Merge reviewed code into `main` after unit, debug and actual Android-device testing (Usage Access, Accessibility, app quotas, approvals, DNS ON/OFF, reboot, notifications and server sync).
2. Update `app/build.gradle.kts`: increment `versionCode` and set a new numeric `versionName`.
3. Update README, protocol/version policy, setup/privacy/security docs and add `docs/releases/vX.Y.Z.md` with capabilities, limitations and migration notes.
4. Confirm no old release already uses that tag. Create the **reviewed, protected** `vX.Y.Z` tag on the intended `main` commit; don't retag or overwrite a previously distributed signed APK.
5. Verify **all four GitHub Actions jobs succeed**. The tag must match `versionName`.
6. Check the public GitHub Release assets and SHA-256, GHCR package, anonymous downloads and matching APKs (smoke test).
7. Upgrade an existing official signed installation **without uninstalling**. Confirm pairing, permissions, app usage, blocked groups, remote logs, DNS controls and boot persistence.

## Historical releases

- **v0.5.3** was the first public maintainer-signed release (`versionCode 18`), aligned with the earlier 0.10.17 server API.
- **v0.10.18** (`versionCode 21`) was released after testing the TEST3 Android candidate. The public APK is signed with the same certificate and uses ScreenGuard server 0.10.18's per-app protocol.
- Earlier temporary TEST1/TEST2/TEST3 workflow artifacts are **not public stable releases** and may expire; do not use them for future installation instructions.

The source is [GNU AGPL-3.0-or-later](../LICENSE), independent of the original [ScreenGuard](https://github.com/adambie/screenguard) project. External contributors are welcome.
