#!/usr/bin/env python3
"""Record P-IADM-01 against frozen bytes; never read/write the reference checkout.

Only the validation overload/dispatch and bounded neutral admission block may
change. No original recurrence, topology or propagation algorithm is patched.
"""
from pathlib import Path
import argparse
import difflib
import hashlib
import json
import zipfile
import xml.etree.ElementTree as ET

MODULE = Path(__file__).resolve().parents[1]
MANIFEST = MODULE / 'src/main/resources/META-INF/use4goal-provenance.json'

def reverse_scope(name: str, text: str) -> str:
    if name == 'GoalModelValidator.java':
        start = text.index('        // P-IADM-01: strict public default is preserved.')
        end = text.index('        List<String> errors = new ArrayList<>();', start)
        text = text[:start] + text[end:]
        text = text.replace('''        if (policy == GoalModelValidationPolicy.STRICT_ORIGINAL)
            validateRefinementParentsAreNotDependencyEndpoints(model, errors);
        else validateDerivedAndDependees(model, errors);''',
                            '        validateRefinementParentsAreNotDependencyEndpoints(model, errors);')
        start = text.index('    // P-IADM-01-BEGIN:')
        end = text.index('    // P-IADM-01-END', start) + len('    // P-IADM-01-END\n\n')
        text = text[:start] + text[end:]
    elif name == 'IStarCompiler.java':
        text = text.replace('import org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy;\n', '')
        start = text.index('        // P-IADM-01: existing entry points retain strict upstream validation.')
        end = text.index('        List<String> errors = new ArrayList<>();', start)
        text = text[:start] + text[end:]
        text = text.replace('GoalModelValidator.validate(model, policy)', 'GoalModelValidator.validate(model)')
    else:
        raise AssertionError('UNREGISTERED_PATCH_TARGET')
    return text

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--verified', action='store_true')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text('utf-8'))
    diffs = []
    with zipfile.ZipFile(MODULE / 'src/test/resources/oracle/pinned-original-core.zip') as archive:
        for name in ('GoalModelValidator.java', 'IStarCompiler.java'):
            entry = next(e for e in manifest['files'] if e['targetPath'].endswith('/' + name))
            original_bytes = archive.read(entry['upstreamPath'])
            assert hashlib.sha256(original_bytes).hexdigest() == entry['sourceSha256']
            original = original_bytes.decode('utf-8').replace('\r\n', '\n')
            target = MODULE / entry['targetPath']
            patched = target.read_text('utf-8').replace('\r\n', '\n')
            assert reverse_scope(name, patched) == original, 'UNAPPROVED_ORIGINAL_ALGORITHM_CHANGE:' + name
            target.write_bytes(patched.replace('\n', '\r\n').encode('utf-8'))
            diffs.append(''.join(difflib.unified_diff(original.splitlines(True), patched.splitlines(True),
                         fromfile=entry['upstreamPath'], tofile=entry['targetPath'])))
            entry.update(targetSha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                         extractionStatus='PATCHED_WITH_PROVENANCE', reuseMode='PATCHED_WITH_PROVENANCE',
                         appliedPatchIds=['P-IADM-01'], plannedPatchIds=['P-IADM-01'],
                         acceptance='Strict default parity; opt-in extension is versioned expected difference, not upstream equivalence')
    patch_path = MODULE / 'provenance/patches/P-IADM-01.diff'
    patch_path.write_text(''.join(diffs), 'utf-8')
    api = MODULE / 'src/main/java/org/vnu/sme/goal/dsl/istar/mm/GoalModelValidationPolicy.java'
    patch = dict(id='P-IADM-01', status='APPLIED_PENDING_PARITY',
                 patchPath=patch_path.relative_to(MODULE).as_posix(), patchSha256=hashlib.sha256(patch_path.read_bytes()).hexdigest(),
                 newApiFiles=[dict(targetPath=api.relative_to(MODULE).as_posix(), sha256=hashlib.sha256(api.read_bytes()).hexdigest())],
                 strictDefaultPreserved=True, originalRecurrenceUnchanged=True, lineEndingsPreserved=True,
                 profile='DERIVED_AND_RESOURCE_V1', validationPolicy='DERIVED_AND_DEPENDEE_V1',
                 expectedDifference='Derived same-owner condition-free Achieve AND parent may be dependee only; external resource/context gates required')
    if args.verified:
        for name, minimum in [('DerivedDependeePolicyTest', 5), ('IStarFrontendParityTest', 29),
                              ('OriginalRuntimeSessionTest', 13), ('EngineExtractionTest', 5)]:
            suite = ET.parse(MODULE / f'target/surefire-reports/TEST-org.tzi.use.semantic.engine.{name}.xml').getroot()
            assert int(suite.attrib['tests']) >= minimum and all(int(suite.attrib[k]) == 0 for k in ('failures','errors','skipped')), name
        patch['status'] = 'APPLIED_VERIFIED'
    manifest['plannedPatches'] = [p for p in manifest['plannedPatches'] if p['id'] != patch['id']] + [patch]
    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', 'utf-8')
    print(json.dumps({'status': patch['status'], 'patchSha256': patch['patchSha256']}))

if __name__ == '__main__':
    main()
