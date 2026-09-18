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

Output: `target/phase20-launcher/launcher-audit.json`, per-scenario logs and jar
SHA-256 values. The helper fails with `PHASE20_PINNED_DEPENDENCY_MISSING` if the
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
   The launcher can still return from start and exit zero, so the probe correctly
   classifies this as TECHNICAL_LIMITATION. No full organisational timeline is claimed.

The canonical fixture is a static import/projection fixture, not a validated JaCaMo
launcher project. Its organisation XML has not been replaced with a guessed runtime
normative specification. Closing full-project E2E requires a separate validated
Moise OS/launcher fixture, with explicit reconciliation of its organisation semantics,
plus observation of actual Jason-driven operations and organisation board transitions.
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
