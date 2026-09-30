# Known Limitations

## Final system acceptance boundary (2026-09-28)

- Original Hello, Auction and House have only bounded evidence for the supported
  Bridge scope. Current native acceptance is defined by the focused/full tests in
  `docs/agent/task.md`; no archived case-study report is a production authority.
- The current original Auction does not contain the self-referencing organizational
  plan or natural-language deadline mentioned by older records. Its plan is
  `start,bid,decide`; its authored constraints are `10 seconds` and `1 hour`.
  Absence in the current input is not evidence of equivalence for an older fixture.
- The original House run observed 22 agents, all eight auction artifacts,
  `simulator.House`, dynamic group/scheme/NPL boards, role players, mission
  commitments, fulfilled commitment obligations and 12 satisfied leaf/intermediate
  goals. It did not reach `house_built=SATISFIED` or print `*** Finished ***` within
  the 50-second acceptance window. Full original termination therefore remains
  unavailable.
- Runtime facts without an exact approved frozen Runtime Mapping target remain
  `EVIDENCE_ONLY`; the 28/28 authored OCL results are not a promotion of those facts
  into USE truth.
- Native Swing click-through was unavailable to the audit automation surface. UI
  component/action/package tests passed; use the generic
  `tools/jacamo-bridge.ps1 -InteractiveGui` launcher for a manual demo.

## Historical post-migration live Bridge boundary (2026-09-27)

- `JaCaMoBridgePlatform` exports the official `ModelSnapshot`, injects
  `BridgeAgArch`, owns `SnapshotCoordinator` with Jason/CArtAgO/Moise/NPL sources,
  and hosts authenticated loopback TCP. A final separate-JVM Hello run produced a
  non-empty cut (`756` facts, `1,095` USE objects); source observations remain
  explicitly evidence-only when the frozen Runtime Mapping has no faithful target.
- The runtime cut is buffered and validated per source; it is not a globally atomic
  Jason+CArtAgO+Moise snapshot. Gaps, topology drift and stale identities require
  resync, and exactly-once delivery is not claimed.
- The distribution SHA-256 is now derived from the actual JaCaMo/Jason/CArtAgO/
  Moise/NPL/Bridge code sources by `RuntimeDistributionFingerprint`, exported with
  component digests, and compared exactly during handshake.
- The GUI does not launch JaCaMo and has no endpoint/secret/fingerprint editor.
  The tested Windows helper `tools/jacamo-bridge.ps1` prepares a temporary
  derived JCM and starts the producer plus isolated consumer; it never edits
  `JaCaMo/` or canonical sources.
- `BridgeVerificationGate` is integrated into the production
  `DefaultJaCaMoFacade` runtime verification path. Evidence-only, unavailable,
  stale or incomplete dependencies are recorded as `INCONCLUSIVE` or
  `NOT_EVALUATED`, never as definitive OCL truth.
- The explicit compatibility branch still uses frozen Mapping 2.2.0 and Runtime
  Mapping schema 3.0.0. The native branch does not load them; current gate results
  and native boundaries are recorded in `docs/agent/task.md`.
- Ordered membership changes require complete authoritative directional orders
  during resynchronization; bare link/endpoint mutations are rejected before
  corrupting rank projection. Rank-only updates and reconnect are tested.
- The V2 contracts are frozen as a release candidate but no Git release tag is
  published. Historical V1 evidence does not establish unrestricted V2 runtime or
  original Auction equivalence.

- Compatibility evidence is limited to Windows 11 amd64, Oracle JDK 21.0.5, Maven
  3.9.9, USE 7.5.0, JaCaMo 1.3.1, Jason 3.3.2, CArtAgO 3.1 and Moise 1.1.
- A bounded JaCaMo launcher/control subset is supported, but it is not equivalent
  to the original static Auction plan/deadline. Direct board observation is
  implemented; no replacement OE is used. Original Auction equivalence remains
  `NO_LIVE_EVIDENCE`/unsupported where the exact authored semantics are absent.
