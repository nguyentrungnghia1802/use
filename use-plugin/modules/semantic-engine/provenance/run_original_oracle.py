#!/usr/bin/env python3
"""Regenerate/compile pinned original core into use-owned isolated test output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "use-jacamo-semantic-engine"


def within(path, parent=ROOT):
    path = path.resolve()
    if not path.is_relative_to(parent.resolve()):
        raise ValueError(f"OUTPUT_ESCAPE:{path}")
    return path


def run(command, log, cwd):
    with log.open("w", encoding="utf-8") as output:
        completed = subprocess.run(command, cwd=cwd, stdout=output,
                                   stderr=subprocess.STDOUT, timeout=120)
    if completed.returncode:
        raise RuntimeError(f"ORACLE_COMMAND_FAILED:{log}\n{log.read_text('utf-8')}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    options = parser.parse_args()
    evidence = within(options.evidence)
    manifest_path = MODULE / "src/main/resources/META-INF/use4goal-provenance.json"
    manifest = json.loads(manifest_path.read_text("utf-8"))
    upstream = within(evidence / "original-source")
    with zipfile.ZipFile(evidence / "pinned-original-core.zip") as archive:
        for entry in manifest["files"] + [manifest["license"]]:
            data = archive.read(entry["upstreamPath"])
            if hashlib.sha256(data).hexdigest() != entry["sourceSha256"]:
                raise ValueError("ORIGINAL_ARCHIVE_HASH_MISMATCH")
            target = within(upstream / entry["upstreamPath"], upstream)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    tool_cp = (evidence / "antlr-tool-classpath.txt").read_text("utf-8").strip()
    java = shutil.which("java")
    javac = shutil.which("javac")
    if not java or not javac:
        raise RuntimeError("JDK21_REQUIRED")
    generated = within(evidence / "generated")
    generated.mkdir(exist_ok=True)
    commands = []
    for name in ("CSL", "IStar"):
        command = [java, "-cp", tool_cp, "org.antlr.v4.Tool", "-visitor", "-listener",
                   "-o", str(generated), str(upstream / f"goal/src/main/resources/grammars/{name}.g4")]
        commands.append(command)
        run(command, evidence / f"generate-{name}.log", ROOT)
    report = ROOT / "use-core/target/surefire-reports/TEST-org.tzi.use.uml.mm.ModelAPITest.xml"
    properties = {item.attrib["name"]: item.attrib.get("value", "")
                  for item in ET.parse(report).findall("./properties/property")}
    cp_items = (properties.get("java.class.path", "") + os.pathsep +
                properties.get("jdk.module.path", "") + os.pathsep + tool_cp).split(os.pathsep)
    classpath = os.pathsep.join(dict.fromkeys(item for item in cp_items if item))
    classes = within(evidence / "original-classes")
    classes.mkdir(exist_ok=True)
    sources = sorted(upstream.rglob("*.java")) + sorted(generated.rglob("*.java"))
    sources += [MODULE / "provenance/OriginalCoreOracle.java"]
    arguments = ["--release", "21", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes)]
    arguments.extend(str(source) for source in sources)
    argfile = evidence / "original-javac.args"
    argfile.write_text("\n".join('"' + item.replace("\\", "/").replace('"', '\\"') + '"'
                                for item in arguments) + "\n", "utf-8")
    command = [javac, "@" + str(argfile)]
    commands.append(command)
    run(command, evidence / "original-compile.log", ROOT)
    command = [java, "-cp", str(classes) + os.pathsep + classpath,
               "OriginalCoreOracle", str(evidence / "original-observations.json")]
    commands.append(command)
    run(command, evidence / "original-run.log", ROOT)
    result = {"status": "PASS", "scope": "ORIGINAL_SOURCE_ONLY_NOT_PRODUCTION_MIGRATION",
              "upstreamCommit": manifest["upstreamCommit"],
              "javaSources": len([e for e in manifest["files"] if e["fqcn"]]),
              "generatedJavaSources": len(list(generated.rglob("*.java"))),
              "hostUseVersion": manifest["hostUseVersion"], "commands": commands,
              "oracleClassPath": classpath,
              "archiveSha256": hashlib.sha256((evidence / "pinned-original-core.zip").read_bytes()).hexdigest()}
    (evidence / "original-acceptance.json").write_text(json.dumps(result, indent=2) + "\n", "utf-8")
    print(json.dumps({key: value for key, value in result.items() if key not in ("commands", "oracleClassPath")},
                     indent=2))


if __name__ == "__main__":
    main()
