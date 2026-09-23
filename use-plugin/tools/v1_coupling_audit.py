"""Reproducible lexical migration inventory at an explicit Git revision.

Exact tokens are review candidates, never accepted semantic mappings or renames.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path
from v2_baseline_audit import MODULE, inventory


def scan(revision):
    root = MODULE.parent
    old = inventory(MODULE / 'Core/Metamodel/version-1/JaCaMo-Metamodel.ecore')
    new = inventory(MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore')
    removed_classes = sorted(old['classifiers'].keys() - new['classifiers'].keys())
    removed_features = sorted(old['features'].keys() - new['features'].keys())
    feature_names = sorted({key.split('#')[1] for key in removed_features})
    strong = r'JaCaMo-Metamodel\.ecore|jacamo-use-mapping-v1|dSML4JaCaMo|STRUCTURAL_MAPPING_V1|c0aafab786c5ff3f|3279f46cb128a252'
    broad = strong + r'|MetamodelKind|\b[CARIE][0-9]{3}\b|\bVP00[1-7]\b|assertEquals\((37|67|63|14)|\b(' + '|'.join(map(re.escape, removed_classes + feature_names)) + r')\b'
    records = {}
    for expression, paths in [(strong, []), (broad, ['use-plugin'])]:
        command = ['git', 'grep', '-n', '-I', '-E', expression, revision, '--'] + paths
        result = subprocess.run(command, cwd=root, capture_output=True, text=True, encoding='utf-8')
        if result.returncode not in (0, 1):
            raise RuntimeError(result.stderr)
        for row in result.stdout.splitlines():
            _, path, number, text = row.split(':', 3)
            historical = any(token in path for token in ('/version-1/', '/release/evidence/', '/docs/project/evidence/', '/docs/agent/tasks/'))
            category = ('KEEP_HISTORICAL' if historical else
                        'VERSION_ABSTRACTION' if 'RuntimeMappingLoader' in path else
                        'MIGRATE' if any(token in path for token in ('/src/main/', '/src/test/', '/src/assembly/', '/pom.xml')) else
                        'REVIEW_REQUIRED')
            records[(path, int(number))] = {'path': path, 'line': int(number), 'classification': category, 'text': text}
    grouped = {}
    for (path, line), row in sorted(records.items()):
        entry = grouped.setdefault(path, {'path': path, 'classification': row['classification'], 'lines': []})
        entry['lines'].append(line)
    return {'revision': revision, 'policy': 'Whole repository strong identity scan plus plugin vocabulary/feature/rule/count scan; exact lexical candidates only; one disposition applies to every listed line; retrieve text from the immutable revision',
            'removedClassNames': removed_classes, 'removedFeatureIdentities': removed_features,
            'classificationPolicy': 'REMOVE is deliberately empty until dead-code proof; ambiguous lexical hits remain review candidates',
            'binaryResources': [{'path': 'use-gui/lib/plugins/use-jacamo-plugin-1.0.1.jar', 'classification': 'MIGRATE',
                                 'reason': 'Checked-in old plugin binary; replace only through tested packaging, not by editing binary bytes'}],
            'occurrenceCount': len(records), 'files': list(grouped.values())}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = scan(args.revision)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Classified exact lexical occurrences:', result['occurrenceCount'])
