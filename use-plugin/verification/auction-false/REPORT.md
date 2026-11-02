# auction-false — implementation receipt

## Scope

`auction-false` is a copied application case.  Its only behavioural change is
the removal of `running=false` from `AuctionArtifact.stop()`.  The native CSL,
iStar, OCL, ASL, MOISE, checkpoint policy, verifier and production mapping
logic are unchanged; the copied XML only pins the new Java-source SHA.

## Evidence obtained on 2026-11-02

1. `copy-manifest.json` records the two source copies and proves that the
   application change is limited to `AuctionArtifact.java` (plus the required
   source-hash pin in the USE mapping).  The false source SHA is
   `f80f98185518348b3d62656aba6856fe6f2e20514e8f90ace29a6d07ac971563`.
2. `healthy-artifact.json` and `false-artifact.json` are focused real CArtAgO
   operation probes.  Both `stop()` invocations succeeded.  The healthy class
   ended with `running=false`; the false class, loaded from `false-classes/`,
   ended with `running=true` while preserving a real winner.  The false class
   SHA is `8ace613431c7c8e8094627211b8d27c4a443e5395c432323be392664d5a42ffe`.
3. `static-inputs.json` accepted all five authored inputs with program hash
   `4defdfc5aeb49463bc4e5b46a9e17edf8e309c4fcfe3dcf8e0ffebdabdbfc0be`.
   The strict source-admission rule was corrected generically so qualified
   provider checks apply to executable Java only; dotted declarative model keys
   are still parsed by their native model parsers.  The focused
   `GenericSourceAdmissionTest` passed.
4. `live-direct/held.json` is a real owned USE/JaCaMo run.  Valid checkpoints
   auto-released; the faulty operation then held at
   `task-post:execution-steps:A_decide_t2` with state `HELD_FAILURE`, reason
   `APPLICABLE_CHECK_FAILED:APPLICABLE_NATIVE_CHECK_FAILED`, and exact token
   `2a2349c9-a077-407c-9d00-8e7d16bfa431`.  Its admitted `src-artifact` hash is
   the false source SHA above.  The immutable held snapshot records
   `running=true`, the frozen barrier and result/use hashes.
5. `AUCTION_FALSE-final-package-receipt.json` proves the final installed
   package contains the example, loads the packaged plugin, and loads
   `bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar` with SHA
   `dede2e2cde568c747a774e33f821cdcee6501515997a710816aa9c02c7c901f5`.

## Deliberate evidence boundary

This run intentionally stopped at the first reviewable failure and did not
press **Acknowledge & Continue**.  Therefore it proves the automatic
continue-to-failure path and fail-closed hold, but does not claim a fresh
manual acknowledgement/history exercise for this new case.  The existing
generic checkpoint desktop evidence remains separate.  `live-direct` also
contains a stale `replay-error.txt` from the rejected pre-admission attempt;
it is retained rather than removed and is not used as a successful replay
claim.

No commit or push was performed.
