# Traceability, Binding and Resolver

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

Recommended:
```text
verification/binding.json
```

Entry:
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

1. exact canonical ID;
2. explicit source reference;
3. owner-qualified symbol;
4. unique candidate in typed scope;
5. explicit binding;
6. fail.

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
