# ScreenGuard Android Agent

An independent Android companion for the self-hosted [ScreenGuard](https://github.com/adambie/screenguard) parental-control server, compatible with its **v0.10.17 protocol**.

> **Experimental software.** This is not an official upstream client or an audited, tamper-proof parental-control product.

**License:** [GNU AGPL-3.0-or-later](LICENSE). Contributions, forks and upstream integration are welcome.

## Features

- WSS pairing and encrypted token storage (Android Keystore).
- Local screen-time accounting and cumulative synchronization of shared profile quotas.
- Offline accounting, automatic reconnection, quota warnings and optional app restrictions.
- Optional parent PIN and Accessibility-based protection of selected Android settings.
- Optional legacy Device Administrator status, providing an additional uninstallation step (not a guaranteed PIN lock).
- One-way parent messages via the existing server `notify_user` command.
- French, English, Spanish, German, Portuguese and Polish interface resources.

**Requirements:** Android 14+ (minSdk 34), a trusted ScreenGuard v0.10.17 server and a valid TLS WebSocket endpoint ending in `/ws`.

## Setup

1. Install an APK from a trusted release or build from source.
2. Enter **your own server endpoint** (for example `wss://example.org/ws`) and a free-form child profile name. The app does not ship with an assigned server or profile.
3. Pair using the code displayed in the agent and associate the device user with an existing profile in ScreenGuard administration.
4. Allow Usage Access; start the persistent tracking service. Allow notifications to receive warnings and parent messages.
5. Optionally enable Accessibility, restrictions and sensitive-settings protection with a separate six-digit parent PIN.
6. Optionally activate Device Administrator after explicit Android consent. Confirm that calls, emergency features, Wi-Fi and unrelated app management remain accessible.

For reliable tracking, some Android vendors require manually disabling battery optimization for the application.

## Downloads and releases

Download maintainer-signed APKs from [GitHub Releases](../../releases) when available. Each release includes an APK and SHA-256 checksum; temporary CI artifacts are not the long-term download location.

See [Releasing](docs/RELEASING.md) for the tag process. This is experimental software, not a certified anti-tampering product.

## Building

Install JDK 17, Gradle 8.9 and Android SDK 35, then run:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

A local debug APK is signed with a different certificate from maintainer releases. Android updates require the same package ID and signing certificate; see [Signing](docs/SIGNING.md). Release signing keys must never be exposed to pull requests.

## Permissions and limitations

| Permission or capability | Purpose |
| --- | --- |
| Usage Access | Locally measure active, unlocked screen time |
| Accessibility (optional) | Apply app restrictions and recognize selected sensitive Settings pages |
| Notifications | Display time-limit warnings and incoming parent messages |
| Device Administrator (optional) | Add an OS deactivation step before uninstalling |
| Internet | Communicate only with the configured ScreenGuard server |

No root or Device Owner is required. **Anti-tampering is not guaranteed:** Accessibility can be revoked, services can be force-stopped and legacy Device Administrator can be deactivated. OEM Settings layouts vary. Offline totals cannot account for other devices until reconnection. Parent messages are not queued by the server for offline delivery.

This project does not change the upstream server, database, wire protocol or Linux agents. It has not completed independent security testing or app-store policy review.

## Documentation

- [Protocol](docs/PROTOCOL.md)
- [Setup and enforcement](docs/PHASE3.md)
- [Security](docs/SECURITY.md)
- [Privacy](docs/PRIVACY.md)
- [Signing and CI](docs/SIGNING.md)
- [Roadmap](docs/ROADMAP.md)
- [Public release checklist](docs/PUBLIC_RELEASE.md)
- [Contributing](CONTRIBUTING.md)

## License and upstream collaboration

The source code of this Android agent is available under the **GNU Affero General Public License v3.0 or later** (`AGPL-3.0-or-later`). See [LICENSE](LICENSE). You may study, modify and redistribute the code under that license, including in commercial projects, subject to its conditions. Any third-party components retain their own licenses; see [Dependencies and attribution](docs/DEPENDENCIES.md).

This project is developed separately from [ScreenGuard](https://github.com/adambie/screenguard) and [ScreenGuard Mobile](https://github.com/adambie/screenguard-mobile), which also publish their software under the GNU AGPL family. Contributions, forks and improvements are encouraged, and the upstream maintainer is **welcome to incorporate this Android agent or parts of it** under the applicable AGPL terms. No affiliation or acceptance by the upstream projects is implied.

## Project status and contributions

This is the **public open-source source repository**. Its first code import was made from a reviewed source snapshot with a new, independent Git history; historical personal testing commits and internal build artifacts are not imported.

**Contributions are welcome**, especially collaboration with the [original ScreenGuard project](https://github.com/adambie/screenguard). Please open an issue or pull request. This agent is independently developed and is not endorsed by upstream.

## Maintainer builds

Official APK releases use a stable maintainer signing certificate for in-place Android upgrades. The corresponding **private signing key must never be committed**, shared in a pull request or placed in any source archive. Public CI verifies contributions without requiring secrets; a separate trusted tag-triggered release job accesses signing secrets stored in GitHub Actions settings. See [Releasing](docs/RELEASING.md).

