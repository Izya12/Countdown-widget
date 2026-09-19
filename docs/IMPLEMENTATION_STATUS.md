# Implementation status — 2026-09-19

## Verification

- Windows, JetBrains Java21, Gradle9.5.0, AGP9.3.3, Android SDK36; bytecode17, minSdk26.
- Domain suite: 18 tests passed, including 7 reminder tests for long offsets, DST, all-day, past dates and disabled rules.
- Backup codec suite: 9 tests passed, including typed reminder round-trip and orphan-rule rejection.
- Debug APK builds. Release APK builds with R8/resource shrinking; it is unsigned.
- Android lint: zero errors; warnings remain.
- All 18 instrumentation tests passed on Pixel9Pro AVD, Android16, including actual notification delivery, idempotence, cancellation, catch-up expiry, rule creation through UI, import, migration2→3 and active widget data updates. See [widget regression report](QA_2026-09-19_WIDGET_FIX.md).
- Room schemas1/2/3 exported.

## Implemented

- Kotlin domain, calendar recurrence and lifecycle-aware countdown clock.
- Room events/categories/widget configuration, pending-settings staging, reminder rules/delivery ledger/runtime; migrations1→2→3.
- Event list/search/filter/sort, editor/details, favorites/pins/archive, custom categories, vector icons and themes.
- Russian and English resources only.
- Reminder presets in event details, contextual POST_NOTIFICATIONS request, notification/channel status, one inexact alarm, stable notification tags, 2h catch-up window and 24h recovery while future reminders exist.
- Four Glance styles, configuration draft restoration, update rollback, background boundary/recovery workers and idempotent restored-ID mapping.
- Separate round1×1 provider with exact-size rendering, transparent background, day/week digits, caption and optional colored remaining-progress ring. Configuration and background updates handle both providers.
- Local JSON backup via Android file picker: bounded strict UTF-8 decoding, validation before writes, conflict preview, keep-existing transaction and resumable settings application. Device-specific widget IDs are excluded.
- Android backup/transfer exclusions; no INTERNET permission, ads or analytics.
- Unit/instrumentation tests and local GitHub Actions workflow.

## Remaining work

- Reminder device hardening remains: Doze/force-stop/reboot, permission revocation and process-death fault injection. UI currently offers presets; custom arbitrary offsets/toggle editing are pending. Old missed history is skipped, not materialized into individual ledger rows. No exactly-once or exact-time delivery is promised.
- Widget custom colors/alpha/fonts/alignment, launcher smoke/restore tests and render-hash batching remain incomplete.
- Durable reconciliation outbox and permanent auto-archive processing remain incomplete; current archive policy affects display.
- Relative-date input, searchable icon picker, complete widget previews and broader accessibility controls remain incomplete.
- Additional backup conflict policies, interrupted-import fault injection and SAF UI testing remain pending.
- API26/31/33 devices, OEM launchers, TalkBack, large fonts, Doze and battery/performance measurements have not been verified.
- Dependency lockfiles/verification metadata, final notices/SBOM, security contact, release signing and publishing remain pending. CI has not run on GitHub.

This is a development build, not a completed v1.0 release. Milestone definitions remain in ROADMAP.md.

## Reproduce

```powershell
.\gradlew.bat :core:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```
