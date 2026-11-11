"""Check FR/EN key parity, plural coverage and printf argument compatibility."""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"


def load(folder):
    items = ET.parse(RES / folder / "strings.xml").getroot()
    result = {}
    for item in items:
        name = item.attrib["name"]
        assert name not in result, f"Duplicate resource: {folder}/{name}"
        result[name] = item
    return result


def arguments(text):
    # Ignore escaped percent signs. Compare positions and types, allowing reordering.
    tokens = re.findall(r"%(?:(\d+)\$)?([dsf])", text.replace("%%", ""))
    return sorted((position or str(i + 1), kind) for i, (position, kind) in enumerate(tokens))


fr, en = load("values"), load("values-en")
assert fr.keys() == en.keys(), f"Missing in EN: {fr.keys() - en.keys()}, missing in FR: {en.keys() - fr.keys()}"
for name, source in fr.items():
    translated = en[name]
    assert translated.tag == source.tag, name
    if source.tag == "plurals":
        variants = {item.attrib["quantity"]: item.text or "" for item in translated}
        assert {"one", "other"} <= variants.keys(), name
        for item in source:
            quantity = "one" if item.attrib["quantity"] == "one" else "other"
            assert arguments(item.text or "") == arguments(variants[quantity]), name
    else:
        assert arguments(source.text or "") == arguments(translated.text or ""), name
print(f"OK: {len(fr)} resources with matching keys, plurals and format arguments.")
