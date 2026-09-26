# Phase 45 canonical JaCaMo case-study audit

Status: **AUDIT COMPLETE — IMPLEMENTATION NOT YET STARTED**.

This audit precedes every production change for the canonical Hello World, Auction
and House-Building cases. The JaCaMo source is authoritative. The supplied analysis
PDF is explanatory evidence only; where its prose differs from the source, this
report follows the source. In particular, Auction bids with probability `0.9`
despite the adjacent `80%` comment, and House-Building has the exact agent instance,
role/cardinality and goal-plan structure listed below.

## Inputs and source identity

| Case | Canonical entry | Entry SHA-256 |
| --- | --- | --- |
| Hello World | `doc/tutorials/hello-world/code/helloworld/helloworld.jcm` | `c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101` |
| Auction | `examples/auction/auction.jcm` | `c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb` |
| House-Building | `examples/house-building/house-building.jcm` | `c14ae6299b0d2e0034d7daaa233b9bf1b94a7b337aa39477ec865c52ca5fef08` |

The audit read every JCM, ASL, local Java Artifact, Moise XML and relevant build or
runtime configuration file in the supplied roots. Hello's `helloworld-grid.jcm`,
`america.jcm`, `europe.jcm`, `hello-grid.asl` and `hello2.asl` are tutorial variants;
they are source-audited but are not silently merged into the canonical
`helloworld.jcm` model. House tutorial `solutions.org` is exercise/reference
material and is not substituted for the original example source. External
`$jacamo` and `$moise` ASL templates are dependencies, not case-owned source.

Representative source hashes used to guard the implementation fixtures are:

| Source | SHA-256 |
| --- | --- |
| Auction `auction_capabilities.asl` | `31679b13210114b211dad862ad86af352cc9003ac9527ea7268e3a1d0f9cfe1b` |
| Auction `AuctionArtifact.java` | `34036fc220274853e39f93682cdb22c350fe6a06739669860f2a66afe606ff4b` |
| Auction `auction-os.xml` | `d2481d4ee3c45b2cead42f942a3943684e86facee992870356ca4d1e47d16f9a` |
| House `giacomo.asl` | `36cc0e6f1ee6f38a5d0bb1d2f3841a2733f233cb60115e67a789e71b8a072bc8` |
| House `AuctionArt.java` | `da3263c013b9a52c3918146d550389c8d00c830cff726f89598f4f2e72de0882` |
| House `House.java` | `2d60342250a03b7fa46a1a10f72a1c838114936b04acd0b9d0c0c6b24a851450` |
| House `house-os.xml` | `6d0896b7c69692683f65f3ef4a413bba1496da2f492f02d3f92efcc5daef0ef4` |

## Frozen V2 constraints

The Phase 44 contract remains authoritative and unchanged:

- Metamodel V2, Structural Mapping V2 and Runtime Mapping V2 remain `FROZEN`.
- V1 remains historical only; no case may activate it or fall back to it.
- Structural Mapping and Runtime Mapping remain separate.
- Runtime mutation still requires exact `RuntimeKey -> SemanticId V2 -> USE target`.
- Unknown or unbound runtime entities cannot mutate USE.
- JaCaMo executes; USE mirrors and verifies.
- Norms remain structural data. No obligation or permission is compiled to OCL.
- Independent target-only order projections remain authoritative in each direction.

The audit found no need to change the Ecore or either mapping. The gaps are in
source discovery, parsing, exact evidence resolution, diagnostics and case assets.

## How the Jason counts were obtained

The inventory uses the pinned Jason 3.3.0 parser in parse-only mode after removing
include directives. Local include files are parsed separately and attributed to
each including agent. No plan is executed. Jason's parsed `.foreach` and
`.if_then_else` constructs count as internal actions; belief/goal update and test
body terms remain source-only because V2 `Action` does not represent them. Rules
remain source facts because frozen V2 has no Rule EClass.

### Hello World exact inventory

