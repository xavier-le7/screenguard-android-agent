# Development roadmap

This file describes the **current implementation and outstanding work** for ScreenGuard Android Agent. It is not a private device-test log. Version numbers are implementation milestones, not guarantees of independent certification.

## Implemented

- [x] Encrypted WSS pairing with the unmodified ScreenGuard server v0.10.17.
- [x] Single logical Android agent user (`local_uid = 1000`) mapped to a chosen upstream profile.
- [x] Android Usage Access accounting for unlocked interactive time, with persisted local daily totals.
- [x] Cumulative `usage_sync` to reconcile shared quota without additive heartbeat double counting.
- [x] Best-effort WebSocket reconnection, screen-wake recovery and boot/app-update recovery.
- [x] Server-provided remaining-time decisions, warning thresholds, time windows and local app restriction overlay.
- [x] Optional six-digit parent PIN, a 60-second restriction test and 15-minute sensitive-Settings authorization.
- [x] Optional legacy Android Device Administrator and best-effort recognition of sensitive settings.
- [x] Parent-to-child message notification using existing server `notify_user` fields.
- [x] Adaptive launcher icon and six interface locales matching the upstream mobile parent client: French, English, Spanish, German, Portuguese and Polish.
- [x] Maintainer-signed non-debuggable release APK and signing certificate verification in CI.
- [x] Generic WSS server and child profile input for new installations, while retaining existing saved configuration.
- [x] Isolated public pull-request test job with no access to maintainer signing secrets.

## Compatibility and resilience still to validate

- [ ] Long offline periods, Android Doze, vendor-specific battery optimization, force-stop and restart.
- [ ] Accuracy of reconstructed usage events around midnight, DST transitions and missing UsageStats events.
- [ ] Android versions/manufacturers other than those already tested during development.
- [ ] Permissions reactivation and accessible parental recovery after OS/service interruption.
- [ ] Reliability of Android notifications across lock screen, Do Not Disturb and vendor settings.
- [ ] Parent-message appearance while a foreground game is active and while the restriction overlay is shown.
- [ ] Parent PIN grace-period expiry after 15 minutes and settings detection across OEM Settings screens.
- [ ] Exceptions for calls, emergency functions, home-screen navigation and network configuration.
- [ ] Exhaustive translations, accessibility, text scaling and internationalized date/time formatting.

## Security and public release

- [ ] Complete review of private development history, branches, PRs, comments, artifacts and logs before sharing.
- [x] Select and add GNU AGPL-3.0-or-later as the project license.
- [ ] Complete third-party dependency attribution, provenance and license compatibility review.
- [ ] Perform independent security and privacy assessment, including Android Accessibility and Device Administrator risks.
- [ ] Validate installation and direct upgrade paths with a trusted, stable signing certificate.
- [ ] Create a clean public source repository rather than exposing private development commits.
- [ ] Review distribution/store policies independently; a signed APK is not automatically approved for Google Play.

## Non-goals and architectural constraints

The upstream ScreenGuard server, schema, authorization, existing quotas, Linux agent and WebSocket message contract are **out of scope**. This Android client cannot implement bidirectional messaging, a remote message queue or guaranteed Device Owner-grade tamper resistance without broader ecosystem changes.

This roadmap must not contain child names, local addresses, infrastructure hostnames, tokens or screenshots of private devices.
