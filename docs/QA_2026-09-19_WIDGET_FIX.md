# Widget update regression — 2026-09-18/19

## Defect and correction

`CountdownWidget.provideGlance` loaded the widget configuration and event once,
outside `provideContent`. Glance 1.2.0 does not restart `provideGlance` when
`update()` is called during an active composition session. Consequently event
edits and configuration changes could leave the launcher showing stale content;
an initially unconfigured widget could also remain on the selection placeholder.

The shared implementation now observes the widget configuration and events with
Room flows inside the Glance composition. An initial flow emission is loaded
before composition. Existing background update requests remain in place for
inactive sessions. No database schema change or migration is required.

## Reproduction and regression evidence

- Before the fix, the extended `CircleWidgetTest` failed with:
  `Event edits must reach an already running Glance session`.
- After the fix, both circular widget tests passed.
- New `WidgetLiveDataTest` covers standard and circular hosts: initial missing
  configuration, event assignment, date changes, independent weeks selection
  for the circle, and event deletion within an active Glance session.
- The standard compact widget intentionally omits the event title. Its test
  checks the displayed countdown rather than expecting a title at that size.
- During pre-commit cleanup, the duplicate edit check was removed from
  `CircleWidgetTest`; `WidgetLiveDataTest` retains countdown and caption update
  assertions without a fixed post-update sleep.

## Current validation

Environment: Pixel_9_Pro AVD, emulator-5554, Android 16, application
`io.github.countdown`. Installed debug APK and instrumentation APK with `adb install -r`.

- Android instrumentation: **18 passed**, full suite including Room, backup,
  reminders, event UI and widgets. `captures/widget-full-tests.txt`.
- Unit tests: **27 passed** (9 backup, 11 countdown engine, 7 reminder planner).
- Lint: **0 errors, 32 warnings**. Existing build deprecation warnings remain.
- Debug and release assembly: successful; release APK is unsigned.
- Added a round 1x1 widget through the actual Pixel launcher picker, selected
  an existing event and saved it. Countdown and caption appeared on the home screen.
- Tapped that widget and verified the matching event detail screen.
- Checked English and Russian content on the home screen. Russian content
  appeared after the asynchronous refresh following the locale change.
- Final crash buffer was empty. `captures/widget-crash.txt`.
- Screenshot inspected: `captures/widget-fixed-home.png` (English).
  Russian screenshot: `captures/widget-fixed-ru.png`.

APK: `app/build/outputs/apk/debug/app-debug.apk`

Initial fix SHA256: `ACE61F4045059F9A4AC9A5654BBD9AD37DC8B1CDB33A30BF99384E6DBD7B270F`.
After unused-resource cleanup SHA256:
`45C1CE5928450B91AE758A7DCB3AD14E2929489DCAE1B6160267CFE767C95990`.
The pre-commit build repeated debug/release assembly, unit tests and lint:
0 errors, 28 warnings. Logs remain local in `captures/precommit-build.txt`.
All 18 Android tests passed again after cleanup; see the local log
`captures/precommit-android-tests.txt`. The emulator was restarted before this
final run because it had disconnected during the build.

## Build environment

The first build attempted to download a JetBrains JDK and failed moving the
downloaded toolchain. Using the installed Android Studio JBR resolved the build:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat '-Dorg.gradle.java.installations.paths=C:\Program Files\Android\Android Studio\jbr' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:domain:test :app:lintDebug --console=plain
```

## Limits

Validated on the Pixel emulator launcher, not a physical Samsung One UI device.
OEM sizing, long-term background refresh, Doze and reboot behavior were not
revalidated in this run. No claim is made that every possible widget malfunction
is covered by the reproduced active-session defect.
