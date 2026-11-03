import pathlib,json,gzip,hashlib
base=pathlib.Path(__file__).resolve().parent
use=base.parents[2]
journal=next((use/'use-assembly/target/new/use-plugin/target/jacamo-runtime/run-5537625689257953083').glob('runtime-*.jsonl'))
requests=[]; wanted={}; previous='';last_native=None
with journal.open('rb') as stream:
    for ordinal,line in enumerate(stream):
        row=json.loads(line);prefix=b'{"entryHash":"'+row['entryHash'].encode()+b'",'
        assert line.startswith(prefix)
        assert hashlib.sha256(b'{'+line[len(prefix):].rstrip(b'\r\n')).hexdigest()==row['entryHash'],ordinal
        assert row['ordinal']==ordinal and row['previousHash']==previous,ordinal
        previous=row['entryHash']; result=row['result']
        if result.get('semantic',{}).get('observation',{}).get('originalEngine',{}).get('evaluated')=='PINNED_NATIVE_CUT':last_native=result
        audit=row.get('payload',{}).get('checkpointAudit',{})
        if audit.get('kind')=='EVALUATED':
            requests.append({'ordinal':ordinal,'request':audit['request'],'resultHash':audit['nativeResultHash']})
            wanted[audit['nativeResultHash']]=None
with journal.open(encoding='utf8') as stream:
    for line in stream:
        result=json.loads(line)['result'];h=result['resultHash']
        if h in wanted:wanted[h]=result
samples=[x|{'result':wanted[x['resultHash']]} for x in requests]
if last_native:samples.append({'ordinal':ordinal,'settled':True,'result':last_native})
with gzip.open(base/'exact-results.json.gz','wt',encoding='utf8') as out:json.dump(samples,out,ensure_ascii=False,separators=(',',':'))
with journal.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
(base/'provenance.json').write_text(json.dumps({'journal':str(journal),'sha256':digest,'entries':ordinal+1,'chainVerified':True,'chainHead':previous,'evaluatedCuts':len(requests),'settledResultIncluded':last_native is not None},indent=2),encoding='utf8')
print('CHAIN_VERIFIED',ordinal+1,'entries',len(requests),'evaluated cuts')
