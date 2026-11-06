# Checkpoint transport, history and startup presentation repair

The existing source admission/drain/frozen-clock boundary is unchanged. Only an
authenticated decision for the exact active identity may release a cut. A lost
reply must be retried idempotently and reconciled against the original receipt;
errors must never become domain PASS. No network/verification waits may acquire
source/artifact/scheduler/Swing locks. Authored models/assertions remain unchanged.

- [x] Reproduce/localize checkpoint reply failure mechanisms with source
  logs, exact receipts and thread dumps; add a deterministic regression.
- [x] Remove repeated full semantic history decoding/re-evaluation while retaining
  required invocation failures/unknowns, disk corruption checks and original data.
- [x] Keep verified live presentation on source checkpoints from the first Start;
  retain passive diagnostics separately and avoid expensive EDT preparation.
- [x] Exercise ordering, missing evidence, lost replies, stale identities, repeated
  review, prefix eviction/corruption and startup presentation tests.
- [x] Run House Building repeatedly through native verification and real Swing
  workbench callbacks, measure startup EDT responsiveness, and regress Hello World
  and Auction as needed for the shared paths. No clean verify.
- [x] Incrementally refresh target/new, verify actual loaded runtime identities
  and rerun installed House Building through the live workbench.
- [ ] Commit/push proved work and report limitations.

Native Goal/Task/OCL semantics and their UNKNOWN/FUTURE distinctions are not
changed. No manual desktop/Computer Use acceptance is implied by offscreen tests.
The earlier missing-stack House Building occurrence cannot be assigned a unique
retrospective cause. Independent failing-before/passing-after reply regressions
are in the report; the original historical failure remains preserved.
