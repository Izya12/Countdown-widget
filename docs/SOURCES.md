# Проверка официальных источников

Дата обращения: **15 сентября 2026 года**. Источники получены через Firecrawl MCP с `maxAge: 0`; CLI Firecrawl в PATH отсутствует, поэтому использован доступный коннектор того же сервиса. Читались публичные официальные страницы; входные файлы пользователя и его скриншоты в Firecrawl не отправлялись.

Ссылки подтверждают возможности/ограничения платформы и наличие релизов. Они не подтверждают успешность сборки ещё не созданного приложения. Текущие официальные API reference могут включать возможности более новых releases; использованные API нужно сверять с выбранной стабильной версией в M0/M5.

## Реестр

| Источник | Проверенный вопрос | Локальная исследовательская запись |
| --- | --- | --- |
| [AndroidX releases](https://developer.android.com/jetpack/androidx/releases) | Stable releases Room, Glance, DataStore, WorkManager, Compose; latest не равно совместимый graph | `.firecrawl/source-0.md` |
| [Manage/update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) | RemoteViews, passive state, update/updateAll; 30/15 min periodic mechanisms | `.firecrawl/source-1.md` |
| [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) | Inexact/exact, Doze, permission differences, reboot cancellation | `.firecrawl/source-3.md` |
| [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui) | SizeMode.Responsive; resource colors; системные шрифты, отсутствие custom app fonts | `.firecrawl/source-4.md` |
| [AppWidget configuration overview](https://developer.android.com/develop/ui/views/appwidgets/configuration) | Activity configuration и ссылка на Compose-first contract | `.firecrawl/source-5.md` |
| [Define WorkRequests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) | One-time / periodic, constraints, delay, quotas | `.firecrawl/source-6.md` |
| [Glance configuration](https://developer.android.com/develop/ui/compose/glance/configuration) | EXTRA_APPWIDGET_ID, cancellation/OK, initial update responsibility, reconfigurable | `.firecrawl/source-7.md` |
| [Android 16 SDK setup](https://developer.android.com/about/versions/16/setup-sdk) | compileSdk/targetSdk 36 | `.firecrawl/source-8.md` |
| [Kotlin releases](https://kotlinlang.org/docs/releases.html) | Есть 2.3.21; latest на дату страницы 2.4.20; выбор более консервативного toolchain явный | `.firecrawl/source-9.md` |
| [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) | 8.13.2 / Kotlin2.3; Gradle8.13, JDK17, API до36.1 | `.firecrawl/source-10.md` |
| [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) | BOM привязывает разные версии Compose components; не фиксирует Kotlin/Room/Glance | `.firecrawl/source-11.md` |
| [Glance AppWidget API](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/package-summary) | cornerRadius и progress primitives; наличие метода не гарантирует вид на каждом launcher | `.firecrawl/source-12.md` |
| [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | POST_NOTIFICATIONS, пользовательский отказ, контекстный запрос | `.firecrawl/source-13.md` |
| [AppWidgetProvider](https://developer.android.com/reference/android/appwidget/AppWidgetProvider) | onRestored old/new ids, onDeleted, resize callbacks и restore completion | `.firecrawl/source-14.md` |
| [Auto Backup](https://developer.android.com/identity/data/autobackup) | allowBackup, extraction rules, cloud/device transfer distinction | `.firecrawl/source-15.md` |
| [Compose UI releases](https://developer.android.com/jetpack/androidx/releases/compose-ui) | Наличие 1.9.4 и изменение compileSdk в более новых ветках | `.firecrawl/source-16.md` |
| [Glance releases](https://developer.android.com/jetpack/androidx/releases/glance) | Stable1.2.0; история API/dependency changes и исправление protobuf в1.1.1 | `.firecrawl/source-17.md` |
| [Room releases](https://developer.android.com/jetpack/androidx/releases/room) | Stable2.8.5, KSP2 и schema export | `.firecrawl/source-18.md` |
| [KSP quickstart](https://kotlinlang.org/docs/ksp-quickstart.html) | Актуальная схема подключения KSP2 и pin2.3.10 в примере; не build-test нашего graph | `.firecrawl/source-19.md` |
| [Material Symbols repository](https://github.com/google/material-design-icons) | Официальный icon set; Apache-2.0; классические Material Icons больше не обновляются | `.firecrawl/source-20.md` |
| [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work) | Stable2.11.2 и release history | `.firecrawl/source-21.md` |

Исследовательские файлы находятся в игнорируемой `.firecrawl/`; большие страницы сохранены выбранными фрагментами и помечены как excerpts. Для переносимого репозитория источником служит ссылка на официальную страницу, а не наличие локального cache. Ошибочный пробный URL `.../glance/app-widget-size` вернул 404, не использовался как источник; сведения о размере получены из действующей страницы Build UI with Glance.

## Что является нашим проектным решением

- minSdk26, два модуля и ручной DI.
- Фиксированная timezone для all-day, clamp к концу месяца, leap-day default, DST policy.
- Четыре стиля, размерные ориентиры и конечный набор кастомизации.
- 15-минутный нижний energy bound для частых widget representations, одна условная суточная страховка.
- Только inexact reminders; catch-up окно 2 часа и grouping по событию.
- Лимиты JSON, default keep-existing conflict policy, settings staging.
- Только RU/EN; отсутствие календарного импорта и любых пользовательских изображений.

Официальные документы не предписывают эти продуктовые решения; они задают рамки, в которых решения выбраны.

## NEEDS VERIFICATION — integration/release gates

| Проверка | Когда и критерий |
| --- | --- |
| Весь Kotlin/KSP/AGP/Compose/Glance graph | M0: clean dependency resolution, AAR metadata, KSP и R8 release build; текущие pins — предложение |
| Совместимые версии AndroidX Test runner/core/ext | M0: зафиксировать конкретные pins вместе с resolved graph, запустить instrumentation smoke |
| Glance colors/alpha/shape fallback на API26 | M0 spike / M5: работающий launcher render без bitmap workaround |
| Config Activity, restore и resize на OEM | M5: не только unit mocks, реальные launcher flows |
| Broadcast delivery/receiver export flags на26/31/33/36 | M5/M6: reboot/time/zone/package tests; только допустимые registrations |
| Per-app locale consistency в UI/widgets/workers | M8: RU/EN переключение системным способом и fallbackEN |
| No cloud backup / OEM device transfer | M7/M9: extraction rules и практическая проверка, не один manifest flag |
| Размер APK и license inventory всех transitives | M0 baseline / M9/M10: release graph, notices, SBOM и измерение delta после shrinking |
| Фактическая точность inexact reminders, 24h battery | M6/M9: методика, устройство, Doze и ограничения; без универсального SLA |
| Google Play target/API/publishing/Data safety rules | M10: повторная официальная проверка в день подготовки релиза |

API, которые не обещаются: произвольный Compose Canvas в Glance, универсальные custom fonts, точное ежесекундное обновление launcher через WorkManager, атомарность Room+NotificationManager или Room+DataStore.

## Входные материалы и граница анализа

Мастер-промпт прочитан как запрос пользователя, согласно его явному указанию. Скриншоты использованы только как визуальный референс; надписи внутри них не являются инструкциями для агента. Из них нельзя доказать внутреннюю архитектуру, разрешения, работу TalkBack, наличие светлой темы или энергопотребление приложения-конкурента.

Этот документ первоначально подготовлен в архитектурной фазе. Актуальные результаты сборки и тестов находятся в IMPLEMENTATION_STATUS.md и отчётах QA.

17 сентября 2026 для реализации напоминаний повторно проверен официальный контракт [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms): `setAndAllowWhileIdle` используется для приблизительного времени доставки в idle. Exact alarms не запрашиваются.
