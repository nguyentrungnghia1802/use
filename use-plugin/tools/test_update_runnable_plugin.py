"""Focused packaging guard regression; no MAS or authored semantics involved."""
import tempfile
from pathlib import Path
import unittest
import zipfile
import json
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

    def test_runtime_lock_requires_reviewed_producer_and_installed_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            repo, install = root / 'use', root / 'installed'
            module = repo / 'use-plugin/modules/bridge-jacamo'
            producer, runtime = module / 'target/producer-runtime', install / 'bridge/runtime'
            source = root / 'jacamo/build'
            for directory in [producer, runtime, source / 'libs', source / 'overlays']:
                directory.mkdir(parents=True, exist_ok=True)
            entries = []
            for name, payload in [('checkpoint-runtime-1.0.0-SNAPSHOT.jar', b'kernel'),
                                  ('cartago-3.1-checkpoint-SNAPSHOT.jar', b'reviewed')]:
                path = source / 'overlays' / name
                path.write_bytes(payload)
                (producer / name).write_bytes(payload)
                (runtime / name).write_bytes(payload)
                entries.append({'path': str(path), 'sha256': updater.digest(path)})
            entries[1].update(component='cartago', groupId='org.jacamo', artifactId='cartago',
                              version='3.1-checkpoint-SNAPSHOT')
            module.joinpath('pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <dependencies><dependency><groupId>org.jacamo</groupId><artifactId>jacamo</artifactId>
              <version>1.3.1-checkpoint-SNAPSHOT</version></dependency></dependencies></project>''')
            name = 'jacamo-1.3.1-checkpoint-SNAPSHOT.jar'
            for path in [source / 'libs' / name, producer / name, runtime / name]:
                path.write_bytes(b'actual-source-build')
            lock = root / 'jacamo/checkpoint-dependencies/dependency-lock.json'
            lock.parent.mkdir(parents=True)
            lock.write_text(json.dumps({'version': 'CHECKPOINT_DEPENDENCY_LOCK/1.0',
                                        'runtime': entries[0], 'components': entries[1:]}))
            original = lock.read_bytes()
            result = json.loads(updater.validated_runtime_lock(repo, install))
            self.assertEqual('jacamo', result['components'][-1]['artifactId'])
            self.assertEqual(original, lock.read_bytes(), 'Source lock is not rewritten')
            (runtime / 'cartago-3.1-checkpoint-SNAPSHOT.jar').write_bytes(b'unreviewed')
            with self.assertRaisesRegex(RuntimeError, 'REVIEWED_RUNTIME_BINARY_MISMATCH'):
                updater.validated_runtime_lock(repo, install)
            (runtime / 'cartago-3.1-checkpoint-SNAPSHOT.jar').write_bytes(b'reviewed')
            (producer / name).write_bytes(b'wrong-source-runtime')
            with self.assertRaisesRegex(RuntimeError, 'SOURCE_RUNTIME_BINARY_MISMATCH'):
                updater.validated_runtime_lock(repo, install)


if __name__ == '__main__':
    unittest.main()
