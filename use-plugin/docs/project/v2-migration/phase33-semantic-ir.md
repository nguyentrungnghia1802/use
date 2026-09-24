# Phase 33 — V2 IR component migration

Status: component and parser integration validation PASS; full phase closure OPEN.
Active inputs remain the unchanged Mapping 2.2.0 and V2 Ecore selected by ActiveBaseline.

## Audit and implementation

| Existing component | Disposition | Contract |
|---|---|---|
| SemanticId | Reuse unchanged | Six escaped components: project, dimension, source kind, ordered owner segments, local ID; no runtime handle or name-only identity |
| SourceProvenance/source index | Reuse with project validation | Exact path/span/parser/source hash/original spelling; stale or absent source rejected |
| MetamodelKind enum | Replace active inventory | Descriptor-derived exact source EClass names and dimensions; named handles only for parser-specific behavior |
| SemanticElement attributes | Adapt | Validate supplied attribute names/types; exact enum literal names; no fabricated absent values |
| Parser-only metadata | Separate | Immutable sourceFacts are preserved but cannot masquerade as Ecore attributes |
| MAS root | Separate in V2 constructor | ProjectDeclaration contains project metadata/provenance, outside the V2 EClass/object inventory |
| References | Reuse ordered list | Exact source feature, original spelling and nullable target ID; typed cross-dimension and inherited-feature validation |
| Symbol index | Reuse | Duplicate local names retain distinct owner-qualified IDs and all candidates |
| Runtime identity | Reuse separate boundary | No connector handle enters the semantic ID or kind registry |
| Debug output | Add | Version 2.0.0, Ecore fingerprint/mapping ID, deterministic elements, typed values, provenance, unresolved references and diagnostics |

The source IR may be partial. Supplied facts must be valid; missing required data
is not invented. Materialization remains responsible for completeness gates.
Reference order is preserved as supplied; membership cannot prove a missing order.

EnumLiteral stores the EEnum **name** (e.g. EXTERNAL), not a guessed uppercase
conversion of source text. Mapping defines the source literal `external` and USE
literal `EXTERNAL`. Parser lexical decoding remains a Phase 34 task. Both target
backends now accept the typed enum value.

## Exact resolution

Resolution priority remains exact canonical ID, explicit target, owner-qualified
symbol, unique typed scope, explicit binding for remaining ambiguity, unresolved.
Owner qualifiers/scopes match a contiguous suffix of owner segments. A name found
somewhere among ancestors is not sufficient. Ambiguous candidates are stable-ID
sorted. A binding cannot choose a different spelling or an object outside the
actual typed/scope candidate set. Duplicate canonical IDs are rejected.

Existing IDs remain stable for unchanged semantics. V2 kinds use their exact source
names. Transitional V1 handles and the MAS constructor have been removed from
production; every model construction validates against the V2 registry. Project
metadata remains a ProjectDeclaration, not a metamodel object. Parser declarations,
instances and source-only facts are separated; runtime identities remain external.

Follow-up focused regression: 28/28 PASS (`target/phase33-no-v1-ir.log`), covering
SemanticModelTest, V2SemanticModelTest, StaticProjectImporterTest,
V2ExtractionTest and TraceBindingTest. The earlier results below are historical
migration snapshots, not the current failure count.

## Evidence

Focused command (29 tests PASS):

```powershell
mvn -B -pl use-plugin '-Dtest=V2SemanticModelTest,SemanticModelTest,SemanticIdTest,ActiveBaselineTest,OrderProjectionTest,V2OppositeOrderingAuditTest,PreMigrationBaselineTest,TraceBindingTest#resolverUsesOnlyExactOrderedStrategiesAndBindingForRealAmbiguity+duplicateLocalSymbolsAndOrganisationInstancesRemainAmbiguousWithoutScope+bindingSchemaPersistsChoiceAndMarksChangedSourceStale' test
```

Tests cover each dimension, duplicate names/IDs, invalid attributes/enums/references,
missing/ambiguous targets, exact binding scope, partial IR, immutable source facts,
deterministic debug output with unresolved spelling, and a reconciled synthetic
subclass inheriting Group.roles through unchanged registry code. Ordering, native
EMF counterexample and historical static controls remain PASS. Full regression
result: 333 executed, 7 failures, 65 errors, 0 skipped. Of these, 59 errors
stop at VSP001 expecting the old Organisation class; remaining failures concern
historical paths, version/count/projection/profile and release assertions. See
[full evidence](phase33-regression.json); focused PASS does not close dependent phase gates.

## Remaining dependencies

- P34 parser migration is implemented in 4992e5d2; transitional handles are now removed.
- P35 transformation/defaults/projections are implemented in cf3cdfc8.
- Subsequent OCL/runtime binding migration: adapt consumers of parser-only metadata
  and obtain authoritative source order evidence before exposing ordered navigation.
- Rerun full suite and close staged P29–32 gates only after these dependencies PASS.

Classification: INTERNAL IMPLEMENTATION CHANGE; IR/RESOLUTION CONTRACT CHANGE;
TEST/EVIDENCE CHANGE. No Ecore/Mapping bytes, USE core or connector mechanics changed.

Final focused follow-up: 18/18 PASS on 2026-09-24, including a new typed-enum
actual SOIL/direct parity control. Full regression above predates this added test.
Failure classification: 60 V1 profile (59 errors + 1 assertion), 6 V1 paths,
3 V1 mapping/projection, 3 stale evidence/release; no new failing identity.
