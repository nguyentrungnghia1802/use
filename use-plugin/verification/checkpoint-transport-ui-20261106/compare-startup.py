from pathlib import Path
import json
import os
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
properties = {p.attrib['name']: p.attrib['value'] for p in ET.parse(REPO / 'use-plugin/target/surefire-reports/TEST-org.tzi.use.plugins.jacamo.semantics.xml.NewCaseStudyBundlesTest.xml').findall('./properties/property')}
receipts = {}
for mode in ('before', 'after'):
    classpath = properties['java.class.path']
    if mode == 'before':
        classpath = str(REPO / 'use-assembly/target/new/lib/plugins/use-jacamo-plugin-1.0.1.jar') + os.pathsep + classpath
    result = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', classpath, 'org.tzi.use.plugins.jacamo.ui.VerifiedStartupPresentationTest'], capture_output=True, text=True, timeout=30)
    (HERE / ('startup-' + mode + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8', newline='\n')
    receipts[mode] = {'exitCode': result.returncode}
    if mode == 'before':
        assert result.returncode != 0 and 'expected: <0> but was:' in result.stderr
    else:
        assert result.returncode == 0, result.stderr[-2000:]
(HERE / 'startup-comparison.json').write_text(json.dumps(receipts, indent=2) + '\n', encoding='utf-8', newline='\n')
print('ORIGINAL_STARTUP_HISTORY_REGRESSION_REPRODUCED_AND_REPAIRED')
