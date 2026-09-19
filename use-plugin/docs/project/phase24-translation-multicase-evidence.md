# Phase 24 — Translation closure and two-case evidence

Classification: bug fixes restoring unsupported boundaries, supported pure guard
translation, and multi-case test/evidence. Frozen Ecore, Structural Mapping V1,
verification projections and Runtime Mapping Draft are unchanged. No V2 freeze or
new deontic semantics are claimed.

## Translation inventory and acceptance contract

Every ConstraintSpec retains source span/hash and semantic dependencies. Generated
EXACT records retain origin/hash/dependencies; non-emitted records retain status,
hash/dependencies and reasons/assumptions. ConstraintClosureTest supplies boundary
negative controls; ConstraintOclTest covers actual USE compilation/evaluation and
operation-owner provenance. CounterTeamIntegrationTest and LiveJaCaMoAuctionIntegrationTest
exercise the same production extraction/transformation/runtime services.

| Construct | Binding and OCL form | Status / limits | Evidence |
|---|---|---|---|
| Native guard int/boolean literals and parameters | Exact operation, identical source parameter sequence/names/types, primitive boolean return; referenced parameters int/boolean; operation PRE | EXACT for a single pure return AST | Auction amount >= 0 and Counter valid; multi-return/assignment/wrapper/renamed-param negatives |
| Native guard comparisons and Boolean equality | Typed same-type operands; =, <>, <, >, <=, >= | EXACT; relational comparison requires Integer; no reference/String equality | Positive guard fixtures; incompatible x > true and missing identifier negatives |
| Pure guard !, &, &&, pipe, double-pipe, parentheses | Boolean operands; not/and/or | EXACT for pure total operands; no side effects | Nested logical Counter variant; not 3, 1 & true, nested unknown-call negatives |
| Native arithmetic, division, unary numeric expressions, arbitrary effects/calls | No approved Java overflow/truncation/effect contract | UNSUPPORTED, no partial formula | +1, /2, missing call, assignment and multi-return variants retain located evidence |
| Jason plan context | No proven applicability checkpoint/global invariant equivalence | UNSUPPORTED even if property bindings supplied | Auction and Counter source contexts retained; no fabricated Plan invariant |
| Typed explicit expression IR | Caller supplies exact typed environment; Boolean/comparison and Integer +,-,* with mathematical IR meaning, property navigation | Parser EXACT within this IR; not proof of source Java/Jason semantics; division rejected | Existing arithmetic/property rendering, unknown calls/type errors; variable/property ambiguity negative |
| Explicit authored postcondition, @pre | Source-backed contract, exact class/operation, explicit assumptions/dependencies | EXACT authored contract, not extracted Java effects | Auction positive/negative postcondition USE evaluation; same-name operation owner test |
| Authored collection/call IR rendering | Caller-owned typed contract and USE compile/type-check required | Renderer capability only, no automatic native rule | Collection render test; unknown native call remains unsupported |
| Core/case OCL profiles | Exact loaded profile, origin/path/content hash, USE compile/type-check | Authored policy, not native equivalence | Auction and Counter compile; path-escape negatives and line-ending determinism |
| SOUND_SUBSET / LOSSY | No approved one-direction preservation or lossy contract in current inventory | No candidates enabled; NOT_EMITTED | All non-EXACT status values preserved, no generated formula |
| Moise Norm/time/lifecycle | Structural metadata or explicit OE observation only | No normative OCL translation | Existing Norm exclusion regression and Phase 23 OE evidence |

No one-direction soundness claim can be made for the unsupported inventory, so
P24.3 closes with zero enabled rules, not an invented approximation. No partial
formula is produced for an unknown nested expression. Missing/ambiguous semantic
operation bindings remain resolver diagnostics; translation does not choose a name
candidate. Shared guard source used by two operations yields two dependency-bound
constraint IDs. OCL insertion uses the exact class declaration, not a prefix match.

## Case selection and generic reuse

Inspected checked-in Auction and jcm/minimal candidates. Auction is already case 1;
the original minimal Counter class/team has too little behavior for acceptance.
Prepared `src/test/resources/counter-team` as a separate local, test-owned extension,
leaving the original fixture intact. Its small explicit semantics allow deterministic
positive/negative execution without external domain decisions. No user-supplied case
is required for Phase 24. A thesis requirement for a particular external case remains
a possible Phase 25 research decision, not an engineering blocker here.

The fixture contains worker AgentSpeak, executable demo.Counter, observable count,
setValue/guardedSet operations, team organisation, worker role, counting scheme,
count mission and count_items goal. Its case policy deliberately rejects count=-1,
while the artifact permits -1; inputs below -1 fail in the artifact. Profiles and
all case identities live under test resources/test code, never generic production.

