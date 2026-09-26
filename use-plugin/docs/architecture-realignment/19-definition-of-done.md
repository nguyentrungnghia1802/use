# Definition of Done

## Audit/planning DoD (this stage)

| Required outcome | Evidence | Status |
|---|---|---|
| Inventory both workspaces/modules/versions | `00`, `01`, `03`–`06` | DONE |
| Trace current static USE pipeline exactly | `01-current-architecture-audit.md` | DONE |
| Trace current runtime USE pipeline exactly | `01`, `13` | DONE |
| Trace official JCM parse/load/launch | `03-jacamo-project-api-inventory.md` | DONE |
| Inventory Jason parser/program/runtime/listeners | `04-jason-api-inventory.md` | DONE |
| Inventory CArtAgO workspace/artifact/operation/property/events | `05-cartago-api-inventory.md` | DONE |
| Inventory Moise OS/ORA4MAS/NPL runtime | `06-moise-api-inventory.md` | DONE |
| Static/initialized/runtime availability matrix | `07-static-runtime-availability-matrix.md` | DONE |
| Official semantics -> every V2 EClass/cross-reference coverage | `08-jacamo-to-v2-coverage.md` | DONE |
| Bridge options, lifecycle and contract feasibility | `09-bridge-contract-design.md` | DONE |
| Exact identity and trace chain | `10-identity-trace-contract.md` | DONE |
| Full required component disposition | `11-use-component-disposition.md` | DONE |
| Frozen V2/mapping/runtime impact | `12-metamodel-mapping-impact.md` | DONE |
| Runtime snapshot/event/reconnect architecture | `13-runtime-architecture.md` | DONE |
| OCL/norm/verification boundary | `14-ocl-verification-impact.md` | DONE |
| Hello/Auction/House migration | `15-case-study-migration.md` | DONE |
| Baseline-aware test strategy | `16-test-strategy.md` | DONE |
| A–K implementation-ready roadmap | `17-migration-roadmap.md` | DONE |
| Risk/blocker register | `18-risk-blocker-register.md` | DONE |
| Capability verdict and all 60 questions | `20-final-feasibility-verdict.md` | DONE when final consistency gate below passes |
| No production implementation | Git diff scope validation | REQUIRED FINAL GATE |

## Final consistency gate for this audit

The audit is complete only when all checks are true:

1. exactly the required `00`–`20` files exist and every README link resolves;
2. terminology and verdicts agree across files (`FEASIBLE_WITH_ADAPTER` overall; no global atomic snapshot claim; no migration-blocking V2 gap but explicit cardinality/runtime representation loss; no core patch; production transport unselected; first separate-JVM proof in Phase D);
3. version/revision/hash values agree across inventory, impact, test and verdict files;
4. every required component has all disposition fields;
5. every roadmap Phase A–K has all 14 required planning fields;
6. all 60 questions are answered and trace to inventory evidence;
7. known test counts/failures and supported-subset boundaries remain explicit;
8. no production, test, Ecore, mapping, runtime mapping, OCL, golden or manifest file was edited by this audit;
9. pre-existing user changes remain untouched;
10. Git diff/status evidence is recorded at handoff.

## Implementation global DoD

The architecture migration as a whole is done only when:

- JaCaMo official objects are the production semantic authority;
- Bridge runs through official extension points without a JaCaMo core patch;
- the neutral contract has no USE/EMF/live JaCaMo object or shared-classpath dependency, and Phase D proves USE and JaCaMo in independent JVMs before Phase H selects/hardens authenticated bounded production transport;
- ModelSnapshot builds a deterministic V2/USE MModel and RuntimeSnapshot initializes an authoritative MSystemState from only faithfully projectable facts while preserving all other observable facts as evidence-only;
- RuntimeEvent application is identity-, generation-, model-revision- and watermark-safe;
- gaps/overflow/disconnect trigger explicit stale/resync behavior;
- frozen V2, Mapping V2.2, Runtime Mapping V2, projections and hashes remain unchanged unless a separately approved gap version is created;
- OCL and NPL normative semantics stay explicitly separate;
- OCL evaluates a runtime fact only when that dependency is faithfully materialized; evidence-only dependencies yield `INCONCLUSIVE`, `NOT_EVALUATED` or capability-blocked results;
- exact relation-scoped Moise cardinality survives in Bridge/provenance and any context-losing V2 projection is explicitly labelled;
- Hello, original Auction supported scope and House supported scope pass the generic case gates;
- any unsupported original semantics remain visible and are excluded from claims;
- legacy authority is removed only after replacement evidence and approval;
- all regression/security/determinism/package evidence is reproducible.

## Per-phase DoD

Each phase in `17-migration-roadmap.md` is complete only when its listed objective, tests, regression gates, evidence outputs and rollback rehearsal all pass. A merged implementation or green focused test alone is insufficient.

Phase D additionally cannot complete without the mandatory separate-JVM model/snapshot/event, restart/stale-event, reconnect/resnapshot and schema-mismatch smoke. After canonical cases stabilize, the `Runtime Verification Projection Review` must record the disposition of each evidence-only runtime fact before final thesis claims are frozen.

## Capability claim rule

Every public/research claim must identify:

```text
source revision + distribution versions
contract/model/snapshot revision
case/input hashes
capability/completeness set
tests/evidence artifact
known limitations
```

Use these scopes explicitly: static/load-only, initialized snapshot, runtime supported subset, original case E2E, or unavailable/unsupported. Never promote a narrower scope to a broader one.

## Stop condition

After the audit consistency gate passes, STOP. No Phase A production/test implementation begins until the user reviews and approves this plan.
