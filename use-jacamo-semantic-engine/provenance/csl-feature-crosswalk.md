# CSL v4 executable crosswalk

Authority: pinned `CSL.g4` → original `CslBuildingVisitor` → AST records →
`CslModelFactory` → `CslSemanticValidator`. `docs/model/csl.ecore` is a
reference M2 crosswalk, not generated runtime code or a second model authority.

| Construct | Stored original model | Admission / runtime boundary |
|---|---|---|
| Entity | CslEntity, specializes, attributes | passive domain type, not a player |
| Role | CslRole, parentRoles, attributes | concrete same-instance inheritance; no play-chain |
| OrgCtx | CslGroup organizationalContext=true, members and nested declarations | declaration ownership; no implied population or runtime containment |
| Property | CslAttribute with exact datatype/optional/mutable/defaultValue | default is authored metadata, not observed state |
| Primitive / enum / named type | CslPrimitiveType / CslEnum / CslNamedDataType | opaque runtime codec needs explicit admission |
| Generalization | CslGeneralization plus classifier parent metadata | same-kind, single-parent, acyclic; inherited field redefinition rejected |
| Association | CslRelation.endpoints preserves n-ary end order, cardinality and optional role name | entity-centered; do not call legacy source()/target()/owners() for n-ary |
| Composition / aggregation | RelationKind and ordered binary ends | first end is whole; context nesting does not generate these links |
| Compatibility | CslCompatibility, symmetric original validation | no invented association; runtime role/player/context policy stays explicit |
| Domain invariant | CslInvariant plus AST source location | original compiler checks OCL syntax; native USE must check domain/type/Boolean |
| Operations / process order / agents | absent from v4 M1 grammar | neutral backend extension or external observer/mapping, not invented CSL syntax |

Original OCL bodies are token-joined with spaces by the visitor. Parity compares
the full original AST/M1, including that reconstruction. The initial test that
assumed raw source formatting is preserved in Phase 4 evidence; original source
was not patched. Legacy Classroom syntax is an expected original parser
rejection, never a v4 positive fixture. The actual upstream ProposalReview v4
fixture is copied unchanged as a test input with source path/commit/hash.
