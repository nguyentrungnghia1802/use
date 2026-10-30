"""Check tested classes and the single runnable installation, without another extraction."""
from pathlib import Path
import hashlib, json, io, shutil, subprocess, zipfile, fnmatch
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[3]
out=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
live=max((root/'use-plugin/verification/live-results-ui').glob('auction-*'),key=lambda p:p.stat().st_mtime)
raw=json.loads((live/'result.json').read_text())
contexts=raw['semantic']['observation']['originalEngine']['contexts']
roots=[r for c in contexts for r in c['occurrences'].values() if r.get('sourceElement')=='O_root']
assert len(roots)==len(contexts)==2 and all(r['aggregateStatus']=='FULFILLED' and r.get('executionQuality') in ['KNOWN_DERIVED','KNOWN_DERIVED_HISTORY'] for r in roots)
assert not json.loads((live/'transport.json').read_text())['journalGap']
archive=root/'use-assembly/target/use-7.5.0-use-bin.zip'
install=root/'use-assembly/target/new'
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'use-plugin/pom.xml')
excludes=[e.text for p in pom.findall('.//m:plugin',ns) if p.findtext('m:artifactId',namespaces=ns)=='maven-jar-plugin' for e in p.findall('.//m:exclude',ns)]
with zipfile.ZipFile(archive) as distribution:
    data=distribution.read('use-7.5.0/lib/plugins/use-jacamo-plugin-1.0.1.jar')
    assert data==(install/'lib/plugins/use-jacamo-plugin-1.0.1.jar').read_bytes()
with zipfile.ZipFile(io.BytesIO(data)) as plugin:
    count=0
    for path in (root/'use-plugin/target/classes/org/tzi/use/plugins/jacamo').rglob('*.class'):
        key=path.relative_to(root/'use-plugin/target/classes').as_posix()
        if any(fnmatch.fnmatchcase(key,p) for p in excludes):continue
        assert path.read_bytes()==plugin.read(key),key
        count+=1
    for manifest in ['use4goal-workflow-provenance.json','use4goal-view-provenance.json']:
        key='META-INF/'+manifest
        assert plugin.read(key)==(root/'use-plugin/src/main/resources'/key).read_bytes()
        for entry in json.loads(plugin.read(key))['files']:
            assert sha((root/'use-plugin'/entry['targetPath']).read_bytes())==entry['targetSha256']
    frozen=json.loads((live/'diagnostic-replay/program.json').read_text(encoding='utf-8'))
    for name,expected in frozen['implementation'].items():
        key=name.lstrip('/') if name.startswith('/') else name.replace('.','/')+'.class'
        hashes=[]
        for path in [install/'lib/plugins/use-jacamo-plugin-1.0.1.jar',*sorted((install/'lib').glob('*.jar'))]:
            with zipfile.ZipFile(path) as library:
                if key in library.namelist():hashes.append(sha(library.read(key)))
        assert hashes and all(h==expected for h in hashes),name
for name in ['responsive-tests.log','responsive-package.log','responsive-final-package.log','responsive-delivery-package.log','responsive-closeout-package.log','runtime-lag-probe-final.log','runtime-lag-probe-after.log']:
    shutil.copy2(root/'use-plugin/target'/name,out/name)
changed=subprocess.check_output(['git','ls-files','-m','-o','--exclude-standard'],cwd=root,text=True).splitlines()
freeze={name:sha((root/name).read_bytes()) for name in changed if (root/name).is_file() and '/verification/' not in name}
(out/'source-freeze.json').write_text(json.dumps(freeze,indent=2),encoding='utf-8')
receipt={'status':'PERFORMANCE_SCOPE_PASS_WITH_LIVE_TEST_LIMITATION','liveEvidence':str(live.relative_to(root)),'liveRoots':'both FULFILLED with native execution qualification','liveJournalGap':False,'unresolvedGate':'AuthoredAuctionResultsIT expects five capability occurrences per execution; actual captured source scope has fewer admitted players. Original assertion retained; two failing attempts preserved. No full-case PASS claimed.','archiveSha256':sha(archive.read_bytes()),'testedClassParity':count,'launcher':str(install/'bin/start_use.bat'),'sourceFreezeSha256':sha((out/'source-freeze.json').read_bytes()),'manualDesktopGui':False,'otherLiveCases':False}
replay=(out/'installed-replay.log').read_text()
assert 'INSTALLED_CERTIFIED_EXECUTION_REPLAY_PASS' in replay
receipt['installedReplay']=replay.strip()
receipt['finalBackendEqualRecordedReplayImplementation']=True
(out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print(json.dumps(receipt,indent=2))
