# Dependencies and attribution

The ScreenGuard Android Agent source code is licensed under **GNU AGPL-3.0-or-later** (see [../LICENSE](../LICENSE)). Third-party dependencies are **not relicensed** by this project.

## Direct Gradle dependencies

| Component | Declared version | Role | Reported upstream license |
| --- | --- | --- | --- |
| [OkHttp](https://github.com/square/okhttp) | 4.12.0 | Android HTTP / WebSocket transport | Apache License 2.0 |
| [JUnit 4](https://github.com/junit-team/junit4) | 4.13.2 | Unit tests only | Eclipse Public License 1.0 |
| [JSON-java](https://github.com/stleary/JSON-java) | 20240303 | JVM unit tests only; Android provides `org.json` at runtime | Public Domain |

The OkHttp dependency also pulls in components such as Okio and Kotlin standard libraries. Their applicable licenses and notices must be checked from the resolved distribution dependency graph before a public binary release. This table is **not** a complete generated software bill of materials.

## Relationship to ScreenGuard

- The ScreenGuard server and Linux agent are authored by their [upstream contributors](https://github.com/adambie/screenguard).
- The [ScreenGuard Mobile](https://github.com/adambie/screenguard-mobile) parent application is a separate upstream project.
- Both upstream repositories include the GNU Affero General Public License v3 text. The mobile repository explicitly specifies `AGPL-3.0-or-later`.
- The Android agent speaks the existing ScreenGuard v0.10.17 wire protocol. This statement of compatibility does not imply upstream authorship or endorsement.

## Before publishing

Verify third-party files and transitive dependencies against their exact release licenses, carry over required notices, check the provenance of any code adapted from other projects, and audit the actual packaged artifacts. A working CI build does not constitute a license or privacy audit.
