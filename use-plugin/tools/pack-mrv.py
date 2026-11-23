#!/usr/bin/env python3
"""Package a JaCaMo verification experiment as MRV v1."""
import argparse
import json
from pathlib import Path
from shutil import copyfileobj
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


def entry(name):
    info = ZipInfo(name)
    info.compress_type = ZIP_DEFLATED
    return info


def pack(project, mapping, csl, istar, extra_files):
    project = project.resolve(strict=True)
    root = project.parent
    entries = {}

    def add(path):
        path = path.absolute()
        name = path.relative_to(root).as_posix()
        path = path.resolve(strict=True)
        path.relative_to(root)
        if not path.is_file() or name == "mrv.json":
            raise ValueError(f"Invalid MRV input: {name}")
        entries[name] = path
        return name

    manifest = {"version": 1}
    for role, path, suffix in (
        ("project", project, ".jcm"), ("mapping", mapping, ".xml"),
        ("csl", csl, ".csl"), ("istar", istar, ".istar"),
    ):
        if path.suffix != suffix:
            raise ValueError(f"Expected a {suffix} file for {role}: {path}")
        manifest[role] = add(path.resolve(strict=True))

    for path in (root / "src").rglob("*"):
        if path.is_file():
            add(path)
    for path in extra_files:
        add(path.resolve(strict=True))

    destination = project.with_suffix(".mrv")
    if destination.name in entries:
        raise ValueError("The destination MRV cannot be included in itself.")
    with TemporaryDirectory(dir=root) as staging:
        archive = Path(staging) / destination.name
        with ZipFile(archive, "w") as bundle:
            bundle.writestr(entry("mrv.json"), json.dumps(manifest, indent=2) + "\n")
            for name, path in sorted(entries.items()):
                with path.open("rb") as source, bundle.open(entry(name), "w") as target:
                    copyfileobj(source, target)
        archive.replace(destination)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for role in ("project", "mapping", "csl", "istar"):
        parser.add_argument(role, type=Path)
    parser.add_argument("--include", type=Path, action="append", default=[],
                        metavar="FILE", help="Add a project-relative asset outside src/.")
    args = parser.parse_args()
    try:
        print(pack(args.project, args.mapping, args.csl, args.istar, args.include))
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
