# TASK — Simplify USE Projection + Generic JaCaMo Launcher

## Goal

Solve two issues quickly:

1. Reduce USE model/state size so Class Diagram/Object Diagram stay useful for OCL verification.
2. Create one generic launcher that can run any valid `.jcm`, not only Hello World or another case study.

# 1. Reduce USE projection

Keep full semantics in:

```text
JaCaMo APIs
→ typed semantic contract
→ JacamoSpecificationModel
```

Only project verification-relevant semantics into:

```text
MModel
MSystemState
```

## Tasks

- [x] Audit generated USE classes/objects.
- [x] Classify mapped concepts:
  - `VERIFICATION_DOMAIN`
  - `STRUCTURAL_SUPPORT`
  - `EVIDENCE_ONLY`
- [x] Materialize `VERIFICATION_DOMAIN` by default.
- [x] Materialize `STRUCTURAL_SUPPORT` only when OCL/verification requires it.
- [x] Keep `EVIDENCE_ONLY` in trace/evidence/diagnostics instead of normal USE objects.

Prioritize removing/hiding from default USE state:

- [x] `ExactBindingEvidence`
- [x] source/import provenance objects
- [x] runtime evidence/history objects
- [x] snapshot-only helper objects
- [x] other trace/diagnostic-only concepts

Review especially:

```text
A17PlanOrderEntry
A19BodyOrderEntry
```

- [x] If possible, replace order-entry objects with a lightweight `ordinal/rank` representation on existing domain objects.
- [x] Preserve A17/A19/A20 ordering semantics and OCL correctness.
- [x] Do not lose source semantics just to reduce object count.

## Projection mode

Add:

```text
AUTO = default, only verification-required semantics
FULL = full mapping for debug/audit
```

- [x] `AUTO` is default.
- [x] `FULL` preserves complete projection capability.
- [x] Separate rule support from materialization:

```text
IMPLEMENTED != MATERIALIZED
```

Use statuses such as:

```text
MATERIALIZED
PROFILE_EXCLUDED
EVIDENCE_ONLY
```

## Acceptance

- [x] Hello still passes existing OCL/verification tests.
- [x] Class count clearly decreases in `AUTO`.
- [x] Object count clearly decreases in `AUTO`.
- [x] Object Diagram becomes usable.
- [x] No required OCL constraint loses required targets.
- [x] `FULL` remains available for audit/debug.

# 2. Generic `.jcm` launcher

Production usage must not depend on `live-hello-bridge.ps1`.

Create a generic launcher, for example:

```text
use-plugin/tools/jacamo-bridge.ps1
```

Usage:

```powershell
.\use-plugin\tools\jacamo-bridge.ps1 `
  -JcmPath "D:\path\project.jcm"
```

## Tasks

- [x] Accept any valid `.jcm`.
- [x] Do not hard-code Hello/Auction/House.
- [x] Derive project root/name/key automatically where possible.
- [x] Start the existing Bridge/native pipeline with the selected `.jcm`.
- [x] Open USE GUI when requested.
- [x] Use `CODE_GROUNDED_NATIVE`.
- [x] Keep case-study scripts only as test harnesses if still needed.
- [x] Remove case-study launcher references from production docs.

## Tests

- [x] Test with Hello `.jcm`.
- [x] Test with at least one different `.jcm`.
- [x] Prove both use the same generic launcher path.
- [x] Invalid/missing `.jcm` fails clearly.

# 3. Safety

- [x] No fuzzy/name-based semantic binding.
- [x] No case-study hard coding.
- [x] Do not modify frozen V2/Ecore/golden artifacts.
- [x] Keep one active `MSystem`.
- [x] Do not reduce semantic extraction; reduce only USE projection.
- [x] Existing supported OCL/runtime behavior must still pass.

# 4. Final verification

Run:

- [x] focused projection tests;
- [x] launcher tests;
- [x] native OCL/session tests;
- [x] full reactor verify;
- [x] package/release tests.

Report:

1. USE classes/objects before vs after in `AUTO`;
2. concepts moved to `EVIDENCE_ONLY` / `PROFILE_EXCLUDED`;
3. ordering representation change, if any;
4. generic launcher command;
5. `.jcm` files tested;
6. test results;
7. remaining limitations.

Stop when both goals are complete.

## Evidence — 2026-09-29

- Projection profile: `NativeProjectionProfileTest` PASS. The default `AUTO`
  projection has **42 classes / 917 objects** for the Hello snapshot; `FULL`
  has **48 classes / 1365 objects**. `AUTO` excludes `A17PlanOrderEntry`,
  `A19BodyOrderEntry`, and the unavailable live-property class from the normal
  USE schema. `ExactBindingEvidence`, `BackingJavaOperation`, and `ArtifactInfo`
  are retained as `EVIDENCE_ONLY` trace records; provenance, runtime-history,
  and snapshot-helper concepts are trace/evidence-only as well.
- Ordering: `Plan.ordinal` and `PlanBodyElement.ordinal` are materialized on
  the existing domain objects. The existing ordered associations remain, and
  A17/A19/A20 native OCL is evaluated against ordinals in `AUTO`; `FULL`
  retains the explicit order-entry projection for audit/debug.
- GUI/native gates: focused projection/OCL/session/GUI tests PASS, including
  Model Browser, Class Diagram, Object Diagram, OCL dialog, event bus, and
  identity with the activated single `MSystem`.
- Generic launcher: `use-plugin/tools/jacamo-bridge.ps1` is the production
  path. Live PASS evidence exists for both
  `use-plugin/src/test/resources/canonical-cases/hello-world/helloworld.jcm`
  (`projectId=helloworld`, `CODE_GROUNDED_NATIVE`, `42/917`, resync PASS) and
  `use-plugin/src/test/resources/jcm/minimal/shared.jcm`
  (`projectId=shared`, `CODE_GROUNDED_NATIVE`, `42/5`, resync PASS), using the
  same launcher path. Missing path and wrong extension fail with explicit
  `JCM_PATH_REQUIRED` / `JCM_EXTENSION_REQUIRED` diagnostics.
- Verification: focused gate **31/31 PASS**; full reactor `verify`
  **498 tests, 0 failures, 0 errors, 0 skips**; package/release assembly,
  checksum, GUI staging, and `ReleasePackageIT` PASS.

## Remaining limitations

- `LiveObservableProperty` / C08 remains `UNAVAILABLE_BY_API`: the audited
  runtime does not expose a live `ObsProperty` API. The implementation keeps
  this explicit and does not synthesize a replacement object or value.
- Auction and House standalone live evidence is not claimed here:
  `NO_LIVE_EVIDENCE`. The generic launcher was live-validated with Hello and
  the independent `shared.jcm`; an Auction attempt stopped before Bridge-ready
  because of the fixture/runtime launcher environment, and no House run was
  promoted to evidence.
- The legacy `live-hello-bridge.ps1` remains only as historical/test harness
  material; production README/workflow commands now use the generic launcher.
