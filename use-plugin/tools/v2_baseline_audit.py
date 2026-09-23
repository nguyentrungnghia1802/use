"""Read-only semantic intake. Never edits canonical inputs or infers renames.

Run from any directory: python use-plugin/tools/v2_baseline_audit.py --output DIR
Outputs are derived evidence, not a production mapping or a freeze approval.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

XSI = '{http://www.w3.org/2001/XMLSchema-instance}type'
MODULE = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory(path):
    data = path.read_bytes()
    if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError('ECORE_UNSAFE_XML')
    root = ET.fromstring(data)
    result = {'package': dict(root.attrib), 'sha256': digest(path),
              'classifiers': {}, 'features': {}, 'inheritance': [], 'diagnostics': []}
    for c in root.findall('eClassifiers'):
        name = c.get('name')
        if name in result['classifiers']:
            result['diagnostics'].append('DUPLICATE_CLASSIFIER:' + name)
        result['classifiers'][name] = {'kind': c.get(XSI), 'abstract': c.get('abstract', 'false') == 'true',
            'attributes': dict(c.attrib), 'literals': [dict(x.attrib) for x in c.findall('eLiterals')],
            'annotations': [ET.tostring(x, encoding='unicode') for x in c.findall('eAnnotations')]}
        for parent in c.get('eSuperTypes', '').split():
            result['inheritance'].append([name, parent])
        for f in c.findall('eStructuralFeatures'):
            key = name + '#' + f.get('name')
            if key in result['features']:
                result['diagnostics'].append('DUPLICATE_FEATURE:' + key)
            result['features'][key] = {
                'kind': f.get(XSI), 'type': f.get('eType'), 'lower': int(f.get('lowerBound', '0')),
                'upper': int(f.get('upperBound', '1')), 'ordered': f.get('ordered', 'true') == 'true',
                'unique': f.get('unique', 'true') == 'true', 'containment': f.get('containment', 'false') == 'true',
                'opposite': f.get('eOpposite'), 'explicitDefault': f.get('defaultValueLiteral'),
                'attributes': dict(f.attrib),
                'annotations': [ET.tostring(x, encoding='unicode') for x in f.findall('eAnnotations')]}
    for key, f in result['features'].items():
        target = (f['type'] or '').removeprefix('#//')
        if f['type'] is None or (f['type'].startswith('#//') and target not in result['classifiers']):
            result['diagnostics'].append('UNRESOLVED_TYPE:' + key)
        if f['kind'].endswith('EReference') and target in result['classifiers'] and not result['classifiers'][target]['kind'].endswith('EClass'):
            result['diagnostics'].append('NON_CLASS_REFERENCE:' + key)
        if f['lower'] < 0 or f['upper'] < -1 or (f['upper'] != -1 and f['lower'] > f['upper']):
            result['diagnostics'].append('INVALID_BOUNDS:' + key)
        if f['opposite']:
            opposite = f['opposite'].removeprefix('#//').replace('/', '#')
            other = result['features'].get(opposite)
            if not other or other['opposite'] != '#//' + key.replace('#', '/') or other['type'] != '#//' + key.split('#')[0]:
                result['diagnostics'].append('INVALID_OPPOSITE:' + key)
            if other and f['containment'] and other['containment']:
                result['diagnostics'].append('DOUBLE_CONTAINMENT:' + key)
    for owner, parent in result['inheritance']:
        if parent.removeprefix('#//') not in result['classifiers']:
            result['diagnostics'].append('UNRESOLVED_SUPERTYPE:' + owner + ':' + parent)
    result['counts'] = {kind: sum(c['kind'].endswith(kind) for c in result['classifiers'].values()) for kind in ('EClass', 'EEnum')}
    result['counts'].update({kind: sum(f['kind'].endswith(kind) for f in result['features'].values()) for kind in ('EAttribute', 'EReference')})
    result['counts']['inheritance'] = len(result['inheritance'])
    return result


def structural_diff(old, new):
    changes = []
    for section in ('classifiers', 'features'):
        for key in sorted(old[section].keys() | new[section].keys()):
            before, after = old[section].get(key), new[section].get(key)
            if before == after:
                continue
            changes.append({'section': section, 'identity': key,
                'change': 'ADDED' if before is None else 'REMOVED' if after is None else 'CHANGED',
                'before': before, 'after': after})
    for section in ('package', 'inheritance'):
        if old[section] != new[section]:
            changes.append({'section': section, 'change': 'CHANGED', 'before': old[section], 'after': new[section]})
    return {'oldSha256': old['sha256'], 'newSha256': new['sha256'],
            'identityPolicy': 'EXACT_LOCAL_IDENTITY; namespace change recorded separately; no inferred rename', 'changes': changes}


def impact_report(diff):
    """Conservative obligation classification, never proof of source-language equivalence."""
    layers = ['semantic IR', 'parser/extractor', 'structural mapping', 'projection',
              'trace identity', 'runtime target binding', 'OCL context/navigation', 'case studies']
    result = []
    extractors = {p.relative_to(MODULE).as_posix(): p.read_text(encoding='utf-8')
                  for p in sorted((MODULE / 'src/main/java/org/tzi/use/plugins/jacamo/extraction').glob('*.java'))}
    for change in diff['changes']:
        status = {'ADDED': 'ADDED_CAPABILITY', 'REMOVED': 'REMOVED_CAPABILITY',
                  'CHANGED': 'SEMANTIC_BREAKING_CHANGE'}[change['change']]
        before, after = change.get('before'), change.get('after')
        fields = sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k)) if isinstance(before, dict) and isinstance(after, dict) else []
        identity = change.get('identity', change['section'])
        owner = identity.split('#')[0]
        source_evidence = [path for path, text in extractors.items() if re.search(r'\bMetamodelKind\.' + re.escape(owner) + r'\b', text)]
        result.append({'identity': identity, 'section': change['section'],
            'status': status, 'changedFields': fields, 'impactedLayers': layers,
            'sourceLanguageDisposition': 'CURRENT_EXTRACTOR_REFERENCES_KIND; retain source facts while migrating structural representation' if source_evidence else
                'NO_EXACT_EXTRACTOR_KIND_REFERENCE; metamodel addition/removal alone is not source-language addition/removal',
            'sourceEvidence': source_evidence,
            'migrationRule': 'No automatic rename or source-language equivalence; retain removed source facts as provenance or report unsupported',
            'representationOnly': False})
    return {'policy': 'Conservative semantic breakage until equivalence is proven; added/removed refer to metamodel capabilities, not source-language removal',
            'changes': result, 'acceptedRenames': []}


def mapping_audit(model, mapping):
    errors = []
    prefix = model['package']['name'] + '::'
    if mapping['sourceMetamodel']['sha256'] != model['sha256']:
        errors.append('MAPPING_ECORE_MISMATCH')
    for section, kind, source in [('classMappings', 'EClass', model['classifiers']),
                                  ('enumMappings', 'EEnum', model['classifiers']),
                                  ('attributeMappings', 'EAttribute', model['features']),
                                  ('referenceMappings', 'EReference', model['features'])]:
        expected = {prefix + k for k, v in source.items() if v['kind'].endswith(kind)}
        found = [e['source'] for e in mapping[section]]
        if len(set(found)) != len(found) or set(found) != expected:
            errors.append({'code': 'SOURCE_COVERAGE', 'section': section,
                           'missing': sorted(expected - set(found)), 'extra': sorted(set(found) - expected)})
        if section not in ('attributeMappings', 'referenceMappings'):
            continue
        for e in mapping[section]:
            f = source.get(e['source'].removeprefix(prefix))
            if f is None:
                continue
            for field, actual in [('sourceMultiplicity', {'lower': f['lower'], 'upper': f['upper']})]:
                if any(e[field][k] != v for k, v in actual.items()):
                    errors.append({'code': 'SOURCE_BOUNDS', 'rule': e['id'], 'expected': actual, 'actual': e[field]})
            if kind == 'EReference':
                for field, expected_value in [('sourceTarget', f['type'].removeprefix('#//')),
                    ('sourceContainment', f['containment']), ('sourceOrdered', f['ordered']), ('sourceUnique', f['unique'])]:
                    if e.get(field) != expected_value:
                        errors.append({'code': 'SOURCE_FEATURE_MISMATCH', 'rule': e['id'], 'field': field})
            else:
                if e.get('sourceExplicitDefaultLiteral') != f['explicitDefault']:
                    errors.append({'code': 'SOURCE_DEFAULT', 'rule': e['id']})
    ids = [e['id'] for k, entries in mapping.items() if k.endswith('Mappings') and isinstance(entries, list) for e in entries]
    if len(ids) != len(set(ids)):
        errors.append('DUPLICATE_MAPPING_ID')
    return {'status': 'PASS' if not errors else 'FAIL', 'scope': 'Exact source coverage, feature metadata and fingerprint; not schema/USE/compiler validation', 'diagnostics': errors}


def main():
    from metamodel_diff import compare
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    old = inventory(MODULE / 'Core/Metamodel/version-1/JaCaMo-Metamodel.ecore')
    new = inventory(MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore')
    mapping = json.loads((MODULE / 'Core/Mapping/version-2/jacamo-use-mapping-v2.json').read_text(encoding='utf-8'))
    exact_diff = compare(MODULE / 'Core/Metamodel/version-1/JaCaMo-Metamodel.ecore',
                         MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore')
    exact_diff['structuralDetails'] = structural_diff(old, new)
    outputs = {'metamodel-v2-inventory.json': new, 'metamodel-v1-to-v2-diff.json': exact_diff,
        'metamodel-v1-to-v2-impact.json': impact_report(exact_diff['structuralDetails']),
        'mapping-v2-source-audit.json': mapping_audit(new, mapping),
        'v2-input-files.json': [{'path': p.relative_to(MODULE).as_posix(), 'sha256': digest(p), 'bytes': p.stat().st_size}
                              for folder in ('Metamodel', 'Mapping') for p in sorted((MODULE / 'Core' / folder / 'version-2').rglob('*')) if p.is_file()]}
    for name, value in outputs.items():
        (args.output / name).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(json.dumps({'counts': new['counts'], 'ecoreDiagnostics': new['diagnostics'], 'mapping': outputs['mapping-v2-source-audit.json']}))
    return 1 if new['diagnostics'] or outputs['mapping-v2-source-audit.json']['status'] != 'PASS' else 0


if __name__ == '__main__':
    raise SystemExit(main())
