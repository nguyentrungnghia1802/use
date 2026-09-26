# Risk and blocker register

## Status vocabulary

- `OPEN`: must be controlled in an implementation phase.
- `KNOWN_LIMITATION`: architecture can proceed, but claims remain bounded.
- `GATE`: must pass before the named transition.
- `BLOCKER`: no sound implementation path exists without new authority/core change. No current item meets this definition.

## Register

| ID | Finding and source evidence | Current impact | Target implication | Likelihood / impact | Mitigation and owning phase | Status / blocker trigger |
|---|---|---|---|---|---|---|
| R-01 | Freeze manifest pins JaCaMo 1.3.0/Jason 3.3.0; audited JaCaMo HEAD declares 1.3.1/Jason 3.3.2 | Existing release evidence and target API graph are not identical | Bridge publishes distribution fingerprint and supports only tested graphs | High / High | Compatibility matrix and compile/runtime API probes in A–B | `GATE`; blocker only if required official APIs are absent from every supportable graph |
| R-02 | `Platform` is created before subsystem/agents, but list order does not prove artifacts/boards are ready | Early observation may miss authority state/events | Explicit adapter readiness, buffer-first capture and retry | Medium / High | Lifecycle integration tests in C | `OPEN`; blocker only if no hook can observe/reconcile a required dimension |
| R-03 | No audited globally atomic snapshot across Jason/CArtAgO/Moise | A “single instant” system state cannot be claimed | Use capture interval, per-source watermarks and validated consistent cut | Certain / High | Snapshot protocol B–C, races H | `KNOWN_LIMITATION`, not blocker unless thesis requires global linearizability |
| R-04 | Jason has no audited universal `BeliefBase` mutation listener | Delta-only belief mirror can drift | Capability flag + periodic/on-demand snapshot reconciliation | High / Medium | Coverage probes C/H | `KNOWN_LIMITATION`; blocker for profiles demanding every belief transition, not for snapshot-based mirror |
| R-05 | CArtAgO properties/descriptors may be created dynamically; source AST is incomplete | Complete design-time artifact MModel cannot always precede init | Initialized ModelSnapshot/model revision; static facts labelled partial | High / High | Descriptor probes B, model revisions D | `KNOWN_LIMITATION`; blocker only if required model cannot be revised before state apply |
| R-06 | Names can be reused after agent/artifact/board recreation | Stale events can mutate the wrong USE object | Session/generation/incarnation + official UUID IDs | High / Critical | Identity tests B–D/H | `GATE`; any demonstrated collision/stale acceptance blocks Phase I |
| R-07 | Jason/Moise/CArtAgO do not expose every cross-dimensional relation | Current resolver may over-link by name/arity | Exact official evidence or exact `binding.json`; otherwise unresolved | High / High | Coverage/binding tests B/D/E | `KNOWN_LIMITATION`; fuzzy fallback is a release blocker |
| R-08 | Event buffers are finite and cross-process delivery can duplicate/gap | Silent loss would make verification unsound | Bounded queues, acknowledgements, explicit GAP, idempotency and resync | High / Critical | Contract B, resilience H | `GATE`; silent drop/undetected gap blocks live verification |
| R-09 | Dynamic descriptors can require a new MModel | Events may target absent classes/properties | `modelRevision` handshake; rebuild MModel then matching snapshot | Medium / High | D/H tests | `OPEN`; apply-before-model is a release blocker |
| R-10 | Production transport is intentionally unselected | No deployable hardened production boundary yet; this does not defer architectural process proof | Prove separate JVMs with a minimal/test mechanism in D; measure candidates and perform ADR/security review in H | Certain / High | D then H | `OPEN`, not audit blocker; Phase I blocked until selected production transport passes |
| R-11 | House includes external templates, expands instances and creates dynamic artifacts/phases | Most likely source/runtime integration stressor | Official directive/version manifests, generic scale/resync tests | High / High | G | `GATE` for broad case-study claim, not for Hello prototype |
| R-12 | JaCaMo `build.gradle` runs `ant.fixcrlf` during configuration | Test invocation can touch checkout line endings; audit observed/restored one content change | Run in isolated copy/distribution; verify content hashes after | High / Medium | A and CI | `OPEN`; source mutation in evidence run invalidates that run |
| R-13 | Current dirty USE baseline has 3 Maven test failures | New failures/corrections can be misattributed | Preserve failure signatures, root-cause separately, no golden masking | Certain / High | A/E before default switch | `GATE`; unexplained failures block Phase I/K |
| R-14 | Legacy frontend and Bridge may differ materially | Premature removal could lose supported behavior | Semantic shadow comparator and reviewed delta register | High / High | E, removal J | `GATE`; unclassified delta blocks component removal |
| R-15 | Operation parameter names need `-parameters`/metadata; dynamic ops may have no `Method` | Some operation schemas remain name/arity-only | Explicit unknown positional params; reflection capability labels | High / Medium | B/F probes | `KNOWN_LIMITATION`; guessing names/types is prohibited |
| R-16 | Moise/NPL norms are not equivalent to OCL | Auto-translation would change semantics | Separate normative and OCL reports; explicit translator only if later proved | High / Critical | All phases, especially F/K | `KNOWN_LIMITATION`; implicit translation is a release blocker |
| R-17 | Public APIs/listener signatures can drift in future JaCaMo releases | Bridge may compile yet lose capabilities | API drift suite and fail-closed negotiation | Medium / High | B and every release K | `OPEN`; unsupported distribution is rejected, not silently adapted |
| R-18 | Snapshot/event volume and House runtime may be large/nondeterministic | Latency, memory and flaky gates | Measure canonical/synthetic workloads, bounded resources, replayable schedules | Medium / Medium | G/H/K | `OPEN`; budgets set from measurements |
| R-19 | Listener/executor lifecycle may leak or alter runtime scheduling | Shutdown hangs or semantic interference | Minimal callback work, bounded stop, leak and behavior comparisons | Medium / High | C/H | `GATE`; thread/listener leak blocks packaging |
| R-20 | Original Auction self-referencing plan/deadline and natural-language semantics remain unproved | Existing supported-subset evidence can be overclaimed | Preserve explicit boundary until original official execution proves it | High / High | F/K | `KNOWN_LIMITATION`; claiming full original E2E without evidence blocks release text |
| R-21 | Remote workspace child enumeration is not proven by local `Workspace.getChildWSPs()` | Bridge may miss remote topology | Capability distinguishes local/remote; add remote API probe or mark unavailable | Medium / Medium | B/H | `OPEN`; required remote coverage without API is a scoped blocker |
| R-22 | CArtAgO `getArtifactInfo(String)` is name-based after ID enumeration | Dispose/recreate race can pair old ID with new info | Revalidate `ArtifactInfo.getId()` against enumerated `ArtifactId`; retry cut | Medium / High | B/C | `GATE`; unchecked name lookup blocks authoritative snapshot |
| R-23 | Contract could accidentally mirror Java object layout or leak paths/secrets | Tight coupling/security/data exposure | Neutral schema, allowlisted values, path redaction and no native serialization | Medium / Critical | B/H | `GATE`; unsafe deserialization/secret leak blocks deployment |
| R-24 | Runtime timestamp/order could be overinterpreted across subsystems | False causal PRE/POST conclusions | Partial order, per-source sequence, explicit correlation; clocks diagnostic only | Medium / Critical | B/D/H | `GATE`; fabricated total order blocks runtime claim |
| R-25 | Moise role and subgroup cardinality are relation-scoped (`Group.getRoleCardinality`, `getSubGroupCardinality`) while frozen V2 stores intrinsic-looking attributes | Multiple contexts can collapse or receive an arbitrary bound | Preserve exact contextual tuples in Bridge/provenance; mark V2 projection `SUPPORTED_SUBSET`/`REPRESENTATION_LOSS`; gate dependent verification | High / High | B/D/F/G and later V2 review | `KNOWN_LIMITATION`; arbitrary select/merge or `EXACT` overclaim is a release blocker |
| R-26 | Mission commitments, goal state, norm lifecycle and scheme/group runtime instances may be observable but lack faithful frozen-V2/Runtime-Mapping targets | Silent discard or fabricated target would make OCL/report claims unsound | Preserve exact evidence with projection status; materialize only faithful facts; run `Runtime Verification Projection Review` after canonical stabilization | High / Critical | B/D/F/G; decision after G | `KNOWN_LIMITATION`; guessed/default materialization or OCL evaluation over evidence-only facts blocks release |
| R-27 | If process independence were first tested only during production transport work, classpath/live-object coupling could be discovered too late | Core architecture could appear valid in-process but fail across JVMs | Mandatory Phase-B isolation contract and Phase-D separate-JVM smoke; Phase H only hardens transport | Medium / Critical | B/D/H | `GATE`; Phase E cannot claim boundary viability until Phase-D smoke passes |

## Current blockers

None proven. The audited APIs provide:

- official project parsing and `Platform` insertion;
- official Jason AST/state/hooks;
- CArtAgO enumeration/descriptor/logger APIs;
- official Moise OS, ORA4MAS board state and NPL listeners;
- a sound fallback from incomplete event coverage to snapshots/resync.

This supports `FEASIBLE_WITH_ADAPTER`. It does not close the `GATE` items; it means they have concrete implementation/test paths rather than requiring a core patch.

## Blocker definition and escalation

An item becomes `BLOCKER` only when reproducible source/API evidence shows that a required semantic fact cannot be obtained, correlated or safely reconciled through official APIs/adapters and the approved architecture cannot weaken that requirement. The escalation record must include exact version/revision, minimal reproducer, attempted options 1/2, affected capability/V2 feature/case, and why snapshot/provenance/explicit limitation cannot preserve soundness. Only then may a small extension or core patch be proposed; it still requires user approval.

## Review cadence

Review the register at every phase entry/exit. Close a risk only with linked test/evidence; convert it to a limitation when the API boundary is intrinsic; never delete an item because a case did not exercise it.
