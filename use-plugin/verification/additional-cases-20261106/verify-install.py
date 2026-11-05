"""Read-only installed identity check; do not replace any running JAR or input."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
INSTALL = REPO / 'use-assembly/target/new'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
classes = HERE / 'identity-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-d', str(classes), str(HERE.parent / 'population-coverage-20261106/PackagedIdentityProbe.java')], check=True)
classpath = os.pathsep.join([str(INSTALL / p) for p in (
    'lib/plugins/use-jacamo-plugin-1.0.1.jar', 'lib/use-core-7.5.0.jar', 'lib/use-gui.jar',
    'bridge/lib/jacamo-bridge-contract-1.0.0.jar', 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar')]
    + [(INSTALL / 'bridge/runtime-classpath.txt').read_text().strip()])
probe = subprocess.run(['java', '-cp', str(classes), 'PackagedIdentityProbe', classpath], check=True, capture_output=True, text=True)
loaded = {}
for line in probe.stdout.splitlines():
    name, path, digest = line.split('\t')
    assert Path(path).resolve().is_relative_to(INSTALL.resolve())
    assert sha(Path(path)) == digest
    loaded[name] = {'path': path, 'sha256': digest}
lock = json.loads((REPO.parent / 'jacamo/checkpoint-dependencies/dependency-lock.json').read_bytes())
for component in lock['components']:
    assert sha(INSTALL / 'bridge/runtime' / (component['artifactId'] + '-' + component['version'] + '.jar')) == component['sha256']
assert sha(INSTALL / 'bridge/runtime/checkpoint-runtime-1.0.0-SNAPSHOT.jar') == lock['runtime']['sha256']
# Rebuilt archives can have different ZIP timestamps. Compare actual class bytes.
parity = {}
for source, target in (
    ('use-plugin/target/use-plugin-1.0.1.jar', 'lib/plugins/use-jacamo-plugin-1.0.1.jar'),
    ('jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar', 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar')):
    with zipfile.ZipFile(REPO / source) as current, zipfile.ZipFile(INSTALL / target) as installed:
        names = [n for n in current.namelist() if n.endswith('.class')]
        assert set(names) == {n for n in installed.namelist() if n.endswith('.class')}
        assert all(current.read(n) == installed.read(n) for n in names)
        parity[target] = len(names)
inputs = {}
for name in ('hello-world', 'house-building'):
    case = REPO / 'use-plugin/examples' / name
    manifest = json.loads((case / 'case-manifest.json').read_bytes())
    inputs[name] = {}
    for kind, filename in manifest['inputs'].items():
        original, installed = case / filename, INSTALL / 'examples' / name / filename
        assert sha(original) == sha(installed), installed
        inputs[name][kind] = {'file': filename, 'sha256': sha(installed)}
receipt = {'method': 'READ_ONLY_FRESH_CLASSLOADER_CODE_SOURCE_AND_CLASS_BYTE_PARITY',
           'launcher': str(INSTALL / 'bin/start_use.bat'), 'loadedClasses': loaded,
           'productionClassParity': parity, 'installedInputs': inputs, 'rebuildRequired': False}
(HERE / 'installed-runtime.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8', newline='\n')
print('INSTALLED_CURRENT_CLASS_BYTES_AND_INPUTS_PASS', len(loaded))
