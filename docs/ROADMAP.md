# Development Roadmap — M0–M10

Статус: план, а не выполненная реализация. Дата: 2026-09-15. Зависимости milestone — порядок интеграции; каждый этап сохраняет зелёную сборку. Только русский и английский входят в локализацию продукта.

## Общий Definition of Done этапа

1. Цель и acceptance criteria этапа выполнены; ограничения задокументированы.
2. Debug и minified release variant собираются, lint и относящиеся к изменению тесты зелёные. Release variant до настройки подписи может быть unsigned.
3. Нет production-заглушки, которая выдаёт фиктивный успех; незавершённые функции не показаны как доступные.
4. Затронутые strings имеют EN/RU версии, ошибки доступны пользователю, нет crash на rotation/process recreation в соответствующем flow.
5. При изменении Room экспортирована schema и проверена миграция от всех уже опубликованных версий.
6. Документация, known limitations и CHANGELOG отражают фактическое состояние.

Не требуется начинать с массовой генерации всего проекта. Каждый шаг — небольшой проверяемый результат. Даты завершения не назначаются до M0: отсутствуют измерения команды, окружения и доступных устройств.

## M0 — Project Bootstrap

**Objective:** воспроизводимый минимальный Android-проект под API 36.

**Tasks:**

- Зафиксировать package/namespace, Gradle wrapper, JDK17, SDK26/36, `:app` и `:core:domain`.
- Проверить pins из архитектуры по AAR minCompileSdk/minAgpVersion, Kotlin metadata и transitive graph. Особое внимание Glance/Compose, Kotlin/KSP/Room. Нельзя подавлять compatibility warnings или использовать force, скрывающий несовместимость.
- Создать один минимальный экран Compose/M3, resource fallback EN и RU, edge-to-edge, light/dark; добавить минимальный Glance compile smoke и Room KSP schema smoke.
- Создать Version Catalog, lock/verification metadata, CI на clean checkout с cache, минимальные разрешения workflow; test-only зависимости не попадают в release runtime.
- Добавить MIT LICENSE с реальным holder, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY без вымышленного контакта, CHANGELOG, шаблоны issue/PR.
- Базовый аудит manifests и лицензий разрешённого графа; список официальных notices. Секреты подписи не хранить в репозитории.

**Dependencies:** архитектурная фаза; доступные Android SDK/JDK, сеть для публичных build dependencies. Не устанавливать инструменты скрытно и не заявлять существование SDK без проверки.

**Acceptance criteria:** clean CI выполняет unit/lint/assembleDebug/assembleRelease; минимальный экран запускается на API26 и API36; Room schema генерируется; Glance API компилируется с выбранной Compose линией.

**Tests:** JVM smoke, Room KSP build, UI launch, release R8 smoke, manifest/dependency inspection.

**DoD:** общий DoD + фактические toolchain/pins записаны. Если graph требует compileSdk >36, принять и документировать совместимую корректировку до M1; сейчас никакая такая корректировка не считается выполненной.

## M1 — Database & Domain

**Objective:** стабильные данные и временные инварианты.

**Tasks:** temporal union; события/категории/reminders/widget configs; FK и indices; repository transactions; Clock/zone seams; recurrence anchor/policy; durable scheduler revision. Seed categories idempotent.

**Dependencies:** M0.

**Acceptance criteria:** CRUD через repository переживает process restart; category delete SET NULL, event delete сохраняет missing widget config; invalid temporal mode не записывается; повторы детерминированы.

**Tests:** DAO/FK, file-backed schema, recurrence 31 января и 29 февраля, DST gap/overlap, bounds, duplicate ids, cancellation/transaction rollback.

**DoD:** общий DoD + schema v1 экспортирована, модель не содержит изображений или локализованных wire codes.

## M2 — Event CRUD

**Objective:** usable создание, просмотр, редактирование и архив.

**Tasks:** list/detail/editor, SavedStateHandle, дата/all-day/time/zone, валидация; search/filter/sort/pin/favorite/manual rank; UI states; delete/archive flows. Пока countdown не завершён — показывать абсолютную дату, а не фальшивый счётчик.

**Dependencies:** M1.

**Acceptance criteria:** создать обычное событие без раскрытия дополнительных блоков; отмена сохраняет исходное; double Save даёт одну запись; поиск работает на кириллице; filter combinations устойчивы; rotation не теряет draft.

**Tests:** ViewModel intents/state, Compose CRUD, process recreation, long title/empty state/error, ручная сортировка с accessibility commands.

**DoD:** общий DoD + пробный usability walkthrough; цель 10–15 секунд измерена как гипотеза на обозначенном наборе участников/попыток, результаты не обобщаются без оснований.

## M3 — Countdown Engine

