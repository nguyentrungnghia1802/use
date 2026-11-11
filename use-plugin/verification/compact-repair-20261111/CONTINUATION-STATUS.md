# Compact mapping: current continuation status

Date: 2026-11-11. **Partial runtime support; the full migration is not complete.**
This supersedes the remaining-work descriptions in `REPAIR-STATUS.md` where
explicitly stated below. Earlier logs remain historical evidence.

## Implemented and exercised

- Source selectors now distinguish an admitted symbol from an explicit
  `OBSERVED_PROPERTY/1.0` runtime selection. A selection pins the actual class;
  it does not claim that a not-yet-observed property exists. Program-template
  aliases retain all actual source-program usages rather than choosing one agent.
- Generated Jason requests carry the actual source path/digest, plan context,
  typed captures and complete, non-overlapping AST partitions. Unmapped regions
  receive trace-only identities, not invented iStar Tasks. The actual Jason
  provider and pinned decoder were exercised with two agents sharing one program;
  source files stayed byte-identical and player identities stayed distinct.
- Field requests now use the actual per-field provider interface: class, field,
  Java type, class hash and expected observable-property types. The existing
  pinned field decoder supplies values to the existing native writer. It checks
  exact run/generation/revision, source watermark, physical owner and receipt
  hash. Missing receipts invalidate the field; an earlier value is not reused.
- Artifact identity attributes and workspace context views use explicit
  correspondences. Repeated property names on unrelated artifacts no longer
  reject a cut. Multiple source slots for a selected scalar produce undefined
  data with `COMPACT_PROPERTY_AMBIGUOUS`; no first-slot choice is made.
- The compiler derives a selective capture scope and fact-kind selection.
  Jason snapshot fields are limited to player/declaration identity rather than
  collecting complete reasoning-cycle/belief dumps without an admitted need.
  Producer metadata records the kinds actually captured. A workspace scope
  requires the matching read-set/hash, fact kinds and a stable official inventory;
  a transported `COMPLETE` flag alone does not establish closure. Each new cut
  invalidates previous inventory authority before applying current observations.
- Compact workspace views with condition-based Goals reach the original
  `RuntimeEngineSessionOwner`/`GoalMarking` engine on the same authoritative
  MSystem. Physical workspaces have independent scope/history identities.
  Authored true/false conditions retain their native effects. An unconditioned
  leaf without source evidence remains unknown. A Goal's direct authored
  condition is retained even when the model also refines that Goal.
- Explicit `<verify target="Actor.Element" scope="source-selector"/>` selections
  feed the existing native requirement inventory. Authored CSL/OCL constraints
  remain mandatory. An undeclared scope, missing intentional selection,
  unsupported episode or unsupported native receiver fails before readiness.
- `NativeTaskBindings` separates qualified Task inputs from the old XML syntax.
  The existing Task journal still owns exact PRE/POST pairing, rollback,
  duplicates, overlaps, lifecycle fencing and evaluation. Its XML implementation
  uses this typed interface; this is **not yet a compact Task-demand adapter**.
- Portable compact manifests now use schema `1.4.0` and rebuild directly from
  frozen source/model/compact bytes. They validate native model/program hashes
  and implementation identity. Replay succeeds with the original selected files
  poisoned; it does not reopen those files or reconstruct a v2 mapping document.

The supported native compact runtime subset is deliberately narrow:
workspace-context viewpoints, `PROJECT_EXECUTION_VIEW` activation,
`STATIC_LIFETIME` episodes, original condition-based Goals and explicit artifact
value/identity mappings. Compact Tasks, MOISE/agent scopes, dependencies with
population policies and same-player realizations still fail admission. They must
not be described as supported just because their source requests can compile.

## Focused evidence

These are separate, overlapping receipts, **not additive test counts**:

- `continuation-focused-final.log`: 82 plugin tests passed, covering the compact
  components, native workspace/field paths, strict ambiguous-property handling,
  existing XML writer/occurrence/Task paths and existing replay paths.
- `native-task-interface.log`: 31 focused plugin tests passed after introducing
  the typed Task boundary, including the existing native Task journal regressions.
- `generated-jason-provider.log`: the generated Jason request passed its actual
  provider/decoder test with two players and trace prefix/suffix regions.
- `continuation-provider-inventory-final.log`: 22 focused tests passed, including
  the actual selected CArtAgO capture metadata -> compact native scope path.
- `readiness-final.log`: 20 tests passed for readiness-policy, selected-requirement, scoped-input,
  provider and writer regressions for the latest admission guards.
- `continuation-binding-final.log`: 30 tests passed on the latest binding revision,
  including the generated Jason/field providers, workspace native session/replay,
  compiler read-set and existing native Task PRE/POST binding/journal paths.
- `continuation-results.json` indexes the logs, suite counts and hashes.

An earlier generated-provider test had an invalid one-child AND fixture. Its
fixture was corrected; native validation and production assertions were kept.
An earlier provider run exposed the unrelated-property ambiguity bug above;
that failure was retained and fixed in production, with a separate regression.

These receipts prove source/component/native-session behavior. They are not a
fresh full Auction workflow, identical-cut migration acceptance, desktop GUI
acceptance or proof that the installed package loaded this revision.

## Verification storage maintenance

The initial 63 top-level evidence directories were audited rather than deleted
by date/name. Their reports, source freezes and replays remain useful evidence.
148 closed untracked raw journals were archived losslessly and 46 empty nested
directories removed. The initial tree was 25,793,425,239 bytes; after maintenance
it was approximately 4.04 GB, reclaiming approximately 21.75 GB. All 148 archives
passed independent decoded SHA-256/size validation. Existing tracked evidence
and active/recent journals were retained.

See `../README.md`, `../../verification-maintenance-report.json`,
`../../verification-directory-inventory.json`, `../compressed-evidence.json`,
`verification-maintenance.log` and `verification-archive-validation.log`.
The maintenance tool's default preview now modifies no files or audit reports.

## Work still required

1. Implement compact physical-player, role/enactment, group and scheme scopes
   from qualified actual source cuts. Initial requests before scheme creation
   must retain their own invocation identity and correlate later creation
   evidence; do not join by names, IDs or timestamps.
2. Implement compact Task demands and the concrete typed Task binding adapter.
   Reuse `NativeTaskBindings` and the existing exact PRE/POST journal; preserve
   late-self rules, actual operation receipts, unassigned/future demand and local
   failures. Do not bypass the current unsupported-runtime admission guards.
3. Connect explicit ANY/ALL/same-player policies to the existing occurrence
   expander and compile typed source-observer views. Unknown native observer
   contexts still fail; no observer class/field recipe is inferred from a case.
4. Migrate the actual Auction, Hello World, House Building and shipped reference
   bundles. Existing compact example drafts are still drafts; active descriptors
   have not been silently switched from v2. Preserve authored graphs/conditions.
5. Prove full checkpoint/history/UI behavior and bounded external unchanged
   Auction live acceptance. No new barrier guarantee is asserted here; the
   whole-platform barrier implementation was not changed.
6. Build/install coherent artifacts into `use-assembly/target/new` only after
   full admission/workflow acceptance, then verify actual loaded runtime JARs.

The installed plugin has **not** been updated in this continuation. No new
launcher/package delivery, live Auction acceptance, Computer Use, commit, push
or branch cleanup is claimed. The sibling `jacamo` repository remains unchanged;
the producer metadata change is in USE's `jacamo-bridge-jacamo` module.

All P00-P09 checklist gates remain open until their full acceptance conditions
are proven. Continue with the first remaining implementation item above.
