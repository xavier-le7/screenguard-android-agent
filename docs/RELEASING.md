# Releasing ScreenGuard Android Agent

GitHub Releases are the public distribution channel for maintainer-signed APKs; workflow artifacts expire.

## What remains private

The signing keystore is **not part of the source**. Store its base64 representation in the GitHub Actions secret `SG_ANDROID_KEYSTORE_B64` and its password in `SG_ANDROID_KEYSTORE_PASSWORD`. Keep an offline encrypted backup of the original PKCS#12 keystore. Neither value should appear in issues, pull requests, logs, commits or documentation.

**Keep the same certificate** and `applicationId` used for previously installed maintainer APKs, so upgrades can preserve pairing and application data. A new signing key would prevent ordinary in-place upgrades.

## Public CI

Pull requests and mainline pushes run source privacy checks, JVM unit tests and debug compilation **without** signing secrets. The signed-release and publication jobs run **only when a `vX.Y.Z` tag is pushed**, after those checks succeed. Before signing, the job verifies that the tagged commit is on `main` and that its version matches `versionName` in `app/build.gradle.kts`. The release APK and corresponding SHA-256 checksum are published together. Existing releases are never silently overwritten.

Protect the `main` branch and `v*` tags with repository rulesets and restrict direct write access. GitHub Actions secrets are not passed to fork pull-request jobs, but malicious changes merged into a trusted signing workflow could expose credentials, so **review workflow changes particularly carefully**.

## First release: v0.5.3

The v0.5.3 APK has already been built and signed with the original maintainer certificate. Its known SHA-256 checksum is:

`1dbc7b7964dd9980a51cea16f717c838d54bc4663f1ab9ffb8c0f01075734f05`

Import that **exact APK** into the public GitHub Release `v0.5.3` rather than producing another build under the same version. Verify its SHA-256 hash before upload and attach `screenguard-android-agent-v0.5.3.apk.sha256`.

## Next versions

1. Increment `versionCode` and update `versionName` in `app/build.gradle.kts` on `main`; review and test the result.
2. Add generic release notes under `docs/releases/vX.Y.Z.md`.
3. Push the reviewed, protected Git tag `vX.Y.Z` matching the application version.
4. Monitor tests, signing certificate checks and GitHub Release publication.
5. Verify the downloaded APK's SHA-256 checksum and upgrade it on an existing installation without uninstalling.

Upstream integration and contributions are welcome under AGPL-3.0-or-later.
