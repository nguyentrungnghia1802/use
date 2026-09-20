"""Run the trusted, explicitly non-equivalent launcher mirror control after launcher_probe.py.
Requires mvn -pl use-plugin test-compile and the existing pinned launcher classpath.
Never executes an arbitrary user-supplied project. Parent timeout/reap is mandatory.
"""
import hashlib, json, os, pathlib, subprocess, sys

module = pathlib.Path(__file__).resolve().parents[2]
negative = '--negative-readiness' in sys.argv
output = module / ('target/phase20-launcher-negative' if negative else 'target/phase20-launcher-mirror')
output.mkdir(parents=True, exist_ok=True)
for stale in ('summary.json', 'failure.json'):
    (output/stale).unlink(missing_ok=True)
repository = pathlib.Path(os.environ.get('MAVEN_REPO_LOCAL', pathlib.Path.home()/'.m2/repository'))
launcher = repository/'org/jacamo/jacamo/1.3.0/jacamo-1.3.0.jar'
assert launcher.is_file(), 'PHASE20_PINNED_DEPENDENCY_MISSING'
classpath = (module/'target/phase20-component-classpath.txt').read_text().strip()
classpath = os.pathsep.join([str(module/'target/classes'), classpath, str(launcher),
    str(repository/'org/jacamo/jaca/3.1/jaca-3.1.jar'), str(repository/'org/jacamo/npl/0.6/npl-0.6.jar'),
    str(module/'target/phase20-launcher')])
project = (module/'target/phase20-launcher/builder-control.jcm').read_text()
original_asl_root = (module/'src/test/resources/auction/src/agt').as_posix()
project = project.replace('auctioneer.asl','probe.asl').replace('goals: start','')
project = project.replace(original_asl_root, output.as_posix())
project = project.replace('org-path: "'+(module/'target/phase20-launcher').as_posix()+'"',
                          'org-path: "'+output.as_posix()+'"')
# Explicit control policy: permission to execute one mission, no source deadline/plan equivalence.
control_os = (module/'target/phase20-launcher/builder-control.xml').read_text()
control_os = control_os.replace('<normative-specification/>', '<normative-specification><norm id="control_permission" role="auctioneer" mission="run_auction" type="permission"/></normative-specification>')
(output/'builder-control.xml').write_text(control_os)
if negative:
    project = project.replace('roles: auctioneer in auction_group','').replace('players: auctioneer auctioneer','')
(output/'probe.jcm').write_text(project)
(output/'probe.asl').write_text('''// Trusted diagnostic control, not the original Auction agent semantics.
{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
+!probe <- commitMission(run_auction)[artifact_name(auction_scheme),wsp(auction_org)];
           placeBid("item1",10)[artifact_name(auction1),wsp(market)];
           closeAuction[artifact_name(auction1),wsp(market)];
           goalAchieved(sell_item)[artifact_name(auction_scheme),wsp(auction_org)]; +probe_done.
+!probe_failure <- placeBid("item1",0)[artifact_name(auction1),wsp(market)].
-!probe_failure <- +failure_seen.
+!probe_disconnected <- removeOpen[artifact_name(auction1),wsp(market)]; +disconnected_done.
''')
java = pathlib.Path(os.environ['JAVA_HOME'])/'bin/java.exe'
log = output/'probe.log'
command = [str(java),'-Djava.awt.headless=true','-cp',classpath,
           str(module/'tools/runtime/LauncherMirrorProbe.java'),str(output/'probe.jcm'),str(module),str(output)]
with log.open('w',encoding='utf-8') as stream:
    try:
        result=subprocess.run(command,cwd=module,stdout=stream,stderr=subprocess.STDOUT,timeout=45)
    except subprocess.TimeoutExpired:
        (output/'failure.json').write_text(json.dumps({'failure':'PHASE20_PROCESS_TIMEOUT', 'timeoutSeconds':45}))
        raise SystemExit('PHASE20_PROCESS_TIMEOUT: child killed and reaped; inspect '+str(log))
text=log.read_text(encoding='utf-8')
print(text)
if negative:
    assert result.returncode != 0 and 'PHASE20_CONTROL_ROLE_READY' in text, 'READINESS_NEGATIVE_WRONG_FAILURE'
    assert 'PHASE20_LAUNCHER_MIRROR_CONTROL_PASS' not in text, 'FALSE_POSITIVE_READINESS'
    failure=json.loads((output/'failure.json').read_text())
    assert failure['failure'].endswith('PHASE20_CONTROL_ROLE_READY'), 'READINESS_NEGATIVE_WRONG_GATE'
    assert failure['mirrorState']=='LIVE' and failure['groups'] and failure['schemes'], 'NEGATIVE_BOARD_PRECONDITION_MISSING'
    assert 'INITIAL_LIVE' in failure['checkpoints'] and 'AGENT_SCENARIO_DONE' not in failure['checkpoints']
    print('PHASE20_READINESS_NEGATIVE_PASS: existing boards without role rejected')
    raise SystemExit(0)
