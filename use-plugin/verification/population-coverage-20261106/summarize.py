"""Reconcile source releases; preserve native truth and raw history coverage."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
previous = HERE.parent / 'accuracy-audit-20261105/summarize.py'
spec = importlib.util.spec_from_file_location('source_receipt_reconciliation', previous)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.HERE = HERE
run = json.loads((HERE / 'latest-run.json').read_bytes())
assert run['exitCode'] == 0
reconciled = module.summarize(Path(run['directory']))
presentation = json.loads((HERE / 'presentation-receipt.json').read_bytes())
assert len(presentation['cuts']) == reconciled['presentedCuts']
assert all(c['compatibility'] == 'PASS' and c['dataCoverage'] == 'COMPLETE' and c['rawSourceCoverage'] == 'INCOMPLETE' for c in presentation['cuts'])
tests = {}
for name in ('focused-build-current.log', 'ui-build.log'):
    data = (HERE / name).read_text(encoding='utf-8', errors='replace')
    rows = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: .*? -- in (\S+)', data)
    counts = {key: sum(int(row[i]) for row in rows) for i, key in enumerate(('tests', 'failures', 'errors', 'skipped'))}
    assert 'BUILD SUCCESS' in data and counts['tests'] and not any(counts[k] for k in ('failures', 'errors', 'skipped'))
    tests[name] = {**counts, 'sha256': sha(HERE / name), 'classes': [row[4] for row in rows]}
package = json.loads((HERE / 'package-receipt.json').read_bytes())
assert len(package['loadedClasses']) == 14
for identity in package['loadedClasses'].values():
    assert sha(Path(identity['path'])) == identity['sha256']
result = {'method': 'EXACT_CURRENT_POPULATION_AND_NATIVE_REQUIREMENT_COVERAGE', 'focusedTests': tests,
          'liveAuction': reconciled, 'presentation': presentation, 'packageReceiptSha256': sha(HERE / 'package-receipt.json'),
          'authoredModelsUnchanged': True, 'deadlineAssertionActive': False, 'manualDesktopAcceptance': False,
          'legacyEventPrefixesNotUpgraded': True,
          'artifacts': {n: sha(HERE / n) for n in ('csl-verification-coverage.png', 'istar-verification-coverage.png', 'launcher-cli.log')}}
(HERE / 'summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
print('CURRENT_POPULATION_AND_COVERAGE_EVIDENCE_PASS', tests['focused-build-current.log']['tests'], reconciled['presentedCuts'])
