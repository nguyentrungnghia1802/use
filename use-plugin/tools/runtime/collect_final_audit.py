"""Retain reactor XML, packages and standalone control evidence after clean verify/probes.

Usage: python use-plugin/tools/runtime/collect_final_audit.py OUTPUT_DIRECTORY
Each run has its own source hashes and Git state; UUIDs/times are not deterministic.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[3]
module = root / 'use-plugin'
output = Path(sys.argv[1]).resolve()
output.mkdir(parents=True, exist_ok=True)
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
git = lambda *args: subprocess.check_output(['git', *args], cwd=root, text=True).strip()
files = set()
suites = []
for name in ('use-core', 'use-gui', 'use-plugin'):
    reports = sorted((root/name/'target').glob('*-reports/TEST-*.xml'))
    assert reports, 'MISSING_REACTOR_MODULE: '+name
    for report in reports:
        suite = ET.parse(report).getroot()
        counts = {k:int(suite.get(k,'0')) for k in ('tests','failures','errors','skipped')}
        assert not any(counts[k] for k in ('failures','errors','skipped')), str(report)
        suites.append(dict(module=name, suite=suite.get('name'), **counts))
        files.add(report)
for name in ('phase20-launcher','phase20-launcher-mirror','phase20-launcher-negative',
             'phase14-auction-evidence','phase19-mirror-evidence','phase24-counter-evidence'):
    directory = module/'target'/name
    assert directory.is_dir(), 'MISSING_EVIDENCE: '+name
    files.update(p for p in directory.rglob('*') if p.is_file() and p.suffix!='.class')
control = json.loads((module/'target/phase20-launcher-mirror/summary.json').read_text())
negative = json.loads((module/'target/phase20-launcher-negative/failure.json').read_text())
assert control['status']=='SUPPORTED_SUBSET_COMPLETE' and control['processExitCode']==0
assert control['traceContractChecks'].startswith('PASS:')
assert negative['failure'].endswith('PHASE20_CONTROL_ROLE_READY') and negative['mirrorState']=='LIVE'
package = module/'target/use-jacamo-plugin-1.0.1.zip'
assert package.with_suffix('.zip.sha256').read_text().split()[0]==sha(package)
files.update((package,package.with_suffix('.zip.sha256')))
source = {}
paths = set(git('ls-files').splitlines()) | set(git('ls-files','--others','--exclude-standard').splitlines())
for name in sorted(paths):
    if name.startswith(('use-plugin/src/','use-plugin/tools/runtime/','use-plugin/Core/')) or name.endswith('pom.xml'):
        p = root/name
        if p.is_file():
            source[name] = hashlib.sha256(p.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
summary = dict(schemaVersion='1.0.0', sourceRevision=git('rev-parse','HEAD'),
    workingTreeStatus=git('status','--porcelain'), command='mvn -B clean verify; launcher probes',
    counts={k:sum(s[k] for s in suites) for k in ('tests','failures','errors','skipped')},
    suites=suites, standaloneControl=control, negativeReadiness=negative,
    sourceSha256LfNormalized=source,
    artifactSha256={p.relative_to(root).as_posix():sha(p) for p in sorted(files)},
    boundaries=['Original Auction self-referencing plan and natural-language deadline unproven',
                'Organisation and Jason observations retain frozen V1 trace-only boundaries',
                'Process exit/reap is not upstream in-process thread quiescence',
                'Final user acceptance pending'])
manifest = output/'validation.json'
manifest.write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
bundle = output/'evidence.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(manifest,'validation.json')
    for p in sorted(files):
        z.write(p,p.relative_to(root).as_posix())
bundle.with_suffix('.zip.sha256').write_text(sha(bundle)+'  evidence.zip\n',encoding='ascii')
print(json.dumps(dict(counts=summary['counts'],bundle=str(bundle),sha256=sha(bundle))))
