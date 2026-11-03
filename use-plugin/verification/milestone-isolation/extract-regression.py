"""Freeze exact EVALUATED results from the reported run, checking its disk chain."""
import gzip, hashlib, json, pathlib

root = pathlib.Path(__file__).resolve().parent
use = root.parents[2]
preserved = root / 'preserved-install.json'
journal = (pathlib.Path(json.loads(preserved.read_text(encoding='utf-8-sig'))['preservedUserJournal']) if preserved.exists()
           else next((use / 'use-assembly/target/new/use-plugin/target/jacamo-runtime/run-2513264743153660805').glob('runtime-*.jsonl')))
results, cuts, previous = {}, [], ''
with journal.open('rb') as stream:
    for ordinal, line in enumerate(stream):
        row = json.loads(line)
        prefix = b'{"entryHash":"' + row['entryHash'].encode() + b'",'
        assert line.startswith(prefix)
        unsigned = b'{' + line[len(prefix):].rstrip(b'\n\r')
        assert hashlib.sha256(unsigned).hexdigest() == row['entryHash'], ordinal
        assert row['ordinal'] == ordinal and row['previousHash'] == previous, ordinal
        previous = row['entryHash']
        result = row['result']
        results[result['resultHash']] = result
        audit = row.get('payload', {}).get('checkpointAudit', {})
        if audit.get('kind') == 'EVALUATED':
            cuts.append({'ordinal': ordinal, 'request': audit['request'],
                         'result': results[audit['nativeResultHash']]})
fixture = use / 'use-plugin/src/test/resources/semantics/checkpoint-milestone-user-run.json.gz'
fixture.write_bytes(gzip.compress(json.dumps(cuts, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0))
receipt = {'journal': str(journal), 'journalSha256': hashlib.sha256(journal.read_bytes()).hexdigest(),
           'entries': ordinal + 1, 'chainHead': previous, 'chainVerified': True,
           'fixture': str(fixture), 'fixtureSha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
           'cuts': [{'ordinal': c['ordinal'], 'token': c['request']['identity']['token'],
                     'resultHash': c['result']['resultHash']} for c in cuts]}
(root / 'user-fixture-provenance.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
print('HASH_CHAIN_VERIFIED', ordinal + 1, 'entries;', len(cuts), 'exact cuts;', len(fixture.read_bytes()), 'fixture bytes')
