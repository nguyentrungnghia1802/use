from pathlib import Path
import ast
import os
import subprocess

HERE = Path(__file__).resolve().parent
script = HERE / 'run-installed.py'
tree = ast.parse(script.read_text())
prefix = []
for statement in tree.body:
    if isinstance(statement, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'folder' for t in statement.targets):
        break
    prefix.append(statement)
values = {'__file__': str(script)}
exec(compile(ast.Module(body=prefix, type_ignores=[]), str(script), 'exec'), values)
classes = HERE / 'identity-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-d', str(classes), str(HERE / 'ConsumerResourceOrigins.java')], check=True)
probe = subprocess.run(['java', '-cp', str(classes) + os.pathsep + values['classpath'], 'ConsumerResourceOrigins'], capture_output=True, text=True, check=True)
(HERE / 'consumer-resource-origins.log').write_text(probe.stdout + probe.stderr, encoding='utf-8', newline='\n')
print(probe.stdout)
