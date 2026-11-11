from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess
import shutil

out = Path(__file__).resolve().parent
root = out.parents[1]
report = root / 'target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.mapping.compact.NativeVerificationCoordinatorTest.xml'
tree = ET.parse(report).getroot()
cp = next(p.attrib['value'] for p in tree.find('properties') if p.attrib.get('name') == 'java.class.path')
args = out / 'probe-compile.args'
args.write_text('-encoding\nUTF-8\n-classpath\n"'+cp.replace('\\','/')+'"\n-d\n"'+out.as_posix()+'"\n"'+(out/'CompactReviewProbe.java').as_posix()+'"\n', encoding='utf-8')
compiled = subprocess.run([shutil.which('javac'), '@'+str(args)],capture_output=True,text=True,errors='replace')
(out/'probe-compile.log').write_text(compiled.stdout+compiled.stderr, encoding='utf-8')
print('COMPILE_EXIT',compiled.returncode)
if compiled.returncode:
    print(compiled.stderr)
    raise SystemExit(compiled.returncode)
runargs=out/'probe-run.args'
runargs.write_text('-cp\n"'+out.as_posix()+';'+cp.replace('\\','/')+'"\nCompactReviewProbe\n',encoding='utf-8')
run=subprocess.run([shutil.which('java'),'@'+str(runargs)],capture_output=True,text=True,errors='replace')
(out/'probe-results.log').write_text(run.stdout+run.stderr,encoding='utf-8')
print('RUN_EXIT',run.returncode)
print(run.stdout)
if run.returncode:
    print(run.stderr)
