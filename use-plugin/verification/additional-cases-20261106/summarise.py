"""Reconcile all attempts, including unsuccessful ones; never select only passes."""
from pathlib import Path
from collections import Counter
import gzip
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
attempts = []
for directory in sorted(HERE.iterdir()):
    receipt = directory / 'audit-receipt.json'
    if not receipt.is_file():
        continue
    r = json.loads(receipt.read_bytes())
    assert r['sourceUnchanged']
    decisions = {row['token']: row for row in r['sourceDecisions'] if row['kind'] == 'DECISION'}
    releases = [row for row in r['sourceDecisions'] if row['kind'] == 'RELEASE_RECEIPT']
    for release in releases:
        decision = decisions[release['token']]
        for key in ('run', 'generation', 'session', 'revision', 'policyHash', 'sequence', 'token', 'executionScope'):
            assert decision[key] == release[key], (directory, key)
        for key in ('commandId', 'resultHash'):
            assert decision['details'][key] == release['details'][key]
        assert decision['details']['action'] == 'APPROVE'
        assert not release['details']['reviewOverride']
    native = json.load(gzip.open(directory / 'final-native.json.gz'))['result']
    semantic = native['semantic']
    row = {'directory': directory.name, 'producerRun': r['producerRun'],
           'nativeOutcomesReached': r['nativeOutcomesReached'], 'sourceUnchanged': r['sourceUnchanged'],
           'presentedCuts': r['presentedCuts'], 'nativeGoalCounts': dict(Counter(r['nativeGoals'].values())),
           'operationChecks': dict(Counter(x['kind'] + ':' + x['verdict'] for x in r['operationChecks'])),
           'reviewedFailureCount': len(r['reviewedFailures']), 'requiredEvidenceGaps': r['requiredEvidenceGaps'],
           'reconciledApprovalReceipts': len(releases), 'rawSourceCoverage': native['coverage'],
           'supportingSkipped': [{'id': x['id'], 'diagnostic': x['diagnostic']}
                                 for x in semantic['constraints'] if x['verdict'] == 'SKIPPED'],
           'finalBusinessObjects': semantic['observation']['presentationState']['objects']}
    presentation = directory / 'presentation-receipt.json'
    if presentation.is_file():
        pr = json.loads(presentation.read_bytes())
        row['presentation'] = pr['ui']
        row['dataCoverage'] = dict(Counter(c['dataCoverage'] for c in pr['cutCoverage']))
    attempts.append(row)
focused = (HERE.parent / 'additional-cases-20261106-focused.log').read_text(encoding='utf-8')
tests = sum(int(n) for n in re.findall(r'Tests run: (\d+), Failures: 0, Errors: 0, Skipped: 0,.* -- in ', focused))
assert tests == 52
summary = {'method': 'ALL_ATTEMPTS_NATIVE_RECEIPT_RECONCILIATION', 'focusedTests': tests,
           'manualDesktopAcceptance': False, 'productionChanges': False, 'attempts': attempts,
           'stabilityFullyProven': False,
           'remainingIssue': 'Initial House Building attempt lost checkpoint control requests after an approved cut; root cause not reproduced in three diagnostic reruns. Retain original failure.'}
(HERE / 'SUMMARY.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8', newline='\n')
print('ALL_ATTEMPTS_RECONCILED', len(attempts), 'FOCUSED_TESTS', tests)
for a in attempts:
    print(a['directory'], a['nativeGoalCounts'], a['presentedCuts'], a['operationChecks'], 'gaps', len(a['requiredEvidenceGaps']))
