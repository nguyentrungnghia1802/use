# Phase 26 final target and runtime mapping audit

D25-01 selects unchanged structural V1. Classification: runtime contract versioning,
freeze enforcement and package/evidence change; no upstream semantic promotion.

## Structural reconciliation P26.1–P26.4 / P26.7

Phase 25 machine inventory enumerates all 37 classes, 67 attributes, 63 references
and 14 inheritance edges. Final Ecore equals V1 byte-for-byte; schema/mapping/freeze
and seven projections retain their hashes. No added/removed/changed target exists,
so parser kinds, resolver identities, trace schema, OCL contexts and goldens need
no edits. MappingTransformationTest validates canonical coverage/hash and negative
mutations and compiles generated USE; GoldenPipelineTest and
InstanceMaterializationTest protect text/direct plan/state consistency and goldens.
Auction and Counter tests regenerate .use/.cmd, traces and OCL evidence in target.
Unresolved source facts stay unresolved. No rename or migration is inferred.

## Rule-by-rule reconciliation P26.5

[evidence/phase26/runtime-migration.json](evidence/phase26/runtime-migration.json)
records every old/final rule. All selectors, actions, authorities, identity,
payload, exact trace, target kind, anchor, mutation and checkpoint are unchanged.
READY_V1_TEMPORARY becomes SUPPORTED, strictly for its existing rule conditions.
TRACE_ONLY and UNSUPPORTED stay non-mutating. No rule is enabled by an imaginary
V2. VP002/VP003 and classMappings/referenceMappings remain final valid anchors.
Organisation runtime instances and mental state observations retain their previous
boundary; structural Role.players cannot encode a group-instance role adoption,
and no Agent-Mission state slot or OGoal runtime state slot is invented.

Research inputs read: baseline, architecture, capability matrix, identity,
candidate targets, machine inventory, source manifest and pinned implementation
reconciliation. Upstream research versions are not substituted for project pins.
Phase 20 and 23 evidence controls standalone/OE/NPL claims.

## Canonical contract P26.6

Resource: runtime/jacamo-use-runtime-mapping-v1.json, mapping version 1.0.0.
Schema version **2.0.0** explicitly breaks the provisional draft's status/support
vocabulary; this avoids silently accepting an incompatible schema under the old
version. Target is STRUCTURAL_MAPPING_V1; status FROZEN. The freeze manifest binds
exact mapping, schema, Ecore and structural mapping bytes. Git disables newline
conversion for these hashed runtime resources. Validation uses a private input
snapshot, strict JSON keys, schema and semantic checks, then hash enforcement.
Schema-valid modifications to FROZEN mapping bytes fail RUNTIME_MAPPING_HASH_MISMATCH.
Draft schema 1.0.0 fails RUNTIME_MAPPING_VERSION_UNSUPPORTED with reconciliation
instructions. Explicit CUSTOM_VALIDATED documents use the same schema/semantic
and target checks but carry no canonical freeze claim. They do not replace the
default resource automatically. Missing rules remain explicit runtime failures.

RuntimeMutationEngine dispatches RuntimeSemanticAction, not a second event mapping;
compareSnapshot and verification checkpoint eligibility use the same mapping.
V1RuntimeBindingAdapter remains required (not dead V1 migration code).

JAR and ZIP include the final mapping, schema and freeze manifest; ReleasePackageIT
checks bytes/inventory/checksum. The isolated installed child JVM loads both the
structural mapping and frozen runtime mapping via the installed plugin loader.
No test dependency supplies the runtime loader implementation.

## Correctness and evidence P26.8

Mirror gates remain scoped to exact projected state and observed operations:
initial full sync, scalar deltas/removal, operation lifecycle, traced object/link
boundaries, forced drift comparison/repair, disconnect/resync and unbound rejection.
Jason/Moise observations are trace-only, not mirrored runtime-instance equivalence.
Auction/Counter assert zero unexplained drift at quiescent checkpoints and no
unexpected failed/rejected/dropped scenario events. Normative lifecycle remains
unsupported. No standalone JaCaMo E2E claim follows from this freeze.

RED: new frozen-byte mutation test failed because the prior loader accepted modified
FROZEN content. GREEN and reactor counts recorded after execution. Logs under
use-plugin/target/phase-evidence/phase26-*.log.

Focused mapping/migration/structure/OCL/two-case gate: **28/28 PASS**.
Clean plugin module verify: **156/156 PASS** (153 unit/component + 3 release IT),
including 30-entry ZIP and installed runtime mapping load. Initial full-reactor
run found the old 27-entry contract assertion; updated it to explicitly require
the three new runtime resources. Hashes were reconciled once for LF byte
normalization before the final build, not to accept semantic mutations.

Final full reactor: **299/299 PASS**, zero failures/errors/skips, 2026-09-19
(core 13, GUI 130, plugin 153+3). Canonical Core diff remains empty.