| Layer | Expected semantic inventory from source |
| --- | --- |
| Jason | 5 Agents; 6 JCM initial Beliefs; 1 initial AGoal; 50 Plans; 50 triggering Events; 49 external and 50 internal Actions |
| CArtAgO | 1 Environment; 5 Workspaces (`jacamo`, `france`, `italy`, `brazil`, `usa`); 5 `GUIConsole` Artifacts; 5 `numMsg` Properties; 5 `printMsg/1` Operations; 0 Signals |
| Moise | 1 Organization; 1 Group `team`; 4 Roles (`rv`, `rl`, `rc`, `rs`); 0 Links; 1 Scheme; 4 Missions; 13 OGoals; 1 sequence OPlan; 4 obligation Norms |

The 50 plans are 12 `hello.asl` plans for each of four agents plus two `hf.asl`
plans for bob. The 99 actions include bob's parsed `.foreach`, its nested
`printMsg`, and the fallback `.print`.

Exact cross-dimensional evidence:

- four Agent-to-Role assignments are explicit in JCM `players`;
- eleven Agent-to-Artifact focus declarations are explicit in JCM;
- the 48 `hello.asl` `printMsg` actions have exact `focused(jacamo,gui,ArtId)`
  receiver evidence and can bind to `jacamo.gui.printMsg`;
- bob's generic iteration over every focused `GUIConsole` leaves its `printMsg`
  receiver runtime-dependent and therefore design-time ambiguous;
- JCM contains no explicit `join`, Belief-to-Property or AGoal-to-OGoal binding.

### Auction exact inventory

| Layer | Expected semantic inventory from source |
| --- | --- |
| Jason | 5 Agents; 0 initial Beliefs; 2 literal initial AGoals on bob; 40 Plans; 40 triggering Events; 75 external and 60 internal Actions; 45 non-Action body terms retained as source facts |
| CArtAgO | 1 implicit CArtAgO environment/default workspace; one exact dynamic `AuctionArtifact` declaration site, with runtime identities `a1` and `a2` proven by bob's two literal initial goals; per runtime artifact: 4 Properties and 4 Operations; 0 Signals |
| Moise | 1 Organization `aorg`; 1 Group `agrp` using `auctionGroup`; 2 Roles; 0 Links; 1 `doAuction` Scheme specification; 2 Missions; 4 OGoals; 1 sequence OPlan; 2 obligation Norms |

Each of five agents uses the same eight plans. The parsed per-agent body contains
15 external actions, 12 internal actions and 9 achievement/test/update terms which
cannot become V2 Actions. The artifact defines `running`, `task`, `best_bid` and
`winner`; operations are `init/0`, `start/1`, `stop/0` and `bid/1`.

Exact cross-dimensional evidence:

- bob is `auctioneer`; alice, maria, francois and giacomo are `participant`;
- JCM player declarations therefore provide five exact Agent-to-Role links;
- `start`, `stop` and `bid` have exact operation names/types, but the concrete
  `a1` versus `a2` receiver is known only from runtime artifact identity;
- property events/queries carry `artifact_id`, so Belief-to-Property attribution is
  exact at runtime but not a single static target across both artifact instances;
- scheme annotations prove organisational origin of `start`, `bid` and `decide`,
  but frozen V2 has no separate goal-type declaration; no AGoal-to-OGoal link will
  be invented from equal names alone.

### House-Building exact inventory

JCM has six agent declarations. JaCaMo's explicit `instances` semantics creates
`companyC1..companyC5` and `companyD1..companyD13`, giving 22 runtime Agents.
Local includes are part of each agent program: all six sources include `common.asl`;
all companies include `org_code.asl`; C, D and E also include `org_goals.asl`.

| Layer | Expected semantic inventory from source |
| --- | --- |
| Jason | 22 expanded Agents; 211 initial Beliefs; 150 initial AGoals; 375 Plans; 375 triggering Events; 365 external and 108 internal Actions; 29 Rules and 138 non-Action body terms retained as source facts |
| CArtAgO | 1 Environment; default contracting workspace plus dynamic `ora4mas`; 8 exact contracting `AuctionArt` instances and one `simulator.House` artifact; 32 local observable Properties; 26 local Operations; 0 local Signals |
| Moise | 1 Organization specification; 1 Group; 10 role definitions (9 direct group occurrences plus `building_company` as the evidenced hierarchy/link anchor); 2 Links; 1 Scheme; 10 Missions; 13 OGoals; 3 OPlans (one sequence, two parallel); 10 obligation Norms |

