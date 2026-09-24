# USE JaCaMo Plugin — Canonical Project Orientation

Active production baseline is Metamodel V2 + Mapping 2.2.0 WORKING_BASELINE.
The V2 IR, parser, transformation, trace, OCL and runtime target consumers are
migrated. Generic target-only rank projection preserves independent opposite
orders; V2-ORDER-001 remains a mandatory negative/positive regression control.

Clean full reactor: **350/350 PASS**; Python evolution/diff: **11/11 PASS**.
See [Phase 29–35 acceptance](v2-migration/phase35-acceptance.md) and its complete
per-suite and original-failure disposition evidence. Phase 29–35 are DONE: post-merge 350/350 PASS, merged and pushed. The V2 working ZIP is explicitly unreleased and unfrozen. V1 evidence
below remains historical; original Auction standalone equivalence is not claimed.

## 1. Historical V1 snapshot

| Item | Current evidence-backed state |
| --- | --- |
| Plugin version | `1.0.1` in `use-plugin/pom.xml` and `useplugin.xml` |
| USE parent/API | `7.5.0` |
| Runtime pins | Jason `3.3.0`, CArtAgO `3.1`, Moise `1.1` |
| Git | Final engineering revisions are recorded in `evidence/closure/validation.json` |
| Tags | `v1.0.1` points to `7f77b1f4`; `use-jacamo-plugin-v1.0.1` is only the manifest's planned tag name and is absent |
| Historical V1 full verification | See `phase28-project-closure.md`; current V2 regression is recorded above |
| Hotfix status | P1 workspace lifecycle FIXED; P2 production binding integration FIXED |
| Archive evidence | Current candidate: 30 entries including frozen runtime mapping; historical hotfix: two identical 27-entry ZIPs |

The annotated `v1.0.1` tag belongs to the historical hotfix baseline,
not the final engineering candidate. Do not describe current `HEAD` as exactly tagged, and do not describe
`use-jacamo-plugin-v1.0.1` as an existing or published tag.

## 2. Authority and claim discipline

For runtime and feature claims, use this order:

1. production code under `src/main`;
2. executable tests and retained validation evidence;
3. frozen Ecore/mapping contracts for their structural scope;
4. active architecture and workflow documentation;
5. historical plans, checklists, and audit reports.

Code is the source of truth for implemented behavior. Frozen Ecore and Mapping V1
remain authoritative for their own identities and structural contract, but they do
not prove that every mapped concept is supported by the production runtime.

Use these labels when making claims:

- **IMPLEMENTED** — production path invokes it.
- **PARTIAL** — production path supports a declared subset.
- **TESTED ONLY** — demonstrated by a fixture/test, without generic product support.
- **NOT IMPLEMENTED** — no production path.
- **OUT OF SCOPE** — intentionally excluded from this release/research scope.
- **RESEARCH LIMITATION** — evidence does not support a stronger semantic claim.

## 3. Repository map

```text
use/
├── pom.xml                         # five-project Maven reactor
├── use-core/                       # USE model, state, parser and OCL engine
├── use-gui/                        # USE GUI and plugin runtime
├── use-assembly/                   # USE distribution assembly
├── docs/report/                    # retained audit reports
└── use-plugin/
    ├── pom.xml                     # plugin 1.0.1 and reproducible timestamp
    ├── src/main/java/              # production pipeline, UI and runtime adapters
    ├── src/main/resources/         # plugin descriptor, schemas, OCL and manifests
    ├── src/test/                   # unit/component/integration and Auction fixtures
    ├── Core/Metamodel/             # reconstructed Ecore baseline
    ├── Core/Mapping/               # frozen Mapping V1, schema and audit evidence
    ├── docs/project/               # active architecture/onboarding documents
    ├── docs/agent/                 # agent guidance and archived task checklist
    ├── release/                    # manifest, hotfix record and retained evidence
    ├── README.md
    ├── CHANGELOG.md
    ├── KNOWN-LIMITATIONS.md
    └── compatibility.json
```

