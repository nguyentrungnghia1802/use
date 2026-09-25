# Phase 43 hardening, security, determinism and performance

Status: DONE. Focused, full-module and full-reactor gates pass with zero failures,
errors or skipped tests. V2 remains `WORKING_BASELINE`; Phase 43 does not freeze or
release any contract.

## Requirement-to-evidence traceability

| Capability | Requirement and implementation | Tests/evidence | Status |
| --- | --- | --- | --- |
| Active V2 selection | `ActiveBaseline` selects exact Metamodel V2 and Structural Mapping V2 fingerprints; historical loaders are explicit | `ActiveBaselineTest`, compatibility and package contract tests | COMPLETE |
| Semantic IR and static extraction | Exact owner-qualified identities from JCM, Jason, CArtAgO and Moise source; no code initialization | extraction, semantic-model and importer security suites | COMPLETE |
| Structural transformation | One Mapping V2 path produces text/direct USE parity and preserves independent target-only order ranks | mapping, materialization, order projection and golden tests | COMPLETE |
| Trace, binding and runtime identity | Exact RuntimeKey to SemanticId V2 to USE target resolution; unknown or mismatched entities are quarantined | trace/binding, runtime alias and identity hardening tests | COMPLETE |
| OCL and constraints | Core V2, translated safe subset, case/user profiles and PRE/POST compile and retain provenance | constraint closure/OCL and verification tests | SUPPORTED_SUBSET_COMPLETE |
| Runtime Mapping and mirror | Runtime Mapping V2 selects exact capability/action rules; ordered mutation, authoritative snapshots, drift and resync preserve JaCaMo authority | runtime mapping/foundation/order and connector integration tests | COMPLETE |
| Runtime verification | Invariants and operation PRE/POST run only on proven mirror state; cross-dimensional and normative semantics remain bounded to declared profiles | runtime verification/history/cross-dimensional tests | SUPPORTED_SUBSET_COMPLETE |
| Generic production pipeline | Auction and CounterTeam use the same import, mapping, transformation, OCL, trace, mirror and verification components | `MultiCaseV2PipelineTest`, Auction and CounterTeam runtime tests | COMPLETE |
| UI and package | UI delegates to the facade and package publishes one canonical V2 target namespace with historical V1 isolated | UI, manifest, namespace and release package tests | COMPLETE |
| Security and lifecycle | Restricted paths, no XXE/DTD, no project-code initialization or shell execution, bounded runtime state and connector cleanup | `V2HardeningAuditTest`, loader/importer and runtime lifecycle suites | COMPLETE |
| Determinism and performance evidence | Stable semantic outputs across fresh JVMs and observed timings without disabled semantics or SLA claims | `phase43-determinism.json`, `phase43-performance.json` | COMPLETE |
| Original Auction plan/deadline and general NPL semantics | No semantic conversion is claimed beyond implemented source/runtime evidence | Phase 20/28 boundary records and current case-study documentation | EXPLICITLY_UNSUPPORTED |
| Publishing a tag or declaring V2 frozen | Reserved for Phase 44 after final freeze and reproducibility gates | Phase 44 checklist | OUT_OF_SCOPE |

No capability is left in an unclassified state.

## Stale V1 and dead-path audit

`V2HardeningAuditTest` rejects unresolved `TODO`/`FIXME` markers, case-study dispatch,
arbitrary process creation, unnecessary production console output and obsolete
`ORDER_V1` identifiers in production Java. It also rejects historical target
vocabulary in canonical V2 resources and known stale V1-baseline claims in active
documentation.

The Phase 43 resource audit made two non-semantic corrections:

- Runtime Mapping V2 explanations now say `V2`; rule IDs, match predicates, actions
  and target semantics did not change. Its new SHA-256 is
  `3af8141b68c27ae7ef2f9e414558c0932ac2c93a63e17580a9d1ede8a9f31a46`.
- The target-only trace provenance label is now `ORDER_V2`. The exact trace diff has
  370 records before and after, with only 202 `projectionRuleId` fields changing.
  The new golden trace SHA-256 is
  `4db0faea1c2379f03459ca82307e4b31f3b4edbfc6d3be89ef9da46075c95edd`.

Retained V1 names are classified rather than silently removed: version-1 resources
and explicit historical loaders are reproducibility evidence, while `binding-v1`,
`trace-v1` and `runtime-event-v1` identify active wire-format versions and are not
structural target baselines. There is no duplicate V1/V2 production dispatch or
commented fallback.

## Determinism

