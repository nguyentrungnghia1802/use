# Auction model audit

The current examples bundle is **not an exact synchronization** of the two
requested Core diagrams. This is source/notation review, separately from parser,
native-engine and live-runtime acceptance. `model-audit.json` records current
input hashes, graph text/edges and authored Task segments. The diagrams are not
executable checkpoint policies.

| Input | Finding |
| --- | --- |
| `auction.csl` | Auction fields and population invariants follow the diagram's embedded source, with the existing user-approved singleton Agent/OrgCtx revision for `auction_capabilities`. It is a separate context, not a parent Role. Native operation contracts are explicit versioned annotations, absent from the diagram's structural CSL DSL. |
| CSL diagram | Its embedded `cslSource` still declares a capability Role and role specialization, while its drawing uses an independent capability node with enactment links. The drawing connects `auctionGroup` to Auction through `entities`; the embedded DSL and example place Auction under `AuctionOrg`. Its visible notes say no Role inheritance. These representations are internally inconsistent. The visible `belief_best_bid : Real` / `belief_winner : Atom` also differ from the optional String tuple views in the embedded DSL and runtime example. |
| `auction.istar` | The actor/capability/Role arrangement, five operational Tasks, Artifact resources and responsibility dependencies follow the drawing. It additionally splits the organizational bidding requirement into attempt and accepted-bid goals. Its `P_bid_t2` POST uses `step.completed` and XML admits the exact source `.fail` branch as a completed attempt. The diagram explicitly requires a successful plan and `step.succeeded`. These are different requirements. |
| `auction-mapping.xml` | Maps all five native Tasks to exact ASL AST segments, plus a trace-only query prefix. It implements same-player capability delegation, actual operation receipts, the current attempt/acceptance split and the exact accepted-failure site. It is not generated automatically from Draw.io and does not implement the diagram's older success-only bidding policy. |
| Observer OCL | Current model annotations use source-confirmed ordering and an EXISTS committed-performer condition. The iStar diagram notes retain older timestamp names and state ALL committed performers. Current optional `auction.ocl` is comment-only; `WithinDeadline` remains disabled by the existing user decision. Enabling it would be a requirement change. |
| Source project | The executable ASL, Artifact and organizational XML are runtime inputs. Structural Draw.io edits do not rewrite them or their mapping contracts automatically. |

The existing model comments and README explicitly document the approved
Agent/OrgCtx and attempt-policy revisions. This implementation preserves those
requirements. It does not replace `step.completed` with `step.succeeded`, remove
the accepted-failure declaration, change EXISTS to ALL, or enable deadline OCL
under the guise of a checkpoint fix. Such changes would change the meaning of
the specification and need a separate explicit requirement decision.

## Checkpoint mapping correction

The first new creation-Task cut exposed an existing observer join assumption:
`monitor-observations` started from every scheme, then required an artifact
binding even for schemes whose creation Task had not run. The resulting partial
population retained the hold at the first valid creation checkpoint.

The creation-binding table now explicitly closes its current physical population
against `observed-domain-state`, using the generic versioned `populationClosure`
certificate. Every existing artifact must have exactly one same-cut, physically
witnessed creation binding. The two monitor tables then use the existing generic
`whereExists` operator before their required join. Unknown/incomplete membership
remains PARTIAL; missing, stale or duplicate bindings never establish closure.
Existing monitors still require exact current operation histories and source
clock. No OCL expression, Task predicate, accepted failure site or domain state
was changed. The cloned false-case mapping receives the same population fix;
its intentional domain fault is unchanged.

## Task boundary correspondence

| Native Task | Original plan body interval | Effect checked after the interval |
| --- | --- | --- |
| `A_start_t1` | `start`: `[0,5)` | Read arguments, create and focus the real artifact. Its intentionally unbound entry does not exclude its terminal checkpoint. |
| `A_start_t2` | `start`: `[5,6)` | Complete `start(Service)`; use captured PRE/POST and the frozen global invariant inventory. |
| `P_bid_t1` | `bid`: `[0,3)` | Read the ID, look up and focus that execution's artifact. |
| `P_bid_t2` | `bid`: `[3,4)` | Complete the entire conditional wait/bid or exact declared failure branch. A completed attempt is not an accepted bid. |
| `A_decide_t2` | `decide`: `[2,3)` | Complete `stop()`. The preceding `[0,2)` query prefix is trace evidence, not an invented native Task. |

These names and ranges appear only in the authored example mapping and this
audit. Production selection iterates admitted native Task identities; it contains
no Auction names, ranges, IDs, agents or expected winners.


## Final resolution: authoritative Draw.io synchronization

The baseline above is historical. The user explicitly selected the supplied
Draw.io and rejected adding inferred social Dependencies. Current source hashes
and graph inventory are in model-audit.json. No semantic edge or geometry was
added to either drawing. Embedded canonical DSL metadata is synchronized with
the examples; the mistaken Agent/SAME_PLAYER explanatory sentence was restored
to the original Role association sentence.

- CSL Auction belongs to auctionGroup.entities. Capability is an independent
  singleton-player OrgCtx with real enactments, without Role inheritance or an
  Agent-to-Entity participates(execution) association. Optional belief_best_bid
  is Real; Atom maps to String; missing/ambiguous optional beliefs stay undefined.
- The visible winner default is the literal `defaul`; this spelling is preserved.
  The source's actual observed winner always supplies mutable runtime state.
- iStar capability is a Role as its actual node metadata declares. There are five
  Tasks, one shared Resource, three needed-by links, two participates-in links,
  exactly three original Dependencies and no attempt/accepted extension Goals.
- The success-only bid Task uses step.succeeded. The .fail branch is not admitted
  as an alternative success. D_bid keeps the explicit ANY dependum policy.
- The three repeated source Goal views are connected by explicit XML realization
  metadata, not fabricated Dependencies. See MODEL-DECISION.md for its strict
  admission, same-scope/player/Role-interval rules and native-engine evaluation.
- SchemeOrder and BidEvidence expose the diagram's field/constraint names. Times
  are qualified MOISE public goal-state transition publication times on the source
  logical clock; private internal truth-change timestamps are not claimed.
  WithinDeadline uses actual NPL activation/deadline/first-terminal annotations,
  including expired/unfulfilled history after administrative recovery.
- A filtered NPL dataset declares the source board class. Its closed population
  requires complete same-cut CArtAgO inventory and, for every such board, an empty
  creation baseline plus an exact retained activation catalog. Late discovery,
  missing/stale catalogs or missing activations never prove absence.

Both auction and auction-false model/OCL files and XML hashes are synchronized.
The false-case Java stop fault and all external Auction runtime inputs remain
unchanged. Optional external OCL adds no duplicate embedded contracts. The canonical
Core OCL inventory is for review only, not a second selectable copy of the clauses.

The drawing's structured shared-source view identities are explicitly modeled by
the adapter. Generic Actor Auction uses a context Agent viewpoint in the concrete
DSL; this creates no additional running agent. Execution results and evidence
limits are reported independently of structural synchronization in REPORT.md.
