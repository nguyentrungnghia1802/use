"""Final package/source receipt for the focused applicability change."""
import collections, fnmatch, hashlib, io, json, pathlib, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = pathlib.Path(__file__).resolve().parent
LIVE = ROOT / "use-plugin/verification/live-results-ui/auction-13471184572337605195"
def sha(data): return hashlib.sha256(data).hexdigest()
def save(name, value): (OUT / name).write_text(json.dumps(value, indent=2), encoding="utf-8")
archive = ROOT / "use-assembly/target/use-7.5.0-use-bin.zip"
pom = ET.parse(ROOT / "use-plugin/pom.xml")
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
excludes = [e.text for plugin in pom.findall(".//m:plugin", ns) if plugin.findtext("m:artifactId", namespaces=ns) == "maven-jar-plugin" for e in plugin.findall(".//m:exclude", ns)]
with zipfile.ZipFile(archive) as distribution:
    plugin_name = next(n for n in distribution.namelist() if n.endswith("/plugins/use-jacamo-plugin-1.0.1.jar"))
    with zipfile.ZipFile(io.BytesIO(distribution.read(plugin_name))) as jar:
        checked = 0
        for path in (ROOT / "use-plugin/target/classes/org/tzi/use/plugins/jacamo").rglob("*.class"):
            key = path.relative_to(ROOT / "use-plugin/target/classes").as_posix()
            if any(fnmatch.fnmatchcase(key, pattern) for pattern in excludes): continue
            assert path.read_bytes() == jar.read(key), key
            checked += 1
        for manifest in ("use4goal-view-provenance.json", "use4goal-workflow-provenance.json"):
            key = "META-INF/" + manifest
            assert jar.read(key) == (ROOT / "use-plugin/src/main/resources" / key).read_bytes()
            for entry in json.loads(jar.read(key))["files"]:
                assert sha((ROOT / "use-plugin" / entry["targetPath"]).read_bytes()) == entry["targetSha256"]
    for path in (ROOT / "use-plugin/examples/auction").rglob("*"):
        if path.is_file():
            key = "use-7.5.0/examples/auction-current/" + path.relative_to(ROOT / "use-plugin/examples/auction").as_posix()
            assert distribution.read(key) == path.read_bytes(), key
    install = ROOT / "use-assembly/target/new"
    assert (install / "bin/start_use.bat").is_file(), "Run the assembly package/publish step first"
    plugin_path = install / "lib/plugins/use-jacamo-plugin-1.0.1.jar"
    assert plugin_path.read_bytes() == distribution.read(plugin_name)
manifest = json.loads((LIVE / "replay/program.json").read_text(encoding="utf-8"))
with zipfile.ZipFile(plugin_path) as plugin:
    for name, expected in manifest["implementation"].items():
        entry = name.lstrip("/") if name.startswith("/") else name.replace(".", "/") + ".class"
        providers = []
        for path in [plugin_path, *sorted((install / "lib").glob("*.jar"))]:
            with zipfile.ZipFile(path) as library:
                if entry in library.namelist(): providers.append(sha(library.read(entry)))
        assert providers and all(value == expected for value in providers), ("live-implementation-drift", name)
failures = collections.Counter()
with (LIVE / "replay/runtime.jsonl").open(encoding="utf-8") as stream:
    for line in stream:
        result = json.loads(line).get("result") or {}
        for context in (result.get("semantic") or {}).get("observation", {}).get("originalEngine", {}).get("contexts", []):
            for check in context.get("executionConstraints", []):
                if check.get("verdict") == "FAIL": failures[check["kind"]] += 1
assert set(failures) <= {"GOAL_CONDITION"}, failures
for name in ("applicability-tests.log", "applicability-tests-final.log", "applicability-package.log", "applicability-final-package.log", "applicability-closeout-package.log", "applicability-delivery-package.log"):
    shutil.copy2(ROOT / "use-plugin/target" / name, OUT / name)
for name in ("applicability-istar-definitions.png", "scoped-failure-points.png", "unmet-condition-points.png"):
    source = ROOT / "use-plugin/target/state-views" / name
    if source.exists(): shutil.copy2(source, OUT / name)
paths = subprocess.check_output(["git", "ls-files", "-m", "-o", "--exclude-standard"], cwd=ROOT, text=True).splitlines()
freeze = {name: sha((ROOT / name).read_bytes()) for name in paths if (ROOT / name).is_file() and "/verification/" not in name}
save("source-freeze.json", freeze)
save("receipt.json", {
    "status": "PASS", "liveEvidence": str(LIVE.relative_to(ROOT)), "replay": json.loads((LIVE / "replay-receipt.json").read_text()),
    "falseScopedChecks": dict(failures), "note": "Unmet GOAL_CONDITION is separately navigable, not an invariant failure.",
    "archiveSha256": sha(archive.read_bytes()), "testedPluginClassesEqualArchive": checked,
    "launcher": str(install / "bin/start_use.bat"),
    "sourceFreezeSha256": sha((OUT / "source-freeze.json").read_bytes()),
    "freshLiveAuction": True, "freshManualDesktopGui": False, "otherLiveCases": False
})
print((OUT / "receipt.json").read_text())
