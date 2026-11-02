import pathlib,json,hashlib,re,collections,shutil
base=pathlib.Path(__file__).resolve().parent
use=base.parents[2]
summary={'verifiedAcceptance':False,'productionChanged':False,'cases':{},'files':[]}
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
owned={'hello-world':'c85534f4-6d1b-4583-92ae-5209266e5bcc','house-building':'9d79fd94-1e15-4f61-a05b-707b55f6af4c'}
controls={'hello-world':'hello-world-2710205972145095189','house-building':'house-building-10184017443313623345'}
for name in owned:
    root=use/'use-plugin/examples'/name
    receipt=json.loads((base/name/'probe.json').read_text())
    pairs=set(re.findall(r'Rejection\[source=([^,]+), reason=([^\]]+)\]',receipt['error']))
    assert receipt['verifiedMode'] and receipt['status']=='ERROR' and not receipt['tokens'] and receipt['manualAcknowledgements']==0
    inputs=json.loads((root/'case-manifest.json').read_text())['sha256']
    for path,expected in inputs.items():assert sha(root/path)==expected
    provenance=json.loads((root/'provenance.json').read_text())
    for row in provenance['files']:assert sha(root/row['path'])==row['localSha256']
    for path in (use/'use-plugin/target/owned-runtime'/owned[name]).iterdir():
        if path.is_file() and path.name in ['prepared.json','prepare.stdout.log','prepare.stderr.log','producer.stdout.log','producer.stderr.log']:
            shutil.copyfile(path,base/name/path.name)
    summary['cases'][name]={'verifiedStatus':'REJECTED_BEFORE_REASONING','tokens':0,'originalInputsAndSourcesMatchPins':True,
        'distinctSourceReasonPairs':len(pairs),'rejectionGroups':dict(collections.Counter(reason.split(':')[0] for source,reason in pairs)),
        'rejections':[{'source':s,'reason':r} for s,r in sorted(pairs)]}
    control=use/'use-plugin/verification/additional-cases'/controls[name]
    for original,target in [('receipt.json','observation-receipt.json'),('result.json','observation-result.json')]:shutil.copyfile(control/original,base/name/target)
    observed=json.loads((control/'receipt.json').read_text())
    assert observed['status']=='PASS' and observed['sameMSystem'] and observed['restartAndCleanup'] and observed['replayAfterAllCopiedInputsDeleted']
    assert set(observed['goalStatuses'].values())=={'FULFILLED'}
    ui=json.loads((base/name/'frozen-ui.json').read_text())
    assert len(ui['availableContexts'])==1 and not ui['checkpointExecutionLabels'] and ui['checkpointMilestones']==0
    summary['cases'][name].update({'observedControl':'PASS','observedGoalStatuses':observed['goalStatuses'],'observedCoverage':observed['coverage'],
        'frozenCheckpointUi':{'nativeContexts':1,'admittedExecutions':0,'milestones':0},'controlEvidence':str(control)})
assert 'Tests run: 5, Failures: 1, Errors: 0, Skipped: 0' in (base/'bundle-tests.log').read_text()
assert 'Tests run: 2, Failures: 0, Errors: 0, Skipped: 0' in (base/'observation-control-tests.log').read_text()
summary['focusedTests']={'bundle':{'run':5,'failures':1,'errors':0,'skipped':0},'liveObservedControl':{'run':2,'failures':0,'errors':0,'skipped':0}}
for path in sorted(base.rglob('*')):
    if path.is_file() and path.name!='summary.json':summary['files'].append({'path':str(path),'sha256':sha(path)})
(base/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
print('SOURCE_PINS_UNCHANGED; BOTH_VERIFIED_STARTUPS_REJECTED; NO_PASS_CLAIM')