if result.returncode or 'PHASE20_LAUNCHER_MIRROR_CONTROL_PASS' not in text:
    raise SystemExit('PHASE20_LAUNCHER_MIRROR_CONTROL_FAILED')
summary=json.loads((output/'summary.json').read_text())
events=json.loads((output/'runtime-events.json').read_text())['events']
outcomes=json.loads((output/'outcomes.json').read_text())
callbacks=json.loads((output/'raw-callbacks.json').read_text())
checkpoints=json.loads((output/'checkpoints.json').read_text())
assert 'CONNECTOR_CLEANUP' in checkpoints, 'CONNECTOR_CLEANUP_NOT_PROVEN'
assert len(events)==len(outcomes) and {e['eventId'] for e in events}=={o['eventId'] for o in outcomes}, 'UNACCOUNTED_EVENT'
assert all(o['status']=='APPLIED' and o['mappingRule'] and o['targets'] for o in outcomes), 'UNRESOLVED_MUTATION'
assert all(e['timestamp'] and e['runtimeSourceId'] and e['semanticSourceId'] for e in events), 'TRACE_IDENTITY_MISSING'
sequences=[e['sequence'] for e in events]
assert sequences==sorted(set(sequences)), 'EVENT_ORDER_OR_DUPLICATE'
operations={}
for event in events:
    if event['kind'] in ('OP_ENTER','OP_EXIT','OP_FAIL'):
        assert event['correlationId'], 'MISSING_OPERATION_CORRELATION'
        operations.setdefault(event['correlationId'], []).append(event)
assert len(operations)==3, 'CONTROL_OPERATION_COUNT'
for lifecycle in operations.values():
    assert [e['kind'] for e in lifecycle] in (['OP_ENTER','OP_EXIT'],['OP_ENTER','OP_FAIL']), 'OPERATION_LIFECYCLE_MISMATCH'
    assert len({e['runtimeSourceId'] for e in lifecycle})==1, 'OPERATION_IDENTITY_MISMATCH'
    assert all(e['payload']['agent']=='auctioneer' for e in lifecycle), 'NOT_ACTUAL_AGENT_OPERATION'
properties=[e for e in events if e['kind']=='OBS_PROPERTY_CHANGED']
assert len(properties)==1 and properties[0]['payload']['property']=='open' and properties[0]['payload']['value'] is False, 'PROPERTY_DELTA_MISMATCH'
close=next(es for es in operations.values() if es[0]['payload']['operation']=='closeAuction')
assert close[0]['sequence'] < properties[0]['sequence'] < close[1]['sequence'], 'PROPERTY_TIMING_MISMATCH'
assert any(es[0]['payload']['arguments']==['item1',10] and es[-1]['kind']=='OP_EXIT' for es in operations.values()), 'NO_STATE_CHANGE_OPERATION_MISSING'
assert any(es[0]['payload']['arguments']==['item1',0] and es[-1]['kind']=='OP_FAIL' for es in operations.values()), 'FAILURE_OPERATION_MISSING'
assert {'opRequested','opStarted','opCompleted','opFailed','newPercept'} <= {c['category'] for c in callbacks}, 'RAW_CALLBACK_COVERAGE'
summary['traceContractChecks']='PASS: exact identity, complete outcomes, ordered unique sequence, paired operation correlations, arguments, property timing, raw callback categories, cleanup'
summary['observedEventKinds']=sorted({e['kind'] for e in events})
summary['rawCallbackCategories']=sorted({c['category'] for c in callbacks})
enum=(module/'src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeEventKind.java').read_text()
known={name.strip() for name in enum.split('{',1)[1].rsplit('}',1)[0].split(',')}
assert set(summary['observedEventKinds']) <= known, 'UNKNOWN_EVENT_KIND'
summary['unobservedEventKinds']=sorted(known-set(summary['observedEventKinds']))
summary['unobservedMeaning']='Not exercised by this scenario; not evidence of API impossibility'
summary['processExitCode']=result.returncode
summary['sourceRevision']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=module,text=True).strip()
summary['workingTreeStatus']=subprocess.check_output(['git','status','--porcelain'],cwd=module,text=True).strip()
summary['processContainment']='child returned and reaped; not a claim of upstream thread-level quiescence'
summary['inputSha256']={str(p.relative_to(module)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
    [module/'tools/runtime/LauncherMirrorProbe.java',pathlib.Path(__file__).resolve(),
     module/'src/main/java/org/tzi/use/plugins/jacamo/runtime/MoiseBoardSnapshotSource.java',
     module/'src/main/java/org/tzi/use/plugins/jacamo/runtime/MoiseRuntimeConnector.java']}
summary['sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.iterdir()) if p.is_file() and p.name!='summary.json'}
(output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
