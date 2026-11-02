# Generic verified execution — implementation plan

Requested 2026-11-02: support authored case studies through common mechanisms,
without case names, expected counts/winners, manufactured identities or verdicts.

## Contract

One existing authoritative MSystem/native Goal/Task/OCL engine. JaCaMo selects
source checkpoints from admitted mapping identities. Close admission, drain/yield
in-flight work, freeze supported logical clocks and verify the exact source cut.
Publishers/control/inspection and presentation remain live. Missing evidence,
failure, unsupported producers, timeout and disconnect never imply approval.
Review releases only the exact reviewed token and preserves all original results.

Support is determined by execution capabilities and explicit mapping topology,
not a catalog of cases. Unknown arbitrary external/native/remote producers must
remain explicitly unsupported until covered; they cannot be silently admitted to
make an unconditional "all Java programs" claim.

## Steps and evidence gates

- [x] G01 — Source admission: resolve actual project include search paths with
  the official loader semantics, pin/hash resolved sources, reject ambiguity,
  traversal/escape and uncovered dependencies. Audit safe cycle-local internal
  actions against the pinned implementation rather than case source labels.
- [x] G02 — Generic presentation/provider capability: cover source-owned GUI
  artifacts and clock usage with a stated domain/presentation boundary. Keep raw
  domain threads/timers/clock/external mutation negative probes. Prove operation
  admission, already-running await operations and logical timers before live PASS.
- [x] G03 — Native context topology: support explicit project/workspace views and
  Entity-resource views. Derive scoped object ownership from admitted declarations
  and exact source identities. Preserve unknown/partial/ambiguous ownership; no
  invented Entity, source-name inference or borrowing another execution's state.
- [x] G04 — UI: expose admitted complete native contexts even when their explicit
  topology has no Entity resource; preserve scope/history and exact active cut.
  State milestones group consecutive identical scoped CSL objects/links; each
  native verification and source token remains auditable beneath that state.
- [x] G05 — Validation: reject absent/invalid abstraction metadata with a useful
  diagnostic and the same semantic rule; do not change the failing test oracle.
- [x] G06 — Focused regressions: include/clock/provider negatives, workspace and
  resource scopes, invariant ownership, state grouping, failure/review lifecycle,
  and synthetic renamed/reordered declarations to detect case-specific coupling.
  Preserve the full hash-chained journal under lossless member compression;
  exercise concurrent committed-prefix reads, corruption, physical quota holds,
  evicted exact-result recovery and explicit decoded replay budgets.
- [x] G07 — Live main Verified Checkpoints path for external Auction and authored
  Hello World/House Building: actual display-before-release receipts, native
  results unchanged, real error/unknown hold, repeated exact-token review,
  supported timer/in-flight boundaries and responsive isolated UI history.
- [x] G08 — Preserve previous installed run/history, package target/new, verify
  loaded patched JaCaMo/component hashes and installed plugin bytes, freeze all
  evidence. No full clean verify, commit or push.

Baseline diagnostics: `../additional-verified-20261102/REPORT.md` and
`../milestone-state-diagnosis-20261101/REPORT.md`. Historical tests are not current
acceptance. Tick each gate only after its actual condition is proven.

G01 evidence: G01-relative-source-path-tests.log, G01-intend-bytecode.log.
G02 evidence: G02-gui-probes.log (six component probes), G02-G06-source-order-fix.log.
G03-G06 evidence: G06-final-focused.log (86), G06-storage-final-tests.log (28), G03-G04-final-tests.log (43), and G02-G06-source-order-fix.log (5). Counts overlap; they are separate focused runs, not summed unique tests.

Discovered during G07: selected terminal markers must close admission before
population capture. The source permit prevents READY until the marker evidence
has been published. Preserve the stable-watermark/topology assertions and bounded
16 attempts; allow a 10-second local capture budget for reads exceeding one
second. An actual missing cut still produces GAP and holds. The original
30-second verification timeout is unchanged. Final evidence: FINAL-RECEIPT.json and REPORT.md. All declared gates now proved.

G07 evidence: G07-closeout-auction-2/receipt.json; G07-closeout-hello-world/receipt.json and replay-checked.json; G07-house-stable-artifacts/receipt.json and replay-checked.json. Auction has actual desktop review clicks twice; Hello/House have real live frames with programmatic UI inspection.
G08 evidence: G08-final-final-package-receipt.json, G08-real-launcher-final.log, launcher-hash-probe/receipt.json, G08-class-byte-parity.json, source-artifact-hashes.json. Previous installs/logs/replays preserved. No clean verify, commit or push.
