# Auction setup, mission authority and physical operations

## Experiment scope

The standalone probe loads the installed producer framework classpath and compiles
the external, unchanged `auction_env.AuctionArtifact` into an isolated target
directory. It uses the original Auction OS and four participant agents through
real CArtAgO/Moise APIs. It repeats the operations in `+!do_auction`, with explicit
artifact/workspace addressing, and then makes calls from a second agent.

This is an operation-level framework experiment. It is not a full Jason intention
run, a live USE checkpoint acceptance, or an imposed conformance verdict. No
production model, source plan, semantic assertion or role cardinality was changed.
`identity.json` records the input hashes and actual framework JARs on the classpath.

## Results

| Attempt | Native result |
| --- | --- |
| Second simultaneous auctioneer in the original group | Rejected: role_cardinality(...,2,1) |
| Other agent focuses the creator-owned scheme | Success |
| Other agent commits mAuctioneer before the creator, without a role | Rejected: mission_permission |
| Creator commits mAuctioneer | Success |
| Other agent adopts auctioneer in a separate group | Success |
| Other-group auctioneer commits the original scheme after creator commitment | Rejected: mission_cardinality(...,2,1) |
| Other auctioneer creates, focuses and starts the domain AuctionArtifact | Success; running=true and task changed |
| Other auctioneer reports organisational goal start achieved | Rejected: ach_not_committed_goal |
| Creator tries to start that already running domain artifact | Rejected by the original running-state guard |
| Other auctioneer starts another domain artifact created by the creator | Success |

The second-group mission attempt establishes a cardinality rejection, not a proof
about all possible cross-group permission situations. The earlier no-role attempt
separately establishes a permission rejection. All diagnostics remain in
`native-probe.log`, including Moise maintenance-condition compatibility warnings;
this experiment does not certify deadline behavior.

The initial harness used BasicContext's implicit focus routing and failed. The
corrected probe addresses the artifact's actual workspace explicitly; the first
log remains as `initial-harness-focus.log` and is not classified as a domain failure.

## Interpretation and proposed modeling

`do_auction` creates/configures a SchemeBoard, sets its arguments/owner, associates
the responsible group and commits the caller to mAuctioneer. It does not create
the domain AuctionArtifact or wait for bidding/decision. `+!start` later creates,
focuses and starts that separate domain artifact.

Model a capability Goal such as AuctionSessionPrepared refined by one
PrepareAuctionSession Task corresponding to the complete setup plan. Keep it
distinct from C_start and the per-scheme organisational auction goal. Bind only
real initial-goal invocations to their players and subsequently created scheme.
Do not require every player sharing the source file to execute setup.

The configured scheme is a prerequisite/input for the start behavior. A resource
needed-by link can express the input in iStar; task preconditions/source evidence
must establish readiness and exact identity. Refinement is not an ordering arrow,
and a social dependency should not be invented just to sequence two same-actor
behaviors. Any change to the authoritative diagram/model needs an explicit design
decision; this experiment implements none of these proposed model changes.

Proposed contract content (not paste-ready OCL over the current schema):
- Setup PRE: actual initial-goal demand, available organisation/workspace and inputs.
- Setup POST: successful source episode; exact created scheme, matching Id/Service,
  owner, responsible group and caller's mission commitment, with source evidence.
- Start authorization: actual performer has the relevant mission/role in the
  correct scheme context at the action boundary; service and domain artifact
  match that scheme. Do not substitute creator==performer as a universal rule.

Current Idle/Opened operation contracts establish not-running -> running with
the supplied service. They do not establish caller authorization or that the
supplied service matches the independent scheme. Existing runtime binding checks
are a separate layer; this experiment makes no claim that a whole USE run would
approve the second agent's actions.
