# SINGLE_OWNER_CONTEXT_V1 — neutral execution topology adapter

This is new adapter code, not an API in the pinned use4Goal upstream. No
original `GoalModelInstanceTranslator`, `GoalMarking`, `TaskMarking`,
`IStarMarking` or `IStarPropagation` source is changed by this extension.

The default `ORIGINAL_CONTEXT_PATHS` continues to call the P-CTX-01 topology
seam. It preserves both an owner-only receiver and a dependency-context copy.
For example, fulfillment of a receiver in `[Reviewer, Worker]` does not assign
the separate receiver in `[Reviewer]`. Existing parity tests retain that result.

An explicit `SINGLE_OWNER_CONTEXT_V1` session instead uses the new
`SingleOwnerContextTopology` adapter. Preconditions are a complete cast, exactly
one instance of each actor type, and positive context proof for every ordered
pair. Admission retains the existing size/depth/resource/policy limits. The
adapter makes a bijective instantiation: each source intentional element maps
to exactly one owner occurrence; refinements, dependencies, contributions,
qualifications, needed-by and actor associations keep their exact endpoints.
It implements no status or observation algorithm. Original marking/propagation
functions evaluate the resulting ordinary original `GoalModel` unchanged.

Expected difference: in this explicitly selected context, an AND parent's child
and a dependency receiver refer to the same owner occurrence. A fulfilled
dependency can therefore participate in the parent's original AND propagation.
This is not claimed as parity with the default contextual-path instantiation.
`SingleOwnerContextTopologyTest` checks both outcomes and the mandatory cast
proof. The topology policy participates in checkpoint definition identity;
cross-policy restore/seed is rejected.

`seedCompatibleLeafHistory` is a separate neutral session adapter seam. Across
changed execution definitions it transfers only identical Goal/Task leaf
contracts, resource requirements, owner/actor kind, structured binding stack and
caller-supplied episode. It never transfers a derived aggregate. The existing
whole-definition `seedStableHistory` check remains strict. Tests cover changed
predicates, unrelated episodes, another scheme and policy changes.

The plugin's Auction adapter selects this policy only after proving actual
scheme/artifact/player/context bindings. It retains canonical GoalModel bytes
and creates a separate derived execution M1. XML's all-relevant-committed bid
quantifier becomes distinct participant dependency receivers under an original
AND. Empty/unproved casts remain unavailable. This is neither another native
MSystem nor an inference that mission commitment or MOISE satisfaction is a
successful Jason Task.

Executable evidence is recorded in `use-plugin/verification/auction/I03/` once
the I03 focused and regression gates pass. Source/class hashes and original
source parity belong to that receipt; packaged/replay acceptance are later gates.
