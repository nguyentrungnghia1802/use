"""Explicit authoring-time refresh of selected .mrv model digests.

Does not change DSL, contracts, source pins or active runs. Native Prepare + Import
still validates syntax, mapping and semantics; frozen replay digests stay strict.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET


def refresh(mrv, check=False):
    mrv = Path(mrv).resolve(strict=True)
    if mrv.suffix.lower() != '.mrv':
        raise ValueError('EXPECTED_MRV_FILE')
    selected = {}
    for number, line in enumerate(mrv.read_text(encoding='utf-8-sig').splitlines(), 1):
        line = line.strip()
        if not line or line.startswith(('#', '//')):
            continue
        match = re.match(r'^([^:=]+)[:=](.*)$', line)
        key, value = (match[1].strip().lower(), match[2].strip()) if match else (Path(line).suffix[1:].lower(), line)
        key = 'xml' if key == 'mapping' else key
        if key not in {'csl', 'istar', 'xml', 'ocl'} or key in selected:
            raise ValueError(f'MRV_UNKNOWN_OR_DUPLICATE_ENTRY:{number}')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in '\"\'':
            value = value[1:-1].strip()
        path = Path(value)
        path = path if path.is_absolute() else mrv.parent / path
        if path.is_symlink() or not path.is_file():
            raise ValueError(f'MRV_TARGET_UNAVAILABLE:{key}')
        selected[key] = path.resolve(strict=True)
    if not {'csl', 'istar', 'xml'} <= selected.keys():
        raise ValueError('MRV_REQUIRED_INPUT_MISSING')
    target = selected['xml']
    original = target.read_bytes()
    if len(original) > 8 * 1024 * 1024 or b'<!DOCTYPE' in original.upper() or b'<!ENTITY' in original.upper():
        raise ValueError('MAPPING_XML_UNSUPPORTED')
    ET.fromstring(original)
    text = original.decode('utf-8-sig')
    receipt = {}
    for tag, fields in [('models', {'cslSha256': 'csl', 'istarSha256': 'istar'}), ('ocl', {'sha256': 'ocl'})]:
        if tag == 'ocl' and 'ocl' not in selected:
            continue
        pattern = r'<(?:[A-Za-z_][\w.-]*:)?' + tag + r'\b[^>]*>'
        matches = list(re.finditer(pattern, text))
        if len(matches) != 1:
            raise ValueError(f'MAPPING_DECLARATION_NOT_UNIQUE:{tag}')
        node = matches[0].group()
        updated = node
        for attribute, key in fields.items():
            data = selected[key].read_bytes()
            if len(data) > 8 * 1024 * 1024:
                raise ValueError(f'AUTHORED_INPUT_CAPACITY:{key}')
            digest = hashlib.sha256(data).hexdigest()
            expression = r'\b' + attribute + r'="([0-9a-f]{64})"'
            found = re.search(expression, updated)
            if found is None:
                raise ValueError(f'MAPPING_DIGEST_ATTRIBUTE_REQUIRED:{attribute}')
            receipt[attribute] = {'previous': found[1], 'selected': digest}
            updated = re.sub(expression, attribute + '="' + digest + '"', updated)
        text = text[:matches[0].start()] + updated + text[matches[0].end():]
    ET.fromstring(text)
    changed = any(v['previous'] != v['selected'] for v in receipt.values())
    if changed and not check:
        backup = Path(__file__).resolve().parents[1] / 'target/authored-hash-backups'
        backup.mkdir(parents=True, exist_ok=True)
        (backup / (hashlib.sha256(original).hexdigest() + '.xml')).write_bytes(original)
        target.write_bytes(text.encode('utf-8'))
    return {'mapping': str(target), 'changed': changed, 'written': changed and not check, 'hashes': receipt}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mrv', required=True)
    parser.add_argument('--check', action='store_true', help='Check only; exit 1 if the selected bytes differ.')
    args = parser.parse_args()
    result = refresh(args.mrv, args.check)
    print(json.dumps(result, indent=2))
    raise SystemExit(1 if args.check and result['changed'] else 0)
