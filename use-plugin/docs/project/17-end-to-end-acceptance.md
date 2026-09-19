# End-to-End Acceptance Criteria

## 1. Metamodel/Mapping

- [ ] Ecore baseline parses.
- [ ] Mapping schema validates.
- [ ] 100% structural coverage.
- [ ] No known unqualified ambiguity.
- [ ] Projection semantics documented.
- [ ] Freeze manifests valid.

## 2. Import

- [ ] `.jcm` selected as entry point.
- [ ] includes/source paths resolve.
- [ ] Jason dimension parsed.
- [ ] CArtAgO dimension parsed.
- [ ] Moise dimension parsed.
- [ ] source spans preserved.
- [ ] diagnostics actionable.

## 3. Semantic model

- [ ] stable IDs.
- [ ] cross-file references resolved.
- [ ] unresolved refs explicit.
- [ ] no USE dependency in parser/IR.

## 4. USE transformation

- [ ] `.use` deterministic.
- [ ] compile/type-check success.
- [ ] `.cmd`/initial state valid.
- [ ] associations/multiplicities valid.
- [ ] concrete Artifact operations project correctly.
- [ ] trace complete for generated elements.

## 5. OCL

- [ ] translated OCL provenance recorded.
- [ ] unsupported semantics not guessed.
- [ ] core OCL loads.
- [ ] case OCL loads.
- [ ] positive/negative offline checks work.
- [ ] pre/post operation checks work.

## 6. Binding/resolution

- [ ] exact resolution preferred.
- [ ] ambiguous case blocks automatic mapping.
- [ ] explicit binding resolves ambiguity.
- [ ] stale binding detected.

## 7. Runtime

- [ ] connect.
- [ ] initial full sync.
- [ ] Agent events.
- [ ] Artifact events.
- [ ] Organisation events where supported.
- [ ] trace lookup.
- [ ] state updates.
- [ ] operation enter/exit.
- [ ] disconnect/reconnect.
- [ ] full resync.
- [ ] no silent dropped events.

## 8. Verification reporting

- [ ] violation result includes OCL constraint.
- [ ] USE context object identified.
- [ ] JaCaMo semantic/source trace available.
- [ ] runtime event link available.
- [ ] report export works.

## 9. Auction

- [ ] valid scenario passes.
- [ ] closed-auction bid fails expected rule.
- [ ] invalid amount fails.
- [ ] trace ambiguity test works.
- [ ] reconnect scenario converges state.

## 10. Engineering

- [ ] clean build.
- [ ] full tests.
- [ ] no destructive warnings ignored.
- [ ] docs updated.
- [ ] release package.
- [ ] tagged commit.
