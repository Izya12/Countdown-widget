# Changelog

## Unreleased — 0.1.0

- Added a separate round1×1 home-screen widget with a transparent background, day/week count, event caption and optional remaining-progress ring.
- Added independent day/week selection for each round widget, with configuration recreation and real widget-click tests.

- Added per-event reminders, permission/channel status, inexact scheduling, a delivery ledger and a two-hour catch-up limit.
- Added reminder-aware JSON backup/import and Room schema migration2→3.
- Added DST/long-offset reminder tests and real Android notification delivery/cancellation coverage.

- Added the Gradle Android project and independent Kotlin domain module.
- Added calendar recurrence, countdown and DST regression tests.
- Added Room storage, event list/editor/details, categories, themes and RU/EN resources.
- Added Glance widget source, configuration and background update workers.
- Added Room/UI instrumentation tests and a GitHub Actions workflow.
- Added validated local JSON backup/import, conflict preview, keep-existing merge and recoverable settings staging.
- Added Room schema migration1→2 and Android backup/transfer exclusions.
- Fixed Android16 test tooling, widget configuration restoration and main-thread navigation after saving.

Validation status and outstanding implementation are recorded in `docs/IMPLEMENTATION_STATUS.md`. These entries describe source changes, not a completed v1.0 release.