The eight auctions are exactly `SitePreparation`, `Floors`, `Walls`, `Roof`,
`WindowsDoors`, `Plumbing`, `ElectricalSystem` and `Painting`. Each `AuctionArt`
has `task`, `maxValue`, `currentBid`, `currentWinner`, and `init/2`, `bid/1`.
`simulator.House` adds ten construction operations and no observable property.
`OrgBoard`, GroupBoard and SchemeBoard are external ORA4MAS artifacts created by
exact source calls; their unavailable Java members must remain explicitly
unsupported rather than being invented.

The organisational source defines `house_owner`, `building_company` and eight
specialised roles. All specialised roles extend `building_company`. The group has
cardinality 1 for every direct role except `bricklayer` max 2, two links, and one
compatibility formation constraint. Frozen V2 has no formation-constraint EClass;
the compatibility must be retained as source evidence with an UNSUPPORTED
diagnostic. A definition-backed `building_company` Role anchor is required to
preserve the explicit hierarchy and link endpoints without guessing.

Exact cross-dimensional evidence:

- giacomo creates/focuses every contracting artifact; A and B discover one exact
  artifact; E discovers three; C and D discover all eight;
- auction winners adopt roles through the exact `task_roles` table, but the winner
  is nondeterministic. Static Agent-to-Role links are therefore unsupported; exact
  runtime winner/task evidence may bind them;
- giacomo explicitly joins `ora4mas`; company joins are conditional on receiving a
  contract and remain runtime facts;
- `bid` maps to `AuctionArt.bid`; ten construction action names map exactly to the
  unique `simulator.House` operations;
- property observations can be attributed by runtime artifact ID. Multi-auction
  static receivers remain ambiguous;
- no source annotation proves AGoal-to-OGoal identity. Equal spelling and comments
  are insufficient evidence.

## Actual Phase 44 plugin output on the original entries

This audit ran the current production `StaticProjectImporter` directly, before any
change. The raw evidence is generated under
`target/case-study-audit/baseline-import.tsv`.

| Case | Import | Diagnostics | Actual notable inventory |
| --- | --- | ---: | --- |
| Hello | FAIL | 56 | 5 Agents, 49 Plans, 49 Events, 97 Actions, 5 Workspaces/Artifacts/Operations/Properties; missing six JCM beliefs and one multiline plan; 48 ambiguous `printMsg` resolutions |
| Auction | FAIL | 230 | 5 Agents and Moise structure only; 190 `JASON_SYNTAX`, 40 unsupported statements; no Environment/Artifact/Plan/Action |
| House | FAIL | 123 | 6 unexpanded Agents, 14 Beliefs, 25 AGoals; 106 `JASON_SYNTAX`; no Java, Environment or Moise source discovered |

The existing Phase 41 Auction fixture is a checked-in reduced project, not this
original source. Its passing evidence is valid for its declared scope and is not
reclassified as original-source coverage.

## Gap classification

