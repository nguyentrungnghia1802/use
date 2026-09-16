# Auction Case Study

## 1. Vai trò

Auction là case study xuyên suốt để chứng minh:
- import đa dimension;
- mapping;
- OCL;
- runtime state;
- operation contract;
- cross-dimensional verification.

Không hard-code Auction vào plugin core.

---

## 2. Expected JaCaMo model

Tối thiểu:

```text
MAS
├── Agents
│   ├── auctioneer
│   └── bidder(s)
├── Workspace
│   └── AuctionArtifact instance
└── Organisation
    ├── roles
    ├── missions
    ├── organisational goals
    └── norms
```

Artifact:
- auction state;
- reserve/highest bid;
- operations `open`, `placeBid`, `close` hoặc equivalent thực tế.

---

## 3. Expected USE representation

Base JaCaMo classes + concrete:
```text
AuctionArtifact < Artifact
```

Projected attributes chỉ khi source type/name resolved.

Projected operations:
- `open`
- `placeBid`
- `close`

---

## 4. OCL profile

Case-specific constraints phải được viết dựa trên project semantics thực tế, ví dụ categories:
- non-negative monetary values;
- valid auction state;
- placeBid only when open;
- amount positive;
- budget/capacity constraint nếu bidder budget thực sự được model;
- postcondition highest bid monotonic;
- close changes state correctly.

Không dùng constraint mẫu nếu JaCaMo case implementation không có corresponding state.

---

## 5. Cross-dimensional scenarios

### Action-operation
Jason ExternalAction `placeBid` phải resolve tới AuctionArtifact operation tương ứng.

### Percept-belief
Observable auction state update phải phản ánh vào mapped Belief nếu project có relation đó.

### Goal alignment
Organisational goal ↔ Jason goal theo `OGoalToGoal`.

### Role/action
Bidder role/mission relation có thể được kiểm consistency với performed action; deontic permission/obligation chỉ đưa vào nếu runtime/source semantics đủ rõ.

---

## 6. Offline tests

Positive:
- import success;
- generated model compiles;
- initial state valid;
- all expected traces exist.

Negative:
- duplicate operation owner ambiguity;
- missing artifact;
- invalid role reference;
- bad binding;
- violated case OCL.

---

## 7. Runtime scenarios

### R1 Valid bid
- auction open;
- bidder valid;
- placeBid positive;
- state update valid;
- pre/post pass.

### R2 Bid while closed
Expected precondition fail.

### R3 Invalid amount
Expected invariant/precondition fail.

### R4 Runtime trace mismatch
Expected diagnostic, no state corruption.

### R5 Reconnect
Disconnect adapter, mutate runtime, reconnect, full resync, USE mirror equals authoritative runtime snapshot.

---

## 8. Evidence artifacts

Thesis/demo should preserve:
- input project commit hash;
- generated `.use`;
- generated `.cmd`;
- generated OCL;
- case OCL;
- trace;
- runtime event log;
- verification report;
- plugin/version manifest.

## 9. Phase 14 reproducible evidence (2026-09-16)

From the repository root on JDK 21, run `mvn -pl use-plugin verify` (or the full
reactor `mvn verify`). `GoldenPipelineTest` and `LiveJaCaMoAuctionIntegrationTest`
regenerate exactly 14 files under `use-plugin/target/phase14-auction-evidence`:

- Offline (11): `auction.use`, `auction.cmd`, `auction-ocl.use`, `core.ocl`,
  `case.ocl`, `trace.json`, `diagnostics.txt`, `translated-ocl-provenance.txt`,
  `verification-pass.json`, `verification-fail.json`, `manifest.json`.
- Runtime (3): `event-log.json`, `verification-reports.json`,
  `scenario-summary.json`.

The manifest lists all 14 relative paths, the starting repository revision,
LF-normalized UTF-8 SHA-256 fingerprints for the checked-in Auction sources,
canonical Ecore/Mapping V1/freeze manifest, core/case OCL, verification profile,
and the ten non-manifest offline outputs. The runtime summary separately hashes
the event log and report and records real connector versions, bindings, event
counts, and the authoritative resync fingerprint. A clean checkout can
regenerate the outputs; runtime timestamps, UUIDs and the resync fingerprint
are run-specific and should not be compared byte-for-byte across runs.

The source fixture is the checked-in `.jcm`, Jason `.asl`, CArtAgO Java
Artifact, and Moise XML. The live test compiles and instantiates that Artifact
source, initializes a real Jason agent from its `.asl`, and constructs a real
Moise `OS`/`OE` subset programmatically through Moise 1.1 APIs. It does not
launch the entire `.jcm` application, execute the Jason plan end-to-end, or
load the checked-in XML as the runtime `OS`. The XML is structural import
provenance; communication link, formation cardinality, sequence-plan, time
constraint, and norm lifecycle behavior are not demonstrated by the live run.

The translated CArtAgO guard is `amount >= 0` and has `CARTAGO_GUARD|EXACT`
provenance. `PositiveBidAmount` (`amount > 0`) is a separately authored
`[OUR-EXT]` case precondition matching the explicit `failed(...)` body
boundary for zero, not an automatic translation of Java effects. The test
observes `opStarted` and a same-correlation `opFailed` for zero. The authored
`AuctionOpenForBid` precondition is a case-study verification policy over
observable `open`; the actual Artifact body still accepts a positive bid
while closed, which USE reports as a violation without blocking JaCaMo.
`AuctionInitiallyOpen` is an authored fixture invariant, not a universal
auction invariant. No Moise deontic obligation is auto-converted to OCL.

The evidence set and tests cover this fixture on the pinned Windows/JDK/USE/
Jason/CArtAgO/Moise versions only. Arbitrary Java effects, unbound dynamic
runtime entities, and norm activation/fulfilment/violation are outside this
demonstrated scope. A guard that remains false can suspend an operation with
no terminal callback; the verification lifecycle therefore begins at the
actual `opStarted`, not at `opRequested`, and the invalid-amount acceptance
scenario passes the guard before deterministically failing in the body.
