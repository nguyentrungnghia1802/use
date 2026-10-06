# Plugin UI and User Workflow

Current Workbench contract, 2026-10-06. Earlier screenshots and phase reports
describe their original revision; they are not the current UI contract.

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
The helper prepares a derived copy and starts the separate official JaCaMo
producer and USE GUI. Original project sources are unchanged. Other projects use
the same `-JcmPath` interface. Native JaCaMo windows depend on project configuration;
`-InteractiveGui` opens USE Workbench. MODEL_READY is model readiness, not evidence
that agents are reasoning or the runtime is LIVE.

## Primary workflow

1. Open `Plugins > JaCaMo > Open Workbench...` and use **Import JaCaMo Project...**.
   The launcher can still supply its derived JCM using the one-shot auto-import.
   Import checks exact project identity/digest against the official Bridge snapshot
   and loads the generated model into the active USE Session.
2. Inspect the native Model Browser/Class Diagram/Object Diagram and the independent
   **Projection Rules** tab. The table has three read-only columns: **Rule**,
   **JaCaMo Concept**, **USE Concept**. All catalog mappings remain unchanged;
   fidelity, implementation/capability status and diagnostic policy remain in tooltips.
   Catalog entries describe the mapping contract, not proof of live capability.
3. Use **Load OCL...** to compile/install authored OCL with the existing profile
   infrastructure. Import remains a prerequisite. Runtime classes still need their
   existing authoritative materialization before a profile can refer to them.
4. Use **Start Runtime** at the managed startup boundary. The original facade call,
   startup ACK, authoritative resync and eligibility guards are unchanged.
5. Evaluate/check OCL through native USE OCL evaluation and invariant/check views.
   Workbench has no separate verification table, result checker or manual verify
   action. The existing runtime verification/checkpoint infrastructure remains active.

The small workflow/runtime status labels read cached facade state only. The timer
does not poll JaCaMo semantics, connect or resync. Import/OCL/start keep the existing
background worker, busy guard, EDT publication, detached-view guard and launch-time
ready evidence. Existing **Export .use...** and **Export .cmd...** remain available.

## Removed UI and preserved dependencies

Project, Verification, Goal View, Trace / Source and Diagnostics views and their
exclusive table models, fields, filters, selections and listeners are removed.
The former Rebuild, Export Report, Export Replay, Recorded Replay, reanalysis and
Verify imported model actions have no Workbench buttons or handlers. GoalViewPanel
and RuntimeHistoryRows are removed; no replacement verification/Goal/diagnostic UI
is built. Projection Rules is retained independently of Diagnostics.

JaCaMoFacade/DefaultJaCaMoFacade, official import, domain projection, native model
generation/loading, exact binding/trace, runtime/control, OCL, snapshots and journal
remain core integration points. CLI report/replay exports and programmatic isolated
replay/reanalysis retain their APIs and regression tests. Immutable Goal and violation
data remain backend evidence; retiring a tab does not delete semantic/runtime state.

Native USE views continue to use the same active MSystem/MSystemState. Recorded
replay stays an explicit core API mode with an isolated read-only context; it does
not become a second live runtime. Pause/resume capability, all-agent ACK, failing
cut retention and authoritative confirmation remain unchanged. PAUSED means Jason
agents paused at reasoning boundaries; in-flight CArtAgO/Moise work may still finish.

Closing/reopening Workbench detaches/reuses one facade for the Session and installs
no duplicate subscription. Project replacement retires old callbacks before accepting
a new authoritative workspace. Current build, headful workflow and three-case
evidence is recorded in `docs/agent/task.md`.
