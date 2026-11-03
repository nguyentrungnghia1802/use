import pathlib,json,hashlib
root=pathlib.Path(__file__).resolve().parent
use=root.parents[2]
preserved=root/'preserved-install.json'
journal=(pathlib.Path(json.loads(preserved.read_text(encoding='utf-8-sig'))['preservedUserJournal']) if preserved.exists()
         else next((use/'use-assembly/target/new/use-plugin/target/jacamo-runtime/run-2513264743153660805').glob('runtime-*.jsonl')))
cuts=[];wanted={};last=None
with journal.open(encoding='utf-8') as stream:
    for line in stream:
        row=json.loads(line);last=row.get('result',last)
        audit=row.get('payload',{}).get('checkpointAudit',{})
        if audit.get('kind')=='EVALUATED':
            request=audit['request'];item={'ordinal':row['ordinal'],'token':request['identity']['token'],'checkpoint':request['checkpointId'],'hash':audit['nativeResultHash'],'scope':request['identity']['executionScope']};cuts.append(item);wanted[item['hash']]=None
with journal.open(encoding='utf-8') as stream:
    for line in stream:
        row=json.loads(line);r=row.get('result',{})
        if r.get('resultHash') in wanted and wanted[r['resultHash']] is None:wanted[r['resultHash']]=r
summary=[]
for cut in cuts:
    result=wanted[cut['hash']]
    if not result:continue
    core=result.get('semantic',{}).get('observation',{}).get('originalEngine',{})
    summary.append(cut|{'version':result['stateVersion'],'contexts':[{'context':c.get('context'),'entities':c.get('scopeView',{}).get('entities'),'constraints':c.get('executionConstraints'),'occurrences':{k:{name:v.get(name) for name in ['sourceElement','aggregateStatus','executionQuality','executionStatus','player']} for k,v in c.get('occurrences',{}).items()}} for c in core.get('contexts',[]) if isinstance(c.get('occurrences'),dict)]})
(root/'user-checkpoint-differences.json').write_text(json.dumps({'journal':str(journal),'cuts':summary},indent=2),encoding='utf-8')
for cut in summary:
    print(cut['ordinal'],cut['checkpoint'],cut['token'],cut['scope'])
    for c in cut['contexts']:
        entities=c.get('entities') or {}
        print(' ',c['context'],json.dumps(entities,sort_keys=True)[:600])
        print('  rows',[(x.get('id'),x.get('verdict'),x.get('diagnostic'),x.get('invocation')) for x in c.get('constraints') or [] if x.get('kind')!='INVARIANT_BODY'])
