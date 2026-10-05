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

The current exposed policy is `NativeProjectionPolicy` 2.1.0. Ten generic bases
are installed once: Agent, Belief, AgentGoal, Workspace, Artifact, Organization,
Group, Scheme, OrganizationalGoal and Mission. Canonical ASL, Java artifact and
OS/group/scheme specifications generate concrete subclasses. Roles are native
MAssociationClasses keyed by exact OS/group/role context; player assignments are
MLinkObjects. Observable properties and supported exact Java operation signatures
become attributes and operations on concrete Artifact subclasses.

`Belief` remains a class, but its objects are a selective verification surface.
Official source AST identifies project-authored initial/write predicates; official
runtime annotations identify percept/Artifact duplicates and hidden library facts.
Only eligible current beliefs demanded by active compiler-derived OCL dependencies
become objects. Profile replacement re-selects from the latest completed-cycle cut,
with exact identity, removal/reappearance and transactional rollback. Missing
provenance fails the affected verification projection closed. Full raw literals
stay in Bridge observations and replay journal, including excluded infrastructure.

Recorded replay still checks every original state/result hash.
Operation declarations available at the recording baseline are exported with that
baseline; runtime-discovered descriptors are applied at their exact journal
positions. Future operations cannot change an earlier unavailable PRE/POST result.
An operation contract added after baseline without a recorded definition is rejected.

Retrospective reanalysis validates that recording and uses the current verification profile in
its existing isolated context. Different Belief selection is reported explicitly;
all other native objects, attributes and links must match at every entry.
Reanalysis evidence includes both domain-state hashes and never claims live or
recorded-result parity for the new profile.

Concrete artifact exposure requires application-provider evidence. Types defined
by the official CArtAgO provider remain internal, regardless of class/object name;
ORA4MAS state keeps its authoritative organisation projection. Unknown provider
evidence does not invent a native class. Current source/runtime type proof is
recorded on each exposed classifier; properties, operations and lifecycle facts
for excluded artifacts remain trace-only.
The exported classifier retains that exact provider proof for packaged replay
without producer-side classes. A loadable type with no provider location is
PARTIAL evidence, not an application type inferred from its name.

Jason execution structures stay in the rich semantic contract and trace.
Moise plan decomposition becomes goal relations, parent operator and explicit
child ordinal. Norms and advanced role constraints retain exact source evidence
with explicit unsupported diagnostics when a faithful OCL translation is absent.
See [projection refactor evidence](projection-refactor-before-after.md) for
current inventories, live gates and fidelity limits. Older deferred Belief/Goal/
Scheme/Mission and plain Role-association decisions are superseded.

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
- Declaration/runtime aliases require official workspace UUID/path and artifact
  name/type evidence; they bind one native object. Source-only declarations stay
  explicit until observed, including unavailable Artifact types discovered later.
- The GUI is an inspection and control surface. Its event bus forwards import,
  connect, reconnect, resync and runtime updates; it is not a JaCaMo authority.
  Already-open native views refresh dynamic classes, attributes and operations
  from the same model and retain the active Session system.
- The production facade accepts only `CODE_GROUNDED_NATIVE`. `LEGACY_V2`
  construction is rejected. Old connector, mutation, verifier and mapping adapters
  live in test scope for historical reproducibility and are absent from the JAR.
  Frozen resources retain their bytes but are never an automatic runtime fallback.

## Verification lifecycle

1. The official Bridge produces an authenticated snapshot and typed semantic
   contract with explicit per-source completeness. Jason literals come from a
   completed reasoning-cycle cut; Moise polling remains PARTIAL for global
   coverage even when individual validated board facts are COMPLETE.
   Official `ArtifactInfo.getObservers()` supplies exact focus endpoints.
   Focus/unfocus and join/quit callbacks update native links, and complete CArtAgO
   cuts remove absent links. Focus never creates a join. The implicit platform
   root declaration binds the actual root UUID, so it is one Workspace object.
   CArtAgO callbacks trigger supported Moise board observations. A five-second
   health observation detects missed changes; it is not the primary checkpoint
   semantics. Explicit `BridgeCheckpointSource` boundaries carry exact observed
   causation, correlation and watermarks; quiet time never invents a boundary.
   Global Moise coverage remains PARTIAL. Missing boards never imply deletion.
