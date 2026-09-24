import copy
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from v2_evolution_check import check

MODULE = Path(__file__).resolve().parents[1]
SOURCE = MODULE / 'Core/Metamodel/version-2/jacamo_v2_complete.ecore'
MAPPING = MODULE / 'Core/Mapping/version-2/jacamo-use-mapping-v2.json'
XSI = '{http://www.w3.org/2001/XMLSchema-instance}type'


class EvolutionTest(unittest.TestCase):
    def test_self_diff_is_reproducible_and_read_only(self):
        before = SOURCE.read_bytes(), MAPPING.read_bytes()
        first = check(SOURCE, SOURCE, MAPPING)
        self.assertEqual(first, check(SOURCE, SOURCE, MAPPING))
        self.assertEqual('SOURCE_COMPATIBLE', first['status'])
        self.assertFalse(first['writesCanonicalInputs'])
        self.assertFalse(first['structuralDiff']['changes'])
        self.assertEqual(before, (SOURCE.read_bytes(), MAPPING.read_bytes()))

    def test_synthetic_changes_fail_compatibility_and_identify_consumer_layers(self):
        baseline = ET.parse(SOURCE)
        for mutation in ('add_class', 'remove_class', 'add_attribute', 'datatype', 'bounds', 'containment', 'target', 'inheritance', 'ordering'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                tree = copy.deepcopy(baseline); root = tree.getroot()
                classes = [c for c in root.findall('eClassifiers') if c.get(XSI) == 'ecore:EClass']
                cls = classes[0]
                attrs = [f for c in classes for f in c.findall('eStructuralFeatures') if f.get(XSI) == 'ecore:EAttribute']
                refs = [f for c in classes for f in c.findall('eStructuralFeatures') if f.get(XSI) == 'ecore:EReference']
                if mutation == 'add_class': ET.SubElement(root, 'eClassifiers', {XSI: 'ecore:EClass', 'name': 'SyntheticType'})
                elif mutation == 'remove_class': root.remove(classes[-1])
                elif mutation == 'add_attribute': ET.SubElement(cls, 'eStructuralFeatures', {XSI: 'ecore:EAttribute', 'name': 'synthetic', 'eType': 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString'})
                elif mutation == 'datatype': attrs[0].set('eType', 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean')
                elif mutation == 'bounds': refs[0].set('lowerBound', '1')
                elif mutation == 'containment': refs[0].set('containment', 'false')
                elif mutation == 'target': refs[0].set('eType', '#//' + classes[-1].get('name'))
                elif mutation == 'inheritance': cls.set('eSuperTypes', '#//' + classes[-1].get('name'))
                elif mutation == 'ordering': refs[0].set('ordered', 'false')
                after = Path(directory) / 'changed.ecore'; tree.write(after, encoding='utf-8', xml_declaration=True)
                result = check(SOURCE, after, MAPPING)
                self.assertEqual('RECONCILE_REQUIRED', result['status'])
                self.assertTrue(result['structuralDiff']['changes'])
                self.assertEqual([], result['impact']['acceptedRenames'])
                layers = {v for c in result['impact']['changes'] for v in c['impactedLayers']}
                self.assertTrue({'semantic IR', 'parser/extractor', 'projection', 'trace identity', 'runtime target binding', 'OCL context/navigation'} <= layers)


if __name__ == '__main__': unittest.main()
