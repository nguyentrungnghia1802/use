# Verification evidence

Reports, receipts, source freezes and focused/final logs retain their original
scope. A passing component test does not replace a packaged or live GUI gate.
Current authored/selective closeout is indexed by
`selective-runtime/S07-final-source-package.json` and `use/plan.md`.

Large generated JSONL journals are stored as deterministic `.jsonl.gz` files so
the complete evidence can be hosted without exceeding GitHub's file-size limit.
`compressed-evidence.json` records both compressed and original SHA-256/size.
Original raw files remain on the development machine and are ignored by Git.
Other replay assets and manifests are unchanged.

From the repository root, verify the archives:

```powershell
python use-plugin/verification/restore-large-evidence.py
```

Before replaying an archived bundle after a fresh clone, restore its exact raw
journal paths (no existing evidence is overwritten):

```powershell
python use-plugin/verification/restore-large-evidence.py --restore
```

The frozen full host ZIP is a build output, not committed binary source. Its
hash and qualification receipts are retained; build the distribution through the
existing Maven reactor when an installation archive is needed.
