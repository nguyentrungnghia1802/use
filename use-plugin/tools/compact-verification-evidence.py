"""Compact closed, untracked replay caches without losing evidence.

Default is dry-run. --apply uses the existing lossless archive manifest and
restore-large-evidence.py protocol. Tracked files and recent/open-run candidates
are retained. Every archive is decoded and checksummed before removing raw bytes.
No recursive deletion, directory relocation or source/model mutation is performed.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
plugin = Path(__file__).resolve().parents[1]
root = (plugin / 'verification').resolve(strict=True)
repo = plugin.parent
manifest = root / 'compressed-evidence.json'
index = json.loads(manifest.read_text(encoding='utf-8'))
if index.get('version') != 'LOSSLESS_EVIDENCE_ARCHIVES/1.0':
    raise ValueError('Unsupported archive manifest')
tracked = set(subprocess.check_output(['git', '-C', str(repo), 'ls-files', '-z'], text=True).split('\0'))
entries = {e['path']: e for e in index['files']}
audit = {'version': 'VERIFICATION_MAINTENANCE/1.0', 'apply': args.apply, 'root': str(root), 'removedRaw': [], 'removedEmptyDirectories': [], 'retained': []}

def safe(path):
    resolved = path.resolve()
    if resolved == root or not resolved.is_relative_to(root) or path.is_symlink() or getattr(path, 'is_junction', lambda: False)():
        raise ValueError(f'Unsafe evidence path: {path}')
    return resolved

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def decoded(path):
    checksum, size = hashlib.sha256(), 0
    with gzip.open(path, 'rb') as stream:
        while block := stream.read(1024 * 1024):
            checksum.update(block)
            size += len(block)
    return checksum.hexdigest(), size

def save_manifest():
    index['files'] = [entries[k] for k in sorted(entries)]
    with tempfile.NamedTemporaryFile(dir=root, suffix='.manifest.tmp', mode='w', encoding='utf-8', newline='\n', delete=False) as stream:
        temporary = Path(stream.name)
        json.dump(index, stream, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    try:
        os.replace(temporary, manifest)
    finally:
        temporary.unlink(missing_ok=True)

candidates = []
now = time.time()
for raw in root.rglob('*.jsonl'):
    safe(raw)
    relative = raw.relative_to(root).as_posix()
    if raw.relative_to(repo).as_posix() in tracked or now - raw.stat().st_mtime < 86400:
        continue
    if relative in entries or raw.stat().st_size >= 8 * 1024 * 1024:
        candidates.append(raw)
print(f'CANDIDATES files={len(candidates)} rawBytes={sum(p.stat().st_size for p in candidates)}', flush=True)
if args.apply:
    for number, raw in enumerate(candidates, 1):
        handle = None
        if os.name == 'nt':
            import ctypes
            from ctypes import wintypes
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
            kernel.CreateFileW.restype = wintypes.HANDLE
            kernel.CloseHandle.argtypes = [wintypes.HANDLE]
            kernel.CloseHandle.restype = wintypes.BOOL
            # Deny concurrent writers, including already-open producer writers;
            # permit our own final unlink while this read lease remains held.
            handle = kernel.CreateFileW(str(raw), 0x80000000, 0x00000001 | 0x00000004, None, 3, 0, None)
            if handle == ctypes.c_void_p(-1).value:
                audit['retained'].append({'path': raw.relative_to(root).as_posix(), 'reason': 'FILE_BUSY_OR_LEASE_UNAVAILABLE'})
                continue
        try:
            relative = raw.relative_to(root).as_posix()
            packed = safe(Path(str(raw) + '.gz'))
            before = raw.stat()
            checksum = digest(raw)
            entry = entries.get(relative)
            if entry is not None and (entry['sha256'] != checksum or entry['bytes'] != before.st_size):
                audit['retained'].append({'path': relative, 'reason': 'RAW_DIFFERS_FROM_ARCHIVE_MANIFEST'})
                continue
            if not packed.exists():
                with tempfile.NamedTemporaryFile(dir=raw.parent, suffix='.gz.tmp', delete=False) as temporary:
                    temporary_path = Path(temporary.name)
                    try:
                        with gzip.GzipFile(fileobj=temporary, filename='', mode='wb', compresslevel=1, mtime=0) as output:
                            with raw.open('rb') as source:
                                while block := source.read(1024 * 1024):
                                    output.write(block)
                        temporary.flush()
                        os.fsync(temporary.fileno())
                        temporary.close()
                        os.link(temporary_path, packed)  # refuses concurrent overwrite
                    finally:
                        temporary_path.unlink(missing_ok=True)
            packed_hash = digest(packed)
            unpacked_hash, unpacked_size = decoded(packed)
            if unpacked_hash != checksum or unpacked_size != before.st_size or entry is not None and packed_hash != entry['compressedSha256']:
                audit['retained'].append({'path': relative, 'reason': 'ARCHIVE_VERIFICATION_MISMATCH'})
                continue
            current = raw.stat()
            if (current.st_size, current.st_mtime_ns) != (before.st_size, before.st_mtime_ns) or digest(raw) != checksum:
                audit['retained'].append({'path': relative, 'reason': 'RAW_CHANGED_DURING_ARCHIVING'})
                continue
            entries[relative] = {'path': relative, 'bytes': before.st_size, 'sha256': checksum,
                                 'compressed': packed.relative_to(root).as_posix(), 'compressedBytes': packed.stat().st_size, 'compressedSha256': packed_hash}
            save_manifest()  # durable restore index exists before raw removal
            safe(raw).unlink()
            audit['removedRaw'].append(entries[relative])
            print(f'ARCHIVED {number}/{len(candidates)} {relative} raw={before.st_size} packed={packed.stat().st_size}', flush=True)
        finally:
            if handle is not None:
                kernel.CloseHandle(handle)
    for directory in sorted((p for p in root.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
        safe(directory)
        if not any(directory.iterdir()):
            directory.rmdir()
            audit['removedEmptyDirectories'].append(directory.relative_to(root).as_posix())
if args.apply:
    report = plugin / 'verification-maintenance-report.json'
    report.write_text(json.dumps(audit, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'MAINTENANCE_DONE apply=True archived={len(audit["removedRaw"])} emptyDirectories={len(audit["removedEmptyDirectories"])} report={report}', flush=True)
else:
    print('MAINTENANCE_PREVIEW_ONLY no files or audit reports modified', flush=True)
