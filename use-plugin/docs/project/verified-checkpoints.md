# Verified checkpoints

The main use4Goal workbench has one execution path: **Prepare + Import**, then
**Start Runtime**, using verified checkpoints. Supply the real project and its
admitted CSL, iStar, semantic mapping XML and optional OCL. There is no passive
mode selector in this workflow. External observation compatibility APIs do not
grant control over an independently launched producer.

The producer loads a versioned `CHECKPOINT_POLICY/1.1` derived from admitted
source requirements and native Task identities. Mapped terminal plan steps with
an already bound artifact select Task POST cuts. Their domain operation failures
also hold; source-domain operation completion is used where no such Task terminal
is admitted. Observation-only workspace construction and framework bookkeeping
are evidence providers, not business milestones. Its source identities, selectors,
program/input hashes and required native-check inventory are persisted alongside
the owned launch evidence. Swing milestones do not select stop points.

At a selected source completion, JaCaMo closes admission. Work already admitted
settles or yields at a supported await boundary. Causal queued work and normative
notifications drain before READY. The actual frozen cut may therefore be later
than the requested event; both identities are recorded. Jason cycles, CArtAgO
operations/external sessions, NPL transitions and timers share this barrier.
Logical source time stops only after quiescence. Artifact waits, Jason `.wait`
and NPL deadlines resume against that same clock.

USE accepts the exact source watermark prefix into the existing authoritative
native MSystem, then waits for its native Goal/Task/OCL result. Applicable required
checks must all pass. The interactive workbench renders that exact evaluated cut
before sending approval; presentation failure or a detached view retains a hold.
The live state selector advances through source checkpoint cuts, independently
of disk audit-history loading. Each exact execution context has a separate
presentation timeline and retained navigation position. Unrelated/unbound cuts
cannot replace its qualified Entity state. Switching scopes or CSL/iStar tabs
preserves the timeline mode and numbering. The final settled native completion
is also displayed after source release. Native invocation checks evicted from
the RAM tail are recovered from the checked persisted prefix.
Whole-MAS admission checks remain explicitly
labelled when inspecting one execution. A future pending goal is progress; an applicable failed,
unknown, missing, skipped or erroneous check retains the hold. Publisher, socket
control and durable disk ingress keep running while execution is held.

**Acknowledge & Continue** is available only for the active reviewed token and
result. It records an override and waits for the producer receipt. It does not
change FAIL to PASS, repair an artifact, erase evidence, or approve another cut.
Selecting an old state in history cannot retarget that button. Existing Jason
Pause/Resume controls retain their cycle-only meaning and cannot release this
barrier.

Commands authenticate the complete canonical body. Release binds session, run,
generation, revision, policy, token/sequence/epoch, execution scope and source,
native-cut and result hashes. Duplicate command IDs return their original
receipt; stale/foreign identities are rejected. Timeout, source drift, missing
evidence, persistence/protocol error and disconnect retain a hold. Reconnect
reconciles the held result; it does not upgrade an earlier failure. Stop cancels
the owned runtime and never counts as approval.

In strict mode, closing the workbench or losing the USE process leaves a started
producer held with its TCP endpoint/evidence alive. Use **Stop JaCaMo** explicitly
when termination is intended. A producer that has not completed startup is still
cancelled by the existing startup lifecycle. Passive-mode ownership is unchanged.

Checkpoint audit uses the existing native hash-chained journal; verified-mode
stores are retained on monitor retirement/Stop. The producer additionally retains
checksummed request/frozen-cut/decision/review/release records in its spool. Export
the native replay before closing the workbench to obtain a portable, hashed bundle.
Replay validates checkpoint evidence without sending any live control commands.

The default retained native journal quota is 512 MiB, also used by the bridge
launcher. Advanced controls can set an explicit quota for the next run. Exceeding
it remains a persistence/coverage failure and holds execution; evidence is not
discarded to manufacture completeness.

Per-execution histories are immutable projections of the same authoritative
MSystem. Execution admission remains whole-MAS when physical agents are shared;
separate histories do not imply separately runnable schedulers. Source coverage
can remain INCOMPLETE while an execution's supported observed requirements are
COMPLETE_OBSERVED; neither is converted into the other.

Support is deliberately bounded to an owned local, source-admitted MAS with the
pinned patched dependencies. It is not arbitrary Java isolation or distributed
execution suspension. Unsupported custom schedulers/providers, raw thread/clock
producers and unsupported internal actions fail strict preflight. The agent cohort
is static; dynamic creation, code loading and external reasoning mutation are
rejected after admission. The web mind inspector is disabled in strict mode.

The runnable distribution is `use-assembly/target/new/bin/start_use.bat`. Its
launcher generates an installed classpath and verifies pinned JAR hashes from
`bridge/dependency-lock.json`; it does not depend on the sibling checkout replacing
an official Maven cache entry. Source build/provenance instructions are in
`../../../../jacamo/checkpoint-dependencies/README.md`. Executable acceptance and
its limitations are recorded in `../../verification/verified-checkpoints` and
the workspace `use/plan.md`.
