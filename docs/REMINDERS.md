# Reminder behavior

Open a saved event and use **Add reminder** in its details. All-day presets are the event date, 1 day before or 7 days before, with an explicit local time. Timed-event presets are at start, 15 minutes, 1 hour, 1 calendar day or 7 calendar days before. Calendar offsets retain local time across DST; hour offsets represent elapsed time. Up to16 distinct rules per event are supported by storage/import.

Rules remain saved if notification permission is denied or the channel is blocked. The detail screen links to Android notification settings. Returning to the app reconciles state. Re-enabling notifications schedules future reminders without replaying the disabled period.

Delivery uses one nearest inexact AlarmManager alarm and bounded asynchronous receiver work, with WorkManager recovery. It can be delayed by Android. An allowed recovery batch sends at most one card per event for due reminders within two hours; older history is skipped. Archived/deleted events do not deliver. Event schedule changes invalidate old delivery keys; visual edits preserve them. Removing a rule or changing a schedule cancels stale notification cards at reconciliation.

A stable event notification tag plus a durable per-rule/occurrence/version ledger makes retries idempotent under ordinary operation. NotificationManager and Room are not atomic: a crash after notification publication but before commit may update the same card again. Delivery is not exactly-once.

Event changes, import, app resume/start, reboot, time/zone changes and app updates request reconciliation. Periodic recovery exists only while future permitted reminders exist. Force-stop is not bypassed. Backups include rules, exclude delivery/runtime state and start imported schedules from import time.

Device tests currently cover Android16/API36.1. Doze, permission revoke/re-enable, reboot and process-kill fault injection need broader validation before release. Historical missed ledger rows and custom-offset UI remain unfinished.
