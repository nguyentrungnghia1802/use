"""Close gates only after the final serial build/live test and installed identity proof."""
from pathlib import Path
import hashlib,json,re,shutil
here=Path(__file__).resolve().parent
repo=here.parents[2]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
log=(here/'final-serial-focused-auction-build.log').read_text(errors='replace')
assert '[INFO] BUILD SUCCESS' in log
rows=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed:.*? -- in ([^\r\n]+)',log)
assert rows and all(int(f)==int(e)==int(s)==0 for n,f,e,s,c in rows)
assert any(c.endswith('VerifiedCheckpointsAuctionIT') for n,f,e,s,c in rows)
count=sum(int(n) for n,f,e,s,c in rows)
live=max((repo/'use-plugin/verification/verified-checkpoints').glob('live-auction-*'),key=lambda p:p.stat().st_mtime)
receipt=json.loads((live/'live-receipt.json').read_text())
assert receipt['originalAuctionUnchanged'] and receipt['automaticCuts']>0 and receipt['reviewedCuts']>=2 and receipt['genuineNativeFailedCuts']>0
assert receipt['reviewedCuts']==receipt['reviewedReleaseReceipts']
assert 'VERIFIED_CHECKPOINT_AUCTION_PASS' in (live/'consumer.log').read_text()
package=json.loads((here/'package-receipt.json').read_text())
install=repo/'use-assembly/target/new'
for relative,digest in package['files'].items():assert sha(install/relative)==digest,relative
for value in package['loadedClasses'].values():assert sha(value['path'])==value['sha256']
for f in ['disconnect-reconnect.json','monitor-stop-restart.json','workbench-close.json']:
 shutil.copy2(live/f,here/('final-'+f))
summary={key:receipt[key] for key in ['distinctHeldCuts','reviewedCuts','genuineNativeFailedCuts','automaticCuts','reviewedReleaseReceipts','originalAuctionUnchanged']}
summary.update({'method':'FINAL_SERIAL_JUNIT_LIVE_MAS','manualDesktopAcceptance':False,'evidenceDirectory':str(live),'focusedTests':count,'testClasses':[{'name':c,'tests':int(n)} for n,f,e,s,c in rows]})
summary['replays']={}
for name in ['replay-before-disconnect','replay-interrupted-monitor','replay']:
 manifest=live/name/'manifest.json';summary['replays'][name]={'manifestSha256':sha(manifest),'entries':json.loads(manifest.read_text())['entries'],'runtimeSha256':sha(live/name/'runtime.jsonl')}
summary['replayResults']={n:json.loads((live/n).read_text()) for n in ['replay-report.json','replay-interrupted-report.json']}
assert all(r['diagnostics']==['REPLAY_PARTIAL_COVERAGE_GAP'] and r['checkedEntries']>0 for r in summary['replayResults'].values())
(here/'final-live-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
lock=json.loads((repo.parent/'jacamo/checkpoint-dependencies/dependency-lock.json').read_text())
artifacts={}
for item in [lock['runtime']]+lock['components']:
 source=Path(item['path']);name=source.name
 paths={'build':source,'producerRuntime':repo/'jacamo-bridge-jacamo/target/producer-runtime'/name,'pinnedBootstrap':repo.parent/'jacamo/checkpoint-dependencies/artifacts'/name,'installed':install/'bridge/runtime'/name}
 for path in paths.values():assert sha(path)==item['sha256'],path
 artifacts[name]={'sha256':item['sha256'],**{k:str(v) for k,v in paths.items()}}
for file in ['use-core/target/use-core-7.5.0.jar','use-gui/target/use-gui.jar','use-plugin/target/use-plugin-1.0.1.jar','jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar','jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar']:
 source=repo/file;artifacts[source.name]={'sha256':sha(source),'build':str(source)}
(here/'source-artifacts.json').write_text(json.dumps({'method':'SOURCE_BUILD_PRODUCER_BOOTSTRAP_INSTALLED_PIN_PARITY','installedTargetNewRefreshed':True,'finalModelLiveAccepted':True,'artifacts':artifacts},indent=2)+'\n')
print('FINAL_EVIDENCE_GATES_PASS',count,summary['automaticCuts'],summary['reviewedCuts'])
