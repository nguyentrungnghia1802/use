"""Run the service-level audit without mutating the selected case-study inputs."""
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
REPORT = PLUGIN / "target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.ui.IStarInstancesPanelTest.xml"
properties = {p.attrib["name"]: p.attrib["value"] for p in ET.parse(REPORT).findall("./properties/property")}
assert "java.class.path" in properties
assert not any("javaagent" in os.environ.get(k, "").lower() for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"))
run = HERE / ("natural-" + str(int(time.time() * 1000)))
run.mkdir()
command = ["java", "-Djava.awt.headless=true", "-Duse.jacamo.runtime.journal.maxBytes=536870912", "-cp", properties["java.class.path"],
           "org.tzi.use.plugins.jacamo.NaturalAuctionVerificationAudit", str(run),
           r"D:\_CODE_BANK\Project_\vnu-sme-lab\jacamo\examples\auction\auction.jcm"]
with (run / "live.log").open("w", encoding="utf-8") as output:
    result = subprocess.run(command, cwd=PLUGIN, stdout=output, stderr=subprocess.STDOUT, timeout=360)
(HERE / "latest-run.json").write_text(json.dumps({"directory": str(run), "exitCode": result.returncode}, indent=2) + "\n", encoding="utf-8", newline="\n")
print(json.dumps({"directory": str(run), "exitCode": result.returncode}))
raise SystemExit(result.returncode)
