"""Refresh the existing runnable installation; preserve work/replays and backups.

This is an incremental source-JAR refresh, not a clean release assembly. The
baseline release descriptor still references user-deleted legacy Core assets.
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

here = Path(__file__).resolve().parent
repo = here.parents[2]
install = (repo / 'use-assembly/target/new').resolve()
assert install.is_relative_to((repo / 'use-assembly/target').resolve())
assert (install / 'bin/start_use.bat').is_file(), 'Existing base installation is required'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
backup = install / 'work/package-backups' / stamp
copied = {}

def copy(source, relative):
    source = Path(source)
    assert source.is_file(), source
    target = install / relative
    if target.is_file() and sha(target) != sha(source):
        saved = backup / relative
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, saved)
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.is_file() or sha(target) != sha(source):
        staged = target.with_name(target.name + '.population-staged')
        shutil.copy2(source, staged)
        assert sha(source) == sha(staged)
        os.replace(staged, target)
    assert sha(source) == sha(target)
    copied[str(relative).replace('\\', '/')] = sha(target)

for source, target in (
    ('use-core/target/use-core-7.5.0.jar', 'lib/use-core-7.5.0.jar'),
    ('use-gui/target/use-gui.jar', 'lib/use-gui.jar'),
    ('use-plugin/target/use-plugin-1.0.1.jar', 'lib/plugins/use-jacamo-plugin-1.0.1.jar'),
    ('jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar', 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'),
    ('jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar', 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'),
):
    copy(repo / source, target)

lock_path = repo.parent / 'jacamo/checkpoint-dependencies/dependency-lock.json'
lock = json.loads(lock_path.read_text())
runtime = repo / 'jacamo-bridge-jacamo/target/producer-runtime'
for jar in runtime.glob('*.jar'):
    copy(jar, Path('bridge/runtime') / jar.name)
copy(lock_path, 'bridge/dependency-lock.json')
copy(repo / 'use-plugin/tools/prepare-runtime-classpath.ps1', 'tools/prepare-runtime-classpath.ps1')
copy(repo / 'use-plugin/tools/refresh-authored-hashes.py', 'tools/refresh-authored-hashes.py')
for file in (repo / 'use-gui/src/main/resources/bin').glob('*'):
    if file.is_file(): copy(file, Path('bin') / file.name)

for component in lock['components']:
    expected = component['artifactId'] + '-' + component['version'] + '.jar'
    selected = [p for p in (install / 'bridge/runtime').glob('*.jar')
                if re.fullmatch(re.escape(component['artifactId']) + r'-[0-9].*\.jar', p.name)]
    assert len(selected) == 1 and selected[0].name == expected, selected
    assert sha(selected[0]) == component['sha256'], expected
assert sha(install / 'bridge/runtime/checkpoint-runtime-1.0.0-SNAPSHOT.jar') == lock['runtime']['sha256']

# Authored example inputs only; preserve generated layouts/builds at both ends.
examples = repo / 'use-plugin/examples'
for case in examples.iterdir():
    if not case.is_dir(): continue
    destination = 'auction-current' if case.name == 'auction' else case.name
    for file in case.rglob('*'):
        relative = file.relative_to(case)
        if not file.is_file() or any(p in {'build', 'target', '.gradle', '.git'} for p in relative.parts): continue
        if file.suffix == '.mrv':
            # Authored system specifications are inputs; USE saved diagram layouts
            # with the same extension remain untouched runtime output.
            if file.stat().st_size > 65536: continue
            try: entries=set(re.findall(r'(?mi)^\s*(csl|istar|xml|mapping)\s*[:=]',file.read_text(encoding='utf-8-sig')))
            except UnicodeError: continue
            if not {'csl','istar'} <= entries or not entries.intersection({'xml','mapping'}): continue
        copy(file, Path('examples') / destination / relative)

subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File',
                str(install / 'tools/prepare-runtime-classpath.ps1')], check=True)
classpath = os.pathsep.join([str(install / 'lib/plugins/use-jacamo-plugin-1.0.1.jar'),str(install / 'lib/use-core-7.5.0.jar'),str(install / 'lib/use-gui.jar'),str(install / 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'),
                            str(install / 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'),
                            (install / 'bridge/runtime-classpath.txt').read_text()])
classes = repo / 'use-plugin/target/package-identity-probe'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '-d', str(classes), str(here / 'PackagedIdentityProbe.java')], check=True)
probe = subprocess.run(['java', '-cp', str(classes), 'PackagedIdentityProbe', classpath], check=True,
                       capture_output=True, text=True)
loaded = {}
for line in probe.stdout.splitlines():
    name, path, digest = line.split('\t')
    assert Path(path).resolve().is_relative_to(install)
    assert sha(Path(path)) == digest
    loaded[name] = {'path': path, 'sha256': digest}
assert loaded['jacamo.infra.JaCaMoLauncher']['sha256'] == next(c['sha256'] for c in lock['components'] if c['component'] == 'jacamo')
descriptor = ET.parse(repo / 'use-plugin/src/assembly/release.xml').getroot()
ns = {'a': descriptor.tag.split('}')[0][1:]}
missing = [n.text for n in descriptor.findall('.//a:file/a:source', ns)
           if n.text.startswith('Core/') and not (repo / 'use-plugin' / n.text).is_file()]
receipt = {'method': 'INCREMENTAL_SOURCE_JAR_REFRESH', 'launcher': str(install / 'bin/start_use.bat'),
           'retainedBaseResources': True, 'cleanReleaseAssemblyClaimed': False,
           'missingLegacyReleaseInputs': missing, 'backup': str(backup), 'files': copied, 'loadedClasses': loaded}
(here / 'package-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', newline='\n')
(install / 'source-checkpoint-build.json').write_text(json.dumps(receipt, indent=2) + '\n', newline='\n')
print('PACKAGED_CURRENT_RUNTIME_IDENTITIES_PASS', loaded['jacamo.infra.JaCaMoLauncher'])
