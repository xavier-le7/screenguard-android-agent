# Security model and limitations — Android v0.10.18

## Trust boundaries

- The app connects to **the server specified by the parent**, using WSS and normal TLS verification. The bearer token is encrypted at rest using Android Keystore AES-GCM; backups are disabled.
- The self-hosted ScreenGuard server controls quotas, policy and records. The Android app uses upstream v0.10.18 wire messages and does not patch the backend.
- Parent PIN gates app-specific administrative actions and optionally recognized sensitive Settings screens. It is **not** an Android system credential, Device Owner policy or unbreakable anti-tamper control.
- Android Usage Access and Accessibility are necessary for accurate accounting and local overlays. They can be revoked, force-stopped or restricted by the user/OEM.
- Legacy Device Administrator adds an Android deactivation step before uninstalling; it can be deactivated and is **not** Device Owner.

## App controls and remote commands

An Accessibility overlay is used to prevent use of apps that exceed group limits or require approval. Android cannot forcibly terminate arbitrary processes. System and emergency recovery must remain accessible. Per-device/group time can be stale while offline; other agents' usage is incorporated only after receiving the server update.

Remote `fetch_logs` returns only short, bounded, application-owned diagnostics (no system logcat or secrets). Remote `update_agent` prompts the parent to open the signed Android Releases page; **it never executes a Linux installer or silently installs an APK**. Do not add arbitrary filesystem/network/command execution to the remote-control surface.

## DNS VPN risks

DNS filtering is **optional and experimental**. It requires Android VPN consent plus the ScreenGuard parent PIN. The service inspects DNS queries destined for its synthetic VPN DNS endpoint and forwards allowed queries via protected UDP to an underlying network resolver.

- **Not bypass-resistant:** DoH/DoT, Android Private DNS, IPv6 DNS, browser-specific resolvers, caches and competing VPNs can avoid this DNS-only filter.
- **Availability risk:** if the physical network's resolver is unavailable, permitted lookups can fail (SERVFAIL), affecting connectivity. A parent must be able to stop the VPN.
- **State/reporting:** local ACTIVE/STOPPING/PENDING/DISABLED flags and `fetch_logs` counters are diagnostics, not independently attested state. The server's `web_filter_available` badge is derived from `agent_hello`, can be stale offline and does **not** certify coverage.
- **Persistence:** after a parent explicitly turns DNS OFF, the opt-in preference should persist through reboot/upgrade. If left ON and still authorized, Android attempts best-effort restart; OEM background limits may prevent it.

Do not call the app tamper-proof or market it as a guaranteed filtering solution. Do not rely on it for emergency services, high-assurance network restrictions or prevention of all bypass routes.

## Public contributions and release signing

Only reviewed tag-triggered CI jobs can use the existing maintainer signing secrets. Public pull-request jobs should have **no signing secrets**; forks must generate their own key/package identity. Protect `main` and `v*` tags with repository rulesets and restrict who can edit workflows or secrets.

Never publish private hostnames, pairing tokens, signing keys, children’s information, raw logs or real device screenshots in issues, PRs or diagnostics.

## Security reports

Use GitHub's private vulnerability reporting feature if enabled, or contact the repository maintainer privately. Do not submit exploit details containing credentials or private data to a public issue. No guaranteed response time or formal security certification is offered.
