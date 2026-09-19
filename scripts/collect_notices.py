"""Collect license/NOTICE files embedded in resolved runtime JAR/AAR archives."""
import io
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
items = json.loads((ROOT / "build/runtime-inventory.json").read_text(encoding="utf-8-sig"))
notices = {}

def scan(data, origin):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith("/"):
                continue
            if re.search(r"(^|/)(LICENSE|NOTICE|COPYING|COPYRIGHT)([._-].*)?$", name, re.I):
                text = archive.read(name).decode("utf-8", errors="replace").strip()
                if text:
                    notices.setdefault(text, []).append(origin + " / " + name)
            elif name.endswith(".jar"):
                scan(archive.read(name), origin + " / " + name)

for item in items:
    if item["group"] == "Countdown.core":
        continue
    scan(Path(item["file"]).read_bytes(), ":".join(item[k] for k in ("group", "name", "version")))

parts = ["Countdown: third-party license and NOTICE texts", "",
         "Exact runtime versions and declared licenses:",
         (ROOT / "docs/dependencies/README.md").read_text(encoding="utf-8")]
for text, origins in sorted(notices.items(), key=lambda entry: entry[1][0]):
    parts += ["\n" + "=" * 72, "\n".join(origins), "", text]
for source in sorted((ROOT / "licenses").glob("*LICENSE*")):
    parts += ["\n" + "=" * 72, source.name, "", source.read_text(encoding="utf-8")]
out = ROOT / "app/src/main/assets/open_source_licenses.txt"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(parts) + "\n", encoding="utf-8")
print(f"Collected {len(notices)} unique embedded license/NOTICE texts into {out.relative_to(ROOT)}")
