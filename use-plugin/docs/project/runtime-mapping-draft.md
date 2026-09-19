# Runtime Mapping Draft

Status: **DRAFT_WAITING_FOR_METAMODEL_V2**. V1 is a temporary compatibility
target, not a final runtime contract. No freeze manifest is created.

## Procedural audit before declarative rules (P18.1)

Baseline engine at 4c6b170d resolves runtime aliases, then switches on event kind.
CREATE_OBJECT is a special branch accepting any semantic trace before using payload
class/object (authorization gap to close in Phase 19). Links accept payload USE
names (target-trace gap). There is no Auction-specific branch in runtime production.

| Existing event | Action / resolution | Checkpoint / test |
|---|---|---|
| CREATE_OBJECT / DESTROY_OBJECT | create from semantic trace / destroy alias object | AFTER_MUTATION; RuntimeFoundationTest |
| SET_ATTRIBUTE | alias object, payload attribute/type/value | AFTER_MUTATION; RuntimeFoundationTest |
| INSERT_LINK / DELETE_LINK | alias plus payload association and participants | AFTER_MUTATION; RuntimeFoundationTest |
| OP_ENTER / OP_EXIT / OP_FAIL | alias object, operation correlation | PRE / POST / failure; RuntimeVerificationEngineTest |
| OBS_PROPERTY_ADDED / CHANGED | set if attribute payload exists; otherwise silent applied | AFTER_MUTATION; CartagoRuntimeConnectorTest |
| OBS_PROPERTY_REMOVED | undefined if bound attribute exists | AFTER_MUTATION; live Auction reconnect |
| Jason BELIEF_*, GOAL_*, ACTION_*, MESSAGE_* | alias lookup then trace-only applied | NONE; JasonRuntimeConnectorTest |
| ARTIFACT_CREATED / DISPOSED, SIGNAL | alias lookup then trace-only applied | NONE; CartagoRuntimeConnectorTest |
| ORGANISATION_DISCOVERED, GROUP_*, SCHEME_*, ROLE_*, MISSION_*, NORM_STATE_CHANGED | alias lookup then trace-only applied | NONE; MoiseRuntimeConnectorTest; no NPL source |

Connectors supply exact normalized payload and binding facts; they do not choose
generic mutation semantics. At the audited baseline RuntimeVerificationEngine
maintained its own state-changing event set; Phase 19 reconciled it with rule checkpoints.

## Layers and vocabulary

Upstream fact -> RuntimeEvent -> exact identity -> generic semantic action ->
temporary V1 binding -> USE mutation. OCL is a downstream observer, not part of
the mapping layer. Research sources: 00_RESEARCH_BASELINE, 01 architecture,
02 capability matrix, 03 identity, 04 candidates, runtime-capabilities-v1.json,
SOURCE_MANIFEST and IMPLEMENTATION_RECONCILIATION in the research pack.

ATTRIBUTE_STATE_SET/UNSET require a traced property slot; OBJECT_AVAILABLE/
UNAVAILABLE require an authorized object/class; RELATION_INSERT/DELETE require
an exact association and all participants; OPERATION_ENTER/EXIT/FAIL require
an exact invocation and correlation. State/link actions converge idempotently;
operation terminals are single-use. TRACE_ONLY and NO_MUTATION forbid USE changes;
UNSUPPORTED rejects explicitly. All action names survive metamodel migration;
binding anchors and target compatibility require reconciliation.

The schema requires selector, authority, identity, correlation, payload, action,
target anchor/kind, trace, mutation, checkpoint, support and evidence/assumption/
unsupported/migration metadata. Rules use one selector per normalized kind;
generic synthetic kinds preserve their existing dimension-neutral contract.

## Temporary V1 compatibility

VP002 authorizes only statically projected observable attributes; VP003 authorizes
resolved operation signatures. Structural class/association anchors authorize no
arbitrary runtime instance. Artifact lifecycle remains trace-only until explicit
instance policy is supplied. Role.players (R019) exists but drops runtime group
context; the existing binding has no exact role-instance policy. Mission commitment
has no Agent-Mission slot, and OGoal has no runtime state slot. These observations
remain TRACE_ONLY / DEFERRED_FOR_V2, with explicit reasons. Norm lifecycle is
UNSUPPORTED. No semantic target is fabricated to increase supported coverage.

Loader/validator tests must validate each rule against embedded frozen V1 anchors,
reject unknown fields/enums, duplicate IDs/selectors, authority conflicts, missing
trace/correlation, incompatible action/mutation/target and mutating deferred rules.
Invalid input fails without a procedural fallback. Engine integration was completed in Phase 19.

## Integrated mirror contract (Phase 19)

RuntimeMutationEngine now selects the validated JSON rule before mutation. The
engine dispatches generic actions; the drift comparator uses the same action,
and verification obtains AFTER_MUTATION eligibility from the rule checkpoint.
Missing rules fail explicitly; unsupported rules and unbound property payloads
are quarantined. Trace-only application carries a diagnostic and makes no claim
that the observed mental/organisational state exists in MSystemState.

Every mutation resolves an active object trace and checks any supplied SemanticId.
Projected attributes now have explicit ATTRIBUTE trace records; operations require
projected OPERATION trace. Links require a resolved association and traced endpoints.
Scalar conversion rejects malformed booleans, fractional/overflowing integers,
nonfinite reals and non-string String values. Operation argument count/type is
checked before registering correlation. A terminal cannot close another object
or operation; completed correlations cannot be reused within the stream.

Object availability permits only the exact object/class present in the original
workspace trace. It may recreate that object after explicit disposal; arbitrary
new instance materialization remains unsupported. Disposal invalidates pending
operations on that object. Links converge on insert/delete replay. Dynamic aliases
are retired on stream/snapshot boundaries. Duplicate event IDs and ordering remain
owned by RuntimeTrace. Failed partial synchronization does not establish LIVE.

The supported mirror gate covers quiescent scalar state, exact generic object/link
mechanics and observed operation lifecycle. Jason mental state and Moise instance
facts are retained as observations; runtime instance equivalence and cross-dimensional
invocation joins are not proven and do not mutate V1. Snapshots do not expose all
in-flight operations or provide a globally atomic three-runtime cut.

LiveJaCaMoAuctionIntegrationTest derives target/phase19-mirror-evidence containing
model.use, initial-state.cmd, runtime-events.json, trace.json (mapping decisions),
mirror-correctness.json and a hash manifest. Initial/state-change/reconnect comparisons
must show zero unexplained drift; scenario queue failures/rejections/drops must be zero.
No replay.cmd is emitted because it would omit trace-only and invocation semantics.
The fixture input commit is recorded separately from the final regression revision.

The golden trace changed only by adding projected attribute declarations. A negative
control removes those new declarations and verifies the exact previous trace digest;
all existing model/state/OCL golden bytes and canonical frozen Core files are unchanged.
