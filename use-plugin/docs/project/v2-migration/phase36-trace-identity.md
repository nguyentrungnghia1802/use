# Phase 36 trace and runtime identity audit

Status: COMPONENT GATES PASS; merge/post-merge/push pending. V2 remains WORKING_BASELINE. Phase 29â€“35 acceptance is preserved.

## P36.1 schema impact

TraceRecord's existing fields can represent V2 without changing the V1 wire schema.
Wire version 1.0.0 is a serialization version, not an active Metamodel V1 selector.
SemanticId retains its six-part, escaped, owner-qualified identity. V2 kinds come
from the active registry; removed kinds are not migrated by name similarity.
USE target identifiers retain their kind prefix and deterministic allocated name.
Mapping rule IDs are scoped to the complete active MappingModel, not globally
interchangeable across versions. Projection rule IDs retain VP001â€“007 and ORDER_V1;
ORDER_V1 identifies the rank projection algorithm, not Metamodel V1.

Persisted trace records lack a complete contract/incarnation proof. TraceStore.read
therefore returns an archival index: records remain readable for reproducibility
and navigation, but runtime lookup, registration, transfer and mutation-engine
construction cannot activate it. A live workspace always rebuilds TraceBuilder
from the active semantic model, mapping, transformation and instance plan.
This applies equally to old V1 traces and saved V2 traces; JSON readability does
not prove current runtime identity. No automatic trace migration is provided.

In-memory replacement may carry aliases only across equal complete MappingModel
contracts and equal semantic ID, USE ID, target kind, source kind, source hash,
mapping rule and projection rule, with both records resolved/projected. A changed
source retires the alias conservatively; explicit rebinding and authoritative
resync are required. Connector incarnation/generation checks remain independent.

## P36.2 coverage defect and repair

The new all-generated-class assertion failed on AuctionArtifact before the fix.
TraceBuilder previously covered canonical classes but omitted concrete projected
class declarations. It now records every exact source instance contributing to a
concrete class, using the materialized class/semantic identity and source provenance.
Projected attributes now retain their source span/hash as well as semantic ID.
OrderProjectionTrace now covers generated rank class, rank attribute and both
support associations, in addition to existing order entries, links and navigation.
No mapping, Ecore, order semantics, source IDs or target model bytes change.

## P36.3â€“P36.6 audit scope

BindingStore recomputes status from current source hashes supplied by import;
absent/changed canonical source or absent canonical target is STALE. SemanticResolver validates exact current
typed candidate membership and rejects unused, wrong-kind and stale bindings.
Bindings remain optional. No migration rewrites historical IDs or source hashes.

Runtime aliases remain exact connector-specific keys. Multiple aliases may point
to the same semantic Agent. CArtAgO property/operation identity remains subordinate
to an explicitly bound artifact and its actual incarnation; OpId is invocation
correlation, not a fabricated static semantic instance. Moise instance IDs remain
distinct from specification IDs. Unknown entities stay quarantined/unbound with
diagnostics; existing connector and mutation negative controls are reused.

## Validation record

- Trace coverage RED: TraceBindingTest, 4 tests, 1 failure (AuctionArtifact).
- First trace repair: 22 focused tests PASS (trace, binding, lifecycle, order,
  materialization); this preceded subsequent alias hardening.
- Alias migration RED: RuntimeAliasTest, 2 tests, 1 failure (changed mapping rule
  incorrectly inherited the runtime alias).
- Complete focused gate: 52/52 PASS, zero skips, including golden, cross-dimensional
  negative control and real CounterTeam connectors. Final reactor PASS: 352 tests, zero failures/errors/skips; see phase36-regression.json.
- First broad reactor exposed two cross-dimensional failures after trace enrichment:
  source membership lookup counted order-support associations as ambiguity. The
  verifier now selects the exact active mapping rule and target, excluding projection
  support. A missing membership trace still returns ERROR even when support exists.

Golden review independently compiled TraceBuilder and OrderProjectionTrace from
c0fb52a5 against the same current V2 fixture/plans. Its normalized output reproduced
the exact old digest 234cb6dc9007785da806e3f32589bdbac6a15e0a4258672599162c721fdf9586.
The corrected output adds 105 declarations, removes none and enriches precisely
one projected property record's source kind/span/hash; all other records remain
identical. The generated model, commands, OCL and diagnostics retain their old
golden digests. See [exact trace delta](phase36-trace-diff.json). Only this reviewed
trace golden was regenerated; no canonical semantic input was changed.

Active documentation reviewed: trace/binding, runtime identity, Phase 35 acceptance.
Contract classification: BUG FIX RESTORING EXISTING CONTRACT and TEST/EVIDENCE CHANGE.
Historical trace bytes remain untouched. Golden changes, if required, must be
limited to reviewed trace additions/provenance; no automatic hash acceptance.

## Cross-layer gate

Canonical V2 Ecore/Mapping bytes are unchanged. Semantic IR, transformation, source
order projection, OCL contexts and runtime mapping target contracts are unchanged.
Trace enrichment and stricter binding/alias activation feed the same mutation and
verification pipelines. Full reactor includes both component case studies, actual
USE state parity, lifecycle, unknown entity controls and all three installed-package
integration checks. Source identity and target-only order support stay distinct.

Moise role/mission/goal fact keys remain observation/correlation identities where
no state projection is authorized; their payload is not promoted into an invented
SemanticId or mutable USE object. Secondary live aliases remain in-memory indexes;
Trace V1 JSON is an evidence record format, not a restartable runtime session.
