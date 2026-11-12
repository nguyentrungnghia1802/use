# Current cleanup

The current examples select compact mapping XML through their `.mrv` files.
The unused v2 mappings have been removed from Auction, Auction False, Hello World
and House Building. Definitions and native contracts remain in CSL/iStar/OCL;
no domain behavior or verdict policy was changed.

The release archive uses the `-release.zip` name. The redundant `-v2-frozen.zip`
copy is no longer generated. Sources already absent from Core are no longer
declared as release files. Historical alternative authored mappings are no longer
copied into the release as additional model candidates. Isolated regression fixtures, original engine sources,
license notices and shared decoders remain necessary development dependencies.

The runnable updater supports `--no-backup`. It still validates dependency class
parity and the source, producer and installed JaCaMo binary hashes before replacing
installed files.

Bulk deletion of historical runs, logs and installed backups was rejected by the
automatic command review with `blocked by policy`. Those directories have not
been removed. Source cleanup must not be presented as completed disk cleanup.
The manual entry point is `tools/remove-old-run-data.ps1`; it validates all target
paths and rejects junctions before deleting anything. It has not been executed.
Use `-WhatIf` to inspect its scope before running it locally.

Fresh validation results are recorded in the commit description; generated logs
belong under `target`, rather than versioned verification directories.
