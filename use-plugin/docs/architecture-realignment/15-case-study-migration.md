# Canonical case-study migration

## Source authority and immutable inputs

| Case | Canonical upstream entry | Entry SHA-256 |
|---|---|---|
| Hello World | `doc/tutorials/hello-world/code/helloworld/helloworld.jcm` | `c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101` |
| Auction | `examples/auction/auction.jcm` | `c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb` |
| House-Building | `examples/house-building/house-building.jcm` | `c14ae6299b0d2e0034d7daaa233b9bf1b94a7b337aa39477ec865c52ca5fef08` |

Representative guards include Auction ASL `31679b13210114b211dad862ad86af352cc9003ac9527ea7268e3a1d0f9cfe1b`, artifact Java `34036fc220274853e39f93682cdb22c350fe6a06739669860f2a66afe606ff4b`, OS XML `d2481d4ee3c45b2cead42f942a3943684e86facee992870356ca4d1e47d16f9a`, House `giacomo.asl` `36cc0e6f1ee6f38a5d0bb1d2f3841a2733f233cb60115e67a789e71b8a072bc8` and OS XML `6d0896b7c69692683f65f3ef4a413bba1496da2f492f02d3f92efcc5daef0ef4`.

Canonical test copies must be byte-identical and carry a source manifest. Existing local/reduced Auction, CounterTeam and programmatic runtime fixtures remain valid only for their declared scope; none is evidence that the original upstream case passed.

## Generic path required for every case

```text
official JaCaMo load
 -> Bridge ModelSnapshot
 -> neutral/native semantic adapter
 -> frozen V2 + Mapping V2.2
 -> USE MModel
 -> accepted RuntimeSnapshot
 -> USE MSystemState
 -> RuntimeEvent stream + OCL/verification
```

Production code may not branch on a case ID, project name, agent/artifact/role/operation name or source path. Case constants and expected outcomes live only in fixtures/profiles.

The generic path has two explicit projection outcomes. Exact official facts always remain in Bridge/provenance; only a faithful subset is materialized into frozen V2/`MSystemState`. Relation-scoped role/subgroup cardinality that loses context is marked `SUPPORTED_SUBSET`/`REPRESENTATION_LOSS`, and captured runtime facts without a faithful USE target remain evidence-only. OCL requiring either missing context is capability-blocked/inconclusive.

Each case passes the same gates:

1. canonical source hash and dependency fingerprint;
2. official JCM/directive/Jason/Moise load with provenance;
3. deterministic ModelSnapshot and explicit unsupported facts;
4. frozen V2 transformation/USE compilation/direct and text backend parity;
5. identity/trace round-trip;
6. Bridge runtime snapshot and event sequence where execution is supported, including explicit per-fact materialization/evidence-only status;
7. Core plus case OCL compile and expected positive/negative verification;
8. reconnect/resync and record/replay determinism;
9. no hidden custom-parser fallback.

## Hello World — first vertical slice

### Scope

The canonical source declares five agents, five workspaces, five `GUIConsole` artifacts and explicit organization/group/scheme/player configuration. It exercises official JCM focus/player facts, Jason programs, artifact descriptors and Moise OS without Auction/House's full dynamic complexity.

### Migration

1. Load `helloworld.jcm` through `JaCaMoProjectParser` and launcher-equivalent source/directive configuration.
2. Parse each expanded agent program with official Jason objects; preserve included-source provenance.
3. Load `o1.xml` through `OS.loadOSFromURI` and traverse SS/FS/NS objects.
4. Initialize the official environment to obtain exact `GUIConsole` artifact/property/operation descriptors.
5. Export exact focus and player relations from JCM; leave Bob's runtime-dependent generic artifact receiver unresolved at design time.
6. Compile/materialize the Bridge-derived model with frozen V2 artifacts.
7. Launch and capture one authoritative snapshot plus print/focus/organizational events, then verify reconnect.

### Acceptance evidence

- Model inventory and digests match an independently generated official-object oracle, not legacy parser output alone.
- All explicit JCM role/focus relations resolve to canonical endpoints.
- Any action-to-operation relation without exact receiver evidence remains unresolved with a diagnostic.
- USE model/core+Hello OCL compile; positive and negative case assertions have exact trace attribution.
- No custom JCM/Moise parser or Java-source authority is invoked on the accepted Bridge path.

## Auction — dynamic artifacts and organizational lifecycle

### Required coverage

