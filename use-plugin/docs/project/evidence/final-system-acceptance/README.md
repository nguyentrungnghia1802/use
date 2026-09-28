# Final System Acceptance — 2026-09-28

## Verdict

The JaCaMo-authoritative, USE-mirror architecture is accepted for the explicitly
observed supported scope. Canonical Hello World, original Auction, and original
House-Building all ran in real JaCaMo producer JVMs with a classpath-isolated USE
consumer JVM. Each run produced official model/runtime snapshots, ordered runtime
events, a USE state, authored OCL results, and a reconnect/resync cut.

This is not an unrestricted semantic-equivalence claim. Runtime facts that have no
approved frozen Runtime Mapping target remain `EVIDENCE_ONLY`. The House run did not
reach the original root goal `house_built`; that upstream lifecycle boundary remains
visible instead of being forced by changing the case or JaCaMo.

## Immutable-input proof

- USE starting revision: `3c9094f37f3f460104360939a27c67cf5c0b546a`.
- JaCaMo revision before and after: `3866858a7ebf6be85d9199c13a09cf4bfb8191be`.
- JaCaMo tracked/untracked working-tree fingerprint before and after:
  `2B3825C99F53F3EDF3E9B20F6EE7A16909386D1B24A6BABCDD5F051459B64DC1`
  over 761 files.
- Hello subtree fingerprint before and after:
  `4E088324651D8A63DB12C9B004E4A8A82B438394FB9A322769F917ADE55331AD`
  over 56 files.
- Auction subtree fingerprint before and after:
  `61CE6572D24A8C6F26590AC3A702255B9F1DAA5F67C2E73E5E24E50DBE03D540`
  over 12 files.
- House subtree fingerprint before and after:
  `95A196CADCA77134D54945A0C47266FD2CA4C8E8C6921261DA95A504A98CA3E3`
  over 37 files.
- The 45 pre-existing JaCaMo working-tree paths remained the same line-ending-only
  dirty set. No JaCaMo source/core/case file was edited.

The aggregate fingerprint is SHA-256 over the CRLF-joined, ordinally sorted rows
`relative-path|file-SHA256`, with paths obtained from
`git ls-files -co --exclude-standard`.

## Frozen resources

| Resource | SHA-256 | Result |
|---|---|---|
| Ecore V2 | `4AE51638A078F0A933982844063993084F17D420694AC8A868D95B9DF063C35C` | unchanged |
| Mapping V2.2 | `FC03B90CF0729260747BFEFFA6A6CD463EEFD2259C0C3CD60ED22BD140EC48B1` | unchanged |
| Runtime Mapping V2 | `5B2C00F052010FB35A71EB7F50AE4650F8A09C7647332B47A7199C12CBF8A5F0` | unchanged |
| Freeze manifest | `7965AEF8EAE099E398DE50CB30A65F1B6C5B9207A6500DB678825340BCA73C77` | unchanged |

## Canonical live case matrix

| Case | Original source SHA-256 | Runtime cut and events | USE/OCL after resync | Classification |
|---|---|---|---|---|
| Hello World | `c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101` | 5 configured agents; 836 reconnect facts; 162 events; Jason, CArtAgO, Moise and NPL sources | 1,095 objects, 1,641 links, state `c3b6fa094dab8570acbebb114817d0546518530e5aaea936f08f63146c858caf`; 28/28 authored OCL PASS; LIVE before/after resync | `SUPPORTED_SCOPE_PASS` |
| Auction | `c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb` | 5 configured agents; 490 initial / 483 reconnect facts; 111 events (`CHANGED=25`, `STARTED=60`, `SUCCEEDED=26`); artifacts, properties, operations, boards, role players, mission commitments, goal and norm state | 994 objects, 1,432 links, state `91b3c1af7639e9873a51c0db1d208332d8003432eb9deed52006a5ec2f6d3202`; 28/28 authored OCL PASS; LIVE before/after resync | `SUPPORTED_SCOPE_PASS` |
| House-Building | `c14ae6299b0d2e0034d7daaa233b9bf1b94a7b337aa39477ec865c52ca5fef08` | 22 configured agents; 957 facts / 174 events in the 25-second broad run; 969 facts / 183 events in the 50-second lifecycle run; all eight `AuctionArt` instances, `simulator.House`, group/scheme/NPL boards, 9 role players, 10 mission commitments and 10 fulfilled NPL commitment obligations observed | 1,024 objects, 1,448 links, state `7f2d5e820a17347aba915d471da4bb5c60d20393894b2d223fb418c5b0463950`; 28/28 authored OCL PASS; LIVE before/after resync | `SUPPORTED_SCOPE_PASS_WITH_ROOT_LIFECYCLE_BOUNDARY` |

