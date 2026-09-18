# Local backup format

The Settings screen uses Android's document picker to read/write UTF-8 JSON. The file contains plaintext event titles and notes; store it in a location you trust. The app does not upload it.

Version 1 contains `schemaVersion`, UTC `exportedAt`, `events`, `categories`, `settings.theme` and typed `reminders`. Event fields preserve local date/time, IANA zone, optional fixed instant, recurrence anchor, appearance and flags. Widget instance IDs and delivery state are excluded; schedule versions are normalized to zero.

Imports accept at most 10 MiB, 10,000 events, 1,000 categories, 100,000 rules (16 per event) and nesting depth32. Invalid UTF-8, UUIDs, dates, enums, zones, duplicate IDs/rules and dangling references are rejected before writes. Unknown fields are ignored. Unsupported versions are rejected. Old files with an empty/missing reminders list remain supported.

The preview reports new, identical and conflicting events, comparing their rules as well. Existing events and all their local rules are preserved; only missing events and their rules are inserted. An incoming rule ID belonging to a different local event rejects the transaction. The imported theme is applied. Repeated imports do not duplicate events. Database writes and settings staging share one Room transaction; a worker resumes pending settings after restart. Importing does not place widgets. Imported rules start scheduling from import time, without historical notifications.

Further conflict policies remain planned. Android file-provider failure can leave an incomplete export file; the UI reports failure and such a file will fail import validation.
