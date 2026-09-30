# System Architecture

This is the production architecture for the JaCaMo USE plugin. The default
authority is the official JaCaMo runtime/API object graph; the USE JVM never
reconstructs JaCaMo semantics from source text.

```text
official JaCaMo/Jason/CArtAgO/Moise objects
        |
        v
typed neutral semantic contract
        |
        v
JacamoSpecificationModel
        |
        v
J/A/C/M/X rule catalog and exact trace identities
        |
        v
NativeUseModelBuilder / NativeUseStateBuilder
        |
        v
native USE MModel + MSystemState
        |
        +--> one Session / OCL evaluator / verification workspace
        |
        +--> runtime projector and event bus updates
        |
        +--> Model Browser, Class Diagram, Object Diagram and OCL dialog
```

## Boundaries

- Official adapters read JaCaMo objects and publish typed facts, capabilities,
  provenance and stable opaque identities.
- `JacamoSpecificationModel` is the semantic hand-off. UI code, USE model
  builders and verification code consume the typed contract rather than parsing
  `.jcm`, AgentSpeak, Java or Moise text.
- J/A/C/M/X rules are explicit and exact. Unknown, ambiguous, stale or
  unsupported facts remain diagnostics/evidence-only and never become guessed
  USE truth.
- Native projection creates the `MModel` and `MSystemState` through USE APIs.
  The session, OCL evaluator, runtime projector and verification services share
  that same system; a second shadow `MSystem` is not created.
- The GUI is an inspection and control surface. Its event bus forwards import,
  connect, reconnect, resync and runtime updates; it is not a JaCaMo authority.
- `LEGACY_V2` remains an explicit compatibility constructor for frozen audit and
  regression tests. It is isolated from the default native branch and does not
  change the native authority contract.

## Verification lifecycle

1. The official Bridge produces a complete, authenticated snapshot and typed
   semantic contract.
2. The native pipeline validates capabilities, rule decisions and exact trace
   identities before building the USE model/state.
3. Constraints supported by the native capability profile are installed in the
   same USE system; unsupported conditions are reported as unavailable rather
   than translated by text generation.
4. Runtime events are validated, projected into the same `MSystemState`, and
   checked through the Session/OCL/verification path.
5. The UI renders the resulting model, runtime state, trace and diagnostics.

See the detailed contracts in `docs/agent/` and the active checklist in
`docs/agent/task.md`.
