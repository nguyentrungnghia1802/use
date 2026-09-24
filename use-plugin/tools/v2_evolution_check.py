"""Read-only V2 evolution preflight; emits exact impact and fails stale mapping compatibility."""
import argparse
import hashlib
import json
from pathlib import Path
from v2_baseline_audit import inventory, structural_diff, impact_report, mapping_audit


def check(before, after, mapping):
    old, new = inventory(Path(before)), inventory(Path(after))
    differences = structural_diff(old, new)
    contract = json.loads(Path(mapping).read_text(encoding='utf-8'))
    coverage = mapping_audit(new, contract)
    return {'status': 'RECONCILE_REQUIRED' if coverage['status'] != 'PASS' or new['diagnostics'] else 'SOURCE_COMPATIBLE',
            'beforeSha256': hashlib.sha256(Path(before).read_bytes()).hexdigest(),
            'afterSha256': hashlib.sha256(Path(after).read_bytes()).hexdigest(),
            'mappingSha256': hashlib.sha256(Path(mapping).read_bytes()).hexdigest(),
            'structuralDiff': differences, 'impact': impact_report(differences), 'sourceCoverage': coverage,
            'ecoreDiagnostics': new['diagnostics'],
            'acceptance': 'NOT_A_FREEZE_OR_FULL_ACCEPTANCE; run schema/native EMF, target compiler and affected consumer regressions',
            'writesCanonicalInputs': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True)
    parser.add_argument('--after', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() in {p.resolve() for p in (args.before, args.after, args.mapping)}:
        parser.error('Output must not overwrite a canonical input')
    result = check(args.before, args.after, args.mapping)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf-8', newline='\n')
    print(result['status'])
    raise SystemExit(0 if result['status'] == 'SOURCE_COMPATIBLE' else 2)
