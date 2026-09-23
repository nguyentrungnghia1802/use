# Active baseline selection contract

Specification status: WORKING_BASELINE. Implementation migration is pending.

There is one intended production semantic selection: supplied Metamodel V2 plus
Mapping 2.1 at the paths recorded in v2-input-inventory.md. V1 is HISTORICAL_BASELINE.
No failure loading, validating or compiling V2 may silently activate V1. Errors
must carry the selected paths, expected/actual fingerprints and failed gate.

The future shared selector must snapshot and validate all input bytes once,
record package/nsURI, schemaVersion, mappingId, Ecore/mapping/schema SHA-256 and
compatibility status, and own filesystem/classpath lookup. Intended classpath
namespace: `org/tzi/use/plugins/jacamo/canonical/version-2/`. Historical resources,
if required by explicit tests, belong under `historical/version-1/` and cannot
be selected by a production fallback. A working-manifest fingerprint is an exact
revision identity, not authorization to freeze or bypass structural validation.

Current implementation still contains a V1 loader, V1 semantic enum, V1 runtime
bindings and V1 release paths. Merely pointing that loader at V2 is unsafe:
schema 2.1 introduces enums, three non-emitting opposite aliases, source requiredness,
explicit default materialization and distinct projection prerequisites. Selection
must fail closed until the selected consumer supports these contracts. The phase
checklist remains open for executable default-selection acceptance.

Existing connector mechanics, normalized events, queues, lifecycle, trace storage,
USE APIs and UI facade boundaries remain reusable. Target bindings and semantic
adapters migrate through the exact diff, not global string substitution.
