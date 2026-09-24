# Phase 29 pre-migration baseline — 2026-09-23

Status: capture complete; migration and phase acceptance remain open.
Classification: TEST/EVIDENCE CHANGE and reproducibility configuration fix. No canonical input or production behavior changed.

Repository started clean on `main`, HEAD `eeb98c41`. Implementation branch:
`phase/29-v2-migration-baseline`. The latest input change is `70e8498b`, which
moved V1 files under `version-1` and added V2 inputs without migrating consumers.
The full initial branch/log/status was inspected before any edits.

Fresh command: `mvn -B verify`, exit 1. **306 executed, 3 failures, 73 errors,
0 skipped** across the stages reached. This is a failed baseline, not historical
309/309 evidence. Plugin unit tests: 163 executed, 3 failures, 73 errors. Plugin
packaging and its three integration/package tests were not reached. Individual
suite counts and error messages are in [pre-migration-baseline.json](pre-migration-baseline.json).
The original command log is retained at `use-plugin/target/phase29-baseline-build.log`
relative to the repository root; its SHA-256 is recorded in the JSON.

Primary reproduced failure: `MappingLoader.loadCanonical` and test fixtures still
read `Core/Mapping/jacamo-use-mapping-v1.json`; the actual historical file now lives
under `Core/Mapping/version-1/`. The POM and release assembly likewise select the
removed locations. Existing target outputs must not be treated as proof of a
fresh canonical package. The baseline JSON captures configured package resources,
not a claim that this failing build produced a valid JAR/ZIP.

Pins remain plugin 1.0.1 candidate, USE 7.5.0, Java minimum 21, Jason 3.3.0,
CArtAgO 3.1, Moise 1.1, JaCaMo target 1.3.0. Exact compatibility metadata and
V1 input hashes are captured in the JSON. V1 bytes are unchanged.

Historical expected outputs are preserved through hashes of the Auction golden
properties and both complete fixture trees (`auction` and `counter-team`).
CounterTeamIntegrationTest defines case 2's expected behavior; it does not have
an independent checked-in golden output set. The initial failing default run is not promoted to passing output. A separate
explicit historical-path harness now preserves compiled/materialized static output
under `historical-static/auction/` and `historical-static/counter-team/`. Historical standalone Auction semantic limits
remain historical limits, not fresh V2 evidence.

Related docs inspected: agent/task, metamodel/mapping contracts, Phase 24
two-case evidence, compatibility/POM/release assembly, both V2 audits.
Next gates: active policy implementation, coupling migration, full regression.

Input relocation also left versioned Ecore/JSON outside the original `.gitattributes`
byte-preservation patterns. Recursive patterns now cover the versioned folders.
`git check-attr` confirms `text: unset`; every versioned canonical Ecore/JSON byte
matches its committed pre-change blob. This prevents host line-ending conversion
from invalidating fingerprints without changing the semantic baseline.

Intake tooling validation: `python -m unittest discover -s use-plugin/tools -p "test_*.py" -v`: **9/9 PASS**, including the existing diff regressions and new missing/orphan/duplicate source, changed bounds, no inferred rename, XXE and unresolved-target controls. `git diff --check`: PASS. Production and V1 canonical bytes are unchanged, so the captured failing Maven baseline remains the applicable full-regression status; it has not been relabelled PASS.

Historical capture command: `mvn -B -pl use-plugin -am -Dtest=PreMigrationBaselineTest -Dsurefire.failIfNoSpecifiedTests=false test`: **2/2 PASS**, zero failures/errors/skips. The selector flag scopes this focused run to the new harness; it does not waive full-suite gates. Both cases compile with the real USE compiler, materialize valid initial structures/invariants and reproduce identical text from the same plan. Manifests hash the exact preserved `.use`, `.cmd` and trace bytes. This is static V1 reference evidence, not runtime or V2 evidence. Log: `use-plugin/target/phase29-historical-static.log`.
