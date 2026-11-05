# Scoped population evidence and verification coverage

## Boundary and semantics

The existing admitted local MAS barrier remains authoritative: close execution
admission, drain in-flight/causal work, freeze the supported source clock, then
capture a watermarked source snapshot. This change does not alter the barrier,
checkpoint selection, Goal/Task/OCL predicates, release protocol or manual review.
No network or USE wait is added under a source, artifact, scheduler or Swing lock.

Current population closure is a statement about an explicit source and fact kind
at that exact captured watermark. It is not proof of continuous history. Agent
inventory must come from the actual runner and match every faithful agent fact;
missing, duplicated, foreign or stale entries cannot become complete. A mapping
certificate also binds program, epoch, source population and current cut. An old
certificate cannot repair a newer unqualified event-prefix cut or replay.

Snapshot data coverage and historical source coverage are reported separately.
Native results remain unchanged by display classification: an evaluated False has
known data; unknown/error/missing required data remains incomplete and fail closed.
All retains native scopes and historical failures. Group population constraints
stay on their authored group. WithinDeadline remains explicitly inactive.

## Gates

- [x] Versioned authoritative Jason agent inventory and strict consumer decoder.
- [x] Mapping population certificates for agent-derived contexts, including exact
      target reconciliation; missing/duplicate/stale population tests.
- [x] Native compatibility and group population tests, genuine upper-bound failure,
      role/agent lifecycle, and scope isolation without case vocabulary in production.
- [x] Verification data coverage UI with raw history/source diagnostics preserved;
      existing failure, All and repeated exact-token review semantics retained.
- [x] Focused build/tests and unchanged external Auction live acceptance.
- [x] Refresh target/new, verify actual loaded/pinned runtime and plugin identities.
- [x] Commit/push only tested source and compact evidence; preserve local raw logs,
      replays, generated layouts and unrelated Draw.io backups.

No gate is complete until its evidence is recorded. Fresh manual desktop/Computer
Use is outside this implementation acceptance unless separately authorized.

Source delivery: 39318f4bfaa1d9045d0451e361de2ddd6c93a570 was pushed to origin/main
and the remote branch hash was independently checked. This checklist closeout is
documentation only; it does not change the tested/installed production artifacts.