The checked-in Auction project under `src/test/resources/auction/` is an acceptance
fixture. It is not a claim of generic support for every JaCaMo application.

## 4. Architecture

```text
.jcm + .asl + Artifact Java + Moise XML
                  |
        static discovery/extraction
                  |
          semantic intermediate model
             /                 \
    frozen Mapping V1       constraint extraction
             \                 /
       USE structure/state + OCL + trace
                  |
       offline verification/reporting
                  |
 configured in-process runtime connectors
                  |
 ordered events -> USE mirror -> event-driven OCL
```

The plugin is a verification mirror. JaCaMo remains the execution engine. Runtime
verification reports violations; it does not block actions or act as a control agent.

## 5. Plugin lifecycle

`useplugin.xml` registers the plugin, `jacamo status`, a Status menu action, and
`Open Workbench...`. `DefaultJaCaMoFacade` owns the active imported workspace.

1. Import a `.jcm` entry.
2. Discover and statically parse the supported Jason/CArtAgO/Moise sources.
3. Load project-root `binding.json` when present and resolve exact typed references.
4. Load Mapping V1, build the verification semantic layer, and plan instances.
5. Load core OCL, optional project case OCL, and optional user OCL.
6. Materialize a USE `MSystem`, build trace/constraint registries, and run a full check.
7. Optionally configure in-process runtime connectors through the service API and connect.
8. Mirror authoritative snapshots and ordered deltas into the same active workspace.

The workbench exposes Import, Rebuild, Load OCL, Run Full Verification, report export,
runtime state controls, and six inspection tabs. It does not contain connector endpoint
configuration and does not launch a standalone external `.jcm` application.

## 6. Ecore and Mapping V1

The reconstructed baseline contains 37 EClasses, 67 declared EAttributes, 63
EReferences, and 14 inheritance edges. Three visible source fields remain annotations
rather than invented EAttributes because their datatypes are not evidenced.

Mapping V1 schema `1.1.0` covers all structural identities and seven projections:

| Projection | Current contract |
| --- | --- |
| VP001 | Conditional concrete Artifact subtype template |
| VP002 | Conditional observable-property attribute template |
| VP003 | Conditional concrete operation template with complete signature |
| VP004 | Reuse ExternalAction-to-AbsOperation structural binding |
| VP005 | Reuse ObsProperty-to-Belief structural binding |
| VP006 | Reuse organisational-goal-to-Goal structural binding |
| VP007 | Preserve Norm structure/labels without deontic-to-OCL equivalence |

VP001–VP003 are conditional templates. Their presence in the mapping does not by
itself prove a concrete production binding. VP004–VP007 reuse structural anchors and
do not add unproven runtime or deontic semantics.

## 7. Static and OCL pipelines

The static importer uses `.jcm` as the entry point and does not execute imported Java
classes merely to inspect them. Unsupported or ambiguous syntax becomes a diagnostic.
Production import reads `<project-root>/binding.json`; valid bindings select only among
existing exact typed candidates. Invalid, duplicate, wrong-kind, malformed, or stale
bindings fail explicitly.

OCL is separated by provenance:

- core profile shipped by the plugin;
- conservative translations of supported expressions;
- project case policy at `verification/<project-id>.ocl`;
- optional user profile selected from the workbench/service API.

Norm preservation is not full deontic-to-OCL translation. Authored Auction policies
are case-study rules, not automatically inferred universal JaCaMo semantics.

## 8. Runtime pipeline and v1.0.1 fixes

Runtime support is for configured, real in-process Jason/CArtAgO/Moise component APIs.
Initial synchronization subscribes, captures an authoritative snapshot, replays safe
post-snapshot deltas, then enters `LIVE`. Disconnect becomes `STALE`; reconnect/resync
requires another authoritative snapshot.

