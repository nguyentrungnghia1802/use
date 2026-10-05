# UI inventory — snapshot verification migration

## Current disposition after migration — 2026-10-05

Five primary tabs: Project, Verification, Goal View, Trace / Source, Diagnostics.
Connection/startup stay facade-owned; violations/checkpoints/control and bounded
immutable comparison are in Verification. Goal tree/detail reads the active native
or selected replay coordinator. Raw runtime/replay and projection rules are
secondary Diagnostics views. BindingResolutionPanel, binding facade APIs, Skeleton
facade and the primary raw runtime dashboard are removed; status/menu consumers
share one Session facade. One cached UI refresh timer detaches on close/reopen.
Native Model Browser/Object Diagram/OCL views use the same active Session system.
Source navigation labels unavailable positions explicitly. Resume requires all-ACK,
connected capable PAUSED control and a fresh synchronized cut, without control error.

The Phase A component table below is **historical baseline/proposed disposition**,
not a claim that old components remain production consumers. Current GUI/test
evidence and screenshots are indexed in task.md section 23.

2026-10-05 audit of JaCaMoWorkbenchPanel, its facade consumers and menu actions.
These are proposed dispositions, not claims that redesign is implemented.

| Component | Current purpose | Data source / owner | Target status |
| --- | --- | --- | --- |
| Project tab | Import/rebuild/export, static model summaries and manual verification | DefaultJaCaMoFacade / native or legacy Workspace | REWRITE: project/model compatibility and runtime sync status; secondary debug hashes |
| Mapping Inspector tab | Source semantic ids, trace rows, diagnostics | facade.traces and TraceIndex; source open action | MERGE into Trace / Source; retain exact file/span and native/runtime target |
| Mapping Rules tab / MappingRulesPanel | Frozen V2 mapping-resource explanation | Frozen mapping rules resource, not active native policy | REMOVE from primary current workflow; historical evidence remains labelled |
| Verification tab | Current constraint list/result and detail | facade verification snapshot/profile/current native result; legacy report branch | REWRITE to checkpoint + violations + retained failing state + control state |
| Runtime tab | Connection/actions, raw capabilities/counters/history, replay controls | facade RuntimeStatus / AuthorityStatus / RuntimeHistoryPage | MERGE current verification status into Verification; debug/history/replay secondary |
| BindingResolutionPanel dialog | Explicit legacy binding compatibility | facade binding resolution/export service | REMOVE after active legacy consumer migration; trace evidence belongs in Trace / Source |
| Diagnostics table/text | Connection/projection/verification messages | facade diagnostics and result diagnostics | MERGE unique error source; add typed control errors separate from OCL FAIL |
| Workbench status refresh timer | One-second cached UI refresh | facade only, no network resync | KEEP one cached refresh mechanism or replace with result notifications; detach on close |
| Run Full Verification / Run Native Check | Manual invariant run | facade.runFullVerification / coordinator.manualVerify | REMOVE from primary live workflow when checkpoints route automatically; retain explicit offline inspection only if consumer remains |
| Connect / Disconnect / Reconnect / Resync | Observation lifecycle | facade bridge synchronization | KEEP lifecycle ownership; distinguish sync state from runtime pause; resync is not an invented boundary |
| Start Runtime | Releases managed official startAgs boundary | ManagedRuntimeWorkflow / acknowledged startup request | KEEP; is not Resume and does not establish pause semantics |
| Export replay / reanalysis / step navigation | Explicit immutable offline evidence workflow | facade native replay services | KEEP secondary inspection with live delivery detached |
| USE Model Browser / Object Diagram / Invariant view | Native model/state/formal result visualization | Same Session MSystem, native atomic state events | KEEP; Workbench must not duplicate their private state |
| Goal View | Absent | No current production consumer/model | ADD facade view over active MSystemState and trace; no duplicated Goal objects |
| JaCaMoStatusAction/Command / SkeletonJaCaMoFacade | Skeleton status output/menu compatibility | Skeleton facade, independent from imported active workspace | REMOVE or migrate current action consumer after registration audit |
| JaCaMoWorkbenchAction | Opens facade-based workbench for USE session | Workbench/facade/session ownership | KEEP; reopening must not subscribe twice |

Current tabs are Project, Mapping Inspector, Mapping Rules, Verification, Runtime.
Binding is a compatibility dialog, not an independent tab. No Goal View and no
production Pause/Resume controls exist. Primary Runtime tab repeats current
verification outcomes and exposes low-value transport/session/debug details.
Verification, Runtime and diagnostics render related result information in
separate places; the redesign must converge on one typed current/violation view.

Workbench's RuntimeStatus, PerformanceMetrics, diagnostics and trace table read
facade-owned cached records. Native VerificationSnapshot is a committed
coordinator result/profile read from the active workspace, but does not include
the current domain objects required by Goal View. The explicit legacy branch
still has its own DirectUseBackend/VerificationReport consumer. Mapping Rules
and the metamodel baseline field expose frozen compatibility internals; move
these out of the primary live workflow. No current UI action was found that
invokes official JaCaMo internals directly. Skeleton status actions are still
registered in useplugin.xml, so they need migration, not deletion as uncalled
code. Manual full/native checks remain active and will require migration to
checkpoint-driven live behavior.

UI semantic actions delegate to facade/service APIs; no official JaCaMo control
call exists here. The cached timer starts/stops with panel lifecycle. Network
subscription ownership belongs to facade and BridgeClient; reopening the panel
does not install a second source listener. Existing tests cover this lifecycle;
the new Goal View, violation auto-selection, snapshot comparison and Resume
enablement remain unimplemented.

Native atomic state notifications feed Object Diagram and other normal USE
views. The baseline actual Auction GUI test verifies all objects/links visible
in the same active system before/after resync. Screenshots and full inventories:
`use-plugin/target/workbench-acceptance/auction-1791171951433`. Baseline commands
and PASS counts are indexed in `runtime-current-inventory.md` and task evidence.

Do not delete a panel before its production menu/dialog/facade/test callers are
migrated. No UI removal has been performed during the audit or pending runtime
control decision.