**Objective:** единый правильный countdown и lifecycle-aware refresh.

**Tasks:** status/amount/format contracts, Date vs Duration, Auto/Days/Weeks/Calendar/Detailed, RU/EN plurals, recurrence display, nextMeaningfulChange, progress, foreground coordinator.

**Dependencies:** M1, M2.

**Acceptance criteria:** UI и preview получают один domain snapshot; correct today/tomorrow/past; offscreen/STOPPED не держит ticker; ни одного таймера на карточку; manual clock change пересчитывает экран.

**Tests:** fixed Clock vectors, locale forms 1/2/5/11/21, 23/25h dates, property invariants, тест lifecycle subscription/cancellation и far-future recurrence без дневного перебора.

**DoD:** общий DoD + engine используется всеми дальнейшими представлениями; документированы precision/rounding/past policies.

## M4 — Categories & Customization

**Objective:** визуальная персонализация без длинной обязательной формы.

**Tasks:** custom categories, seed localizations, icon picker/search RU/EN, curated vector subset и licenses, color presets, theme mode, Dynamic Colors fallback, preview, contrast validation, progressStart и formatting options.

**Dependencies:** M2, M3.

**Acceptance criteria:** custom категории переживают перезапуск; неизвестная imported icon даёт fallback; изменение цвета не меняет scheduleVersion; светлая/тёмная темы читаемы; API26 не вызывает API31 без guard.

**Tests:** category lifecycle, icon whitelist/search, formatting preferences, light/dark/dynamic, 200% fonts, TalkBack selected state.

**DoD:** общий DoD + все иконки имеют источник/версию/лицензию; нет runtime asset download.

## M5 — Widgets

**Objective:** четыре рабочих стиля и независимая конфигурация экземпляров.

**Tasks:** provider/receiver/metadata; config Activity result contract; Minimal/Standard/Progress/Calendar; responsive sizes; design capability fallbacks; batch renderer/hash; next-deadline worker; conditional daily reconcile; restore/remap/delete/resizing/system events.

**Dependencies:** M3, M4, durable revisions M1.

**Acceptance criteria:** один event в трёх widgets имеет разные settings; edit обновляет все связанные; deleted event даёт action; initial cancellation не оставляет config; reconfigure cancellation сохраняет старую; resize readable; reboot и восстановленные ids имеют корректное состояние.

**Tests:** config RESULT_CANCELED/OK, размер/тема/locale, old→new ids, missing source, 30 widgets/500 events, Doze и time change, kill после DB commit перед enqueue, race worker finish vs event edit.

**DoD:** общий DoD + реальные launcher screenshots и результаты Pixel/OEM тестов; нет секундного polling и неоправданных alarms. Задержка ОС не считается багом математики, но stale виджет без понятной абсолютной даты/времени — UX-дефект.

## M6 — Notifications & Reminders

**Objective:** несколько правил на событие, предсказуемые повторения и recovery.

**Tasks:** contextual POST_NOTIFICATIONS, channel, duration/calendar offsets, all-day reminder time, earliest inexact alarm, stable notification tags/delivery ledger, scheduleVersion validation, catch-up window, boot/timezone/time/edit reconciliation, permission state UI.

**Dependencies:** M1, M3, M5 coordinator.

**Acceptance criteria:** правило хранится при отказе в permission; будущие reminders возобновляются после разрешения; archive/delete/edit отменяет устаревшее уведомление; offset длиннее recurrence interval не пропускает будущую отправку; нет exact permissions.

**Tests:** 0/1/несколько правил; DST calendar day vs hour; несколько одинаковых due, disable channel, process death между notify и delivered, reboot locked/unlocked, catch-up >2h без flood, pending alarm после delete.

**DoD:** общий DoD + пользователь видит приблизительную семантику, отсутствует обещание точности будильника; устройство проверено с Doze и permission revoke.

## M7 — Backup / Restore

**Objective:** безопасный локальный перенос данных с понятными конфликтами.

**Tasks:** typed schemaVersion1 DTO, SAF export/import, limits, validation, conflict preview/default keep-existing, Room transaction и settings staging, migration pipeline, reconcile после импорта; исключить device-specific widget ids.

**Dependencies:** M1, M4, M6.

**Acceptance criteria:** round-trip сохраняет семантику дат/правил/категорий/settings; повторный импорт идемпотентен; повреждённый/неподдерживаемый файл не меняет базу; crash между Room и DataStore восстанавливает pending apply; перенос не обещает автоматическое размещение widgets.

**Tests:** malformed/truncated/oversized JSON, unsupported versions, duplicate UUID, dangling FK, invalid ZoneId/recurrence, unknown fields/icon, conflict replace/keep, SAF cancellation/IO error, round-trip RU/EN, restart во время apply.

