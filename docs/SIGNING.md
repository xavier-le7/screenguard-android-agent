# Signing, CI and upgrade compatibility

Android only accepts an APK update when the **application ID** and **signing certificate** match the installed application and the new `versionCode` is higher. Never commit a production keystore or publish its password.

## Local development

Install JDK 17, Gradle 8.9 and the Android 35 SDK:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The resulting debug APK uses a developer-specific signing certificate unless the stable signing environment is configured. It cannot update a device installed with another certificate.

## Creating your own stable signing key

The official maintainer must **reuse the existing signing keystore** to preserve in-place upgrades. Only new forks or maintainers creating a genuinely separate application should generate a **new** key on a trusted machine, outside the repository:

```bash
mkdir -p "$HOME/.local/share/screenguard-signing"
chmod 700 "$HOME/.local/share/screenguard-signing"
keytool -genkeypair -v -storetype PKCS12 -keyalg RSA -keysize 3072 \
  -validity 3650 -alias screenguard-agent \
  -keystore "$HOME/.local/share/screenguard-signing/agent.p12"
```

Retain encrypted backups of the keystore and its password. **Forks must use their own key** rather than the original maintainer certificate.

If using the repository's release GitHub Actions job, configure encrypted repository secrets:

- `SG_ANDROID_KEYSTORE_B64`: base64 content of the PKCS#12 file (not its file path).
- `SG_ANDROID_KEYSTORE_PASSWORD`: the keystore password.

Create a single-line base64 representation without printing it to public logs:

```bash
base64 < "$HOME/.local/share/screenguard-signing/agent.p12" | tr -d '\n' > "$HOME/.local/share/screenguard-signing/agent-base64.txt"
chmod 600 "$HOME/.local/share/screenguard-signing/agent-base64.txt"
```

Keep both the key and the encoded file out of Git. **Never paste signing credentials into an issue, PR, chat or build log.**

## CI isolation

The `test` job checks untrusted pull requests **without secrets**, running unit tests and compiling a debug APK.

The `signed-release` job runs only after successful tests and **only when a version tag (`vX.Y.Z`) is pushed**, for a commit belonging to `main`. It restores the stable PKCS#12 from GitHub Actions secrets, builds debug and release, verifies both signatures and checks that the **release is not debuggable**. The resulting artifact is called `screenguard-android-agent-release-signed`.

Protect `main` and `v*` version tags, review all changes to build workflows before merging, and restrict who can manage GitHub Actions secrets. Do not configure signing secrets on an untrusted fork.

## Upgrade safety

The existing Android application ID is `fr.sevenskies.screenguard.agent`. It is retained for **in-place upgrades of existing installs**. A fork changing the ID or signing certificate becomes a separate app and cannot update the original.

To upgrade, install a higher-`versionCode` APK with the **same signing certificate** directly over the app. Do not uninstall the existing app first: doing so deletes its locally stored pairing token, settings and usage counters.

There is no implied promise that an APK from another project's fork is compatible with your installed signature.

## Publishing

This is a source build and direct APK signing procedure, not Play Store certification. Google Play distribution separately requires review of sensitive Accessibility, Usage Access, Device Administrator, foreground service permissions and Play App Signing.