- Official JCM and shared Jason program for all agents.
- Dynamic auction artifact creation/identity (`a1`, `a2` only when official runtime evidence establishes them), properties and `init/start/stop/bid` operations.
- Moise roles, player declarations, scheme, missions, organizational goals, sequence plan and norm definitions.
- Role assignment and mission/scheme/goal/norm runtime state from boards/NPL.
- Jason action request through CArtAgO operation completion/failure correlation.
- Property deltas attributed by exact `ArtifactId`, not the operation/property name alone.

### Migration

1. Import the original source unchanged and build the official static ModelSnapshot.
2. Represent dynamic artifact declaration capability as incomplete until initialized; never synthesize an instance from Java text.
3. Launch the original JaCaMo case under the Bridge hook and observe artifact UUIDs, descriptors and property state.
4. Observe group/scheme boards, role players, commitments, goal states and NPL lifecycle with their board incarnations; preserve facts even when no faithful USE projection exists.
5. Materialize only the faithfully representable snapshot subset, replay correlated start/bid/stop events, and evaluate only explicitly authored case OCL whose dependencies are materialized.
6. Kill/reconnect/resync and prove the final fingerprint/report is stable.

### Semantic boundary

The existing programmatic `LiveJaCaMoAuctionIntegrationTest` is a supported-subset control and is not the original Auction. “Original Auction standalone E2E PASS” is prohibited until the original self-referencing plan behavior and natural-language/deontic deadline semantics are executed and evidenced by the official runtime. Structural OS import, board snapshots or a reduced runtime do not prove that equivalence.

### Acceptance evidence

- Original source hashes and JaCaMo distribution fingerprint recorded.
- Every runtime artifact/role/mission/norm state refers to an official UUID/incarnation and is either faithfully materialized or explicitly retained as evidence-only.
- Operation PRE/POST checks carry exact correlation and source watermarks.
- Unsupported deontic semantics remain explicit and are not converted to OCL.
- Reduced/current Auction evidence remains separately labelled and is not overwritten.

## House-Building — scale, includes, instances and phases

### Required coverage

- Local and external ASL includes using official Jason directive/source-path semantics.
- JCM `instances`: six declarations expand to 22 live agents, including `companyC1..5` and `companyD1..13` according to official launcher behavior.
- Eight dynamic contracting `AuctionArt` instances plus the simulator `House` artifact when observed.
- Moise role hierarchy/cardinality, links, missions, 13 organizational goals and sequence/parallel plans.
- Dynamic winner-driven role adoption, mission/scheme instances, organization board state and cross-phase resync.
- Formation compatibility retained as unsupported/provenance because frozen V2 has no corresponding structural class.

### Migration

1. Prove official includes and instance expansion in the ModelSnapshot/declaration-to-incarnation trace.
2. Initialize/launch under the Bridge without executing any case-specific discovery rule.
3. Discover artifact and board identities exclusively through official runtime APIs/events.
4. Snapshot the pre-contracting state; stream contracting auction lifecycles and winner-dependent role changes.
5. Snapshot the organization/build phase, preserving sequence/parallel goal plan structure.
6. Disconnect during a phase transition, resync, and prove no old-generation event mutates the replacement state.
7. Run only case-authored constraints whose required capabilities are present; label unobservable cross-phase claims inconclusive.

### Acceptance evidence

- Canonical included sources and external dependency versions/digests recorded.
- Exactly observed agent incarnations are distinct from JCM declaration templates.
- Every dynamic artifact/board relation is UUID/incarnation-backed.
- Bridge hierarchy/cardinality/mission/plan facts match official OS objects; any context-losing frozen-V2 cardinality projection is explicitly labelled and never asserted exact.
- Compatibility constraint, dynamic winner and any unavailable external ORA4MAS members remain explicit—not guessed.
- Record/replay and reconnect yield the same accepted terminal fingerprint for a controlled run.

## Migration order and stop gates

1. Hello proves the end-to-end contract and simplest official runtime integration.
2. Auction adds dynamic artifacts, operation correlation and organizational/norm lifecycle.
3. House adds include/instance expansion, scale, dynamic phase transitions and complex resync.

Do not advance when the preceding case uses a hidden legacy fallback, has unresolved required identities, changes frozen artifacts, or passes only by reducing assertions. A limitation may remain, but it must be capability-labelled and excluded from the claimed acceptance scope.

## Case-independent proof

After all three migrations, a parameterized integration gate must feed three different project roots through the same Bridge/adapter/planner/materializer/runtime/verification classes. A source scan and mutation test must demonstrate that removing/changing a case fixture does not change generic production behavior.

Only after that stabilization may the separate `Runtime Verification Projection Review` and any V2.x/V3 relation-cardinality review decide whether evidence-only facts need a target-side projection or metamodel evolution.
