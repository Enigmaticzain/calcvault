#!/usr/bin/env python3
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_SRC = ROOT / "app" / "src" / "main" / "java"
DISABLED_SRC = ROOT / "disabled_sources"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


TYPE_PATTERN = re.compile(
    r"^(?:public\s+|internal\s+|private\s+)?(?:(?:data|enum|sealed)\s+)?"
    r"(?:class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
PACKAGE_PATTERN = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def fqcn_map(source_root: Path) -> dict[str, Path]:
    classes: dict[str, Path] = {}
    for path in source_root.rglob("*.kt"):
        text = read_text(path)
        package_match = PACKAGE_PATTERN.search(text)
        if not package_match:
            continue
        package_name = package_match.group(1)
        for class_name in TYPE_PATTERN.findall(text):
            classes[f"{package_name}.{class_name}"] = path
    return classes


def resolve_manifest_name(name: str, namespace: str) -> str:
    if name.startswith("."):
        return f"{namespace}{name}"
    if "." in name:
        return name
    return f"{namespace}.{name}"


def manifest_components(namespace: str) -> list[str]:
    tree = ET.parse(MANIFEST)
    components: list[str] = []
    for element in tree.iter():
        tag = element.tag.rsplit("}", maxsplit=1)[-1]
        if tag not in {"application", "activity", "service", "receiver", "provider"}:
            continue
        name = element.attrib.get(f"{ANDROID_NS}name")
        if not name:
            continue
        fqcn = resolve_manifest_name(name, namespace)
        if fqcn.startswith("android.") or fqcn.startswith("androidx."):
            continue
        components.append(fqcn)
    return components


def main() -> int:
    live_classes = fqcn_map(APP_SRC)
    disabled_classes = fqcn_map(DISABLED_SRC)
    missing_disabled = sorted(
        (fqcn, path)
        for fqcn, path in disabled_classes.items()
        if fqcn not in live_classes
    )

    namespace = "com.calcvault"
    missing_manifest = sorted(
        fqcn
        for fqcn in manifest_components(namespace)
        if fqcn not in live_classes
    )

    print("CalcVault feature integration audit")
    print(f"  Live top-level types: {len(live_classes)}")
    print(f"  Disabled-source top-level types checked: {len(disabled_classes)}")
    print(f"  Manifest components checked: {len(manifest_components(namespace))}")

    if missing_disabled:
        print("\nMissing live counterparts for disabled-source types:")
        for fqcn, path in missing_disabled:
            print(f"  - {fqcn} from {path.relative_to(ROOT)}")

    if missing_manifest:
        print("\nManifest components without live top-level types:")
        for fqcn in missing_manifest:
            print(f"  - {fqcn}")

    if missing_disabled or missing_manifest:
        return 1

    print("  Result: all disabled-source feature types and manifest components are represented live.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
