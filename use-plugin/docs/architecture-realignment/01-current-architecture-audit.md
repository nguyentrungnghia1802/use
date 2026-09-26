# Current architecture audit

## Exact static call path

The active entry is `DefaultJaCaMoFacade.importProject(Path)` (`DefaultJaCaMoFacade.java:75-82`), which calls the private `build(Path, Path)` method (`:256-307`). The exact path is:

```text
DefaultJaCaMoFacade.importProject/rebuild
  -> DefaultJaCaMoFacade.build
  -> StaticProjectImporter.importProject
     -> JcmProjectLoader.discover
     -> JcmSemanticParser.parse
     -> JasonSourceParser.parse
     -> CartagoSourceExtractor.parse
     -> MoiseXmlParser.parse
     -> BindingStore.read(binding.json, sourceHashes), if present
     -> SemanticResolver.resolve
        -> ExactSemanticResolver.resolve
     -> OrderEvidenceLoader.apply
     -> ElementDraft.freeze
     -> JaCaMoSemanticModel.<init>
  -> MappingLoader.loadCanonical / ActiveBaseline
  -> TransformationPlanner.plan
  -> VerificationSemanticLayer.apply
  -> InstancePlanner.plan
  -> ConstraintExtractor.extract
  -> OclProfileLoader.loadCore/loadCase/loadUser
  -> OclGenerator.generate
  -> TextBackend.generate
  -> DirectUseBackend.materialize
  -> TraceBuilder.build
  -> ConstraintRegistry.load
  -> DefaultVerificationService.runFullVerification
```

The importer sequence is executable source, not an inferred diagram: `StaticProjectImporter.java:18-53`. The downstream calls are contiguous in `DefaultJaCaMoFacade.java:256-293`.

## Exact runtime call path

```text
DefaultJaCaMoFacade.configureRuntime(connector, endpoint, capacity)
  -> RuntimeMappingLoader.loadDefault
  -> RuntimeMutationEngine(MSystem, TraceIndex)
  -> RuntimeVerificationEngine
  -> RuntimeMirrorService

DefaultJaCaMoFacade.connectRuntime
  -> RuntimeMirrorService.connect
     -> RuntimeConnector.connect
     -> RuntimeConnector.subscribe (buffer deltas first)
     -> RuntimeConnector.fullSnapshot
     -> RuntimeMutationEngine.applySnapshot
     -> drain events with sequence > snapshot.sequence
     -> RuntimeVerificationEngine observer callbacks

rebuild/import while connected
  -> RuntimeMirrorService.replaceWorkspace
  -> new RuntimeMutationEngine + observer
  -> new authoritative snapshot and stream cut
```

Evidence: `DefaultJaCaMoFacade.java:167-186,242-253`; `RuntimeMirrorService.java:43-79,186-286`; `RuntimeMutationEngine.java:83-555`.

Current connectors are not process transports. All three only validate a `jacamo:` URI. Jason holds `TransitionSystem` objects, CArtAgO holds a live `CartagoEnvironment` adapter, and Moise holds an `OE` or supplied board objects. Evidence: `JasonRuntimeConnector.java:28-60`, `CartagoRuntimeConnector.java:29-79`, `MoiseRuntimeConnector.java:20-76`.

## Component audit