CounterTeamIntegrationTest uses project discovery -> extraction -> exact resolution
-> V1 plan/profile -> deterministic .use/.cmd -> actual USE materialization and
initial structure/invariants -> trace -> real pinned Jason/CArtAgO/Moise connectors.
It checks source-declared cross-dimensional links, authoritative initial sync,
zero drift after setValue(3), POST PASS, exact event/semantic attribution of count=-1
violation, OP_FAIL/SKIPPED for -2, OE goal observation, explicit normative gaps,
zero failed/rejected/dropped queue outcomes, disconnect mutation and authoritative
reconnect/resync to a clean LIVE state. Static Java discovery does not execute code;
only the integration harness compiles the trusted fixture into a temporary folder.

Jason source is loaded in a real Agent; operations are issued by the harness. The
OE is built through OSBuilder from the simple declared facts. Static XML is not the
runtime launcher OS. This is a second in-process supported-subset case, not proof
of autonomous Agent -> Artifact -> Organisation behavior or standalone JaCaMo E2E.
Phase 20 technical limitations remain unchanged.

Production diff since Phase 16 closure (7a848105) was reviewed for dispatch/identity
branches. Production Java search for Auction/AuctionArtifact/placeBid/closeAuction,
counter-team/CounterTeam/counter1/team_group/count_items found no case-specific
matches. Runtime routing uses declared mapping action kinds, dimensions and exact
trace/configuration keys. No new special case or hidden case binding was required.

## Reproduction and evidence

Pinned Windows/JDK 21.0.5, USE 7.5.0, Jason 3.3.0, CArtAgO 3.1, Moise 1.1;
dependency versions and canonical resource fingerprints remain covered by reactor
checks. Run from repository root, with JAVA_HOME/bin first on PATH:

```text
mvn --batch-mode -pl use-plugin test -Dtest=ConstraintClosureTest,ConstraintOclTest,CounterTeamIntegrationTest
mvn --batch-mode verify
```

Generated evidence under `use-plugin/target/phase24-counter-evidence`:
model.use, initial-state.cmd, translation-manifest.txt, runtime-events.json,
reports.json, trace.json, summary.json. Summary records Java version, input/output
SHA-256 hashes, elapsed nanoseconds and accepted event count. Event timestamps and
runtime incarnation IDs are intentionally runtime-specific; deterministic model/OCL
generation is separately asserted. Evidence is regenerated by the test, not committed
as mutable target output. Build logs live in `use-plugin/target/phase-evidence`.

Initial RED reproduced nested unknown expressions incorrectly classified EXACT.
Later boundary review reproduced the overly broad primitive-signature rejection of
Auction's unused String parameter; the final check restricts only referenced guard
variables and retains Auction support. Tests cover the corrected contract.

Full reactor `mvn --batch-mode verify`: **298/298 PASS**, zero failures/errors/skips
on 2026-09-19 (core 13, GUI 130, plugin 152, release integration 3). Elapsed reactor
2 min 23 s. This includes both Auction and Counter runtime cases, parser negatives,
freeze/hash gates, lifecycle/security regressions and package tests. The final
ConstraintClosure/ConstraintOcl/Counter focused subset contributes 12 passing tests
within that reactor; an earlier dedicated focused run also passed 12/12 before the
last assignment/Boolean AST boundary additions.

Counter scenario measured 1,214,284,700 ns (~1.21 s) and 33 accepted events in this
reactor. Timing includes fixture import, generation, component setup, operations,
verification and resync; it is a local smoke measurement, not a throughput claim.

Documentation impact: synchronized extraction, constraint/OCL, testing, limitations,
roadmap and active task. Architecture reviewed unchanged (parser/runtime separation);
Auction policy reviewed unchanged. Canonical contracts and compatibility versions
reviewed unchanged; this is not a new release or V2 finalization.

## Git closure

Implementation and synchronized task/docs committed as `8a3d8040`, fast-forward
merged into main. Post-merge smoke on that commit:

```text
mvn --batch-mode -pl use-plugin test -Dtest=ConstraintClosureTest,ConstraintOclTest,CounterTeamIntegrationTest,LiveJaCaMoAuctionIntegrationTest
```

**13/13 PASS**, zero failures/errors/skips (2026-09-19). No source changes followed
this gate. Metrics commit `0e9474a0` remains an ancestor and its workflow bytes are
unchanged. Phase 23 remains the separate `10225d84` commit. The accompanying docs-only
closure commit records this post-merge result; push is verified against remote main
at completion. Phase 25 and later final acceptance gates remain outside this request.
