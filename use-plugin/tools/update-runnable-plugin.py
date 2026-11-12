"""Validate reactor dependency bytes before refreshing an existing USE installation.

Build first: mvn -pl use-plugin -am package (with the project's selected checks).
This does not replace framework runtime JARs, case inputs or saved work.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile
import xml.etree.ElementTree as ET


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(plugin, dependencies):
    counts = {}
    with zipfile.ZipFile(plugin) as shaded:
        for dependency in dependencies:
            with zipfile.ZipFile(dependency) as source:
                entries = [name for name in source.namelist()
                           if name.endswith('.class') and not name.startswith('META-INF/')
                           and name != 'module-info.class']
                if not entries:
                    raise RuntimeError('EMPTY_DEPENDENCY_CLASSES:' + str(dependency))
                for name in entries:
                    try:
                        actual = shaded.read(name)
                    except KeyError:
                        raise RuntimeError('SHADED_DEPENDENCY_CLASS_MISSING:' + name) from None
                    if actual != source.read(name):
                        raise RuntimeError('SHADED_DEPENDENCY_CLASS_MISMATCH:' + name)
                counts[dependency.name] = len(entries)
    return counts


def validated_runtime_lock(repo, install):
    """Refresh provenance only after the installed runtime equals reviewed inputs.

    Never adjust expected hashes to accommodate arbitrary installed binaries.
    Framework binaries themselves are not replaced by this plugin updater.
    """
    lock_path = repo.parent / 'jacamo/checkpoint-dependencies/dependency-lock.json'
    lock = json.loads(lock_path.read_text(encoding='utf-8'))
    if lock.get('version') != 'CHECKPOINT_DEPENDENCY_LOCK/1.0':
        raise RuntimeError('DEPENDENCY_LOCK_VERSION_UNSUPPORTED')
    producer = repo / 'use-plugin/modules/bridge-jacamo/target/producer-runtime'
    runtime = install / 'bridge/runtime'
    for entry in [lock['runtime'], *lock['components']]:
        name = Path(entry['path']).name
        for candidate in [Path(entry['path']), producer / name, runtime / name]:
            if not candidate.is_file() or digest(candidate) != entry['sha256']:
                raise RuntimeError('REVIEWED_RUNTIME_BINARY_MISMATCH:' + str(candidate))
    # JaCaMo is built separately from the dependency overlays. Resolve its
    # coordinate from the producer POM and require the actual source build too.
    pom = ET.parse(repo / 'use-plugin/modules/bridge-jacamo/pom.xml')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    dependencies = [d for d in pom.findall('m:dependencies/m:dependency', ns)
                    if d.findtext('m:groupId', namespaces=ns) == 'org.jacamo'
                    and d.findtext('m:artifactId', namespaces=ns) == 'jacamo']
    if len(dependencies) != 1:
        raise RuntimeError('PRODUCER_RUNTIME_COORDINATE_NOT_UNIQUE')
    version = dependencies[0].findtext('m:version', namespaces=ns)
    name = 'jacamo-' + version + '.jar'
    source = repo.parent / 'jacamo/build/libs' / name
    expected = digest(source)
    for candidate in [producer / name, runtime / name]:
        if not candidate.is_file() or digest(candidate) != expected:
            raise RuntimeError('SOURCE_RUNTIME_BINARY_MISMATCH:' + str(candidate))
    if any(e['artifactId'] == 'jacamo' for e in lock['components']):
        raise RuntimeError('RUNTIME_COORDINATE_ALREADY_IN_OVERLAY_LOCK')
    lock['components'].append({'component': 'jacamo', 'groupId': 'org.jacamo',
                              'artifactId': 'jacamo', 'version': version,
                              'path': str(source), 'sha256': expected})
    return (json.dumps(lock, indent=2) + '\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', type=Path)
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--no-backup', action='store_true', help='Replace validated files without saving old binaries')
    parser.add_argument('--plugin', type=Path, help='Alternative shaded JAR to validate')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    install = (args.install or repo / 'use-assembly/target/new').resolve()
    plugin = args.plugin or repo / 'use-plugin/target/use-plugin-1.0.1.jar'
    contract = repo / 'use-plugin/modules/bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar'
    engine = repo / 'use-plugin/modules/semantic-engine/target/use-jacamo-semantic-engine-7.5.0.jar'
    counts = validate(plugin, [contract, engine])
    runtime_lock = validated_runtime_lock(repo, install)
    if args.check_only:
        print(json.dumps({'validatedClasses': counts, 'plugin': str(plugin)}, indent=2))
        return
    if args.plugin:
        raise RuntimeError('ALTERNATIVE_PLUGIN_IS_CHECK_ONLY')
    if not (install / 'bin/start_use.bat').is_file():
        raise RuntimeError('EXISTING_INSTALLATION_REQUIRED')
    generated_lock = repo / 'use-plugin/target/validated-dependency-lock.json'
    generated_lock.write_bytes(runtime_lock)
    files = [(plugin, 'lib/plugins/use-jacamo-plugin-1.0.1.jar'),
             (contract, 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'),
             (repo / 'use-plugin/modules/bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar',
              'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'),
             (generated_lock, 'bridge/dependency-lock.json')]
    # Resolve and validate every input before replacing any installed file.
    for source, _ in files:
        if not source.is_file():
            raise RuntimeError('BUILD_ARTIFACT_MISSING:' + str(source))
    backup = install / 'work/package-backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-validated-plugin')
    for source, relative in files:
        target = install / relative
        if target.is_file() and digest(target) == digest(source):
            continue
        if target.is_file() and not args.no_backup:
            saved = backup / relative
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, saved)
        target.parent.mkdir(parents=True, exist_ok=True)
        staged = target.with_name(target.name + '.validated-staged')
        shutil.copy2(source, staged)
        if digest(staged) != digest(source):
            raise RuntimeError('STAGED_ARTIFACT_MISMATCH:' + relative)
        os.replace(staged, target)
    installed_counts = validate(install / files[0][1], [install / files[1][1], engine])
    print(json.dumps({'validatedClasses': installed_counts, 'backup': None if args.no_backup else str(backup),
                      'files': {relative: digest(install / relative) for _, relative in files}}, indent=2))


if __name__ == '__main__':
    main()
