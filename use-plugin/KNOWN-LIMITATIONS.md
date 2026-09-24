# Known Limitations

- V2 consumers now use Mapping 2.2.0 and runtime target schema 3.0.0. Full
  clean-build passes 350/350; phase merge/post-merge/push remains OPEN. See [current runtime boundaries](docs/project/v2-migration/phase35-runtime-targets.md).
- Ordered membership changes require complete authoritative directional orders
  during resynchronization; bare link/endpoint mutations are rejected before
  corrupting rank projection. Rank-only updates and reconnect are tested.
- V2 working package is not frozen or published. Historical V1 evidence below
  does not establish unrestricted V2 runtime or original Auction equivalence.

- Compatibility evidence is limited to Windows 11 amd64, Oracle JDK 21.0.5, Maven
  3.9.9, USE 7.5.0, Jason 3.3.0, CArtAgO 3.1 and Moise 1.1.
- Phase 20 now passes a real JaCaMo 1.3.0 `.jcm` launcher control through
  AgentSpeak role/mission/goal execution, the checked-in artifact, board connectors,
  frozen Runtime Mapping and USE mirror/resync. This is a supported subset, not
  equivalence to the original static Auction plan/deadline. Direct board observation
  is implemented; no replacement OE is used. Original Auction remains
  EXPLICITLY_UNSUPPORTED due to invalid/incomplete fixture semantics (B).
  See [final audit](docs/project/phase20-final-completeness-audit.md).
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
  reimport. It drains the old event stream, rebinds mutation/verification consumers,
  transfers only exact matching runtime aliases, and applies an authoritative snapshot.
  In-flight operation correlations and historical runtime reports do not cross the
  workspace boundary. A snapshot failure leaves consumers aligned and disconnects in
  `ERROR`; it does not silently retain a stale `LIVE` state.
- Project-root `binding.json` is production input only for exact typed ambiguity.
  Invalid, duplicate, wrong-kind, malformed, or source-hash-stale entries block import;
  bindings do not create candidates or provide fuzzy resolution.
- Interactive installed-distribution GUI testing and other OS/JDK/component versions
  are outside the automated release gate.
- The plugin requires its documented host/runtime libraries on the USE classpath;
  the release does not redistribute the JaCaMo component dependencies.

## Runtime research development (Phase 17)

The runtime ledger currently retains all accepted/rejected outcomes in memory; long-duration retention is not bounded. Jason mind and Moise instance observations are not proof of corresponding USE state mutation. CArtAgO unknown/retired observations are explicitly quarantined outside the bound mirror subset. See docs/project/runtime-event-identity.md and the research reconciliation matrix. No new Ecore or OCL support is claimed.

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
that blocks indefinitely is supported. See docs/project/phase27-hardening-audit.md
and the final acceptance/boundary matrix in docs/project/phase28-project-closure.md.
