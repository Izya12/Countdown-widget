# Third-party notices

The application source is MIT licensed. Dependencies retain their own licenses. The [resolved release runtime inventory](docs/dependencies/README.md) lists exact versions and upstream POM license declarations; [JSON](docs/dependencies/runtime.json) also records artifact SHA-256 values. The current graph declares Apache-2.0 and BSD-3-Clause licenses. Build and test tools are not shipped in the APK.

The six event vector icons and launcher vector in this repository were drawn for this project. No reference-app artwork, photographs or downloaded icon font is included.

The [embedded license and NOTICE texts](app/src/main/assets/open_source_licenses.txt) are also included as an APK asset. Regenerate the inventory after dependency changes:

```sh
./gradlew -I scripts/dependency-inventory.init.gradle :app:writeRuntimeInventory
python scripts/dependency_inventory.py --fetch-missing
python scripts/collect_notices.py
```

The inventory is based on resolved artifacts and publisher declarations, not an independent legal audit. Do not describe the entire APK as exclusively MIT licensed.
