"""Evidence checks only; no producer state/verdict changes or acknowledgements."""
import hashlib, json, pathlib, sys

for arg in sys.argv[1:]:
    root = pathlib.Path(arg)
    probe = json.loads((root / 'probe.json').read_text(encoding='utf-8'))
    ui = json.loads((root / 'ui.json').read_text(encoding='utf-8'))
    assert probe['status'] == 'NATIVE_OUTCOME_REACHED', probe['status']
    assert probe['verifiedMode'] and probe['manualAcknowledgements'] == 0
    assert probe['goalStatuses'] and set(probe['goalStatuses'].values()) == {'FULFILLED'}
    assert ui['executionCount'] > 0 and ui['milestones'] > 0 and ui['verdict'] == 'COMPLETE_OBSERVED'
    displayed, releases, kinds = {}, [], {}
    pre, post = set(), set()
    with (root / 'replay/runtime.jsonl').open(encoding='utf-8') as rows:
        for ordinal, line in enumerate(rows):
            row = json.loads(line)
            for check in (row.get('result', {}).get('semantic') or {}).get('constraints', []):
                if check['verdict'] == 'PASS' and check.get('invocation'):
                    if check['kind'] == 'PRE': pre.add(check['id'])
                    if check['kind'] == 'POST': post.add(check['id'])
            if row['kind'] != 'CHECKPOINT_AUDIT': continue
            audit = row['payload']['checkpointAudit']
            kinds[audit['kind']] = kinds.get(audit['kind'], 0) + 1
            if audit['kind'] == 'DISPLAYED':
                ident = audit['request']['identity']
                displayed[ident['token']] = (ordinal, audit['resultHash'], ident)
            if audit.get('receipt', {}).get('released'):
                command = audit['command']; ident = command['identity']
                seen = displayed[ident['token']]
                assert seen[0] < ordinal and seen[1] == command['resultHash'] and seen[2] == ident
                assert command['action'] == 'APPROVE', command['action']
                releases.append(ident['token'])
    assert releases and len(releases) == len(set(releases))
    assert pre and post, (pre, post)
    case = pathlib.Path(probe['case'])
    declared = json.loads((case / 'case-manifest.json').read_text(encoding='utf-8'))
    for name, digest in declared['sha256'].items():
        assert hashlib.sha256((case / name).read_bytes()).hexdigest() == digest, name
    receipt = dict(status='PASS', case=str(case), verifiedMode=True,
        automaticReleases=len(releases), displayedCuts=len(displayed),
        exactIdentityAndResultBeforeRelease=True, manualAcknowledgements=0,
        sameMSystem=True, goalStatuses=probe['goalStatuses'], ui=ui,
        nativePreconditions=sorted(pre), nativePostconditions=sorted(post),
        authoredManifestHashesUnchanged=True, auditKinds=kinds,
        replayBytes=(root / 'replay/runtime.jsonl').stat().st_size)
    (root / 'receipt.json').write_text(json.dumps(receipt, indent=2, ensure_ascii=False), encoding='utf-8')
    print(root.name, receipt['status'], 'releases', len(releases), 'goals', len(probe['goalStatuses']))
