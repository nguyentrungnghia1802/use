# Traceability, Binding and Resolver

> V2 exact-resolution update: owner qualification matches a contiguous owner-path
> suffix; explicit bindings must belong to the actual exact typed/scope candidate
> set. Candidate ordering is deterministic. See [Phase 33 evidence](v2-migration/phase33-semantic-ir.md).
> V2 parser/trace integration is implemented: membership links, source defaults,
> independent order entries/navigation and per-source projection traces. Existing
> trace/binding serialization versions are reused; clean full regression PASS 350/350.
> Phase 36 hardening: persisted trace is archival evidence and cannot authorize
> runtime mutation. Live trace is rebuilt from the active V2 workspace. Alias carry
> forward requires equal mapping contract, exact IDs/kinds/rules and source hashes.
> Concrete projected classes and order-support declarations now have complete trace.
> [Audit and current gate status](v2-migration/phase36-trace-identity.md).

## 1. Mục tiêu

Traceability là nền tảng cho:
- runtime update;
- violation navigation;
- ambiguity handling;
- reproducibility;
- debugging mapping.

---

## 2. Trace model

```text
TraceRecord
- traceId
- sourceSemanticId
- targetUseId
- sourceKind
- targetKind
- mappingRuleId
- projectionRuleId?
- sourceSpan?
- runtimeKey?
- status
```

Status:
- RESOLVED
- PROJECTED
- AMBIGUOUS
- UNRESOLVED
- STALE

---

## 3. Resolver types

### Type resolver
JaCaMo type → semantic/metamodel type.

### Symbol resolver
name/reference → declaration.

### Operation resolver
ExternalAction → AbsOperation/concrete USE operation.

### State resolver
ObsProperty/belief/runtime property → USE state slot.

### Organisation resolver
Agent/role/group/scheme/mission runtime entity → USE object/link.

---

## 4. Binding file

Case-specific binding **không bắt buộc**.

Chỉ dùng khi deterministic resolver không đủ.

Production location:
```text
<project-root>/binding.json
```

The file uses Binding V1 schema (`schemaVersion` plus `entries`). Each entry records
the canonical source/target IDs, binding kind, reason, provenance, source hash, and
ACTIVE/STALE status. A conceptual entry is:
```json
{
  "source": "canonical-semantic-id",
  "target": "canonical-target-id",
  "kind": "OPERATION_BINDING",
  "reason": "ambiguous target after exact semantic resolution"
}
```

Không dùng binding để sửa parser yếu một cách đại trà.

---

## 5. Resolution algorithm

1. preserve an already resolved source reference;
2. enumerate exact typed candidates for the original spelling;
3. when a binding file is present, validate source, target kind/candidate membership,
   uniqueness, and source hash;
4. allow `ExactSemanticResolver` to select a valid ACTIVE binding or another exact
   deterministic result;
5. otherwise accept a single exact typed candidate;
6. emit `RESOLUTION_AMBIGUOUS` or `RESOLUTION_UNRESOLVED` and fail formal operation
   resolution rather than guessing.

Không:
- Levenshtein/fuzzy as formal resolution;
- nearest filename;
- matching action name globally khi có nhiều owners.

Fuzzy suggestions chỉ được hiển thị như UI assistance, không auto-accept.

---

## 6. Ambiguity diagnostics

Diagnostic phải liệt kê:
- source;
- candidates;
- scopes;
- why each candidate matched;
- required binding form.

---

## 7. Runtime trace

Khi runtime starts:
- register runtime IDs;
- link runtime Agent/Artifact/Org instances tới static semantic IDs;
- cache lookup.

Runtime event nếu không có trace:
- không mutate sai object;
- store pending/unresolved event;
- diagnostic ERROR/WARNING theo criticality.

---

## 8. Staleness

Nếu project source thay đổi:
- content hash mismatch;
- trace record affected → STALE;
- rebuild semantic model;
- preserve stable traces only nếu identity proof vẫn đúng.

---

## 9. Audit

Trace tests phải bao phủ:
- one-to-one;
- one-to-many projection;
- ambiguous;
- missing;
- renamed;
- duplicate operation names across artifacts;
- multi-agent same source file;
- multiple organisation instances.

## 10. Phase 7 implementation contract

- Trace V1 records class, attribute, association, object, and projected operation targets. The in-memory index
  supports semantic ID, USE ID, and optional runtime key lookups; JSON persistence is validated by
  `trace-v1.schema.json`.
- Binding V1 stores canonical source/target IDs, kind, reason, provenance, source hash, and ACTIVE/STALE state.
  A source hash mismatch marks the entry STALE before resolution; JSON is validated by `binding-v1.schema.json`.
- Formal resolution order is exact canonical ID, explicit target ID, owner-qualified exact name, unique exact typed
  scope, and finally one ACTIVE binding. A binding is considered only after two or more exact typed candidates exist;
  it cannot create a relation when the source spelling has zero candidates.
- Invalid target kinds and multiple active bindings fail explicitly. Similar spelling, case folding, edit distance,
  nearest file, and global fuzzy selection are never formal resolution strategies.

## 11. v1.0.1 production integration

`StaticProjectImporter` reads project-root `binding.json` after extraction, computes
current semantic source hashes, and passes the validated Binding V1 data to
`SemanticResolver`. `DefaultJaCaMoFacade` uses this importer, so the binding path is
part of production import rather than a resolver-only test utility. Malformed,
duplicate, absent-target, wrong-kind, or stale entries block import with
`BINDING_INVALID` or `BINDING_STALE`. A valid selected identity is retained in the
semantic reference and subsequent trace.

## Phase 17 identity hardening

[Runtime identity contract](runtime-event-identity.md) records canonical aliases, subordinate identity, immutable event evidence and generation boundaries. Runtime keys reject duplicate registration and stale/unresolved trace records; reverse alias lookup is deterministic.

## Phase 19 projected attribute authorization

TraceBuilder emits explicit ATTRIBUTE records for verification-projected attributes.
Runtime state mutation checks this declaration in addition to the exact object alias;
an object alias does not authorize an arbitrary attribute. Existing trace records and
frozen structural mapping bytes remain unchanged. Runtime create may only restore an
exact originally materialized object/class, not invent a new semantic instance.
