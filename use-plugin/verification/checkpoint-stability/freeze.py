"""Freeze the stability fixes, full live receipt, installed bytes and evidence."""
import datetime,hashlib,json,pathlib,subprocess
base=pathlib.Path(__file__).resolve().parent
use=base.parents[2]
live=base/'live-1793507424631'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def row(p): return {'path':str(p),'sha256':sha(p)}
def git(repo,*args): return subprocess.check_output(['git','-C',str(repo),*args],text=True).strip()
receipt=json.loads((live/'receipt.json').read_text(encoding='utf-8-sig'))
package=json.loads((base/'stability-final-package-receipt.json').read_text(encoding='utf-8-sig'))
assert receipt['nativeRootsFulfilled']==receipt['uiExecutionsComplete']==2
assert receipt['desktopReviewedReleases']==2 and len(set(receipt['distinctReviewedTokens']))==2
assert receipt['automaticReleases']>0 and receipt['originalAuctionUnchanged'] and receipt['sameMSystem']
assert package['strictSourceHooks'] and package['currentBuildBytesVerified'] and package['installedPluginLoader']
assert 'BUILD SUCCESS' in (base/'final-package.log').read_text()
sources=[];heads={}
for repo in [use,use.parent/'jacamo']:
    assert git(repo,'branch','--show-current')=='main'
    heads[repo.name]=git(repo,'rev-parse','HEAD')
    if repo.name=='jacamo': assert not git(repo,'diff','--name-only','--','examples/auction')
    paths=set(git(repo,'diff','--name-only').splitlines()+git(repo,'ls-files','--others','--exclude-standard').splitlines())
    for relative in sorted(paths):
        if relative.startswith('use-plugin/verification/'):
            if relative not in ['use-plugin/verification/checkpoint-stability/freeze.py','use-plugin/verification/verified-checkpoints/check-final-package.ps1','use-plugin/verification/verified-checkpoints/InstalledVerifiedCheckpointSmoke.java']: continue
        path=repo/relative
        if path.is_file() and path.suffix in ['.java','.xml','.gradle','.json','.properties','.md','.py','.ps1','.bat']:
            sources.append({'repo':repo.name,'relative':relative,**row(path)})
artifacts=[row(p) for p in sorted((use/'use-assembly/target/new').rglob('*.jar'))]
evidence=[row(p) for p in sorted(live.rglob('*')) if p.is_file()]
evidence += [row(base/name) for name in ['REPORT.md','final-package.log','focused-live-2.log','user-use-threads.txt','user-producer-threads.txt','user-history-diagnostics.json','stability-final-package-receipt.json','stability-packaged-code-sources.log','stability-installed-loader-smoke.log']]
value={'version':'CHECKPOINT_STABILITY_FREEZE/1.0','recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heads':heads,'sourceFiles':sources,'workspaceFiles':[row(use.parent/p) for p in ['AGENTS.md','plan.md']],'installedJars':artifacts,'evidenceFiles':evidence,'liveReceipt':receipt,'packageReceipt':package,'commitOrPush':False}
(base/'final-freeze.json').write_text(json.dumps(value,indent=2),encoding='utf-8')
print('CHECKPOINT_STABILITY_SOURCE_ARTIFACT_EVIDENCE_FREEZE_PASS',package['jacamoSha256'])