**DoD:** общий DoD + текстовое описание backup format; файлы пользователей не попадают в тестовые fixtures или CI artifacts.

## M8 — Localization & Accessibility

**Objective:** полный паритет только RU/EN и доступные основные flows.

**Tasks:** resource/plural audit, supportedLocales только en/ru, система 12/24h, locale week start, widget/worker consistency, контраст/targets/semantics/focus, font scale, edge-to-edge/IME, predictive back, landscape/large windows.

**Dependencies:** M2–M7. Переводы выполняются на каждом milestone; здесь закрывается общий аудит, а не начинается локализация.

**Acceptance criteria:** нет hardcoded user-visible strings; нет смешения языка app/widget/notifications; системный иной язык даёт EN; TalkBack позволяет создать событие и настроить widget; 200% шрифта не скрывает Save.

**Tests:** RU/EN golden values, Compose semantics, ручной TalkBack и large font, keyboard date/time, back cancellation, small screen и tablet layout.

**DoD:** общий DoD + отчёт accessibility со статусом каждого найденного дефекта; остальные языки не требуются.

## M9 — Testing & Hardening

**Objective:** доказать надёжность основных сценариев и энергии.

**Tasks:** matrix API26/31/33/36, Pixel/OEM launchers, migration matrix, all crash windows, performance baseline/benchmarks, 24h idle/Doze, dependency/license/security manifest audit, backup stress, release R8, no-network inspection.

**Dependencies:** M0–M8.

**Acceptance criteria:** все критические сценарии проходят; idle без потребителей не держит app timers/alarms/periodic jobs; 500 events/30 widgets без ANR/OOM; отсутствуют известные блокирующие потери данных/неверные сроки; батарея измерена с методикой и устройством.

**Tests:** существующий набор + meaningful regression для найденных ошибок, Macrobenchmark/test-only module, Perfetto, WorkManager/AlarmManager dumpsys, offline installation/use/export.

**DoD:** общий DoD + сохранённый QA report с реальными результатами; недоступные устройства/сценарии отмечены как непроверенные, а не pass.

## M10 — Release

**Objective:** воспроизводимый устанавливаемый релиз и готовность к публикации.

**Tasks:** versionCode/name, signing workflow и безопасное хранение ключа; clean CI APK/AAB; README install/build, LICENSE/notices/SBOM, реальные собственные screenshots, CHANGELOG/release notes, SECURITY contact, privacy text, актуальный Play target/policy/Data safety audit.

**Dependencies:** M9; реальный signing key/holder, release metadata и доступ издателя для фактической публикации.

**Acceptance criteria:** signed APK устанавливается/обновляет прошлую версию; AAB проходит необходимые проверки; CI outputs воспроизводимы в описанном окружении; документация не обещает несуществующую точность или tested coverage.

**Tests:** clean install, upgrade с данными/widgets/reminders, restore/import, signed release smoke, dependency notices, merged manifest, package identity и checksum artifacts.

**DoD:** общий DoD + v1.0 checklist ниже. Публикация в магазине или внешнем репозитории — отдельное действие после подготовки конкретных artifacts; в текущую архитектурную фазу не входит.

## Release Definition of Done v1.0

- [ ] Debug/release собираются на clean CI; подписанный install/upgrade проверен.
- [ ] Event CRUD, поиск, фильтры, сортировка, категории, favorites/pins, archive работают.
- [ ] Countdown, recurrence, DST/zone/time changes и past policy соответствуют спецификации.
- [ ] Иконки, цвета, темы и preview работают без изображений/сети.
- [ ] Четыре widget styles, responsive sizes и независимые экземпляры проверены.
- [ ] Reboot, app update, restored widget ids, deleted/missing event обработаны.
- [ ] Reminders, denial/revoke/channel disable и catch-up протестированы.
- [ ] Нет exact permissions или обходов фоновых ограничений.
- [ ] JSON export/import, validation, duplicates и interrupted apply проверены.
- [ ] RU/EN UI/widgets/notifications, plural forms и accessibility проверены.
- [ ] Unit/DAO/migration/ViewModel/Compose/launcher tests имеют реальные отчёты.
- [ ] Idle не содержит ненужных timers/jobs; battery/performance проверены с методикой.
- [ ] Нет ads/analytics/tracking/account/INTERNET или ненужных permissions.
- [ ] MIT/third-party notices, contribution/security docs, release notes готовы.
- [ ] Требования публикации перепроверены по официальным источникам в день release.

Чек-лист v1.0 ещё не закрыт. Реализованные функции и результаты локальных проверок приведены в IMPLEMENTATION_STATUS.md и QA_2026-09-16.md.
