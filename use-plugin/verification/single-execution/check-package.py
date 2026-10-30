"""Run from repository root after package; retain a fresh extracted final install."""
from pathlib import Path
import hashlib, json, shutil, tempfile, zipfile

root = Path.cwd()
evidence = root / 'use-plugin/verification/single-execution'
live = root / 'use-plugin/verification/live-results-ui/auction-3514017882445095492'
archive = root / 'use-assembly/target/use-7.5.0-use-bin.zip'
sha = lambda value: hashlib.sha256(value).hexdigest()
directory = Path(tempfile.mkdtemp(prefix='single-execution-final-', dir=root / 'use-assembly/target'))
with zipfile.ZipFile(archive) as package:
    for name in package.namelist():
        assert (directory / name).resolve().is_relative_to(directory.resolve()), name
    package.extractall(directory)
install = directory / 'use-7.5.0'
plugin_path = install / 'lib/plugins/use-jacamo-plugin-1.0.1.jar'
manifest = json.loads((live / 'replay/program.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(plugin_path) as plugin:
    # Actual TCP replay freezes backend resource/class bytes. UI polish may not alter them.
    for name, expected in manifest['implementation'].items():
        entry = name.lstrip('/') if name.startswith('/') else name.replace('.', '/') + '.class'
        providers=[]
        if entry in plugin.namelist():providers.append(sha(plugin.read(entry)))
        for host in sorted((install / 'lib').glob('*.jar')):
            with zipfile.ZipFile(host) as library:
                if entry in library.namelist():providers.append(sha(library.read(entry)))
        assert providers and all(value==expected for value in providers), ('live-backend-drift', name, providers)
    count = 0
    for source in (root / 'use-plugin/target/classes/org/tzi/use/plugins/jacamo/ui').rglob('*.class'):
        entry = source.relative_to(root / 'use-plugin/target/classes').as_posix()
        assert plugin.read(entry) == source.read_bytes(), entry
        count += 1
    prior = json.loads((root / 'use-plugin/verification/result-scope/receipt.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(Path(prior['install']) / 'lib/plugins/use-jacamo-plugin-1.0.1.jar') as previous:
        native = [name for name in plugin.namelist() if name.endswith('.class') and
                  (name.startswith('org/vnu/') or name.startswith('org/tzi/use/semantic/engine/'))]
        for name in native:
            assert plugin.read(name) == previous.read(name), ('original-engine-drift', name)
    original = {name: sha(plugin.read('org/vnu/sme/goal/verify/conformance/semantics/' + name + '.class'))
                for name in ['GoalMarking', 'TaskMarking', 'IStarMarking', 'IStarPropagation']}
case_files = list((root / 'use-plugin/examples/auction').rglob('*'))
for source in case_files:
    if source.is_file():
        target = install / 'examples/auction-current' / source.relative_to(root / 'use-plugin/examples/auction')
        assert target.read_bytes() == source.read_bytes(), source
driver = directory / 'isolated-driver'
entry = Path('org/tzi/use/plugins/jacamo/semantics/CertifiedExecutionReplayMain.class')
(driver / entry).parent.mkdir(parents=True)
shutil.copy2(root / 'use-plugin/target/test-classes' / entry, driver / entry)
for name in ['bound-aol-1.png', 'bound-aol-2.png', 'bound-execution-1.png', 'bound-execution-2.png', 'bound-iscn-1.png', 'bound-iscn-2.png']:
    shutil.copy2(root / 'use-plugin/target/state-views' / name, evidence / name)
checks = dict(status='PASS', install=str(install), isolatedDriver=str(driver),
              archiveSha256=sha(archive.read_bytes()), liveImplementationEntriesEqualPackage=len(manifest['implementation']),
              packagedUiClassesEqualTested=count, originalEngineClassesEqualPriorAcceptedPackage=len(native),
              originalMarkingHashes=original, currentCaseFilesEqualPackage=True,
              liveEvidence=str(live.relative_to(root)))
(evidence / 'package-checks.json').write_text(json.dumps(checks, indent=2) + '\n', encoding='utf-8')
print(json.dumps(checks, indent=2))
