import copy
import tempfile
import unittest
from pathlib import Path

from v2_baseline_audit import MODULE, inventory, structural_diff, mapping_audit
import json


class BaselineAuditTest(unittest.TestCase):
    def setUp(self):
        self.model = inventory(MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore')
        self.mapping = json.loads((MODULE / 'Core/Mapping/version-2/jacamo-use-mapping-v2.json').read_text())

    def test_current_source_coverage(self):
        self.assertEqual([], self.model['diagnostics'])
        self.assertEqual('PASS', mapping_audit(self.model, self.mapping)['status'])

    def test_mismatch_is_not_accepted_by_updating_only_fingerprint(self):
        changed = copy.deepcopy(self.model)
        changed['features']['Scheme#missions']['lower'] = 0
        self.assertEqual('FAIL', mapping_audit(changed, self.mapping)['status'])

    def test_orphan_and_missing_entries_rejected(self):
        for mutation in ('remove', 'duplicate', 'rename'):
            changed = copy.deepcopy(self.mapping)
            if mutation == 'remove':
                changed['classMappings'].pop()
            elif mutation == 'duplicate':
                changed['classMappings'].append(changed['classMappings'][0])
            else:
                changed['classMappings'][0]['source'] += 'Typo'
            self.assertEqual('FAIL', mapping_audit(self.model, changed)['status'], mutation)

    def test_self_diff_and_exact_rename(self):
        self.assertEqual([], structural_diff(self.model, self.model)['changes'])
        changed = copy.deepcopy(self.model)
        changed['classifiers']['AgentRenamed'] = changed['classifiers'].pop('Agent')
        self.assertEqual(['REMOVED', 'ADDED'], [e['change'] for e in structural_diff(self.model, changed)['changes']])

    def test_feature_and_inheritance_impacts_are_not_hidden(self):
        for field, value in [('type', '#//Agent'), ('lower', 9), ('containment', False), ('ordered', False)]:
            changed = copy.deepcopy(self.model)
            changed['features']['Scheme#missions'][field] = value
            self.assertEqual('CHANGED', structural_diff(self.model, changed)['changes'][0]['change'])
        changed = copy.deepcopy(self.model)
        changed['inheritance'].append(['Agent', '#//Artifact'])
        self.assertEqual('inheritance', structural_diff(self.model, changed)['changes'][0]['section'])

    def test_unsafe_xml_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'unsafe.ecore'
            path.write_text('<!DOCTYPE x [<!ENTITY y SYSTEM "file:///secret">]><x/>')
            with self.assertRaisesRegex(ValueError, 'ECORE_UNSAFE_XML'):
                inventory(path)

    def test_unresolved_reference_is_diagnostic(self):
        source = MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore'
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'invalid.ecore'
            path.write_text(source.read_text().replace('eType="#//Mission"', 'eType="#//Missing"'))
            self.assertTrue(any(d.startswith('UNRESOLVED_TYPE:') for d in inventory(path)['diagnostics']))


if __name__ == '__main__':
    unittest.main()