**P1 FIXED:** import, rebuild, and user-profile load use one workspace-install path.
When already connected, the old event stream is drained, exact matching runtime aliases
are transferred, mutation and verification consumers are replaced together, and the
connected transport is synchronized before returning `LIVE`. Failed builds keep the old
workspace. Snapshot replacement failure enters `ERROR` and disconnects rather than
silently mixing old consumers with a new model.

**P2 FIXED:** `StaticProjectImporter` now loads project-root `binding.json` and passes it
through the production `SemanticResolver`/`ExactSemanticResolver` path. The facade uses
that importer. The selected canonical identity reaches semantic references and trace.

## 9. Build and run

Requirements: JDK 21 and Maven. From the repository root:

```powershell
mvn --batch-mode clean verify
```

The release ZIP and SHA-256 sidecar are generated under `use-plugin/target/`. For a
focused mapping gate:

```powershell
mvn --batch-mode -pl use-plugin -am '-Dtest=MappingTransformationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Install by extracting the release ZIP into a compatible USE 7.5.0 installation,
providing the pinned JaCaMo component libraries on the USE JVM classpath, and checking
`Plugins > JaCaMo > Status` or `jacamo status`. Automated tests cover packaged plugin
discovery and child-JVM loading; interactive installed-distribution GUI execution remains
a manual environment check.

## 10. Auction acceptance workflow

The checked-in fixture supports deterministic static import, generated USE/OCL/trace,
offline positive/negative verification, and an in-process live scenario using real
component APIs. The live test exercises runtime events, a traced violation, operation
failure, disconnect, reconnect, and zero-drift authoritative resynchronization.

It does not launch the full application from `.jcm`, execute the Jason plan end-to-end,
or load the checked-in Moise XML as the runtime OS. The XML remains static provenance;
the live Moise scenario constructs a real programmatic OS/OE subset.

## 11. Limitations and next direction

Authoritative limits are in [KNOWN-LIMITATIONS.md](../../KNOWN-LIMITATIONS.md). In
particular, arbitrary Java effects, unsupported language syntax, complete normative
lifecycle semantics, cross-toolchain reproducibility, generic dynamic-entity invention,
and runtime enforcement are not claimed.

Reasonable next work is evidence expansion, not retroactive widening of v1.0.1 claims:

- add an explicitly designed external JaCaMo launcher/configuration workflow if required;
- validate more OS/JDK/component combinations;
- add larger-project and long-duration runtime measurements;
- add per-projection executable research checks where the source evidence permits;
- design explicit semantics before any deontic translation or enforcement feature.

## 12. Documentation map

Documents `01`–`18` describe active scope, architecture, implementation contracts,
quality, evidence, and limitations. [19-roadmap.md](19-roadmap.md), [task-01.md](../agent/tasks/task-01.md),
and `docs/superpowers/plans/` are historical execution records, not current status.
[HOTFIX-1.0.1.md](../../release/HOTFIX-1.0.1.md) and [report.md](../../../docs/report/report.md) retain release evidence; the
current synchronization inventory is [DOCUMENTATION-SYNC-v1.0.1.md](DOCUMENTATION-SYNC-v1.0.1.md).

## Runtime development after baseline

[Runtime research pack](../research/jacamo_runtime_research/README.md) and [Phase 16 reconciliation](../research/jacamo_runtime_research/IMPLEMENTATION_RECONCILIATION.md) distinguish pinned implementation from upstream proposals. Follow [active tasks](../agent/task.md) and [roadmap](19-roadmap.md). The version/test counts above describe the retained v1.0.1 baseline, not subsequent development revisions.

## Current engineering closure

See [Phase 27 hardening](phase27-hardening-audit.md) and the
[final acceptance matrix](phase28-project-closure.md) for current scope and evidence.
Earlier phase test totals and draft/temporary-target descriptions are historical.
The final structural target is unchanged V1; Runtime Mapping V1 is frozen.
Final user acceptance remains separate from autonomous engineering verification.
