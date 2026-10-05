# Plugin UI and User Workflow

Current snapshot/Goal workflow, 2026-10-05. Historical screenshots and phase
reports describe their original revision; they are not the current tab contract.

## Build and launch

From the repository root:

```powershell
Set-Location 'D:\_CODE_BANK\Project_\08_Thesis\use'
mvn -B -pl use-plugin -am '-DskipTests' package
& '.\use-plugin\tools\jacamo-bridge.ps1' `
  -JcmPath 'D:\_CODE_BANK\Project_\08_Thesis\jacamo\examples\auction\auction.jcm' `
  -InteractiveGui -Headless:$false -TimeoutSeconds 1800 `
  -EvidenceDirectory '.\use-plugin\target\gui-demo' -SkipBuild
```

The package phase stages the byte-identical shaded plugin in `use-gui/lib/plugins`.
The generic helper prepares a derived copy of the selected project and starts the
separate JaCaMo producer and configured USE GUI. Original sources are unchanged.
Other projects use the same `-JcmPath` interface. Native JaCaMo windows depend on
project configuration; `-InteractiveGui` opens USE Workbench. `MODEL_READY` is
static readiness, not evidence that agents are reasoning or verification is LIVE.

## Primary workflow

1. Open `Plugins > JaCaMo > Open Workbench...` and import the derived `.jcm`
   selected by the helper. Selection asserts exact project key/digest against the
   official Bridge snapshot; USE does not reconstruct JaCaMo from source text.
2. Inspect Project and the native Model Browser/Class Diagram/Object Diagram.
   Start Runtime releases the managed startup boundary. It is different from Resume.
3. Connect/authoritative synchronization enters LIVE. Verification checkpoints run
   automatically; no manual repeated live check is needed.
4. Load authored OCL and inspect its compiled constraint/capability policy.
   All constraints, including native HARD rules, default REPORT_ONLY. Severity
   alone grants no control authority. Explicitly approved HARD false conditions
   may pause; ERROR/UNDEFINED/SKIPPED do not gain automatic control authority.
5. A violation selects Verification and the exact failing Goal. Inspect the retained
   failing values, previous/last-passing cut, authoritative confirmation, related
   exact evidence objects and Trace / Source. Open the native Object Diagram from
   the same active Session if needed.
6. Explicitly choose **Resume Jason agents** after inspection. Re-enable ACK and
   authoritative resync must finish before LIVE returns. There is no auto-resume.

## Five primary tabs

| Tab | Current purpose and source |
| --- | --- |
| Project | Imported model, projection compatibility, separate connection/sync lifecycle and runtime startup/connection actions |
| Verification | Committed checkpoint/result, approved constraint policies, bounded violations, immutable failure/confirmation comparison and control state/Resume |
| Goal View | Scheme specification versus exact runtime instance, nested goal operator/ordinal, available typed runtime state, mission/agent/group responsibility, violation/source details |
| Trace / Source | Semantic/runtime identity, USE target, mapping rule, exact available source position and bounded source excerpt |
| Diagnostics | Connection, identity/projection/capability, OCL and control errors; secondary runtime details, recorded replay and projection-rule inspection |

Old Binding resolution and the primary raw Runtime/dashboard tabs were removed.
Historical parser binding files are not production identity input. Ambiguity fails
closed with diagnostic evidence; the UI never ranks candidates or guesses targets.
Debug hashes/events and replay controls are secondary to the verification workflow.

## Control and evidence semantics

Synchronization states remain OFFLINE / MODEL_READY / CONNECTING / SYNCING / LIVE /
STALE / ERROR. Control states are RUNNING / PAUSE_REQUESTED / PAUSED /
RESUME_REQUESTED. PAUSED is labeled **Jason agents paused** only after all current
admitted agent incarnations ACK an official ExecutionControl reasoning boundary.
This is not atomic whole-platform suspension: already in-flight CArtAgO operations
and Moise/OrgBoard activity may complete. The console-output Pause API and Swing
button automation are not used.

The failing cut is frozen before the request. A separate authoritative post-pause
cut is checked and labeled CONFIRMED, TRANSIENT_NOT_REPRODUCED or
CONFIRMATION_ERROR. Control failures are separate from OCL FAIL. Missing ACK,
stale/disconnected state or failed confirmation disables Resume until authoritative
recovery. Disconnect does not secretly resume the producer.

Moise 1.1 public `goalState` supports waiting/enabled/satisfied. Impossible is
unavailable through that API; missing/currently stale evidence is UNKNOWN /
UNAVAILABLE, not a fabricated state. Last-observed values are explicitly labeled.
Native source positions are shown only when extraction provided them. Otherwise
navigation shows file + semantic element + **line unavailable**, never line 1 by
assumption. CASE-selected extra Goal/Artifact context requires exact identities and
explicit domain rationale; it creates no inferred domain relation.

## Replay and lifetime

Recorded replay/reanalysis is an explicit detached workflow. Step replay displays
the selected active replay coordinator's Goal state and recorded verification,
without live control buttons or original-workspace violations leaking into it.
Recorded hashes, capability/policy timeline, boundaries and native PRE/POST `@pre`
are validated. Reanalysis states its changed verification profile explicitly.

UI components call facade/services only. Their cached refresh timer does not poll
JaCaMo semantics. Closing/reopening the Workbench detaches/reuses one facade for the
Session and never installs duplicate runtime subscriptions. Project replacement
retires old consumers/callbacks before accepting a new authoritative workspace.

Current headful/component/replay and three-case evidence is indexed in
`docs/agent/task.md`. Archived `evidence/final-system-acceptance` reports are historical.
