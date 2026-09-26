# Identity and trace contract

## Invariants

1. Identity is structured and authority-scoped; display names are never primary keys.
2. Every runtime ID includes `sessionId` and `generation`, directly or through its parent incarnation.
3. Dispose/stop followed by recreation produces a new identity even when the local name is unchanged.
4. Cross-dimensional references require exact canonical endpoints and evidence; no fuzzy or similarity resolution.
5. The complete chain remains reversible:

```text
official canonical/source ID
 -> BridgeEntityId
 -> SemanticId V2
 -> TraceIndex entry
 -> USE class/object/link identity
```

## Structured `BridgeEntityId`

```text
BridgeEntityId {
  projectKey,
  sessionId?,
  generation?,
  subsystem,
  kind,
  namespace[],
  authorityId,
  incarnation?
}
```

The canonical wire form is a versioned, length-delimited encoding with a digest for long IDs. Human-readable strings are rendering only. Reserved characters, case, Unicode normalization and path separators are normalized by the encoder, not ad-hoc replacement.

## Entity identity rules

| Entity | Authoritative components | Incarnation/recreation rule | V2/USE mapping |
|---|---|---|---|
| Project | normalized source-root/project URI + content namespace/digest | New project key when authority root changes | Namespace for all semantic IDs; no V2 root class required |
| Agent declaration | project + declaration provenance + declared instance key | Stable within one model revision | `SemanticId(Agent,...)` -> USE Agent object template/declared object |
| Live agent | session + generation + Bridge-issued incarnation UUID + official local name | New UUID on every `AgArch.init`, including same-name recreation | Trace links live object to declaration when exact |
| Workspace declaration | project + official JCM path/name | Stable within model revision | `Workspace` model identity |
| Live workspace | session + generation + official `WorkspaceId` UUID/full path | New official UUID means new object | Runtime target for `Workspace` |
| Artifact declaration | workspace declaration + JCM artifact key | Stable within model revision | Declared `Artifact` identity |
| Live artifact | live workspace + `ArtifactId.getId()` UUID | Dispose/recreate is always distinct | Runtime `Artifact`; exact declaration trace optional |
| Operation descriptor | artifact type/instance scope + official signature/name/arity + descriptor discriminator | Dynamic replacement increments descriptor/model revision | `Operation` semantic identity |
| Operation execution | live artifact + official token when available + Bridge correlation UUID | One ID per request lifecycle | Runtime event/checkpoint, not a structural object |
| Property instance | live artifact + official property full ID/name and discriminator/index | Removal/re-add gets new incarnation when official identity changes; otherwise revision | `Property` descriptor + value state |
| Signal occurrence | live artifact + event/correlation UUID | Every emission is a distinct occurrence | `Signal` descriptor plus runtime event |
| OS definition | OS canonical URI/digest + definition kind + official ID/path | Stable within OS model revision | `Role`, `Group`, `Scheme`, `Mission`, `OGoal`, `Norm` |
| Group instance/board | session + generation + group board `ArtifactId` UUID + official instance ID | New board UUID/generation is distinct | State relation anchored to static `Group` |
| Scheme instance/board | session + generation + scheme board `ArtifactId` UUID + official instance ID | New board UUID/generation is distinct | State relation anchored to static `Scheme` |
| Role play | group-board incarnation + role definition ID + live agent ID | Tuple creation/removal is versioned | `Agent.roles`/`Role.agents` link identity |
| Mission commitment | scheme-board incarnation + mission definition ID + live agent ID | Commit/uncommit/recommit creates lifecycle revisions | Runtime tuple; trace to `Mission` and `Agent` |
| Org-goal state | scheme-board incarnation + goal definition ID | State revision increments on transitions | Runtime fact traced to `OGoal` |
| Norm instance | board incarnation + official NPL source token + Bridge discriminator | Engine rebuild/generation never reuses prior ID | Runtime normative fact; definition traces to `Norm` |
| Group-role cardinality | group definition ID + role definition ID + official cardinality evidence | Stable per model revision and relation occurrence | Exact Bridge/provenance fact; V2 intrinsic role attributes may be lossy |
| Parent-subgroup cardinality | parent group definition ID + subgroup definition ID + official cardinality evidence | Stable per model revision and relation occurrence | Exact Bridge/provenance fact; V2 intrinsic group attributes may be lossy |