### Auction source audit correction

The current original `examples/auction/auction.jcm` and official model snapshot do
not contain the stale constructs described by older records. The organizational
plan is the sequence `start,bid,decide`; the two authored time constraints are
`10 seconds` and `1 hour`. No self-referencing organizational plan and no
natural-language deadline occur in the current source. Therefore this acceptance
does not invent or claim equivalence for constructs that are absent from the
audited input.

### House lifecycle boundary

The original House case autonomously created all eight auction artifacts, selected
winners, created `housegui` (`simulator.House`), formed the dynamic organization,
assigned roles and missions, and satisfied 12 leaf/intermediate organizational
goals. At the 50-second reconnect cut, `house_built` was still
`NOT_SATISFIED`, the final goal obligation was not present in the official NPL
snapshot, and the producer never printed `*** Finished ***`. The record therefore
does not claim complete original House termination. Changing the JaCaMo case or
core to force that transition was prohibited and unnecessary for the supported
Bridge acceptance.

## Transport, buffering, and reconnect result

- Producer and consumer are separate JVMs; the consumer evidence confirms that no
  JaCaMo/Jason/CArtAgO/Moise/NPL runtime JAR is present.
- Authenticated loopback TCP, exact distribution fingerprint, session, generation,
  model revision and per-source watermarks are validated.
- Snapshot-covered bootstrap events are acknowledged and not replayed as gaps.
- Subscriber acknowledgements are cumulative/batched and flushed on close.
- Evidence-only high-rate events advance authoritative watermarks without entering
  the USE materialization queue.
- Concurrent subscriber/reconnect and idle subscriptions remain live; a real loss
  records `BRIDGE_EVENT_GAP` and requires resync.
- House no longer fails with `BRIDGE_BUFFER_OVERFLOW`.

## GUI acceptance

Native Swing click-through was unavailable because the Codex native-app surface
returned no controllable windows. The allowed substitute gate passed:

- `JaCaMoWorkbenchPanelTest`: 8/8 PASS, covering Project, Trace, Diagnostics,
  Verification, Runtime, Binding, reconnect/resync controls and report delegation.
- `JaCaMoPluginTest`: 4/4 PASS, covering descriptor, shell/menu actions and workbench
  launch delegation.
- `ReleasePackageIT`: 3/3 PASS in a clean JVM.
- A temporary installation under `target/gui-acceptance-20260928-111954` launched a
  responsive `javaw` process with window title `USE`. Its plugin JAR was byte-identical
  to the production JAR, contained `useplugin.xml` and `BridgeClient`, and contained
  neither the legacy JCM parser nor JaCaMo-side adapter classes.
- The final `-InteractiveGui` helper smoke is
  `target/final-system-acceptance/gui-interactive-smoke/20260928-114146`: original
  Hello launched a real producer plus a responsive `USE` window, and closing that
  window cleanly stopped the producer. A subsequent default-mode smoke at
  `post-gui-default-smoke/20260928-114234` also returned
  `REAL_LIVE_JACAMO_EVIDENCE_PASS`.

Manual click-through steps are in
`docs/project/12-plugin-ui-workflow.md` and the root `report.md`.

## Regression result

Final command:

```powershell
mvn -B -pl use-plugin -am test
```

Result: 292 reactor tests, 289 PASS, exactly 3 preserved historical failures,
0 errors and 0 skips. Module totals are contract 8/8, official adapters 10/10,
`use-core` 12/12, `use-gui` 1/1, and `use-plugin` 261 tests with 258 PASS and the
same 3 known failures:

