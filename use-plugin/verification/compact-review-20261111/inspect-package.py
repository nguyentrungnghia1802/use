from pathlib import Path
import zipfile
import hashlib
import json

out=Path(__file__).resolve().parent
repo=out.parents[2]
jar=repo/'use-assembly/target/new/lib/plugins/use-jacamo-plugin-1.0.1.jar'
compiled=repo/'use-plugin/target/classes'
prefix='org/tzi/use/plugins/jacamo/semantics/mapping/compact/'
with zipfile.ZipFile(jar) as z:
    names=[n for n in z.namelist() if n.startswith(prefix) and n.endswith('.class')]
    mismatch=[n for n in names if not (compiled/n).exists() or hashlib.sha256((compiled/n).read_bytes()).digest()!=hashlib.sha256(z.read(n)).digest()]
    missing=[str(p.relative_to(compiled)).replace('\\','/') for p in (compiled/prefix).rglob('*.class') if str(p.relative_to(compiled)).replace('\\','/') not in names]
r={'installedJar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'compactClassCount':len(names),'mismatches':mismatch,'missing':missing,'evidenceLevel':'ZIP class parity only; not fresh installed class loading/live MAS'}
(out/'installed-parity.json').write_text(json.dumps(r,indent=2),encoding='utf-8')
print(json.dumps(r,indent=2))
