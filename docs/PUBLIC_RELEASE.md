# Public source release checklist

This repository is a clean-history, public snapshot. It deliberately excludes the former development commit history, historical GitHub Actions logs, signing key material and personal test data. No private backend configuration is included.

## Checks for each contribution

- [x] Project licensed under GNU AGPL-3.0-or-later; [LICENSE](../LICENSE).
- [x] Privacy preflight automatically checks committed text files.
- [x] Build and unit tests run on pull requests without signing credentials.
- [ ] Manually review contributed code for personal addresses, tokens, account IDs, screenshots, children, local paths, binary payloads and third-party licensing.
- [ ] Check the exact resolved dependency license notices; see [Dependencies](DEPENDENCIES.md).
- [ ] Maintain parity with the existing ScreenGuard WebSocket protocol unless upstream coordinates a change.
- [ ] Check restrictions, offline accounting and Android permission behavior on real devices.

## Signing and release boundaries

The GitHub repository may be public while maintainer signing credentials are private. Only reviewed tag pushes on main can invoke the signed-release job. Third-party pull requests do not have access to secrets. Protect the main branch and v* tags with GitHub rulesets before enabling public contributions; do not grant unknown contributors write access.

The Android application ID, package path and existing maintainer certificate are intentionally retained for compatibility with already installed versions. A fork that wants an independently upgradable package should use its own application ID and signing certificate.

See [Releasing](RELEASING.md) for version tags and signing secrets. If a source file or historical artifact accidentally exposes a credential, rotate the credential; deleting the file does not invalidate a copied secret.
