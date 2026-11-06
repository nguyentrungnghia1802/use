"""Use the packaged consumer classes, preparation script and producer JARs."""
from pathlib import Path
import json
import os
import subprocess
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
INSTALL = PLUGIN.parent / 'use-assembly/target/new'
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(PLUGIN / 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.xml.NewCaseStudyBundlesTest.xml').findall('./properties/property')}
runtime = INSTALL / 'bridge/runtime-classpath.txt'
test_support = [entry for entry in properties['java.class.path'].split(os.pathsep)
                if entry.endswith('use-plugin\\target\\test-classes') or
                any(group in entry.replace('\\', '/') for group in ('/org/junit/', '/org/opentest4j/', '/org/apiguardian/'))]
# Exactly the shipped Get-FilteredConsumerClasspath: host + shaded plugin.
# Framework JARs (and their transitive manifest Class-Path entries) belong to
# the separate producer, not the consumer's pinned ANTLR 4.9.3 engine.
classpath = os.pathsep.join([str(INSTALL / name) for name in ('lib/use-gui.jar', 'lib/plugins/use-jacamo-plugin-1.0.1.jar')] + test_support)
folder = HERE / ('installed-house-building-' + str(int(time.time() * 1000)))
folder.mkdir()
old = json.loads((HERE / 'installed-run.json').read_bytes()) if (HERE / 'installed-run.json').exists() else {}
run = {'directory': str(folder), 'state': 'RUNNING', 'method': 'PACKAGED_CONSUMER_AND_PRODUCER_JARS_WITH_LIVE_WORKBENCH',
       'previousDirectories': old.get('previousDirectories', []) + ([old['directory']] if 'directory' in old else [])}
(HERE / 'installed-run.json').write_text(json.dumps(run, indent=2) + '\n', encoding='utf-8', newline='\n')
command = ['java', '-Djava.awt.headless=true', '-Daudit.workbench=true', '-Daudit.checkout=' + str(INSTALL), '-Daudit.runtime.classpath=' + str(runtime),
           '-cp', classpath, 'org.tzi.use.plugins.jacamo.AuthoredCaseVerificationAudit', str(INSTALL / 'examples/house-building'), str(folder)]
with (folder / 'live.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=PLUGIN, stdout=log, stderr=subprocess.STDOUT, timeout=540)
run.update(state='FINISHED', exitCode=result.returncode)
(HERE / 'installed-run.json').write_text(json.dumps(run, indent=2) + '\n', encoding='utf-8', newline='\n')
print('INSTALLED_HOUSE_BUILDING_EXIT', result.returncode)
raise SystemExit(result.returncode)
