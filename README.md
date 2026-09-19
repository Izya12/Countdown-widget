<!-- suppress SpellCheckingInspection -->
# Отсчёт / Countdown

Проект offline-first Android-приложения для событий, обратного отсчёта и
виджетов домашнего экрана.

## Текущий статус

Доступна разрабатываемая версия: события, категории, отсчёт, Glance-виджеты,
напоминания и локальные резервные копии JSON. Debug и unsigned release
собираются; тесты проходят, lint не сообщает ошибок. Напоминания настраиваются в
деталях сохранённого события; время доставки приблизительное.
Расширенные настройки виджетов и часть проверок на устройствах
ещё предстоят. Проверки и оставшаяся работа —
[Implementation status](docs/IMPLEMENTATION_STATUS.md).

Поддерживаемые языки продукта: **только русский и английский**. Без рекламы,
аккаунтов, аналитики, сервера и пользовательских изображений.

В списке виджетов доступен отдельный **«Круглый отсчёт 1×1»** —
[настройка](docs/ROUND_WIDGET.md).

## Документация

1. [Архитектура и спецификация: все 18 разделов](docs/ARCHITECTURE.md)
2. [Анализ девяти скриншотов референса](docs/REFERENCE_ANALYSIS.md)
3. [План реализации M0–M10 и критерии готовности](docs/ROADMAP.md)
4. [Официальные источники и границы проверки](docs/SOURCES.md)

Рекомендуемая основа: Kotlin, Compose / Material 3, Room, DataStore, Glance,
WorkManager; minSdk 26, targetSdk 36, compileSdk 36. Конкретные версии и
оговорки совместимости приведены в архитектуре.

## Сборка

Gradle wrapper: 9.5.0; AGP: 9.3.3. Для Gradle daemon закреплён JetBrains JDK 21
в `gradle/gradle-daemon-jvm.properties`; подойдёт JBR из Android Studio.
При отсутствии подходящего JDK Gradle попытается скачать его. Bytecode остаётся JVM 17.
Требуется Android SDK 36. Создайте локальный `local.properties` с
`sdk.dir`, затем выполните:

```powershell
.\gradlew.bat :core:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Это тестовая версия с
текущими экранами и резервным копированием. Release APK пока не подписан. Для
проверки базы и UI на подключённом устройстве: `:app:connectedDebugAndroidTest`.

Лицензия исходного кода — [MIT](LICENSE). Transitive notices, security contact и
release signing ещё требуют завершения. Скриншоты стороннего приложения
используются только для анализа и не включены в ресурсы приложения.
