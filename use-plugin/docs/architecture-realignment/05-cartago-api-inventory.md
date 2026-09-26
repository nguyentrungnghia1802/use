# CArtAgO API inventory

## Inspected baseline and authority

- JaCaMo resolves CArtAgO 3.1. The official source inspected is annotated tag `v3.1`, peeled commit `440cd41c1810ceef6a627477c461776b3200236b`.
- Principal source packages are `cartago`, `cartago.events` and `cartago.tools` in the official CArtAgO repository.
- The objects below are runtime/environment authorities. Java source inspection remains a secondary design-time source only where the runtime has not initialized an artifact.

## Workspace and artifact model

| Concern | Exact API/object | Exact obtainable facts | Limit |
|---|---|---|---|
| Environment root | `cartago.CartagoEnvironment.getInstance().getRootWSP()` -> `WorkspaceDescriptor` | Root descriptor/ID and, for a local workspace, `getWorkspace()` | Exists only in an initialized environment; a remote descriptor need not expose a local `Workspace` object |
| Workspace resolution | `CartagoEnvironment.resolveWSP(String)` -> `WorkspaceDescriptor` | Workspace descriptor by path | Resolution failure must remain explicit |
| Controller | `CartagoEnvironment.getController(String)` | `ICartagoController` for the named/path-selected workspace | Authority is per workspace, not a global transaction |
| Child workspaces | local `Workspace.getChildWSPs()` / `getChildWSP(String)` from `WorkspaceDescriptor.getWorkspace()` | Local workspace tree | Remote child enumeration is not proven by this API; repeat enumeration during resync because workspaces are dynamic |
| Agents in workspace | `ICartagoController.getCurrentAgents()` | Runtime agent presence in that workspace | This is CArtAgO membership, not automatically a Moise role |
| Artifacts in workspace | `ICartagoController.getCurrentArtifacts()` and `getArtifactInfo(String artifactName)`; validate `ArtifactInfo.getId()` against the enumerated ID | Artifact IDs and descriptors/state | Name lookup can race with replacement; ID revalidation is mandatory and a cut across controllers is not atomic |
| Artifact identity | `cartago.ArtifactId`; `getId()`, `getName()`, `getArtifactType()`, `getWorkspaceId()`, `getCreatorId()` | Stable runtime UUID plus namespace/type/creator | Local artifact name is not a stable identity across dispose/recreate |
| Workspace identity | `cartago.WorkspaceId`; UUID/full-name accessors | Stable runtime workspace UUID and path | Declared JCM workspace has no runtime UUID before creation |
| Artifact information | `cartago.ArtifactInfo`; operations and observable properties accessors | Exact initialized descriptor/state | Information is a point observation, not a global revision |

The Bridge recursively enumerates every reachable workspace, opens a controller for each, then validates that the workspace/artifact set did not change during capture. A failed validation causes retry or an explicitly incomplete snapshot.

## Operations and parameters

| Fact | Official source | Fidelity rule |
|---|---|---|
| Operation name/arity | `cartago.OpDescriptor.getOp()` / operation signature | Exact after descriptor discovery |
| Backing method | `cartago.ArtifactOpMethod.getMethod()` where the operation has a Java method | Java reflection can provide declared types and annotations |
| Parameter types | `java.lang.reflect.Method.getParameterTypes()` / `getGenericParameterTypes()` | `REQUIRES_REFLECTION`; serialize normalized type names only |
| Parameter names | `Method.getParameters()` | Exact only when compiled with `-parameters` or an official annotation/descriptor carries a name; otherwise emit unknown positional parameters |
| Result | ordinary artifact operation methods are normally `void`; output commonly uses `OpFeedbackParam<T>` | Model each feedback parameter explicitly; do not invent a single Java return type |
| Dynamic operation | descriptor may have no usable Java `Method` | Name/arity can remain exact; types/names are `API_NOT_EXPOSED` |

The existing `CartagoSourceExtractor` can discover declarations in Java source, but it must not remain semantic authority. Its safe target role is an optional provenance/enrichment tool for facts absent from runtime descriptors, labelled by evidence origin and never allowed to override runtime metadata.

## Observable properties and signals