## Relations and cross-dimensional bindings

A `BridgeRelationId` is the digest of `(relationKind, ordered canonical endpoint IDs, authority evidence ID, incarnation)`. It prevents two relations with identical display endpoints but different group/scheme/artifact contexts from collapsing.

Cardinality relation IDs include both contextual endpoints and never reduce to the role/subgroup ID alone. If two contexts expose different bounds, both relation facts and their evidence remain independently traceable; target-projection status is attached per relation.

Accepted relation origins are:

- explicit official JCM configuration;
- exact official runtime callback/state relation;
- an exact `binding.json` entry resolving both canonical endpoints uniquely;
- a deterministic adapter rule whose source API itself encodes the relation.

Rejected origins include case-study constants, basename match, literal/name similarity, same arity, “nearest” workspace or first candidate.

## `binding.json` role

`binding.json` remains an augmentation/provenance input, not semantic authority over official objects. Each entry must contain:

- schema/version and project/model revision constraint;
- exact source and target canonical selectors;
- relation kind;
- evidence/provenance and author intent;
- uniqueness policy (`exactlyOne` by default);
- optional validity scope.

Zero or multiple matches is a hard diagnostic. A binding cannot create an entity that no official/static source defines, override official UUID/type, or mask an unsupported API capability.

## `SemanticId`, `TraceIndex` and USE names

- Refactor existing `SemanticId` to accept Bridge authority fields and preserve current deterministic V2 behavior for legacy input.
- The `TraceIndex` stores both directions: Bridge ID -> semantic/model element -> USE classifier/object/link, and USE identity -> exact source evidence.
- Runtime facts without a faithful USE target still receive Bridge IDs and trace/evidence records with `EVIDENCE_ONLY`; absence of a USE object/link is explicit, not a broken trace.
- USE object names may be sanitized for USE syntax. The sanitized name is not the key; a deterministic collision table binds it to the canonical ID.
- A model-revision-scoped identity map is immutable after materialization. Model enrichment creates a new revision and an explicit old-to-new compatibility map.
- Diagnostics, OCL reports and UI selections carry canonical IDs even when showing friendly names.

## Session, generation and reconnect

| Situation | Required behavior |
|---|---|
| Same connection resumes with valid token and retained buffers | Keep session/generation; resume after acknowledged per-source watermarks |
| Event gap/overflow or unverifiable resume | Increment generation or establish a replacement snapshot boundary; old events rejected |
| Bridge/JVM restart | New `sessionId`; no live identity reuse |
| USE restart against live Bridge | Negotiate current session/generation, request full ModelSnapshot/RuntimeSnapshot, then stream |
| Model revision changes | Pause affected events, compile new MModel, transactionally replace state, resume on acknowledgement |
| Delayed message from old connection | Reject by session/generation before decoding mutations |

Generation is monotonically increasing within a session lineage. It is not reset because the network reconnects successfully. A snapshot declares the only generation it can initialize.

## Action/operation correlation

The Bridge `AgArch` creates a `correlationId` when it observes an external `ActionExec`. When official CArtAgO callbacks expose the downstream operation token, the CArtAgO adapter records an exact edge between them. Completion/failure closes that correlation. If the edge cannot be proven, the Jason action and CArtAgO operation remain separate facts; the V2 `Action.operation` reference is unset and diagnosed.

## Validation and failure policy

- Duplicate canonical entity ID with different payload: reject snapshot/event batch.
- Same event ID with same digest: idempotent duplicate; ignore after accounting.
- Same event ID with different digest: protocol corruption; quarantine and resync.
- Unknown endpoint or stale incarnation: do not mutate USE; quarantine and request resync.
- Dangling required model reference: fail ModelSnapshot acceptance.
- Optional unresolved cross-reference: retain source entity, omit the reference and emit an evidence-gap diagnostic.

## Required tests

1. Same-name agent, workspace artifact and board recreation across one generation and across restart.
2. Canonicalization with Unicode, reserved characters, long names and sanitized-name collisions.
3. Round-trip Bridge ID -> `SemanticId` -> `TraceIndex` -> USE -> source.
4. Old-generation and old-model-revision event rejection.
5. Duplicate/idempotent and duplicate/conflicting event handling.
6. Exact binding resolution with zero/one/multiple candidates.
7. Action-to-operation correlation success and deliberately uncorrelated failure.
