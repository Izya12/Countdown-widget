# Contributing

Use OpenJDK 21, Android SDK 36 and the checked-in Gradle 9.5.0 wrapper. Set JAVA_HOME to your JDK installation. Android Studio is not required. Kotlin and Java bytecode target JVM 17.

Run `./gradlew :core:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` before proposing a change. Run `:app:connectedDebugAndroidTest` with an emulator/device for database and UI changes.

Keep domain logic independent of Android. New user-visible strings require English and Russian resources. Do not add analytics, accounts, network calls or image support. Preserve calendar semantics and add regression tests for temporal bugs. Schema changes need exported Room schemas and migration tests once a schema is released.

Use small pull requests describing the user-visible behavior and actual validation. Avoid publishing real event data, backups, signing keys, device identifiers or local SDK paths. Contributions are licensed under the project MIT license; third-party notices must remain intact.
