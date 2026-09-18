# 05 — Official Auction Runtime Walkthrough

## 1. Static project facts

The official `examples/auction/auction.jcm` declares:

- agents: `bob`, `alice`, `maria`, `francois`, `giacomo`;
- all use `auction_capabilities.asl`;
- `bob` has initial `do_auction(...)` goals;
- organisation `aorg` uses `auction-os.xml`;
- group `agrp : auctionGroup`;
- `bob` is `auctioneer`;
- the other four agents are `participant` players.

This is static configuration. It does not by itself prove every dynamic event ordering.

## 2. Environment artifact behavior

`AuctionArtifact` exposes observable properties initialized by `init()`:

```text
running  = false
task     = "no_task"
best_bid = Double.MAX_VALUE
winner   = no_winner
```

Operations:

### `start(task)`

- fails if already running;
- sets `running = true`;
- updates `task`.

Expected CArtAgO logger pattern (conceptual; exact callback interleaving must be captured in a live trace):

```text
opRequested(start)
opStarted(start)
newPercept(changed running/task)
opCompleted(start)
```

### `bid(bidValue)`

- fails if not running;
- reads `best_bid`;
- if new bid is lower than current best, updates `best_bid` and internal `currentWinner` to the current operation agent;
- otherwise the operation may still complete without changing best bid.

This is a useful verification nuance:

```text
operation completed != observable property necessarily changed
```

For a better bid:

```text
ARTIFACT_OPERATION_STARTED bid(value)
OBS_PROPERTY_CHANGED best_bid
ARTIFACT_OPERATION_COMPLETED
```

For a non-improving bid:

```text
ARTIFACT_OPERATION_STARTED bid(value)
ARTIFACT_OPERATION_COMPLETED
```

For bid while stopped:

```text
ARTIFACT_OPERATION_STARTED/requested (depending logger semantics)
ARTIFACT_OPERATION_FAILED
```

No fake `best_bid` mutation should be generated.

### `stop()`

- fails if not running;
- sets `running = false`;
- sets observable `winner` from `currentWinner`.

## 3. Organisation runtime

`auction-os.xml` defines:

- roles `auctioneer`, `participant`;
- group cardinality: auctioneer exactly 1, participant 0..300;
- scheme `doAuction`;
- root goal `auction`;
- sequence: `start`, `bid`, `decide`;
- timing constraints in the OS;
- missions `mAuctioneer` and `mParticipant`;
- obligations linking roles to missions.

At runtime, JaCaMo's Moise platform creates organisation workspace/boards and the Moise organisational entity state tracks agents, role players, scheme instances, mission players and goal instances.

## 4. Cross-dimensional runtime correlations worth verifying

### Agent -> role

```text
Jason agent alice
<-> semantic Agent alice
<-> Moise OEAgent alice
<-> RolePlayer participant in agrp
```

### Agent action -> artifact operation

```text
Jason external action bid(...)
<-> semantic ExternalAction
<-> semantic AbsOperation/AuctionArtifact operation
<-> CArtAgO OpId on AuctionArtifact
```

### Artifact state -> agent percept/belief

CArtAgO observable property changes may eventually become Jason percepts/beliefs through JaCa integration. The plugin must not assume every observable property has a static `ObsProperty -> Belief` binding unless the semantic model proves it.

## 5. Candidate live trace to capture for evidence

The project should add an instrumentation run that records at minimum:

```text
seq
time
source runtime
raw callback
agent runtime id
workspace id
artifact id
operation id/name
arguments
observable property name
old/new value if available
Moise agent/group/scheme/mission/goal snapshot delta
normalized event
resolved SemanticId
resolved USE target
```

Recommended scenarios:

1. platform boot + initial role/focus setup;
2. auction start;
3. improving bid;
4. non-improving bid;
5. stop;
6. bid after stop -> failure;
7. role/mission changes if the example performs them dynamically;
8. reconnect/resnapshot.

## 6. What the first USE runtime mapping should prove with Auction

A minimal but strong V1 demonstration:

```text
CArtAgO running/task/best_bid/winner
  -> exact projected USE attributes

CArtAgO bid/start/stop operation lifecycle
  -> exact USE MOperation enter/exit/fail

Moise role players
  -> exact Role.players / related organisation links

Jason action identity
  -> cross-dimensional trace to the corresponding artifact operation
```

Then load independent Auction OCL and verify the mirrored runtime state/operation contract.