| Gap | Classification | Required disposition |
| --- | --- | --- |
| Line-by-line ASL parsing rejects multiline plans and block comments | BUG | Replace statement slicing with parse-only Jason AST consumption while preserving physical source spans |
| Local ASL includes are not in the project graph or agent program | MISSING_GENERIC_CAPABILITY | Resolve literal local includes recursively, reject cycles/path escape, keep external template includes explicit |
| JCM `instances` is stored but not expanded | MISSING_GENERIC_CAPABILITY | Expand exact JaCaMo instance names and attach shared-source provenance |
| JCM initial `beliefs`/`goals` are source text only | PARTIAL | Produce typed Belief/AGoal elements from exact config literals |
| Dynamic literal Java/Moise sources are undiscovered | MISSING_GENERIC_CAPABILITY | Discover only literal, in-root include/type/XML references; no class loading or execution |
| Dynamic artifact identities requiring variable/data-flow evaluation | AMBIGUOUS unless all substitutions are literal and unique | Evaluate only a bounded exact literal call chain; otherwise retain declaration template and diagnostic |
| Current Moise role-definition reader expects obsolete `role-def` nodes | BUG | Support source `role-definitions/role` and nested `extends` exactly |
| Moise compatibility has no frozen V2 class | UNSUPPORTED | Preserve exact XML/source span and emit a specific diagnostic |
| Global role definition versus group role occurrence is conflated by V2 | PARTIAL | Create only definition-backed anchors required by explicit hierarchy/link evidence and mark projection provenance |
| Resolver ignores JCM focus/owner receiver evidence | MISSING_GENERIC_CAPABILITY | Filter exact typed candidates by explicit receiver/access evidence; never use fuzzy spelling |
| Multiple dynamic artifacts share the same operation/property | AMBIGUOUS at design time | Leave link unresolved; bind only from exact runtime artifact identity |
| Rule, goal/test/update body terms, ORA4MAS external members | UNSUPPORTED | Preserve source facts and actionable diagnostics |
| Moise obligation/permission execution | UNSUPPORTED by structural import | Preserve Norm only; Case OCL must remain separately authored |
| Original Auction self-referencing plan/deadline equivalence | UNSUPPORTED | Keep the Phase 44 boundary; do not upgrade component/runtime evidence to full standalone equivalence |

## Generic production changes

Implementation will be sequential and shared by all cases:

1. Extend source discovery for exact local ASL includes and literal dynamic
   Java/Moise references, retaining the existing path-escape, cycle and hash rules.
2. Extend JCM extraction for exact `instances`, initial beliefs/goals and qualified
   focus relations without changing frozen semantic kinds.
3. Replace line-based ASL statement handling with the pinned parser's AST in a
   parse-only adapter. Directives are discovered separately and never executed.
4. Add bounded, fail-closed dynamic declaration evidence: literal names and exact
   literal substitutions only; variables with multiple candidates stay ambiguous.
5. Extend CArtAgO Java evidence for parameter/static-constant property types where
   the Java AST proves the type; no type inference from names.
6. Reconcile modern Moise role definitions, nested `extends`, dynamic
   group/scheme aliases and unsupported formation constraints.
7. Add exact receiver-aware resolution from source focus/artifact evidence and keep
   unresolved multi-receiver actions/properties explicit.
8. Vendor byte-identical canonical source snapshots plus a hash manifest for
   deterministic CI. The upstream supplied trees remain untouched.
9. Add case profiles and runtime fixtures outside production core. Runtime aliases
   are registered only against exact resolved trace records.

No generic change may branch on `helloworld`, `auction`, `house-building`, agent
names, task names, artifact names or business operations.

## Case OCL plan

- **Hello:** smoke constraints over the five declared GUI artifacts and the exact
  team-role assignments. No business policy is promoted to core.
- **Auction:** case invariants/preconditions over projected `running` and supported
  typed state, plus exact organisation/player structure. Bid/open and negative
  mutation scenarios remain case-authored.
- **House:** case constraints for the declared hierarchy/cardinalities, mission-goal
  coverage, sequence/parallel plan structure, and supported auction state. Winner
  selection and deontic enforcement are not invented as OCL.

Core OCL remains byte-identical unless a separately proved generic defect is found;
case constants appear only in each case profile.

## Test and evidence plan

Each case receives the same production-path gate:

1. byte/hash equality with the supplied canonical source;
2. exact semantic inventory and diagnostic assertions;
3. transformation and target-only ordering assertions;
4. generated USE compilation and direct/text materialization parity;
5. trace/source-span and exact-binding assertions;
6. Core plus Case OCL compilation and positive design verification;
7. supported runtime mirror scenario using exact aliases and original Java source;
8. negative mutation/precondition scenario with violation attribution;
9. deterministic repeated import/generation/trace comparison.

The implementation order is Hello World, then Auction, then House-Building. A final
three-case parameterized test must prove they use the same `StaticProjectImporter ->
TransformationPlanner -> VerificationSemanticLayer -> InstancePlanner ->
OclGenerator -> DirectUseBackend -> TraceBuilder -> Runtime Mapping -> verifier`
path. Focused tests, the full plugin module, the full reactor and frozen-resource
hash gates must pass with zero unexpected skips.

