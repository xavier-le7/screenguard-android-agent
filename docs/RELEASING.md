# Releasing ScreenGuard Android Agent

GitHub Releases are the public distribution channel for maintainer-signed APKs; workflow artifacts expire.

## What remains private

The signing keystore is **not part of the source**. Store its base64 representation in the GitHub Actions secret `SG_ANDROID_KEYSTORE_B64` and its password in `SG_ANDROID_KEYSTORE_PASSWORD`. Keep an offline encrypted backup of the original PKCS#12 keystore. Neither value should appear in issues, pull requests, logs, commits or documentation.

**Keep the same certificate** and `applicationId` used for previously installed maintainer APKs, so upgrades can preserve pairing and application data. A new signing key would prevent ordinary in-place upgrades.

## Public CI

Pull requests and mainline pushes run source privacy checks, JVM unit tests and debug compilation **without** signing secrets. The signed-release, GHCR package, and GitHub Release jobs normally run **only when a `vX.Y.Z` tag is pushed**, after those checks succeed. A one-time pinned `main` bootstrap is used to verify and publish v0.5.3; it cannot trigger on later commits. Before signing, the job verifies that the tagged commit is on `main` and that its version matches `versionName` in `app/build.gradle.kts`. The release APK and corresponding SHA-256 checksum are published together. Existing releases are never silently overwritten.

Protect the `main` branch and `v*` tags with repository rulesets and restrict direct write access. GitHub Actions secrets are not passed to fork pull-request jobs, but malicious changes merged into a trusted signing workflow could expose credentials, so **review workflow changes particularly carefully**.

## First public release: v0.5.3

The public v0.5.3 is produced from the reviewed public source snapshot using the **migrated original maintainer signing certificate**. Its APK SHA-256 can differ from the previously published private-repository build because build outputs are not necessarily reproducible byte-for-byte. This is not a change of package ID, signing certificate or Android versionCode. The public APK is verified as signed, non-debuggable, versionCode 18, and is accompanied by its **own calculated SHA-256 checksum**.

Do not uninstall or reinstall existing v0.5.3 installations to replace the build. Their next normal upgrade should have a higher versionCode (v0.5.4 or later).

## Next versions

1. Increment `versionCode` and update `versionName` in `app/build.gradle.kts` on `main`; review and test the result.
2. Add generic release notes under `docs/releases/vX.Y.Z.md`.
3. Push the reviewed, protected Git tag `vX.Y.Z` matching the application version.
4. Monitor tests, signing certificate checks, GitHub Packages (GHCR) publication and GitHub Release publication.
5. Verify the downloaded APK's SHA-256 checksum and upgrade it on an existing installation without uninstalling.

## GitHub Packages

GitHub Packages does not provide a native installer for Android APKs. The pipeline also publishes the **same signed APK** as a minimal non-runnable OCI archive on `ghcr.io/xavier-le7/screenguard-android-agent-apk:vX.Y.Z`. This is for CI and automation use, **not** an Android application container or a substitute for the APK in Releases. The first GHCR package might default to private; the owner can make it public under package settings. Only the packaging job has `packages:write` permission, and its artifact comes from the same signed build as the GitHub Release.

Upstream integration and contributions are welcome under AGPL-3.0-or-later.
