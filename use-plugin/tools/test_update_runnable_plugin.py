"""Focused packaging guard regression; no MAS or authored semantics involved."""
import tempfile
from pathlib import Path
import unittest
import zipfile
from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location('updater', Path(__file__).with_name('update-runnable-plugin.py'))
updater = module_from_spec(spec)
spec.loader.exec_module(updater)


class DependencyParityTest(unittest.TestCase):
    def test_missing_changed_and_matching_dependency_classes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            dependency, plugin = root / 'contract.jar', root / 'plugin.jar'
            entry = 'contract/Inventory.class'
            with zipfile.ZipFile(dependency, 'w') as jar:
                jar.writestr(entry, b'current-dependency-bytecode')
            for payload, expected in [(None, 'CLASS_MISSING'), (b'old-bytecode', 'CLASS_MISMATCH'),
                                      (b'current-dependency-bytecode', None)]:
                with self.subTest(expected=expected):
                    with zipfile.ZipFile(plugin, 'w') as jar:
                        if payload is not None:
                            jar.writestr(entry, payload)
                    if expected:
                        with self.assertRaisesRegex(RuntimeError, expected):
                            updater.validate(plugin, [dependency])
                    else:
                        self.assertEqual({'contract.jar': 1}, updater.validate(plugin, [dependency]))


if __name__ == '__main__':
    unittest.main()
