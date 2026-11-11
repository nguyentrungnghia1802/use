# Verification evidence

These directories contain historical test reports, source audits, screenshots,
runtime journals and replay inputs. A date or a failed test does not make evidence
disposable. Keep the evidence when changing an implementation or verdict.

Reports, receipts, source freezes and focused/final logs retain their original
scope. The earlier authored/selective closeout remains indexed by
`selective-runtime/S07-final-source-package.json` and `use/plan.md`; its receipts
do not establish acceptance for the subsequent compact mapping migration.

The frozen full host ZIP remains a build output, not committed binary source.
Its hash and qualification receipts are retained. Build a distribution through
the existing Maven reactor when an installation archive is needed.

## Storage maintenance (2026-11-11)

The initial inventory contained 63 top-level directories and 25,793,425,239 bytes.
Maintenance archived 148 closed, untracked JSONL journals (23,612,061,679 raw bytes)
using gzip and removed 46 empty directories. Existing tracked evidence was kept.
Every archive was verified against the original journal's SHA-256 and byte count
before removing its redundant raw copy. The final independent archive check
reported `COMPRESSED_EVIDENCE_PASS files=148 restore=False`.

The audit is in `../verification-maintenance-report.json`; the initial inventory is
in `../verification-directory-inventory.json`. Archive identities and both raw and
compressed hashes are recorded in [compressed-evidence.json](compressed-evidence.json).
The maintenance tool refuses paths outside this tree and skips recent, tracked
or writer-locked journals. It does not recursively delete evidence directories.

From the `use` repository root:

```powershell
# Verify every registered archive without restoring large raw files.
python use-plugin/verification/restore-large-evidence.py

# Restore byte-identical raw journals when a replay tool requires them.
# This restores all registered journals and needs the corresponding disk space.
python use-plugin/verification/restore-large-evidence.py --restore

# Preview eligible storage maintenance; this is read-only by default.
python use-plugin/tools/compact-verification-evidence.py
```

Do not remove archived journals, source audits or receipts merely to make this
directory look smaller. Runtime logs and replay inputs are not build caches.

## Current compact mapping work

- [Review findings](compact-review-20261111/REVIEW.md)
- [Repair status and evidence boundaries](compact-repair-20261111/REPAIR-STATUS.md)

Compilation/component tests, a real native writer, live MAS execution, offscreen
Swing and manual desktop acceptance are separate evidence levels. A successful
component test is not proof that an example was migrated or the installed package
was updated. Use the root `plan.md` for the remaining integration gates.
