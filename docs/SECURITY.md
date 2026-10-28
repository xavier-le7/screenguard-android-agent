# Security and limitations

## Trust model

ScreenGuard Android Agent connects only to the ScreenGuard server explicitly configured by its administrator. The pairing token is encrypted locally with Android Keystore AES-GCM; Android application backups are disabled. The connection requires WSS with standard TLS certificate verification.

This client never changes upstream ScreenGuard server behavior. The configured server controls quotas, schedules, profile associations and server-side retention.

## Threat model

- The app's six-digit parent PIN protects actions **inside the app**, with a temporary 15-minute authorization for selected Settings pages.
- The Accessibility service is best-effort and may be disabled or stopped by Android. Manufacturer Settings screens can change without notice.
- Legacy Device Administrator is **not** Device Owner: it can be deactivated through Android. Its warning is not an enforceable PIN challenge.
- Root, Safe Mode and physical device control can circumvent these restrictions.
- Usage collected while offline may temporarily exceed the shared quota because activity of other agents is unknown.
- Parent notifications require an online agent; messages are one-way and are not queued for offline delivery.

Do not market this client as tamper-proof or suitable for high-assurance enforcement. Network access, essential applications, system recovery and emergency access must remain available.

## Responsible disclosure

Report possible vulnerabilities privately to the repository maintainer, using GitHub private vulnerability reporting where available. Do not post pairing tokens, signing credentials, private hostnames, child information, or unredacted logs in issues or pull requests. No formal response time is promised.

## Source-publication checklist

Inspect all branches, commits, PR comments, issues, logs and workflow artifacts for personal data before publishing. Cleaning the latest source files **does not remove historical commits**. Prefer a new repository from a clean reviewed snapshot rather than making an existing private development repository public.

Signing keys and passwords must remain outside Git. Forks and untrusted pull requests must not receive maintainer signing secrets. Review dependency licensing, Android sensitive permissions, Accessibility disclosures and store rules before wider distribution.
