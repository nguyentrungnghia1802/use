# Full JaCaMo launcher audit and bounded runtime evidence

Date: 2026-09-19. Status: **SUPPORTED_SUBSET_COMPLETE for component mirror evidence;
TECHNICAL_LIMITATION for full-project Agent -> Artifact -> Organisation E2E**.

## Pinned launch path

The plugin POM includes Jason 3.3.0, CArtAgO 3.1 and Moise 1.1, not the JaCaMo
launcher. Local Maven artifacts additionally contain JaCaMo 1.3.0, JaCa 3.1 and
NPL 0.6. The JaCaMo 1.3.0 POM agrees with Jason 3.3.0; no runtime upgrade was made.
`javap` confirms public `init(String[])`, `create()`, `start()` and
`finish(int, boolean, int)` on `jacamo.infra.JaCaMoLauncher`.

The checked-in probe runs each scenario in its own JVM, replaces the GUI logging
handler with console logging, compiles only the checked-in Auction artifact,
uses a 30-second subprocess timeout and terminates the child process. It never
executes arbitrary imported user projects. It checks platform readiness and scans
upstream error diagnostics; a zero JVM exit alone is not E2E success.

Reproduce from the repository root (JDK 21 and the pinned local jars required):

```powershell
mvn -B -pl use-plugin dependency:build-classpath '-Dmdep.outputFile=target/phase20-component-classpath.txt'
python use-plugin/tools/runtime/launcher_probe.py
```

Output (under `use-plugin/`): `target/phase20-launcher/launcher-audit.json`,
`moise-schema-audit.json`, per-scenario logs, source/probe hashes, the embedded XSD
hash and all six runtime jar SHA-256 values. The helper fails with `PHASE20_PINNED_DEPENDENCY_MISSING` if the
explicit local launcher inputs are absent. It does not download or substitute versions.

## Actual findings

1. Unchanged `src/test/resources/auction/auction.jcm`: launcher init returns 3.
   The real parser rejects `budget` at line 3, column 27: the belief list lacks a comma.
2. A derived probe-only variant supplies commas between beliefs and platforms,
   and quoted absolute source paths. The real launcher initializes, creates the
   Jason agent, starts the market workspace and instantiates the checked-in
   `auction.AuctionArtifact` bytecode.
3. Moise 1.1 cannot load the existing static `auction.xml`:
   `cvc-elt.1.a: Cannot find the declaration of element 'organisational-specification'`.
   OrgBoard initialization then fails (`OS` is null / ArtifactConfigurationFailedException).
   The previous probe could return zero because a workspace contains infrastructure
   artifacts even when OrgBoard initialization failed. The current probe requires
   the named group/scheme artifacts and initialized board specifications; it exits 3
   with `PHASE20_ORGANISATION_NOT_READY`. Log classification also rejects upstream
   errors. No full organisational timeline is claimed.
4. `MoiseSchemaProbe.java` validates against `/xml/os.xsd` inside the pinned
   `moise-1.1.jar`. The namespace is `http://moise.sourceforge.net/os` and
   `os-version` is required. Adding only these two declarations still produces six
   validation diagnostics: `role-def` must use `role-definitions/role`, cardinality
   `object` must be `role` or `group` with a separate required `id`, `goal@root` is
   invalid, and a scheme-level `plan` is invalid (plans belong inside goals).
5. A separate OSBuilder control passes XSD validation and `OS.loadOSFromURI`, then
   starts OrgBoard, GroupBoard, SchemeBoard, Jason and the original Java artifact
   with the same pinned jars. It is explicitly **not equivalent to Auction**: it
   does not carry the source communication link, formation constraints, scheme-level
   self-referencing plan, or natural-language normative deadline. Moving the source
   plan under `sell_item` would give that goal itself as a child; replacing or dropping
   it is a semantic change, not a demonstrated format-only compatibility fix.
   The control is `PLATFORM_START_ONLY_NOT_MIRROR_E2E`, never full E2E success.
6. Reflection on the pinned public API confirms `GroupBoard.getGrpState()` returns
   `ora4mas.nopl.oe.Group` and `SchemeBoard.getSchState()` returns
   `ora4mas.nopl.oe.Scheme`. The current `MoiseRuntimeConnector` constructor consumes
   `moise.oe.OE`. The launcher boards cannot simply be passed to that connector.
   A board-state observation adapter and explicit identity/snapshot reconciliation
   are required; fabricating an independent OE would not observe launcher truth.

These are fixture and integration limitations of the current implementation, **not
proof that the pinned runtime cannot launch a valid project**. The positive control
demonstrates that it can. XSD validity alone also does not establish valid NPL deadline
semantics: `time-constraint` is only an XSD string. No deadline meaning is invented.

The canonical fixture is a static import/projection fixture, not a validated JaCaMo
launcher project. Its organisation XML has not been replaced with a guessed runtime
normative specification. Closing full-project E2E requires a separate validated
Moise OS/launcher fixture, with explicit reconciliation of its organisation semantics,
plus the board-state connector adapter and observation of actual Jason-driven
operations and organisation board transitions.
This is an engineering follow-up, not a request for a routine user decision.

## Closest supported evidence

`LiveJaCaMoAuctionIntegrationTest` imports the checked-in project, executes its real
Java artifact and Jason source in-process, and drives the pinned Moise OE API with a
programmatic organisation fixture. It does not load that OE from the static XML.
Its actual timeline covers successful/no-state-change operations, failure, property
change/removal, Jason event observation, Moise goal delta, disconnect and full resync.
The environment operations are issued by the test CArtAgO context, not a claim of a
complete autonomous Agent -> Artifact -> Organisation execution.

Phase 19 adds a derived mirror bundle with USE model, initial commands, event log,
mapping rule/action/target/result decisions, summary and hashes. Quiescent initial,
state-change and reconnect comparisons pass with zero unexplained drift. Queue
failed/rejected/dropped counts are zero. Jason/Moise facts without proven V1 state
slots remain explicitly trace-only; NPL norm lifecycle is unsupported. The existing
Phase 14 bundle also retains verification reports and fixture source provenance.

Full-project event categories not reached by the bounded scenario remain unproven;
absence in this run is not proof that an upstream event never occurs. No runtime
mapping freeze, normative translation or metamodel change follows from these results.
