import tempfile
import unittest
from pathlib import Path
from metamodel_diff import compare

class DiffTest(unittest.TestCase):
    def test_exact_diff_and_conservative_impacts(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)
            prefix = '<ecore:EPackage xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="test">'
            a = prefix + '<eClassifiers xsi:type="ecore:EClass" name="A"><eStructuralFeatures xsi:type="ecore:EAttribute" name="value" eType="EInt"/></eClassifiers><eClassifiers xsi:type="ecore:EClass" name="Old"/></ecore:EPackage>'
            b = a.replace('eType="EInt"', 'eType="EString" upperBound="2"').replace('name="A"', 'name="A" eSuperTypes="#//New"').replace('name="Old"', 'name="New"')
            (p/'a').write_text(a); (p/'b').write_text(b); (p/'mapping.json').write_text('{"anchor":"test::A#value"}')
            report = compare(p/'a',p/'b',[p/'mapping.json'])
            self.assertEqual(report, compare(p/'a',p/'b',[p/'mapping.json']))
            self.assertEqual({'CLASS_ADDED','CLASS_REMOVED','FEATURE_CHANGED','INHERITANCE_CHANGED'}, {c['kind'] for c in report['changes']})
            self.assertEqual('REVIEW_REQUIRED_NOT_ACCEPTED',report['renameCandidates'][0]['status'])
            self.assertEqual(['test::A','test::A#value'],report['impacts'][0]['exactTokenReferences'])
            self.assertEqual([],compare(p/'a',p/'a')['changes'])
    def test_reference_bounds_containment_target_and_add_remove(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)
            a='<EPackage xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" name="p"><eClassifiers xsi:type="EClass" name="A"><eStructuralFeatures xsi:type="EReference" name="r" eType="#//A"/><eStructuralFeatures xsi:type="EAttribute" name="gone"/></eClassifiers></EPackage>'
            b=a.replace('eType="#//A"','eType="#//B" containment="true" lowerBound="1"').replace('name="gone"','name="new"')
            (p/'a').write_text(a);(p/'b').write_text(b)
            result=compare(p/'a',p/'b')
            self.assertEqual({'FEATURE_ADDED','FEATURE_REMOVED','FEATURE_CHANGED'},{c['kind'] for c in result['changes']})
            change=next(c for c in result['changes'] if c['kind']=='FEATURE_CHANGED')
            self.assertEqual('true',change['after']['containment'])
            self.assertEqual('#//B',change['after']['eType'])
if __name__ == '__main__': unittest.main()
