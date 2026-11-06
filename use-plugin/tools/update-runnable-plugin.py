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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', type=Path)
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--plugin', type=Path, help='Alternative shaded JAR to validate')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    install = (args.install or repo / 'use-assembly/target/new').resolve()
    plugin = args.plugin or repo / 'use-plugin/target/use-plugin-1.0.1.jar'
    contract = repo / 'jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar'
    engine = repo / 'use-jacamo-semantic-engine/target/use-jacamo-semantic-engine-7.5.0.jar'
    counts = validate(plugin, [contract, engine])
    if args.check_only:
        print(json.dumps({'validatedClasses': counts, 'plugin': str(plugin)}, indent=2))
        return
    if args.plugin:
        raise RuntimeError('ALTERNATIVE_PLUGIN_IS_CHECK_ONLY')
    if not (install / 'bin/start_use.bat').is_file():
        raise RuntimeError('EXISTING_INSTALLATION_REQUIRED')
    files = [(plugin, 'lib/plugins/use-jacamo-plugin-1.0.1.jar'),
             (contract, 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'),
             (repo / 'jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar',
              'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar')]
    # Resolve and validate every input before replacing any installed file.
    for source, _ in files:
        if not source.is_file():
            raise RuntimeError('BUILD_ARTIFACT_MISSING:' + str(source))
    backup = install / 'work/package-backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-validated-plugin')
    for source, relative in files:
        target = install / relative
        if target.is_file() and digest(target) == digest(source):
            continue
        if target.is_file():
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
    print(json.dumps({'validatedClasses': installed_counts, 'backup': str(backup),
                      'files': {relative: digest(install / relative) for _, relative in files}}, indent=2))


if __name__ == '__main__':
    main()
