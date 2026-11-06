"""Run the two requested authored bundles independently, with unchanged inputs."""
from pathlib import Path
import json
import os
import subprocess
import time
import sys
import re
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PLUGIN = HERE.parents[1]
report = PLUGIN / 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.xml.NewCaseStudyBundlesTest.xml'
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(report).findall('./properties/property')}
assert not any('javaagent' in os.environ.get(k, '').lower() for k in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'))
results = json.loads((HERE / 'runs.json').read_bytes()) if (HERE / 'runs.json').exists() else {}
arguments = sys.argv[1:]
workbench = '--plain' not in arguments
prefix = 0
if '--prefix' in arguments:
    option = arguments.index('--prefix')
    prefix = int(arguments[option + 1])
    del arguments[option:option + 2]
    assert prefix > 0
names = [n for n in arguments if n != '--plain'] or ('hello-world', 'house-building')
assert all(n in ('hello-world', 'house-building', 'auction') for n in names)
logging = HERE / 'bridge-debug.properties'
logging.write_text('handlers=java.util.logging.ConsoleHandler\n.level=INFO\njava.util.logging.ConsoleHandler.level=FINE\norg.jacamo.bridge.adapter.LocalTcpBridgeServer.level=FINE\n', encoding='utf-8', newline='\n')
for name in names:
    folder = HERE / (name + '-' + str(int(time.time() * 1000)))
    folder.mkdir(parents=True)
    old = results.get(name, {})
    previous = old.get('previousDirectories', []) + ([old['directory']] if 'directory' in old else [])
    results[name] = {'directory': str(folder), 'state': 'RUNNING', 'previousDirectories': previous}
    (HERE / 'runs.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8', newline='\n')
    command = ['java', '-Djava.awt.headless=true', '-Daudit.workbench=' + str(workbench).lower(), '-Duse.jacamo.runtime.journal.maxBytes=536870912', '-cp', properties['java.class.path'],
               'org.tzi.use.plugins.jacamo.AuthoredCaseVerificationAudit', str(PLUGIN / 'examples' / name), str(folder)]
    if prefix:
        command.insert(1, '-Daudit.transport.prefix=' + str(prefix))
    case = PLUGIN / 'examples' / name
    if not (case / 'case-manifest.json').is_file():
        candidates = []
        for specification in case.glob('*.mrv'):
            fields = dict(re.findall(r'(?m)^\s*(csl|istar|xml|ocl)\s*=\s*(\S+)\s*$', specification.read_text(encoding='utf-8-sig')))
            if {'csl', 'istar', 'xml', 'ocl'} <= fields.keys():
                candidates.append(fields)
        launchers = list(case.glob('*.jcm'))
        assert len(candidates) == len(launchers) == 1, 'Audit requires unambiguous authored inputs'
        fields = candidates[0]
        selected = {'inputs': {'jcm': launchers[0].name, 'csl': fields['csl'], 'istar': fields['istar'], 'mapping': fields['xml'], 'ocl': fields['ocl']}}
        inputs_file = folder / 'authored-inputs.json'
        inputs_file.write_text(json.dumps(selected, indent=2) + '\n', encoding='utf-8', newline='\n')
        command.insert(1, '-Daudit.inputs=' + str(inputs_file))
    environment = os.environ.copy()
    environment['JAVA_TOOL_OPTIONS'] = environment.get('JAVA_TOOL_OPTIONS', '') + ' -Djava.util.logging.config.file=' + str(logging)
    with (folder / 'live.log').open('w', encoding='utf-8') as log:
        try:
            result = subprocess.run(command, cwd=PLUGIN, stdout=log, stderr=subprocess.STDOUT, timeout=540, env=environment)
        except subprocess.TimeoutExpired:
            results[name].update(state='TIMED_OUT', exitCode=124)
            (HERE / 'runs.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8', newline='\n')
            raise
    results[name].update(state='FINISHED', exitCode=result.returncode)
    (HERE / 'runs.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(name, result.returncode, flush=True)
raise SystemExit(0 if all(r.get('exitCode') == 0 for r in results.values()) else 1)
