# Privacy and data handling

ScreenGuard Android Agent is a client for a self-hosted parental-control server. It does not operate a separate cloud backend or include third-party analytics.

## On-device information

The agent uses an app-scoped generated identifier, a user-entered child profile name, pairing credentials, locally aggregated daily usage, recent quota decisions and diagnostic state. Parent messages are received over the existing server connection and displayed as notifications.

With explicit Usage Access, the app reads screen interaction and keyguard events to calculate interactive, unlocked screen time. Optional Accessibility detects foreground applications and selected Android Settings titles to enforce restrictions. This is not designed to record screen images, microphone content or camera content.

## Network and server

The client sends its agent identity, logical user and cumulative usage totals only to the secure ScreenGuard WebSocket endpoint configured during pairing. That independently administered server controls retention, access and deletion of server records.

Parent messages are delivered only to connected agents. The Android app has no separate messaging server and does not send replies.

## Storage and user controls

The bearer token is encrypted with Android Keystore AES-GCM and application backups are disabled. Usage counters and policies are kept in app-private storage. Android permission controls let an administrator revoke notifications, Usage Access, Accessibility and Device Administrator; relevant app functions may then stop.

Uninstalling typically removes app-private local data. Server-side records must be managed through the self-hosted ScreenGuard administration interface.

This notice describes the client only, not the privacy practices of independent server operators or distribution platforms. Independent review is recommended before a wide deployment.
