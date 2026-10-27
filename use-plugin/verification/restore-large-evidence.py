"""Verify or restore byte-identical journals stored compressed for Git hosting.

Run without arguments to verify; pass --restore to recreate missing raw files.
Existing raw evidence is verified and never overwritten.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile

base = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--restore", action="store_true")
args = parser.parse_args()
index = json.loads((base / "compressed-evidence.json").read_text(encoding="utf8"))

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

for entry in index["files"]:
    raw, packed = ((base / entry[key]).resolve() for key in ("path", "compressed"))
    if not raw.is_relative_to(base) or not packed.is_relative_to(base) or packed != raw.with_suffix(raw.suffix + ".gz"):
        raise ValueError("Evidence path outside the verification tree")
    if digest(packed) != entry["compressedSha256"]:
        raise ValueError(f"Compressed hash mismatch: {packed}")
    if raw.exists() and digest(raw) != entry["sha256"]:
        raise ValueError(f"Existing evidence differs; refusing to overwrite: {raw}")
    temporary = None
    try:
        output = None
        if args.restore and not raw.exists():
            raw.parent.mkdir(parents=True, exist_ok=True)
            output = tempfile.NamedTemporaryFile(dir=raw.parent, delete=False)
            temporary = Path(output.name)
        checksum, size = hashlib.sha256(), 0
        try:
            with gzip.open(packed, "rb") as stream:
                while block := stream.read(1024 * 1024):
                    size += len(block)
                    if size > entry["bytes"]:
                        raise ValueError(f"Decoded evidence exceeds declared size: {raw}")
                    checksum.update(block)
                    if output:
                        output.write(block)
        finally:
            if output:
                output.close()
        if size != entry["bytes"] or checksum.hexdigest() != entry["sha256"]:
            raise ValueError(f"Decoded evidence hash/size mismatch: {raw}")
        if temporary:
            # Link creates a new directory entry and refuses any concurrent overwrite.
            os.link(temporary, raw)
    finally:
        if temporary:
            temporary.unlink(missing_ok=True)
print(f"COMPRESSED_EVIDENCE_PASS files={len(index['files'])} restore={args.restore}")
