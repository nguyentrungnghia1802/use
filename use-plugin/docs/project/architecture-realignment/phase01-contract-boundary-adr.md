# ADR: neutral contract physical boundary

## Decision

Create `jacamo-bridge-contract` as a JDK-21-only Maven module in the USE reactor.
Create the JaCaMo-side adapter as a separate `jacamo-bridge-jacamo` module that
depends on the contract and exact official distribution artifacts. The USE plugin
may depend on the contract but must not consume the JaCaMo adapter module.

The canonical wire representation is dependency-free canonical JSON with a closed
envelope, explicit DTO-to-tree mapping, SHA-256 payload digest, size/depth/string
limits, and no class metadata or executable object deserialization. Production
transport selection remains deferred to Phase 8 behind `BridgeTransport`.

## Evidence and consequences

- `jacamo-bridge-contract` has no production dependencies.
- Its source scan rejects USE, EMF, JaCaMo, Jason, CArtAgO, Moise, and NPL imports.
- Its isolated consumer test uses only contract classes in a separate JVM.
- The exact model retains relation-scoped cardinality and evidence-only facts even
  when frozen V2 cannot materialize them faithfully.
- Official mutable runtime objects are copied by the JaCaMo adapter; they never
  appear in contract records or cross the process boundary.

This keeps the frozen Ecore, Mapping V2.2, Runtime Mapping V2, OCL, goldens, and
freeze manifest outside the contract change set.
