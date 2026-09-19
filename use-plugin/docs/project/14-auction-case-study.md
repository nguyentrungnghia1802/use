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