Two fresh Maven/JVM invocations used identical project bytes, V2 Ecore, Structural
Mapping V2, plugin version, Runtime Mapping and OCL/verification profiles. Both
produced input fingerprint
`8c60d1fe017b9036849aca2bef357db6e21a45946f05e9c9a97905005e98c888` and
output fingerprint
`cb6d0818493f40d0372a333b608a1ab6a85dbd44f28f71b6585c43765a334e24`.
The comparison covers 24 SemanticIds, generated `.use`, generated `.cmd`, 370 trace
records, generated OCL, OCL provenance, diagnostic order and 151 mapping decisions.
Runtime report UUIDs and timestamps remain deliberately run-specific.

## Security

Focused security coverage verifies path traversal and symlink escape rejection,
archive/classpath confinement, XML external entity and DTD rejection, static Java
analysis without project initialization, restricted case-OCL/report paths, no shell
execution surface in production, and the absence of unnecessary plugin console
logging. Exact unbound or mismatched runtime entities remain quarantined and cannot
mutate USE.

## Runtime lifecycle and retention

`RuntimeRetention` centralizes deterministic in-memory bounds: 4,096 operation
correlations, 4,096 operation history entries, 1,024 quarantined events, 16,384
trace entries, 256 trace boundaries, 8,192 accepted event IDs, 4,096 verification
reports and 1,024 connector diagnostics.

Active operation correlations are never evicted. If rejected terminal tombstones
fill the correlation capacity, mutation and PRE-state capture fail closed with
`OPERATION_CORRELATION_CAPACITY_REQUIRES_RESYNC`; a stream boundary or authoritative
snapshot clears that condition. Completion, evidence and diagnostic windows retire
their oldest entries deterministically and expose retained/retired counters. Global
sequence monotonicity continues to reject replay outside the bounded event-ID window.
Existing listener cleanup, queue/scheduler shutdown, subscription generations,
workspace replacement isolation and stale-alias retirement remain covered by the
runtime lifecycle suites.

## Performance evidence

The recorded run is observational evidence with policy `OBSERVED_NO_SLA`; it uses the
full production pipeline and disables no semantic check.

| Stage | Observed nanoseconds |
| --- | ---: |
| Import | 641,723,000 |
| Ecore and Mapping validation | 22,594,700 |
| Transformation | 75,482,100 |
| OCL generation and compile | 241,518,900 |
| Full verification | 7,206,500 |
| Runtime event to result | 3,567,500 |
| Authoritative resync | 6,521,100 |

The queue capacity was 8, high-watermark 1, final depth 0, with zero rejected,
failed or dropped events. Observed used heap was 22,045,728 bytes. The evidence run
covered 24 semantic elements, 22 target classes, 48 target objects, 31 constraints
and 370 trace records.

## Consistency chain and gates

The gate validates the chain Metamodel V2 -> Structural Mapping V2 -> Semantic IR ->
transformation -> trace/binding -> OCL -> Runtime Mapping V2 -> `MSystemState` mirror
-> verification -> tests/evidence. Canonical semantic hashes at this phase are:

| Contract | SHA-256 |
| --- | --- |
| Metamodel V2 | `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c` |
| Structural Mapping V2 | `15d330c70400d16958559b83daf27c3755dc0cb28a7dffb8a08d94879d1ddf67` |
| Structural Mapping schema | `e9f555ad2e19cbf8382ff4c17d44179d3b47a13d03ee9a58c2b491f99b72c919` |
| Runtime Mapping V2 | `3af8141b68c27ae7ef2f9e414558c0932ac2c93a63e17580a9d1ede8a9f31a46` |
| Runtime Mapping schema | `be843872c909a8d44ca7c038f5896a8d829f07b068b4861f8169faf552f6a3bb` |
| Core OCL V2 | `a725c1fe0524f2b477327b58c54d317df693a0fbe37d3773bbe44372beeefab5` |
| Core OCL manifest | `14b098508167dc5198fa60589d098d5251d808e3ed2e828376324c751a009917` |
| Verification profile V2 | `91c85d42080b56754e8c2f5e9d37127e80c1483fdb4d71f2d61df7eab58fcab2` |

- Focused hardening/security/determinism/performance gate: 111/111 PASS.
- Full plugin module verify: 229/229 PASS.
- Full reactor verify: 372/372 PASS.
- Every gate has zero failures, zero errors and zero skipped tests.

Machine-readable gate records are `phase43-focused-gate.json`,
`phase43-module-verify.json` and `phase43-reactor-verify.json`. Detailed measurements
and semantic diff evidence are in `phase43-performance.json`,
`phase43-determinism.json` and `phase43-trace-projection-diff.json`.

