# Authored model repair and current iStar instances - 2026-11-05

## Confirmed authoring issue and explicit user decision

The initial CSL edit used HTML comment syntax around ParticipantPopulation.
That was not the time constraint. The edited bytes also differed from cslSha256
in auction-mapping.xml, so import correctly stopped before runtime verification
with AUTHORED_MODEL_CHECKSUM_MISMATCH:authored-csl.

The user explicitly selected: temporarily disable WithinDeadline and keep
ParticipantPopulation. The CSL population rule is restored with valid CSL syntax.
WithinDeadline is retained as an ordinary inactive comment in auction.istar and
its explicit XML oclClause binding is disabled. The current example has seven
active embedded native clauses instead of eight, plus its active CSL population
invariants. SchemeOrder, BidEvidence, operation contracts and Goal/Task predicates
remain unchanged. The Core Draw.io and auction-false deadline policy are unchanged.
This is a documented temporary example policy, not a theory/model-graph rewrite.

The selected model digests are refreshed to the actual bytes. Checksum validation
was not removed. A mismatch now explains the authoring action required; the new
tools/refresh-authored-hashes.py reads the explicitly selected .mrv and updates
only its authored model/OCL digests, with the old XML backed up under target.
It does not change DSL, disable declarations, modify source pins, repair verdicts,
or mutate an active run. --check performs no writes. Native import still validates
syntax and binding semantics. Deliberate edits require a new Prepare + Import.
Frozen input replay still rejects inconsistent/tampered hashes.

The authored auction.mrv is included as a system specification. Packaging copies
recognized authored .mrv manifests and continues to preserve native saved diagram
layouts with that extension.

## Current iStar State UI

The main iStar Model tab retains the existing authored model view. iStar State
now shows one persistent instance card per admitted execution, using that same
canonical model and the already qualified per-scope Goal/Task summary. The current
Auction data produces separate a1/a2 cards; production derives the instances and
labels from admitted contexts, not case names, fixed counts or hard-coded IDs.

All is the default instance selection. Each card updates current evidence without
rebuilding an expanded ISCN model. Canonical model browsers are not repeated in
the cards. Native iStar diagram components/layouts remain in use. Instance labels
remain stable when source context iteration order changes. Views/layouts are reused
while an instance remains admitted, and detached on withdrawal/reimport/disposal.

The State view consumes the newest accepted unprojected evidence. Selecting an
old State in Verification only navigates verification/CSL history; it does not
rewind the current iStar instance view. Verification history, native engines,
checkpoint policy/barrier/control and exact-token review semantics are unchanged.
The internal native Goal/Task occurrence machinery remains authoritative; this
change removes historical ISCN expansion from the visible State tab only.

## Executed evidence

- 27 focused tests passed with zero failures/errors/skips; package
  BUILD SUCCESS in focused-build.log. This includes the final stable-order and
  TempDir tests, not an earlier intermediate build.
- CurrentAuctionBundleTest imports the actual user-selected external project
  D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples/auction/auction.jcm against the
  edited model selection. Native WithinDeadline is absent, ParticipantPopulation
  is present, and the frozen revised input round-trip verifies. This is native
  import/MODEL_READY evidence, not a full live domain-conformance claim.
- A deliberate edited CSL initially rejects on checksum; the explicit refresh
  admits the new bytes, frozen replay validates, and another unpinned edit rejects.
- Both current/false bundles still prepare natively. The false-case source fault
  and its eight-clause deadline policy are retained. The current intentional graph
  fingerprint still matches the authoritative Core graph.
- Actual frozen two-execution evidence drives the new State component. Tests cover
  All/one selection, stable ordering, reused views, independent history navigation,
  clearing/disposal, model fingerprint immutability and a neutral non-Auction model.
- current-instances.png was rendered and inspected offscreen. Native diagram bounds
  and scrollbars are retained. The standalone probe needed a display-capable JVM
  for USE DropTarget; no application window or Computer Use was opened. The rejected
  headless attempt is preserved separately and not counted as acceptance.
- Installed runtime/native-plugin CodeSource and JAR hashes passed. The batch
  launcher -nogui/quit exited 0, and the installed authoring utility --check confirms
  the installed .mrv references matching model bytes.

No new live MAS, Computer Use/manual desktop acceptance or full clean verify was
performed. Existing retained native results are evidence, not rewritten history.
A real failed source Task can still be Incomplete after disabling WithinDeadline.

Launcher: `D:\_CODE_BANK\Project_\08_Thesis\use\use-assembly\target\new\bin\start_use.bat`.
Installed plugin SHA-256: `1dd346371f807ced05e67698a057cee8c67dac64ff1614231cb3f585461e4f76`.
The existing incremental distribution process and legacy clean-release limitation
remain as documented in the preceding reports. Work/logs/replays/layouts are kept.
