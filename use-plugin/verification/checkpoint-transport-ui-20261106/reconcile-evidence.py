"""Check every recorded release against the exact native cut and presentation."""
from pathlib import Path
from collections import Counter
import gzip
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
canonical = lambda value: json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
attempts = []
for folder in sorted(p for p in HERE.iterdir() if p.is_dir() and (p.name.startswith(('house-building-', 'hello-world-', 'auction-', 'installed-house-building-')))):
    receipt = folder / 'audit-receipt.json'
    if not receipt.exists():
        log = (folder / 'live.log').read_text(encoding='utf-8', errors='replace') if (folder / 'live.log').exists() else ''
        reason = 'NO_FINAL_RECEIPT'
        if 'NoSuchFileException' in log and 'case-manifest.json' in log:
            reason = 'AUDIT_INPUT_SELECTION_ERROR_BEFORE_PREPARATION'
        elif 'ENGINE_INSTALLATION_DUPLICATE_RESOURCE' in log:
            reason = 'AUDIT_CONSUMER_CLASSPATH_ERROR_GUARD_REJECTED'
        attempts.append({'directory': folder.name, 'acceptance': False, 'reason': reason})
        continue
    r = json.loads(receipt.read_bytes())
    assert r['sourceUnchanged']
    cuts = {}
    for path in folder.glob('cut-*.json.gz'):
        with gzip.open(path) as source:
            cut = json.load(source)
        token = cut['identity']['token']
        cut['inventoryHash'] = hashlib.sha256(canonical({'version': 'APPLICABLE_NATIVE_REQUIRED_INVENTORY/1.0',
            'nativeResultHash': cut['native']['result']['resultHash'], 'checks': cut['checks']})).hexdigest()
        cuts[token] = cut
    decisions = {row['details']['commandId']: row for row in r['sourceDecisions'] if row['kind'] in ('DECISION', 'ACKNOWLEDGED')}
    ui = json.loads((folder / 'live-workbench.json').read_bytes()) if (folder / 'live-workbench.json').exists() else None
    approvals = acknowledgements = 0
    for release in (row for row in r['sourceDecisions'] if row['kind'] == 'RELEASE_RECEIPT'):
        decision = decisions[release['details']['commandId']]
        for key in ('run', 'generation', 'session', 'revision', 'policyHash', 'sequence', 'token', 'barrierEpoch', 'executionScope'):
            assert release[key] == decision[key], (folder, key)
        assert release['details']['resultHash'] == decision['details']['resultHash']
        cut = cuts[release['token']]
        assert cut['inventoryHash'] == release['details']['resultHash']
        assert cut['checks'] == decision['details']['checks']
        assert cut['native']['result']['stateHash'] == decision['details']['acceptedUseCutHash']
        if ui:
            assert ui['displayedNativeResults'][release['token']] == cut['native']['result']['resultHash']
        if decision['kind'] == 'ACKNOWLEDGED':
            acknowledgements += 1
            assert release['details']['reviewOverride']
        else:
            approvals += 1
            assert not release['details']['reviewOverride']
            assert all(c['truth'] == 'PASS' for c in cut['checks'].values() if c['applicability'] == 'APPLICABLE')
    assert len(cuts) == r['presentedCuts']
    attempts.append({'directory': folder.name, 'acceptance': not r['requiredEvidenceGaps'] and (r['nativeOutcomesReached'] or r.get('transportPrefixChecked', False)),
        'producerRun': r['producerRun'], 'sourceUnchanged': r['sourceUnchanged'], 'presentedCuts': r['presentedCuts'],
        'nativeOutcomesReached': r['nativeOutcomesReached'], 'nativeGoalCounts': dict(Counter(r['nativeGoals'].values())),
        'transportPrefixChecked': r.get('transportPrefixChecked', False), 'requiredEvidenceGaps': r['requiredEvidenceGaps'],
        'nativeOperationChecks': dict(Counter(v['kind'] + ':' + v['verdict'] for v in r['operationChecks'])),
        'reviewedFailureCount': len(r['reviewedFailures']), 'matchedApprovals': approvals, 'matchedReviewedReleases': acknowledgements,
        'ui': {k: v for k, v in ui.items() if k != 'displayedNativeResults'} if ui else None})
log = (HERE / 'release-validation.log').read_text(encoding='utf-8')
tests = sum(int(n) for n in re.findall(r'Tests run: (\d+), Failures: 0, Errors: 0, Skipped: 0,.* -- in ', log))
assert tests == 80
summary = {'method': 'ALL_ATTEMPTS_EXACT_NATIVE_INVENTORY_RELEASE_AND_RENDER_RECONCILIATION', 'focusedTests': tests,
    'manualDesktopAcceptance': False, 'attempts': attempts}
(HERE / 'SUMMARY.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8', newline='\n')
print('RECONCILED_ALL_ATTEMPTS', len(attempts), 'FOCUSED_TESTS', tests)