| Component | Current responsibility / I/O | Dependencies and authority | Genericity/tests | Target reuse and risk |
|---|---|---|---|---|
| `JcmProjectLoader` | Discovers JCM/includes/source files and produces `ProjectDiscoveryResult` | Custom `JcmLexer`; filesystem is treated as source authority | Generic; discovery tests | Replace semantic authority. Its safe path/hash utilities may move to provenance support. It explicitly does not load project classes. |
| `JcmSemanticParser` | Reconstructs agents, workspaces, artifacts, org declarations, instances and config into drafts | Custom tokens and manually encoded JCM grammar | Generic but grammar-drift risk | `DEPRECATE_AFTER_MIGRATION`; official `JaCaMoProjectParser`/`JaCaMoProject` replace it. |
| `JasonSourceParser` | Parses ASL and adapts AST to V2 drafts | It already calls official Jason `Agent.parseAS`, `PlanLibrary`, `Plan`, etc.; masks directives and supplies inert internal-action lookup | Reusable AST traversal; parser tests | Refactor into Bridge-side `JasonModelExtractor`. Stop masking official include/directive semantics. Pin to Jason 3.3.2 used by JaCaMo. |
| `CartagoSourceExtractor` | Parses Java source using JDK compiler tree, recognizes annotations/calls, infers operations/properties/signals | Java source is acting as semantic authority | Generic but fails for bytecode/dynamic behavior; extractor tests | Replace with official class reflection plus initialized `ArtifactInfo`; property definitions remain runtime-dependent. |
| `MoiseXmlParser` | Direct DOM parsing of OS XML into V2 drafts | Duplicate of Moise's official `OS.loadOSFromURI` object model | Generic but schema/API drift risk | Replace with `OS`/SS/FS/NS object traversal. Keep secure XML handling only as non-authoritative provenance validation if useful. |
| `SemanticResolver` | Resolves draft references, completes opposites/order diagnostics | Workaround over separately reconstructed dimensions | Exact, no fuzzy matching | Refactor into a thin Bridge snapshot validator/adapter. It must not invent official cross-dimensional links. |
| `ExactSemanticResolver` | Exact ID, explicit ref, owner-qualified and unique typed scope; binding may choose only an existing exact candidate | Valid fail-closed policy | Unit-tested | Keep as guarded augmentation for relations that official APIs do not expose; never use it to repair loader disagreement. |
| `binding.json` | Explicitly selects one existing typed candidate and is hash-checked | User-authored augmentation | Project-local; stale/invalid is error | Keep only for explicit cross-dimensional facts not represented by official authority; deprecate parser-ambiguity use. |
| `SemanticId` | Six-part encoded `jacamo:project:dimension:kind:ownerPath:localId` | Plugin-generated identity | Generic; identity tests | Keep with adapter and add project namespace plus runtime incarnation outside frozen Ecore. Legacy aliases remain during shadow phase. |
| `JaCaMoSemanticModel` | Immutable IR with project, registry, elements, diagnostics, source/symbol indexes | V2 semantic kind registry | Generic; model tests | Keep. Build it from `ModelSnapshot`, not parsers. Add source-evidence/completeness at adapter boundary rather than coupling it to JaCaMo classes. |
| Mapping/planning | V2 mapping load/validation, structural and verification projections | Frozen Ecore/Mapping V2 | Strong audit/mutation tests | Keep. Source adapter changes; rules do not. |
| `InstancePlanner` | Converts semantic elements/links/order into deterministic object/link/value plan | IR + frozen mapping | Generic; materialization tests | Keep with dynamic-instance adapter. Runtime-only entities are added by runtime target planning, not guessed statically. |
| `TextBackend` / `DirectUseBackend` | Produce `.use`/`.cmd`; compile/materialize `MModel`/`MSystem` | USE 7.5 APIs | Text/direct parity tests | Keep. Maintain MModel/MSystemState separation. |
| Trace | `TraceBuilder`/`TraceIndex` map source, semantic and USE targets | IR/mapping/instance plan | Generic; trace tests | Keep and extend with Bridge canonical IDs, snapshot IDs, session/generation and evidence keys. |
| OCL | Core/case/user profiles, generated structure, registry | Explicit OCL policy, not Moise semantics | Generic; OCL/verification tests | Keep. Capability-gate projection-dependent constraints; never auto-convert norms. |
| Runtime mapping/mutation | Maps normalized events to exact traced USE targets and applies atomic mutations | Frozen Runtime Mapping V2, `MSystemState`, trace | Strong runtime tests | Keep, but feed through a new Bridge adapter. Unknown/unbound entities remain quarantined. |
| `RuntimeMirrorService` | Subscribe-before-snapshot buffering, ordered queue, resync/drift, workspace replacement | Current `RuntimeConnector` contract | Generic; reconnect/backpressure tests | Refactor contract for session/generation/vector watermark; core algorithm remains reusable. |
| Jason connector | In-process snapshot of beliefs and listeners for belief-update events/goals/actions/messages | Official Jason 3.3 API plus custom `AgArch` | Useful proven hooks | Move capture to Bridge; USE connector becomes transport-neutral. Direct in-process connector remains compatibility/test adapter. |
| CArtAgO connector | Pre-bound workspace enumeration, logger deltas, op correlations, property values | Official CArtAgO 3.1 APIs | Dynamic unbound observations are quarantined | Move capture to Bridge; remove requirement that USE pre-own live CArtAgO objects. Preserve logger/event normalization logic. |
| Moise connector | Snapshots classic `OE` or exact supplied launcher boards; board mode polls net state | Official Moise 1.1 objects | Board control is explicitly limited | Move capture/listeners to Bridge. Replace polling where NPL/CArtAgO events exist; retain polling as reconciliation only. |
| `CompositeRuntimeConnector` | Re-sequences three connector streams in one JVM | Child connectors | Generic tests | Replace as USE authority with Bridge stream; keep as test/legacy adapter until cut-over. |
| UI | Project import, profiles, verification, runtime controls/status | Facade DTOs | UI tests | Keep UI and change facade data source; show capabilities/completeness/session rather than claiming unavailable semantics. |

