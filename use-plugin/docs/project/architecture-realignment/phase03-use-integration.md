# Phase 3 — USE integration and separate-JVM boundary

Status: PASS.

The USE production package depends on the neutral contract, not on JaCaMo,
Jason, CArtAgO, Moise or NPL. `BridgeClient` validates schema, payload digest,
distribution and capabilities before model materialization; subscribes before
runtime snapshot acceptance; bounds the bootstrap buffer; acknowledges accepted
events; and exposes LIVE/STALE/RESYNC lifecycle. Session, generation, model
revision, source sequence, event-id conflict, gap, overflow and unknown
incarnation checks fail closed.

`NativeSemanticAdapter` performs exact Bridge-ID mapping into the existing
semantic IR and retains relation-scoped cardinality outside the frozen V2
projection. `BridgeRuntimeProjector` admits only complete, faithfully projected
facts with an exact `runtime-model-binding`, then reuses the frozen Runtime
Mapping V2 and `RuntimeMutationEngine`. Other observations remain evidence-only.
`BridgeVerificationGate` prevents stale, partial, evidence-only or unavailable
dependencies and incomplete operation correlations from reaching OCL. Reports
carry the accepted session/generation/model revision/snapshot/event/correlation,
constraint hash and negotiated capability context.

`SeparateJvmBridgeTest` proves both the minimal recorded boundary and the
production TCP boundary. Process B's classpath is scanned and excludes the
Bridge adapter, JaCaMo, Jason, CArtAgO, Moise, NPL, Jaca, IntMas and SAI jars.
Model snapshot, runtime snapshot and event cross the boundary as canonical JSON;
the model compiles, state initializes, the event changes the mirror, and replay
is deterministic. The production variant passes Hello, Auction and House.
