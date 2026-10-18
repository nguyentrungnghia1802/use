# Plugin UI and User Workflow

Current Workbench contract, 2026-10-18 (semantic engine migration Phase17). Earlier screenshots and phase reports
describe their original revision; they are not the current UI contract.

## Build and launch

From the repository root:

```powershell
Set-Location 'D:\_CODE_BANK\Project_\08_Thesis\use'
$env:JAVA_HOME = 'D:\DevTools\Java\jdk-21.0.5'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
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

For the original CSL/iStar engine, supply a project-authored **MappingRuleSet
2.0** matching the official source declarations. Add
`-SemanticProfile USE4GOAL_OBSERVED_V1 -MappingSpecificationPath 'C:\path\rules.json'`
to that same helper command. The helper passes the selected profile/specification
to USE, not the JaCaMo producer. Legacy CSL_OBSERVER_V1/V2 accept their existing
configuration format. NATIVE_CURRENT remains the default. A .csl or .istar file
alone is not a complete JaCaMo mapping: rules also declare exact source bindings,
root/episode policies and any operation contracts. No engine is copied into
use4Goal and no USE 7.1.1 launcher path is involved.

## Primary workflow

1. Open `Plugins > JaCaMo > Open Workbench...` and use **Import JaCaMo Project...**.
   Select the engine under **On next Import**, with the authored JSON specification
   path when required. Editing these widgets has no effect on an installed live
   owner. Import validates a new selection on its worker and freezes accepted
   specification bytes for resync/monitor restart; Import again to accept file edits.
   An invalid selection leaves the old native system and engine owner installed.
   The launcher can still supply its derived JCM using the one-shot auto-import.
   Import checks exact project identity/digest against the official Bridge snapshot
   and loads the generated model into the active USE Session.
2. Inspect the native Model Browser/Class Diagram/Object Diagram and the independent
   **Projection Rules** tab. The table has three read-only columns: **Rule**,
   **JaCaMo Concept**, **USE Concept**. All catalog mappings remain unchanged;
   fidelity, implementation/capability status and diagnostic policy remain in tooltips.
   Catalog entries describe the mapping contract, not proof of live capability.
   **Program / trace** shows the accepted mapping/version, canonical program,
   CSL/Goal/source hashes, original validator admission and source-target bindings.
   Authored abstraction/unsupported diagnostics remain visible there.
3. Use **Load OCL...** to compile/install authored OCL with the existing profile
   infrastructure. Import remains a prerequisite. Runtime classes still need their
   existing authoritative materialization before a profile can refer to them.
4. Use **Start Runtime** at the managed startup boundary. The original facade call,
   startup ACK, authoritative resync and eligibility guards are unchanged.
5. Evaluate/check OCL through native USE OCL evaluation and invariant/check views.
   Workbench has no separate verification table, result checker or manual verify
   action. The existing runtime verification/checkpoint infrastructure remains active.
6. **Semantic verification** shows native constraints/invocation conformance,
   **Original iStar history**, legacy requirements (only for those profiles), and
   coverage/decisions. Original leaf and propagated statuses are shown separately,
   including full binding stacks, exact episode identities and observation quality.
   Original NONE/PENDING is not relabeled VIOLATED. Historical Task marking does
   not replace the per-invocation @pre contract verdict. An empty/pending occurrence
   table explicitly means no occurrence verdict is available, not PASS.
7. **Stop monitor** closes USE observation/engine resources and retains the last
   native model/state as stale for diagrams/manual OCL. It does not kill JaCaMo.
   **Restart monitor** reconnects with fresh monitor history; it does not restart
   the external producer or send an invented protocol command. Producer run UUID
   replacement is handled separately at Import/reconnect. The workflow tooltip
   distinguishes a managed startup ACK from an unowned external process.

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
is restored wholesale. The bounded Semantic verification and Program / trace views
above display immutable backend evidence without becoming another state authority.
Projection Rules is retained independently of Diagnostics.

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
