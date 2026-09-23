# Active baseline selection contract

Status: WORKING_BASELINE. Phase 32 selection/evolution component gates PASS;
full IR/parser/facade migration remains OPEN on Phase 33–35.

ActiveBaseline owns filesystem and classpath selection. MappingLoader.loadCanonical
now delegates to it and selects only Metamodel V2 + Mapping 2.2.0. It reads each
input once, validates owned byte snapshots, records package/nsURI, mapping version,
ID, Ecore/mapping/schema SHA-256, origin and compatibility status. A missing or
invalid filesystem V2 never activates classpath or V1 as a fallback. Packaged
selection is explicit and has exact fingerprint parity with filesystem selection.

Active classpath namespace is org/tzi/use/plugins/jacamo/canonical/version-2/.
POM resources now populate it from versioned canonical inputs. Explicit historical
V1 readers use historical/version-1/; their immutable historical manifest hash
keys are preserved while lookup resolves to that historical namespace. This is
temporary historical runtime compatibility evidence, not a second active structural
selector. V1 runtime consumers still require migration in their dependent tasks.

MappingModel is a data-derived structural descriptor; planners have no fixed
class/feature/inheritance/projection counts. Inheritance validation derives exact
edges from Ecore and schema-defined owner-qualified source keys. The remaining
V1 semantic enum/parser vocabulary is a separate Phase 33/34 migration obligation.

## Repeatable evolution loop

Run `python use-plugin/tools/v2_evolution_check.py --before <previous.ecore>
--after <candidate.ecore> --mapping <mapping.json> --output <impact.json>`.
The read-only command compares exact structure and emits source compatibility,
hashes, affected consumer layers and conservative dispositions. Exit 2 means
RECONCILE_REQUIRED. It refuses to overwrite an input. It never accepts a rename
or updates mapping/hash/golden files automatically.

Then reconcile affected mapping declarations and source identities; run native
EMF/schema/USE compiler checks, affected IR/parser/trace/OCL/runtime bindings and
regression; regenerate reviewed output and update the working manifest. Passing
source coverage alone is not final acceptance or freeze authorization.

## Evidence

- ActiveBaselineTest: filesystem/packaged parity; missing/stale V2 fail closed;
  a synthetic added subclass first fails coverage even after its hash is updated,
  then compiles through the unchanged generic planner after class/inheritance
  mappings are reconciled. No canonical source was edited by this control.
- Python evolution/diff tests cover add/remove class/attribute, type, bounds,
  containment, target, inheritance, ordering, self diff and unaccepted rename
  candidates. phase32-self-diff.json records unchanged canonical Ecore.
- Focused Maven regression: **20/20 PASS** including ordering and unmodified
  CArtAgO/Jason/Moise/composite connector tests. Python: **11/11 PASS**.
- The earlier full regression belongs to pre-default-selection revision f50f1435.
  Do not claim its exact failure count as a fresh result after default selection.
  Full consumer regression and phase merge/push closure remain OPEN through P35.
