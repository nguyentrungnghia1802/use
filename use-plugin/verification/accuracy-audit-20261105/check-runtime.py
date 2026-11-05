"""Read-only installation identity checks and isolated source-boundary probes."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
JACAMO = REPO.parent / "jacamo"
INSTALL = REPO / "use-assembly/target/new"
CLASSES = HERE / "probe-classes"
CLASSES.mkdir(exist_ok=True)
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
sources = JACAMO / "checkpoint-dependencies/tests"
runtime_cp = (REPO / "jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt").read_text().strip()
probe_source = HERE.parent / "authoring-istar-instances-20261105/PackagedIdentityProbe.java"
subprocess.run(["javac", "-cp", runtime_cp, "-d", str(CLASSES),
                str(sources / "S01PositiveBoundaryProbe.java"), str(sources / "S01LifecycleProbe.java"), str(probe_source)], check=True)


def boundary(kind):
    folder = HERE / "boundary" / kind
    folder.mkdir(parents=True, exist_ok=True)
    main_args = ["S01PositiveBoundaryProbe", str(folder)] if kind == "positive" else ["S01LifecycleProbe", kind, str(folder)]
    with (folder / "probe.log").open("w", encoding="utf-8") as output:
        result = subprocess.run(["java", "-Djava.awt.headless=true", "-cp", str(CLASSES) + os.pathsep + runtime_cp, *main_args],
                                stdout=output, stderr=subprocess.STDOUT, timeout=60)
    assert result.returncode == 0, (kind, result.returncode)
    return {"kind": kind, "exitCode": result.returncode, "logSha256": sha(folder / "probe.log")}


with ThreadPoolExecutor(max_workers=5) as pool:
    boundary_checks = list(pool.map(boundary, ["positive", "admission", "notifications", "queue", "stop"]))
installed_cp = os.pathsep.join([str(INSTALL / p) for p in (
    "lib/plugins/use-jacamo-plugin-1.0.1.jar", "lib/use-core-7.5.0.jar", "lib/use-gui.jar",
    "bridge/lib/jacamo-bridge-contract-1.0.0.jar", "bridge/lib/jacamo-bridge-jacamo-1.0.0.jar")])
installed_cp += os.pathsep + (INSTALL / "bridge/runtime-classpath.txt").read_text().strip()
probe = subprocess.run(["java", "-cp", str(CLASSES), "PackagedIdentityProbe", installed_cp],
                       capture_output=True, text=True, check=True)
loaded = {}
for line in probe.stdout.splitlines():
    name, path, digest = line.split("\t")
    assert Path(path).resolve().is_relative_to(INSTALL.resolve()) and sha(Path(path)) == digest
    loaded[name] = {"path": path, "sha256": digest}
lock = json.loads((JACAMO / "checkpoint-dependencies/dependency-lock.json").read_bytes())
assert loaded["jacamo.infra.JaCaMoLauncher"]["sha256"] == next(c["sha256"] for c in lock["components"] if c["component"] == "jacamo")
parity = []
for built, installed in (
    ("use-core/target/use-core-7.5.0.jar", "lib/use-core-7.5.0.jar"),
    ("use-gui/target/use-gui.jar", "lib/use-gui.jar"),
    ("use-plugin/target/use-plugin-1.0.1.jar", "lib/plugins/use-jacamo-plugin-1.0.1.jar"),
    ("jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar", "bridge/lib/jacamo-bridge-contract-1.0.0.jar"),
    ("jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar", "bridge/lib/jacamo-bridge-jacamo-1.0.0.jar")):
    with zipfile.ZipFile(REPO / built) as a, zipfile.ZipFile(INSTALL / installed) as b:
        names = {n for n in a.namelist() if not n.endswith("/") and not n.startswith("META-INF/")}
        assert names == {n for n in b.namelist() if not n.endswith("/") and not n.startswith("META-INF/")}
        assert all(a.read(n) == b.read(n) for n in names), installed
    parity.append({"installed": installed, "sha256": sha(INSTALL / installed), "productionEntriesIdentical": True})
result = {"boundaryProbes": boundary_checks, "loadedInstalledClasses": loaded, "installedProductionParity": parity,
          "installationMutated": False, "manualDesktopAcceptance": False}
(HERE / "runtime-receipt.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
print("CURRENT_RUNTIME_BOUNDARY_AND_INSTALLED_IDENTITY_PASS")
