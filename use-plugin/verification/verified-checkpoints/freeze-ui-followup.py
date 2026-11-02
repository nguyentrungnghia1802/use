"""Bind the UI follow-up to actual built/installed bytes and its own live receipt."""
import hashlib,json,pathlib,subprocess,sys,datetime
base=pathlib.Path(__file__).resolve().parent
use=base.parents[2]
live=pathlib.Path(sys.argv[1]).resolve()
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def row(p): return {'path':str(p),'sha256':sha(p)}
receipt=json.loads((live/'ui-receipt.json').read_text(encoding='utf-8-sig'))
package=json.loads((base/'UI-final-final-package-receipt.json').read_text(encoding='utf-8-sig'))
assert receipt['originalAuctionUnchanged'] and receipt['sameMSystem'] and receipt['manualDesktopReview']
assert package['currentBuildBytesVerified'] and package['strictSourceHooks']
sources=[];heads={}
for repo in [use,use.parent/'jacamo']:
    def git(*args): return subprocess.check_output(['git','-C',str(repo),*args],text=True).strip()
    assert git('branch','--show-current')=='main'
    heads[repo.name]=git('rev-parse','HEAD')
    for relative in sorted(set(git('diff','--name-only').splitlines()+git('ls-files','--others','--exclude-standard').splitlines())):
        if relative.startswith('use-plugin/verification/') and not relative.startswith('use-plugin/verification/verified-checkpoints/'): continue
        if relative.startswith('use-plugin/verification/') and pathlib.Path(relative).name not in ['check-final-package.ps1','freeze-ui-followup.py','InstalledVerifiedCheckpointSmoke.java']: continue
        path=repo/relative
        if path.is_file() and path.suffix in ['.java','.xml','.gradle','.properties','.json','.md','.py','.ps1','.bat']:
            sources.append({'repo':repo.name,'relative':relative,**row(path)})
evidence=[row(p) for p in sorted(live.rglob('*')) if p.is_file()]
evidence += [row(base/name) for name in ['UI-FOLLOWUP.md','UI-final-package-retest.log','UI-final-packaged-code-sources.log','UI-final-installed-loader-smoke.log','UI-final-final-package-receipt.json']]
installed=[row(p) for p in sorted((use/'use-assembly/target/new').rglob('*.jar'))]
freeze={'version':'CHECKPOINT_UI_FOLLOWUP_FREEZE/1.0','recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heads':heads,'live':str(live),'liveReceipt':receipt,'packageReceipt':package,'sourceFiles':sources,'workspaceInstructions':[row(use.parent/name) for name in ['AGENTS.md','plan.md']],'installedJars':installed,'evidenceFiles':evidence,'commitOrPush':False}
(base/'UI-source-artifact-evidence-freeze.json').write_text(json.dumps(freeze,indent=2),encoding='utf-8')
print('UI_SOURCE_ARTIFACT_EVIDENCE_FREEZE_PASS',package['jacamoSha256'])