## Where the current USE Plugin is architecturally misaligned

### FOUNDATION VALID

The frozen V2 vocabulary, mapping, IR boundary, deterministic planning, USE materialization, exact trace, fail-closed runtime mutation, OCL separation, and verification engine are valid foundations. Runtime snapshot-before-delta buffering and reconnect/resync are also sound ideas.

### ARCHITECTURAL BOUNDARY MISALIGNED

- `JcmProjectLoader`/`JcmSemanticParser` duplicate the official JCM parser and project lifecycle.
- `MoiseXmlParser` duplicates official OS loading and object semantics.
- `CartagoSourceExtractor` treats Java syntax as the model even though authoritative artifact descriptors exist only after class loading/instance initialization.
- `SemanticResolver` bears cross-subsystem reconstruction work that should be performed only when an official identity relation or explicit binding exists.
- Runtime connectors require JaCaMo objects inside the USE process; the endpoint is a marker, not a remote boundary.
- Pre-bound CArtAgO/Moise identities constrain dynamic real cases and make the test harness look more complete than the independent-process architecture is.

### IMPLEMENTATION REUSABLE

`JasonSourceParser` demonstrates that the AST-to-IR traversal is valuable because it already consumes official Jason objects. The change is to run it against the same parser/directive environment as JaCaMo. Current CArtAgO logger normalization, Jason listeners/`AgArch`, board snapshot logic, ordered queue, drift comparison, trace, mappings and verification can be relocated or adapted rather than discarded.

### FRONTEND NEEDS REPLACEMENT

The authoritative input becomes a Bridge `ModelSnapshot`; `StaticProjectImporter` becomes a compatibility facade around `BridgeSemanticAdapter`. Legacy discovery/parsers remain shadow-only until parity gates pass, then become historical tools and are removed only after a separately approved proven-replacement gate.

## Important limits exposed by current tests

`LiveJaCaMoAuctionIntegrationTest` is a real-API constructed subset, not the unmodified upstream Auction: it programmatically creates a Jason agent, CArtAgO artifact and classic Moise `OE`, and writes `SUPPORTED_SUBSET_COMPLETE` plus `PROGRAMMATIC_REAL_MOISE_API_SUBSET_NOT_LOADED_FROM_STATIC_XML`. It therefore proves runtime building blocks, not original Auction plan/deadline semantic equivalence.

`MultiCaseV2PipelineTest` covers the local Auction fixture and Counter Team, not upstream Hello World or House-Building. The current branch has an untracked canonical Hello World fixture under `src/test/resources/canonical-cases`; it is input work, not accepted evidence yet.

## Finding record

| Finding | Evidence | Current impact | Target implication | Confidence | Open question |
|---|---|---|---|---|---|
| Official frontend can replace most custom extraction | JaCaMo parser/project, Jason AST, Moise OS, CArtAgO reflection/runtime descriptors | Duplicate semantics/drift | ModelSnapshot adapter | High | Exact build/module packaging spike |
| No official global atomic snapshot | Separate Jason/CArtAgO/Moise state and listener APIs | Cross-subsystem races possible | Buffered cut + watermarks + resync | High | Measure retry rate under House load |
| Cross-dimensional intent is not universally exposed | Jason terms do not intrinsically identify a Moise goal or CArtAgO operation | Resolver temptation | Exact runtime IDs or explicit binding only | High | Which case bindings remain necessary? |
| V2 remains sufficient | All official structural concepts fit 21 V2 classes/features; gaps are evidence/runtime metadata | No schema edit warranted | Preserve freeze | High | Re-evaluate only after bridge snapshots |
