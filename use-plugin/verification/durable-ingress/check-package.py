"""Link final source/classes/package to focused tests and observed Auction evidence."""
from pathlib import Path
import fnmatch, hashlib, io, json, re, shutil, struct, subprocess, zipfile
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[3]
out=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
install=root/'use-assembly/target/new'
archive=root/'use-assembly/target/use-7.5.0-use-bin.zip'
live=root/'use-plugin/verification/live-results-ui/auction-14420620117277149684'
assert json.loads((live/'receipt.json').read_text())['status']=='PASS'
assert json.loads((live/'replay-receipt.json').read_text())=={'entries':1728,'status':'PASS'}
assert not json.loads((live/'transport.json').read_text())['journalGap']
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'use-plugin/pom.xml')
excludes=[e.text for p in pom.findall('.//m:plugin',ns) if p.findtext('m:artifactId',namespaces=ns)=='maven-jar-plugin' for e in p.findall('.//m:exclude',ns)]
parity={}
for module,location in [('use-plugin','lib/plugins/use-jacamo-plugin-1.0.1.jar'),('jacamo-bridge-contract','bridge/lib/jacamo-bridge-contract-1.0.0.jar'),('jacamo-bridge-jacamo','bridge/lib/jacamo-bridge-jacamo-1.0.0.jar')]:
    data=(install/location).read_bytes()
    with zipfile.ZipFile(archive) as package:assert data==package.read('use-7.5.0/'+location)
    with zipfile.ZipFile(io.BytesIO(data)) as library:
        count=0
        for path in (root/module/'target/classes').rglob('*.class'):
            key=path.relative_to(root/module/'target/classes').as_posix()
            if module=='use-plugin' and any(fnmatch.fnmatchcase(key,p) for p in excludes):continue
            assert path.read_bytes()==library.read(key),key
            count+=1
        parity[module]=count
        if module=='use-plugin':
            for name in ['use4goal-workflow-provenance.json','use4goal-view-provenance.json']:
                key='META-INF/'+name
                assert library.read(key)==(root/'use-plugin/src/main/resources'/key).read_bytes()
                for entry in json.loads(library.read(key))['files']:
                    assert sha((root/'use-plugin'/entry['targetPath']).read_bytes())==entry['targetSha256']
frozen=json.loads((live/'replay/program.json').read_text(encoding='utf-8'))
for name,expected in frozen['implementation'].items():
    key=name.lstrip('/') if name.startswith('/') else name.replace('.','/')+'.class'
    hashes=[]
    for path in [install/'lib/plugins/use-jacamo-plugin-1.0.1.jar',*sorted((install/'lib').glob('*.jar'))]:
        with zipfile.ZipFile(path) as library:
            if key in library.namelist():hashes.append(sha(library.read(key)))
    assert hashes and all(h==expected for h in hashes),name
logs=['durable-ingress-tests.log','durable-ingress-package.log','durable-ingress-final-package.log','durable-ingress-closeout.log','durable-ingress-bootstrap-closeout.log','durable-ingress-delivery.log','durable-ingress-lifecycle-final.log','durable-ingress-gui-first.log','durable-ingress-gui-probe.log']
for name in logs:shutil.copy2(root/'use-plugin/target'/name,out/name)
probe=(out/'durable-ingress-gui-probe.log').read_text(encoding='utf-8',errors='replace')
counts=[int(v) for v in re.findall(r'UI=milestones=(\d+)',probe)]
assert counts and len(set(counts))>1,(counts,'Milestones must publish progressively')
assert 'RESULT=CURRENT_OBSERVED' in probe
assert re.findall(r'pending=(\d+)',probe)[-1]=='0'
heartbeat=re.search(r'HEARTBEAT p95Ms=(\d+) maxMs=(\d+)',probe)
assert heartbeat
spool=Path(re.findall(r'spool=([^,}]+)',probe)[-1])
frames=0
with spool.open('rb') as stream:
    while header:=stream.read(4):
        assert len(header)==4
        size=struct.unpack('>i',header)[0];expected=stream.read(32);frame=stream.read(size)
        assert len(frame)==size and hashlib.sha256(frame).digest()==expected
        json.loads(frame);frames+=1
assert int(Path(str(spool)+'.cursor').read_text())==frames
changed=subprocess.check_output(['git','ls-files','-m','-o','--exclude-standard'],cwd=root,text=True).splitlines()
freeze={name:sha((root/name).read_bytes()) for name in changed if (root/name).is_file() and '/verification/' not in name}
(out/'source-freeze.json').write_text(json.dumps(freeze,indent=2),encoding='utf-8')
receipt={'status':'PASS_WITH_NATIVE_WRITER_LATENCY_LIMIT','liveEvidence':str(live.relative_to(root)),'sourceReplayEntries':1728,'finalBackendMatchesSourceReplay':True,'testedClassParity':parity,'archiveSha256':sha(archive.read_bytes()),'sourceFreezeSha256':sha((out/'source-freeze.json').read_bytes()),'heartbeatMs':{'timer':100,'p95':int(heartbeat[1]),'max':int(heartbeat[2])},'milestoneSamples':counts,'finalPendingFrames':0,'manualDesktopGui':False,'otherLiveCases':False,'nativeWriterRemainsOnEdt':True,'automaticCrossSessionRecovery':False,'launcher':str(install/'bin/start_use.bat')}
receipt['closedSpoolIntegrity']={'path':str(spool),'frames':frames,'allHashesValid':True,'consumptionCursorMatches':True}
receipt['guiProbePluginSha256']='bae8b4ada642452583d9ebaa498aac9d1a59bb2eccb13c445311d2a21a726d29'
receipt['afterGuiProbe']='Only additional transport lifecycle guard: reject a second subscription while the previous delivery worker retires. Covered by final DurableIngressTest; UI and native verification unchanged.'
(out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print(json.dumps(receipt,indent=2))
