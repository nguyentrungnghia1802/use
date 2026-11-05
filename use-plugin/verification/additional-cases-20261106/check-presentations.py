from pathlib import Path
import json
import os
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(PLUGIN / 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.xml.NewCaseStudyBundlesTest.xml').findall('./properties/property')}
classes = HERE / 'presentation-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-cp', properties['java.class.path'], '-d', str(classes), str(HERE / 'CasePresentationProbe.java')], check=True)
for name, run in json.loads((HERE / 'runs.json').read_bytes()).items():
    assert run['exitCode'] == 0, (name, run)
    result = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + properties['java.class.path'],
                             'org.tzi.use.plugins.jacamo.CasePresentationProbe', str(PLUGIN / 'examples' / name), run['directory']], cwd=PLUGIN,
                            capture_output=True, text=True, timeout=120)
    (Path(run['directory']) / 'presentation.log').write_text(result.stdout + result.stderr, encoding='utf-8', newline='\n')
    print(name, 'PRESENTATION_EXIT', result.returncode, flush=True)
    assert result.returncode == 0
