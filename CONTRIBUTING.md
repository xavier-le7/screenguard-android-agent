# Contributing

This project is an independent Android client for ScreenGuard v0.10.17.

## Before contributing

- Keep the upstream server, database, Linux agent and WebSocket contract unchanged.
- Preserve cumulative `usage_sync` and zero-active-seconds heartbeat semantics: a duplicate additive usage delta would break shared quotas.
- Protect network credentials, tokens, signing material, local server names and information about children. Use fictional profiles and domains in examples.
- Write code comments, documentation, commit messages and pull-request descriptions in **English**. French is retained as a localization source, not as a documentation language.
- Put translatable user-facing text in Android resources; provide all six supported locales where practical.
- Do not introduce root, mandatory Device Owner or restrictions that block network, emergency access or unrelated app installation.

## Development

Required tooling: Java 17, Gradle 8.9 and Android SDK 35.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Test quota accounting, UI permissions, network reconnection and message parsing when changing related features. Android instrumentation/manual device validation is required for manufacturer-specific Accessibility screens; passing unit tests alone is not sufficient.

## Pull requests and releases

Pull requests run a test/compile job without maintainer signing secrets. Maintainer-only signed release jobs run on reviewed mainline changes or an explicitly initiated trusted workflow. Never share or request a private keystore.

All contributions to this Android agent are submitted under **GNU AGPL-3.0-or-later**, unless explicitly agreed otherwise in writing. Contributors must have the necessary rights to submit their changes. Do not copy code from projects under incompatible terms.

Maintainers of the original ScreenGuard project are explicitly welcome to reuse, adapt or integrate this agent under the applicable AGPL terms. Please coordinate API/protocol changes with upstream rather than changing the ScreenGuard v0.10.17 server in this repository.

Do not assume publication readiness solely because the current source has been sanitized: old Git history and attachments must be independently checked.