1. `ConstraintClosureTest.distinctV2OwnersWithJavaHashCollisionsKeepDistinctConstraintIdentities`;
2. `GoldenPipelineTest.auctionArtifactsMatchReviewedGoldenDigests`;
3. `InstanceMaterializationTest.textAndDirectBackendsShareOneVerificationPlanAndPassInitialValidation`.

No golden, expected digest, assertion, or frozen semantic resource was changed.

## Evidence index and digests

The full logs, snapshots, event streams, reports and exact launch commands remain
under `use-plugin/target/final-system-acceptance`.

| Run | Evidence directory | `summary.json` SHA-256 | `consumer-evidence.json` SHA-256 | Event-log SHA-256 |
|---|---|---|---|---|
| Hello | `hello-final/20260928-111219` | `ddb9ac0090ef8bfccc91bdd05b2808c94c1d55947bab85ce16db4fa3a0338d5b` | `eaf271b7cb053c200163621b15813619cad385affb8bd703289cf43d392c3a6e` | `71884da3a5ffaee2e4b5c41b68e72da856f0b6a7e47659f237f0ef10e52e93da` |
| Auction | `auction-final/20260928-111045` | `51b30ac508f461990b645f02422b385a3bfaf92796a52dee6af7bcbc96964d9a` | `1c0242aa8899f0dec895301617df9c38df55bedba14f8959f887746bf3419011` | `a804c533dc5b95f9222b2ee261fac8fa6ede242627c3b6888df4d054be3a8af1` |
| House broad | `house-building-final/20260928-110816` | `e25e9b4dd1446cc899192a4ae165942462c578c2d4dbcdfb4d211e4a66bd7542` | `e56dfca05fb175c030cde4ad5cba205c2c9b1c16bb0c9dd2829a45d7e4823a30` | `46421bb453f77c20cdd0c1eb5c172eddcacc6b3c07093f1dc1e01d62042b1e35` |
| House 50 s | `house-building-long/20260928-111351` | `5a8b877e4927f6f8799fce884b4583a7e6a3ae6540a2ddb72a363428bfaa1948` | `a5c6d3558bba61451e3a9449c270dd7423f87b3f33075fb6402ec08cdc1c8b56` | `7fa30ab3ed6b72913a5ad9d81b85fe56d21adea87f087c398de3edbdbde0144f` |

The adjacent `summary.json` is the machine-readable acceptance index.

## Reproduction commands

Run from `D:\_CODE_BANK\Project_\08_Thesis\use` after the Maven build:

```powershell
# Original tutorial Hello World
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\live-hello-bridge.ps1 `
  -JcmPath ..\jacamo\doc\tutorials\hello-world\code\helloworld\helloworld.jcm `
  -ProjectKey helloworld -CaseName original-tutorial-hello-world `
  -ObservationSeconds 15 -Headless:$false `
  -EvidenceDirectory .\use-plugin\target\final-system-acceptance\hello-rerun

# Original Auction
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\live-hello-bridge.ps1 `
  -JcmPath ..\jacamo\examples\auction\auction.jcm `
  -ProjectKey auction -CaseName original-auction `
  -ObservationSeconds 25 `
  -EvidenceDirectory .\use-plugin\target\final-system-acceptance\auction-rerun

# Original House-Building
powershell -ExecutionPolicy Bypass -File .\use-plugin\tools\live-hello-bridge.ps1 `
  -JcmPath ..\jacamo\examples\house-building\house-building.jcm `
  -ProjectKey house_building -CaseName original-house-building `
  -ObservationSeconds 50 -Headless:$false `
  -EvidenceDirectory .\use-plugin\target\final-system-acceptance\house-rerun
```

The helper copies the selected project to a temporary derived directory, compiles
project-local Gradle classes there when required, injects only Bridge configuration,
and never edits the original JaCaMo repository.

## STOP-condition result

No frozen-resource, golden, assertion, case-specific production branch, JaCaMo core
patch, or source-case modification was needed. No STOP condition was crossed.
