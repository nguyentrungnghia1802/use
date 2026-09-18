"""Reproduce the pinned launcher audit in isolated, timeout-bounded JVMs.
Run after: mvn -B -pl use-plugin dependency:build-classpath -Dmdep.outputFile=target/phase20-component-classpath.txt
No fixture source is modified. Local pinned launcher jars are required explicitly.
"""
import hashlib, json, os, pathlib, subprocess, zipfile

module = pathlib.Path(__file__).resolve().parents[2]
root = module.parent
output = module / 'target/phase20-launcher'
output.mkdir(parents=True, exist_ok=True)
java_home = pathlib.Path(os.environ['JAVA_HOME'])
java = java_home / 'bin/java.exe'
javac = java_home / 'bin/javac.exe'
repository = pathlib.Path(os.environ.get('MAVEN_REPO_LOCAL', pathlib.Path.home() / '.m2/repository'))
jars = [repository / f'org/jacamo/{name}/{version}/{name}-{version}.jar' for name,version in
        [('jacamo','1.3.0'),('jaca','3.1'),('npl','0.6')]]
for jar in jars:
    if not jar.is_file(): raise SystemExit(f'PHASE20_PINNED_DEPENDENCY_MISSING:{jar}')
component_classpath = (module/'target/phase20-component-classpath.txt').read_text().strip()
component_jars = [pathlib.Path(path) for path in component_classpath.split(os.pathsep)
                  if pathlib.Path(path).name in ['jason-interpreter-3.3.0.jar', 'cartago-3.1.jar', 'moise-1.1.jar']]
if len(component_jars) != 3: raise SystemExit('PHASE20_PINNED_COMPONENT_MISSING')
classpath = component_classpath + os.pathsep + os.pathsep.join(map(str,jars)) + os.pathsep + str(output)
fixture = module/'src/test/resources/auction'
subprocess.run([str(javac), '-cp', classpath, '-d', str(output), str(fixture/'src/env/auction/AuctionArtifact.java')],check=True,timeout=30)
source = (fixture/'auction.jcm').read_text(encoding='utf-8')
adapted = source.replace('auction_open budget','auction_open, budget').replace('local(pool,2) cartago()','local(pool,2), cartago()')
for kind,directory in [('asl','agt'),('org','org'),('java','env')]:
    adapted = adapted.replace(f'{kind}-path: src/{directory}',f'{kind}-path: "{(fixture / "src" / directory).as_posix()}"')
variant = output/'auction-launch.jcm'
variant.write_text(adapted,encoding='utf-8')
subprocess.run([str(java), '-cp', classpath, str(module/'tools/runtime/MoiseSchemaProbe.java'),
                str(fixture/'src/org/auction.xml'), str(output)], check=True, timeout=30)
control = output/'builder-control.jcm'
control.write_text(adapted.replace('organisation auction_org : auction.xml',
    'organisation auction_org : builder-control.xml').replace(
    f'org-path: "{(fixture / "src/org").as_posix()}"', f'org-path: "{output.as_posix()}"'), encoding='utf-8')
results=[]
for name, project in [('original', fixture/'auction.jcm'), ('syntax-path-adapted', variant),
                      ('builder-control-not-equivalent', control)]:
    log=output/f'{name}.log'
    with log.open('w',encoding='utf-8') as stream:
        try:
            process=subprocess.run([str(java),'-Djava.awt.headless=true','-cp',classpath,
                str(module/'tools/runtime/LauncherProbe.java'),str(project)],cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=30)
            code=process.returncode
        except subprocess.TimeoutExpired: code='TIMEOUT'
    text=log.read_text(encoding='utf-8')
    errors=[line for line in text.splitlines() if any(word in line for word in ['Exception','SEVERE','PHASE20_','Parser creation error'])]
    status='TECHNICAL_LIMITATION' if code != 0 or 'PHASE20_PLATFORM_PROBE_RETURNED' not in text or any(word in text for word in ['SEVERE','Exception','Parser creation error']) else 'PLATFORM_START_ONLY_NOT_MIRROR_E2E'
    results.append(dict(scenario=name,status=status,exitCode=code,diagnostics=errors,logSha256=hashlib.sha256(log.read_bytes()).hexdigest()))
summary=dict(jacamo='1.3.0',jason='3.3.0',cartago='3.1',moise='1.1',jaca='3.1',npl='0.6',
    sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
    originalFixtureModified=False,fullRuntimeE2E=False,results=results,
    limitation='Static Auction OS violates pinned XSD beyond its namespace. Builder control is not semantically equivalent. Launcher organisation boards expose ora4mas.nopl.oe state, whereas the current connector consumes moise.oe.OE. Closest supported mirror evidence is LiveJaCaMoAuctionIntegrationTest.',
    moiseSchemaAudit=json.loads((output/'moise-schema-audit.json').read_text()),
    jarSha256={jar.name:hashlib.sha256(jar.read_bytes()).hexdigest() for jar in jars + component_jars},
    inputSha256={str(path.relative_to(module)):hashlib.sha256(path.read_bytes()).hexdigest() for path in
        [fixture/'auction.jcm',fixture/'src/org/auction.xml',fixture/'src/agt/auctioneer.asl',
         fixture/'src/env/auction/AuctionArtifact.java',module/'tools/runtime/LauncherProbe.java',
         module/'tools/runtime/MoiseSchemaProbe.java',pathlib.Path(__file__).resolve()]})
with zipfile.ZipFile(next(path for path in component_jars if path.name == 'moise-1.1.jar')) as archive:
    summary['moiseSchemaSha256'] = hashlib.sha256(archive.read('xml/os.xsd')).hexdigest()
(output/'launcher-audit.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,indent=2))
if [row['status'] for row in results] != ['TECHNICAL_LIMITATION', 'TECHNICAL_LIMITATION',
                                        'PLATFORM_START_ONLY_NOT_MIRROR_E2E']:
    raise SystemExit('PHASE20_PROBE_EXPECTATION_FAILED: inspect launcher-audit.json')