2. The native pipeline validates capabilities, rule decisions and exact trace
   identities before building the USE model/state.
3. Constraints supported by the native capability profile are installed in the
   same USE system; unsupported conditions are reported as unavailable rather
   than translated by text generation.
4. One EDT writer validates order/identity and applies each transaction to that
   same `MSystemState`. SNAPSHOT runs all eligible invariants; AFTER_MUTATION
   selects compiled feature dependencies with conservative full fallback.
   Native OPERATION_PRE/POST use the exact operation/arguments, correlated success
   and retained `@pre`; failure/abort cannot become a successful POST.
5. Each committed checkpoint freezes a portable VerificationSnapshot: exact
   aliases, typed values, links, SOIL, profile/result hashes, source capabilities,
   sequence/generation, correlation, freshness and completeness. Default retention
   is eight cuts and 16 MiB of serialized tail data; constant bounded pins preserve
   previous/last-passing/failure/confirmation evidence separately. These copies are
   diagnostics, never another active model. Capture overflow fails closed with GAP.
6. Unknown/user constraints default REPORT_ONLY. Only approved HARD false
   conditions may trigger RuntimeControlService. Approval is bound to the exact
   compiled condition, including negation; changed conditions lose authority.
   ERROR/UNDEFINED/SKIPPED never masquerade as false or automatically pause.
7. Freeze and publish the original violation before sending authenticated control.
   Official Jason ExecutionControl waits for every current admitted agent's exact
   incarnation ACK. PAUSED means **Jason agents paused** at reasoning boundaries.
   In-flight CArtAgO and Moise/OrgBoard work may finish. There is no atomic whole
   platform pause, console output API or UI-click control path.
8. All-ACK pause requires another authoritative cut and re-check: CONFIRMED,
   TRANSIENT_NOT_REPRODUCED or CONFIRMATION_ERROR. Explicit Resume uses the same
   controller, re-enable ACK and resync before LIVE. USE never repairs JaCaMo state.
9. Goal View, native Object Diagram, Model Browser, OCL and violation diagnostics
   share the active Session system. Goal View uses immutable facade DTOs and marks
   unsupported/currently unavailable goal evidence honestly. Recorded replay is an
   explicit detached mode; selected replay views read its active coordinator and
   cannot leak live control or original-workspace diagnostic data.

## Contract and diagnostic scope

Bridge control is version 1.0.0 over negotiated protocol 1.1.0; capability depends
on supported scheduler/controller ownership. Verification policy 1.1.0 adds at
most 64 exact current evidence identities with explicit domain rationales.
It expands violation diagnostics only: it creates no inferred Goal–Artifact link
or new classifier. Unknown/ambiguous identities are rejected before configuration.
Old 1.0 policy records remain readable. Replay manifest 1.1.0 records capability,
policy and boundary/PRE/POST timeline changes; old 1.0 records remain readable.
Source positions unavailable from the official Moise object API are reported as
file/element plus line unavailable, never a guessed XML line.
Source excerpts for installed OCL intervals use the accepted immutable bytes and
their hash even if the file changes or disappears. Native HARD severity does not
implicitly approve pause: every constraint defaults REPORT_ONLY.

Timing counters measure snapshot/mutation/OCL/control/resync costs and serialized
retention. They are observational evidence, not a controlled JaCaMo slowdown or
per-case JVM heap benchmark. A separate isolated native fixture measures retained
heap release after explicit GC while the active state/latest cut remain alive;
its scope and reproducible evidence are recorded in task.md.

See the detailed contracts in `docs/agent/` and the active checklist in
`docs/agent/task.md`.
