# План публикации в F-Droid

Google Play исключён. Цель — основной каталог F-Droid, без платной регистрации.
Появление приложения в каталоге зависит от проверки и сборки сопровождающими F-Droid.

## Решения

- Название: «Отсчёт» / Countdown.
- Application ID: `io.github.izya12.countdown`. Прежний `io.github.countdown`
  использовался только в тестовых сборках. Перенос событий: экспорт JSON из
  старой сборки, импорт в новую. Удалять старую сборку до экспорта не нужно.
- Лицензия исходников: MIT; зависимости сохраняют собственные лицензии.
- Подпись: стандартная подпись F-Droid. Не публикуем отдельно подписанный
  установочный APK с тем же ID: он не обновлялся бы поверх версии F-Droid.
- Репозиторий: https://github.com/Izya12/Countdown-widget
- Сборка: OpenJDK 21, Android SDK 36, Gradle wrapper; Android Studio не требуется.
- Первый релиз: 0.1.0 / versionCode 1, тег `v0.1.0`.

## Последовательность

1. [x] Утвердить ID в исходниках и проверить новый пакет тестами.
2. [x] Подготовить свободный toolchain и перечень runtime-зависимостей/лицензий.
3. [x] Добавить описания, иконку и реальные скриншоты RU/EN в Fastlane metadata.
4. [x] Добавить политику конфиденциальности и канал обратной связи.
5. [ ] Выполнить сборки, unit/Android-тесты, lint и проверку метаданных.
6. [ ] Сделать исходники публичными, отправить изменения и проверить Linux CI.
7. [ ] Создать тег и исходный GitHub Release, зафиксировать commit в рецепте F-Droid.
8. [ ] Отправить merge request в fdroid/fdroiddata либо Request for Packaging.
9. [ ] После замечаний сопровождающих исправить рецепт и дождаться публикации.

## Проверка рецепта

Файл из `fdroid/metadata/` предназначен для переноса в `metadata/` репозитория
fdroiddata. Команды в Linux-окружении fdroidserver:

```sh
fdroid rewritemeta io.github.izya12.countdown
fdroid lint io.github.izya12.countdown
fdroid build io.github.izya12.countdown
```

Локальная Gradle-сборка не заменяет проверку официальным F-Droid buildserver.
Рецепт нельзя считать принятым до проверки сопровождающими.

## Источники

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/docs/Build_Metadata_Reference/
