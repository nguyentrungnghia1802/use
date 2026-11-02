# Supported source boundary after dependency patches

This supersedes the capability conclusion of the historical `S01-boundary.md`
for the newly pinned checkpoint binaries only. It does not change the meaning
of the existing Jason Pause control or claim support for arbitrary Java/MASs.

One owned local MAS shares one execution admission ledger. Jason local cycles,
CArtAgO operation steps/external sessions and NPL transitions/notifications enter
before platform locks. Already admitted operations drain or yield at declared
artifact await boundaries. Reserved queued work and causal child notifications
drain even after admission closes. New work waits outside source locks. READY
requires no active steps or pending admitted work. Unsupported source providers,
raw threads/clocks/timers, custom schedulers/agent classes and remote workspaces
are rejected by strict preflight. The supported source profile is conservative
and is not an isolation sandbox for arbitrary hostile Java code.

Source time freezes only at READY, after drain. It is distinct from audit/control
wall time. CArtAgO `await_time`, Jason `.wait` and NPL obligation deadline scheduling
use this same logical clock. Admission closure and the final frozen boundary are
recorded separately. No network/USE wait occurs under a source or Swing lock.
Disk publisher, authenticated TCP control and durable ingestion remain alive.

`S01-final-probes.log` and `S01-extended-runtime/positive-proof.txt` execute real
CArtAgO operations and external sessions, two real Jason agents and an actual NPL
obligation with a two-second deadline. The probe waits for an in-flight latch,
proves READY cannot be reached before that work settles, then holds for 2.5 wall
seconds. Agent cycles, artifact mutations, actual `.wait` and NPL deadline expiry
remain held; the source clock is unchanged. Exact release resumes them. Guard
awaits also yield safely. Separate child processes prove queued causal drain,
notification drain, timeout hold, unsupported admission and Stop cancellation.

The source-level coordinator tests cover required unknown/error/skipped/pending,
future pending goals, stale/foreign/duplicate commands, persistence loss, drift,
timeout/disconnect, immutable original failures and exact reviewed release.
Authenticated socket tests cover body tampering and nonce replay. See
`S02-final-source-tests.log` and the focused protocol/authentication logs.

The runtime owns a static admitted agent cohort. Dynamic agent/code mutation is
unsupported after admission. A non-yielding in-flight source operation prevents
READY and retains a hold; cancellation is explicit Stop, never forced thread
suspension or an automatic approval. The source overlays and original binary/
source digests are recorded in `jacamo/checkpoint-dependencies`. Each patched
dependency and JaCaMo itself has distinct local Maven coordinates. Their actual
loaded bytes are checked independently of editing sibling source files.

This is source/runtime-probe evidence. Live Auction, offscreen Swing, installed
loader/JAR provenance and any fresh manual desktop exercise are separate gates.