- The Moise live scenario constructs a real programmatic OS/OE subset. The checked-in
  XML is static import provenance, not the runtime OS used by that scenario.
- Communication links, formation cardinality, sequence plans, normative time
  constraints and norm activation/fulfilment/violation are not executed.
- Arbitrary Java effects and unsupported Jason/CArtAgO syntax remain explicit
  diagnostics; they are not guessed or silently translated to OCL.
- A CArtAgO request whose guard stays false may suspend without a terminal callback;
  operation correlation begins at `opStarted`.
- Runtime verification observes and reports; it does not block JaCaMo actions.
- LIVE workspace replacement is supported for rebuild, user OCL profile load, and
  reimport by opening a candidate Bridge session, validating a complete cut, and
  atomically replacing the USE workspace before closing the previous client. A
  candidate failure preserves the prior workspace but reports the Bridge error; it
  never invokes the historical parser path.
- Project-root `binding.json` is production input only for exact typed ambiguity.
  Invalid, duplicate, wrong-kind, malformed, or source-hash-stale entries block import;
  bindings do not create candidates or provide fuzzy resolution.
- Interactive installed-distribution GUI testing and other OS/JDK/component versions
  are outside the automated release gate. The 2026-09-27 audit did start the
  source-tree GUI successfully and observed a responsive window titled `USE`; the
  available automation surface could not inspect native Swing controls.
- The JaCaMo-side Bridge JAR requires the host application's JaCaMo component
  libraries. USE requires only its plugin JAR; live JaCaMo objects never cross the
  process or classpath boundary.

## Runtime research development (Phase 17)

Runtime evidence, completion, quarantine, correlation and connector diagnostic
windows have deterministic bounds and expose retired-entry counters. Active
operations are never evicted; correlation-capacity overflow fails closed until a
stream boundary or authoritative resync. Jason mind and Moise instance observations
are not proof of corresponding USE state mutation. CArtAgO unknown/retired
observations remain quarantined outside the bound mirror subset. See
`docs/project/runtime-event-identity.md`. No new Ecore or OCL support is claimed
for the native path.

## Phase 21-23 boundaries

- Migration tooling reports exact structural diffs and conservative affected-artifact
  review hints; it does not accept renames or constitute a reconciled V2 mapping.
- RuntimeHistoryVerifier checks finite recorded order outside OCL; missing terminal
  evidence cannot prove eventual completion or a deadline violation. Non-LIVE OCL
  checkpoints are SKIPPED.
- CrossDimensionalVerifier checks declared source links only. Runtime percept-to-belief
  delivery, Jason-action/CArtAgO invocation joining and instance-specific organisation
  state projection remain unsupported.
- Moise derived obligation/permission snapshots describe public OE API output only,
  separate from structural Norm and OCL truth. No NPL lifecycle is inferred.

## Phase 24 translation and second-case boundary

- Native guard translation is limited to pure Boolean conditions over primitive
  int/boolean parameters. Java arithmetic/overflow, wrapper/reference semantics,
  calls, assignment and arbitrary bodies are not translated. Jason applicability
  is not promoted to an invariant. No approved SOUND_SUBSET or LOSSY rule emits.
- Counter Team is a test-owned local fixture extending the minimal counter concept.
  Its real in-process component scenario is driven by the test harness and uses an
  explicit OSBuilder OE. It adds multi-case reuse evidence, not standalone launcher
  or autonomous Agent-to-Artifact-to-Organisation evidence. Performance numbers are
  smoke measurements on the pinned local toolchain, not throughput guarantees.

## Final engineering boundary

Observer infrastructure failures leave ERROR rather than current LIVE truth.
Cleanup failures remain explicit and retryable; disconnect and authoritative
resync are required before claiming current state again. No arbitrary observer
that blocks indefinitely is supported. See `docs/agent/task.md` for the current
acceptance boundary and `docs/agent/CODE-GROUNDED-NATIVE-README.md` for the
production path.