- After initialization, `ArtifactInfo` exposes current `ArtifactObsProperty` values. Name, ordered values and runtime value types are authoritative for that capture.
- `Artifact.defineObsProperty(...)` creates a property during artifact initialization or later. There is no general proof that every property definition exists statically in Java source or annotations.
- Before initialization, a Java AST scan can report potential calls but cannot faithfully execute branches, factories, inherited setup or dynamic declaration. Static property completeness is therefore `NOT_AVAILABLE` unless an official artifact descriptor supplies it.
- CArtAgO percept/logger callbacks distinguish emitted signals and observable-property additions/removals/changes. The Bridge maps them to `Signal`/`Property` only with their artifact identity and exact callback payload.
- Values belong in `RuntimeSnapshot`/`RuntimeEvent`, not in the frozen V2 `Property` structural class, which stores name and arity.

## Runtime event API

`cartago.ICartagoLogger` is the inspected official observation surface. `CartagoEnvironment.registerLogger(...)` installs it. Its callbacks cover:

| Dimension | Logger callbacks/evidence | Bridge event |
|---|---|---|
| Agent membership | agent joined/quit workspace | `CARTAGO_AGENT_JOINED` / `CARTAGO_AGENT_QUIT` |
| Artifact lifecycle | artifact created/disposed | `ARTIFACT_CREATED` / `ARTIFACT_DISPOSED` |
| Focus | focus/unfocus succeeded | `FOCUS_ADDED` / `FOCUS_REMOVED` |
| Operation lifecycle | requested, started, suspended, resumed, completed, failed | Correlated operation events with `ArtifactId` and operation token |
| Percepts/state | `newPercept(...)` carrying signal/property additions/removals/changes | Signal/property delta events |

Callback signatures and correlation objects from the exact 3.1 build must be wrapped by a thin adapter; they must not leak into the neutral contract. The adapter assigns a source sequence at callback ingestion and preserves any official operation IDs available in the callback.

## Snapshot consistency finding

CArtAgO exposes authoritative enumeration and rich events, but no inspected API provides one atomic snapshot of all workspaces, agents, artifacts, operation descriptors and properties. The achievable protocol is:

1. register the logger and begin buffering;
2. record a Bridge watermark;
3. enumerate workspace tree and each controller;
4. re-enumerate identities and validate the cut;
5. publish the snapshot with capture start/end, per-workspace observations and completeness;
6. replay buffered events strictly after the accepted watermark;
7. retry/resync on topology change, overflow or missing correlation.

This is a validated consistent cut, not a claim of CArtAgO atomicity.

## V2 mapping consequences

| CArtAgO concept | V2 target | Fidelity |
|---|---|---|
| Workspace | `Workspace` | Exact after initialization; declaration-to-runtime identity is traced separately |
| Artifact ID/type | `Artifact` | Exact name/type; UUID retained in Bridge/trace identity |
| Operation descriptor | `Operation` | Name/arity exact; parameter detail is outside V2 and retained in contract/provenance |
| Observable property descriptor | `Property` | Exact after property exists; design-time completeness limited |
| Signal | `Signal` | Exact when emitted/declared by official descriptor |
| Agent workspace/focus relations | `Agent.workspaces` / `Agent.artifacts` | Exact from official configuration/runtime callbacks, with relation provenance |
| Jason action to operation | `Action.operation` | Exact only from action execution/selection or explicit binding, never name similarity |

## Verdict

| Capability | Verdict |
|---|---|
| Initialized workspace/artifact/property snapshot | `FEASIBLE_WITH_ADAPTER` |
| Operation and property event stream | `FEASIBLE_WITH_ADAPTER` |
| Operation parameter names/types/results | `REQUIRES_REFLECTION`, with explicit unavailable cases |
| Complete pre-initialization artifact/property model | `FEASIBLE_WITH_LIMITATION` |
| Cross-workspace atomic snapshot | `NOT_FEASIBLE_WITH_CURRENT_API`; validated consistent cut is feasible |
| Bridge without CArtAgO core patch | `FULLY_FEASIBLE` |

## Required implementation probes

1. Enumerate root, child workspaces and artifacts in official examples and compare controller results with lifecycle events.
2. Verify logger callback ordering and operation correlation for complete, failed, suspended and resumed operations.
3. Test inherited, overloaded, annotated and dynamically defined operations; record which parameter facts are exposed.
4. Create/dispose/recreate an artifact with the same local name and prove UUID-based identity separation.
5. Force a workspace/artifact topology change during snapshot and prove retry/incomplete behavior.
