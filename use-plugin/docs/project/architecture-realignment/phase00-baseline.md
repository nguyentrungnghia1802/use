# Phase 0 baseline

Phase 0 passed on 2026-09-26. The implementation continues on
`phase/00-08-architecture-realignment` from USE revision `deba0152`; no reset,
checkout, clean, or overwrite was used. All pre-existing parser, canonical-case,
order-evidence, task-document, and line-ending changes remain in place.

The four frozen hashes match their audited values byte-for-byte. The USE reactor
reproduced 12 passing core tests, 1 passing GUI test, and 228 plugin tests with
exactly the three audited failures and no errors or skips. JaCaMo was tested from
the detached worktree `C:/Windows/Temp/jacamo-phase0-01a0dcbe`; all 6 tests passed.
The authoritative JaCaMo checkout retained the same dirty-state fingerprint before
and after, and its 45 dirty paths have no content difference when end-of-line
changes are ignored.

The resolved runtime graph is JaCaMo 1.3.1, Jason 3.3.2, CArtAgO 3.1, Moise 1.1,
and NPL 0.6.1. Exact commands, revisions, hashes, failure identities, and source
entry hashes are in `phase00-baseline.json`.
