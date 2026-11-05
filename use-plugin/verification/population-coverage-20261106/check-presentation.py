from pathlib import Path
import json
import os
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(PLUGIN / 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.ui.VerificationDataCoverageTest.xml').findall('./properties/property')}
classes = HERE / 'presentation-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-cp', properties['java.class.path'], '-d', str(classes), str(HERE / 'PopulationPresentationProbe.java')], check=True)
run = json.loads((HERE / 'latest-run.json').read_bytes())
assert run['exitCode'] == 0
result = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + properties['java.class.path'],
                         'org.tzi.use.plugins.jacamo.PopulationPresentationProbe', run['directory'], str(HERE)], cwd=PLUGIN,
                        capture_output=True, text=True, timeout=90)
(HERE / 'presentation.log').write_text(result.stdout + result.stderr, encoding='utf-8', newline='\n')
print('PRESENTATION_EXIT', result.returncode)
raise SystemExit(result.returncode)
