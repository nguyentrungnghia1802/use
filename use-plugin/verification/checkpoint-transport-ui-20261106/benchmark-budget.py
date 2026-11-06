from pathlib import Path
import json
import os
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(REPO / 'use-plugin/target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.xml.NewCaseStudyBundlesTest.xml').findall('./properties/property')}
classes = HERE / 'benchmark-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-cp', properties['java.class.path'], '-d', str(classes), str(HERE / 'SnapshotBudgetBenchmark.java')], check=True)
source = HERE.parent / 'additional-cases-20261106/house-building-1793905863758/final-native.json.gz'
receipts = {}
for mode in ('before', 'after'):
    classpath = [str(classes)]
    if mode == 'before':
        classpath += [str(REPO / 'use-assembly/target/new/lib/plugins/use-jacamo-plugin-1.0.1.jar')]
    classpath += [properties['java.class.path']]
    output = HERE / ('budget-' + mode + '.json')
    result = subprocess.run(['java', '-Xmx768m', '-Djava.awt.headless=true', '-cp', os.pathsep.join(classpath), 'SnapshotBudgetBenchmark', str(source), str(output)], capture_output=True, text=True, timeout=90)
    (HERE / ('budget-' + mode + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8', newline='\n')
    assert result.returncode == 0, (mode, result.stderr[-2000:])
    receipts[mode] = json.loads(output.read_bytes())
for field in ('nativeResultHash', 'snapshotBytes', 'retainedBytes', 'statusReads'):
    assert receipts['before'][field] == receipts['after'][field]
assert receipts['after']['sizingOperations'] == 1
receipts['statusReadSpeedup'] = receipts['before']['statusReadNanos'] / receipts['after']['statusReadNanos']
(HERE / 'budget-benchmark.json').write_text(json.dumps(receipts, indent=2) + '\n', encoding='utf-8', newline='\n')
print('IDENTICAL_SNAPSHOT_BYTE_BUDGET', 'status read speedup', receipts['statusReadSpeedup'])
