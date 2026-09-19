"""Create a runtime license inventory from Gradle's resolved artifacts and cached POMs.

First run :app:writeRuntimeInventory using dependency-inventory.init.gradle.
No network requests or credentials are used by this script.
"""
import hashlib
import json
import os
import sys
import urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def licenses(group, name, version, seen=None):
    seen = set() if seen is None else seen
    key = (group, name, version)
    if key in seen:
        return []
    seen.add(key)
    files = list((CACHE / group / name / version).glob("*/*.pom"))
    local = ROOT / ".tools/poms" / group / name / version / (name + ".pom")
    if not files and not local.exists() and "--fetch-missing" in sys.argv:
        base = "https://dl.google.com/dl/android/maven2/" if group.startswith("androidx.") else "https://repo.maven.apache.org/maven2/"
        url = base + group.replace(".", "/") + f"/{name}/{version}/{name}-{version}.pom"
        local.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url, timeout=30) as response:
            local.write_bytes(response.read())
    if local.exists():
        files.append(local)
    for file in files:
        pom = ET.parse(file).getroot()
        result = []
        for item in pom.findall("m:licenses/m:license", NS):
            result.append({"name": item.findtext("m:name", default="", namespaces=NS),
                           "url": item.findtext("m:url", default="", namespaces=NS)})
        if result:
            return result
        parent = pom.find("m:parent", NS)
        if parent is not None:
            args = [parent.findtext("m:" + part, default="", namespaces=NS)
                    for part in ("groupId", "artifactId", "version")]
            return licenses(*args, seen)
    return []


items = json.loads((ROOT / "build/runtime-inventory.json").read_text(encoding="utf-8-sig"))
rows, missing = [], []
for item in items:
    if item["group"] == "Countdown.core":
        continue
    coordinate = ":".join(item[k] for k in ("group", "name", "version"))
    terms = licenses(item["group"], item["name"], item["version"])
    if not terms:
        missing.append(coordinate)
    rows.append({"coordinate": coordinate, "licenses": terms,
                 "sha256": hashlib.sha256(Path(item["file"]).read_bytes()).hexdigest()})
out = ROOT / "docs/dependencies"
out.mkdir(parents=True, exist_ok=True)
(out / "runtime.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = ["# Resolved release runtime dependencies", "",
         "Generated from releaseRuntimeClasspath and cached upstream POM license declarations.",
         "The JSON inventory includes artifact SHA-256 values. Test/build tools are not shipped in the APK.",
         "This inventory records declared licenses; it does not replace upstream license/NOTICE texts.", "",
         "| Dependency | Declared license |", "| --- | --- |"]
for row in rows:
    names = "; ".join(f'[{x["name"]}]({x["url"]})' if x["url"] else x["name"] for x in row["licenses"])
    lines.append(f'| `{row["coordinate"]}` | {names or "MISSING"} |')
(out / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Recorded {len(rows)} artifacts; {len(missing)} missing POM licenses")
for coordinate in missing:
    print(coordinate)
raise SystemExit(bool(missing))
