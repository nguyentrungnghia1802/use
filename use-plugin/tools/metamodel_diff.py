"""Read-only exact Ecore diff and conservative migration impact report. Never accepts renames."""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

XSI = "{http://www.w3.org/2001/XMLSchema-instance}type"

def inventory(path):
    result = {}
    def visit(package, prefix=""):
        namespace = prefix + package.get("name", "")
        for cls in package.findall("eClassifiers"):
            if cls.get(XSI, "").split(":")[-1] != "EClass":
                continue
            key = namespace + "::" + cls.attrib["name"]
            result[key] = {"class": dict(sorted(cls.attrib.items())), "features": {}}
            for feature in cls.findall("eStructuralFeatures"):
                values = {"lowerBound": "0", "upperBound": "1", "containment": "false"}
                values.update(feature.attrib)
                result[key]["features"][feature.attrib["name"]] = dict(sorted(values.items()))
        for child in package.findall("eSubpackages"):
            visit(child, namespace + "::")
    visit(ET.parse(path).getroot())
    return result

def compare(before, after, artifacts=()):
    old, new = inventory(before), inventory(after)
    changes = []
    for key in sorted(old.keys() | new.keys()):
        if key not in old or key not in new:
            changes.append({"key": key, "kind": "CLASS_ADDED" if key not in old else "CLASS_REMOVED"})
            continue
        for field in sorted(old[key]["class"].keys() | new[key]["class"].keys()):
            left, right = old[key]["class"].get(field), new[key]["class"].get(field)
            if left != right:
                changes.append({"key": key, "kind": "INHERITANCE_CHANGED" if field == "eSuperTypes" else "CLASS_CHANGED", "field": field, "before": left, "after": right})
        a, b = old[key]["features"], new[key]["features"]
        for name in sorted(a.keys() | b.keys()):
            if a.get(name) != b.get(name):
                changes.append({"key": key + "#" + name, "kind": "FEATURE_ADDED" if name not in a else "FEATURE_REMOVED" if name not in b else "FEATURE_CHANGED", "before": a.get(name), "after": b.get(name)})
    removed = [c["key"] for c in changes if c["kind"] == "CLASS_REMOVED"]
    added = [c["key"] for c in changes if c["kind"] == "CLASS_ADDED"]
    candidates = []
    for a in removed:
        for b in added:
            left, right = dict(old[a]["class"]), dict(new[b]["class"])
            left.pop("name", None); right.pop("name", None)
            if left == right and old[a]["features"] == new[b]["features"]:
                candidates.append({"from": a, "to": b, "status": "REVIEW_REQUIRED_NOT_ACCEPTED"})
    impacts = []
    for artifact in sorted(map(Path, artifacts)):
        text = artifact.read_text(encoding="utf-8")
        hits = []
        for change in changes:
            tokens = re.split(r"::|#", change["key"])[1:]
            if any(re.search(r"(?<![A-Za-z0-9])" + re.escape(t) + r"(?![A-Za-z0-9])", text) for t in tokens):
                hits.append(change["key"])
        # Generated/projection artifacts can depend indirectly on any source change.
        impacts.append({"path": artifact.as_posix(), "exactTokenReferences": sorted(set(hits)),
                        "status": "REGENERATE_OR_REVIEW" if changes else "UNCHANGED"})
    return {"changes": changes, "renameCandidates": candidates, "impacts": impacts,
            "reviewLayers": [] if not changes else ["semantic kinds", "structural mappings", "projection anchors", "runtime target bindings", "OCL contexts/navigation", "golden .use/.cmd"],
            "policy": "Exact diff only; indirect impacts conservatively require review; no source or mapping writes"}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("before"); parser.add_argument("after")
    parser.add_argument("artifacts", nargs="*")
    args = parser.parse_args()
    print(json.dumps(compare(args.before, args.after, args.artifacts), indent=2, sort_keys=True))
