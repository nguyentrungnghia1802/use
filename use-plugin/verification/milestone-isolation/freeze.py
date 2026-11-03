"""Bind this narrow fix, authentic regression, verified live run and installed bytes."""
import datetime, hashlib, json, pathlib, subprocess
base = pathlib.Path(__file__).resolve().parent
use = base.parents[2]
def row(path):
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'path': str(path), 'sha256': digest}
def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()
package = json.loads((base / 'milestone-final-package-receipt.json').read_text(encoding='utf-8-sig'))
live = base / 'live-1793520132787'
receipt = json.loads((live / 'receipt.json').read_text())
assert receipt['nativeRootsFulfilled'] == 2 and receipt['automaticReleases'] > 0
assert receipt['displayedSourceCuts'] >= receipt['automaticReleases']
assert receipt['sameMSystem'] and receipt['originalAuctionUnchanged'] and not receipt['manualAcknowledgement']
assert all(view['verdict'] == 'COMPLETE_OBSERVED' for view in receipt['navigation'].values())
assert package['strictSourceHooks'] and package['currentBuildBytesVerified'] and package['installedPluginLoader']
preserved = json.loads((base / 'preserved-install.json').read_text(encoding='utf-8-sig'))
fixture = json.loads((base / 'user-fixture-provenance.json').read_text())
assert preserved['journalSha256'] == fixture['journalSha256'] == row(pathlib.Path(preserved['preservedUserJournal']))['sha256']
log = (base / 'final-package.log').read_text()
assert 'Tests run: 42, Failures: 0, Errors: 0, Skipped: 0' in log and 'BUILD SUCCESS' in log
heads = {}
for repo in [use, use.parent / 'jacamo']:
    assert git(repo, 'branch', '--show-current') == 'main'
    heads[repo.name] = git(repo, 'rev-parse', 'HEAD')
assert not git(use.parent / 'jacamo', 'diff', '--name-only', '--', 'examples/auction')
paths = [use / 'plan.md', use.parent / 'plan.md', use.parent / 'AGENTS.md']
paths += [use / 'use-plugin/src/main/java/org/tzi/use/plugins/jacamo/ui/use4goal/conformance' / name
          for name in ['ObservedConformancePanel.java', 'CheckpointTimeline.java', 'ExecutionScopeViews.java', 'ObservedConformancePresenter.java']]
paths += [use / 'use-plugin/src/test/java/org/tzi/use/plugins/jacamo/ui/CheckpointMilestoneIsolationTest.java',
          use / 'use-plugin/src/test/java/org/tzi/use/plugins/jacamo/CheckpointMilestoneAuctionIT.java',
          use / 'use-plugin/src/test/resources/semantics/checkpoint-milestone-user-run.json.gz']
evidence = [p for p in base.rglob('*') if p.is_file() and p.name != 'final-freeze.json']
value = {'version': 'MILESTONE_ISOLATION_FREEZE/1.0', 'recordedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'heads': heads, 'sources': [row(p) for p in paths],
         'installedJars': [row(p) for p in sorted((use / 'use-assembly/target/new').rglob('*.jar'))],
         'evidence': [row(p) for p in sorted(evidence)], 'liveReceipt': receipt, 'packageReceipt': package,
         'manualDesktopAcceptance': False, 'commitOrPush': False}
(base / 'final-freeze.json').write_text(json.dumps(value, indent=2), encoding='utf-8')
print('MILESTONE_ISOLATION_FREEZE_PASS', package['jacamoSha256'])
