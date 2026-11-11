#!/usr/bin/env python3
"""Record only the bounded P-CTX-01 seam; reject any change to original methods."""
from pathlib import Path
import difflib
import hashlib
import json
import zipfile
import argparse
import xml.etree.ElementTree as ET

options = argparse.ArgumentParser()
options.add_argument('--verified', action='store_true')
options = options.parse_args()

MODULE = Path(__file__).resolve().parents[1]
manifest_path = MODULE / 'src/main/resources/META-INF/use4goal-provenance.json'
manifest = json.loads(manifest_path.read_text('utf-8'))
entry = next(e for e in manifest['files'] if e['targetPath'].endswith('/GoalModelInstanceTranslator.java'))
target = MODULE / entry['targetPath']
with zipfile.ZipFile(MODULE / 'src/test/resources/oracle/pinned-original-core.zip') as archive:
    original_bytes = archive.read(entry['upstreamPath'])
assert hashlib.sha256(original_bytes).hexdigest() == entry['sourceSha256']
original = original_bytes.decode('utf-8').replace('\r\n', '\n')
patched = target.read_text('utf-8').replace('\r\n', '\n')
start = patched.index('    /** P-CTX-01: structured, in-memory topology seam.')
end = patched.index('    public static GoalModelInstanceEvaluation evaluate(', start)
assert patched[:start] + patched[end:] == original, 'UNAPPROVED_ORIGINAL_ALGORITHM_CHANGE'
# Retain the upstream file's CRLF convention; do not turn the patch into a reformat.
target.write_bytes(patched.replace('\n', '\r\n').encode('utf-8'))
patch_path = MODULE / 'provenance/patches/P-CTX-01.diff'
patch_path.parent.mkdir(parents=True, exist_ok=True)
patch_path.write_text(''.join(difflib.unified_diff(original.splitlines(True), patched.splitlines(True),
    fromfile=entry['upstreamPath'], tofile=entry['targetPath'])), encoding='utf-8')
entry['targetSha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
entry['appliedPatchIds'] = ['P-CTX-01']
entry['extractionStatus'] = 'PATCHED_WITH_PROVENANCE'
added = MODULE / 'src/main/java/org/vnu/sme/goal/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTopology.java'
for patch in manifest['plannedPatches']:
    if patch['id'] == 'P-CTX-01':
        if options.verified:
            suite = ET.parse(MODULE / 'target/surefire-reports/TEST-org.tzi.use.semantic.engine.OriginalRuntimeSessionTest.xml').getroot()
            assert int(suite.attrib['tests']) >= 13 and all(int(suite.attrib[k]) == 0 for k in ('failures', 'errors', 'skipped')), 'PARITY_TEST_EVIDENCE_REQUIRED'
        patch.update(status='APPLIED_VERIFIED' if options.verified else 'APPLIED_PENDING_PARITY', patchPath='provenance/patches/P-CTX-01.diff',
            patchSha256=hashlib.sha256(patch_path.read_bytes()).hexdigest(),
            newApiFiles=[{'targetPath': added.relative_to(MODULE).as_posix(),
                          'sha256': hashlib.sha256(added.read_bytes()).hexdigest()}],
            originalMethodsUnchanged=True, lineEndingsPreserved=True)
manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
print(json.dumps({'status':'SOURCE_SCOPE_AND_PARITY_PASS' if options.verified else 'SOURCE_SCOPE_PASS_PARITY_PENDING','targetSha256':entry['targetSha256']}))
