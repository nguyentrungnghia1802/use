# Current population certificates and verification coverage - 2026-11-06

## Implemented behavior

JaCaMo's actual runner now publishes a versioned JASON_AGENT_INVENTORY/1.0 with
the session, source watermark, unique physical agent identities and a canonical
digest. It is captured with the existing local source publication cut. It does
not claim continuous history or change the whole-platform barrier.

The mapping consumer checks the roster against every faithful agent fact at the
same watermark. A supplied certificate contradicted by missing, duplicate,
foreign or stale facts is rejected as JASON_AGENT_INVENTORY_MISMATCH. An older
snapshot without this new certificate remains unproven; it is not retroactively
upgraded. Unknown data does not turn into PASS.

For each supported mapping rule, the native writer records a versioned
MAPPED_CURRENT_POPULATIONS/1.0 receipt with its actual mapped members and source
authority. Population closure checks all declared rules and the complete native
object set, not just the remaining objects. Deleting a mapped object, keeping an
old unqualified context, changing the cut or losing a source row therefore cannot
produce a complete population. Receipts live on an existing M0 carrier and are
included in the MSystem metadata savepoint; rollback restores them with the state.
No additional world model, object or forced verdict was introduced.

Source-derived agent contexts can now participate in the same native OrgContext
population proof as the existing workspace/group contexts. The backend-created
CslV4RoleCompatibility predicate is unchanged. Its result becomes PASS only when
its bodies and its current population are actually proved by the native engine.
Generic production logic uses source kinds, mapping declarations and physical
identities, not case names, expected counts, Auction IDs or winners.

## Coverage and UI

Both CSL Verification and iStar Verification now visibly show Data coverage in
their footers. It is distinct from the verification verdict: a known False can
have COMPLETE data coverage. Required unknown/error evidence is INCOMPLETE.

At a checkpoint, this classification consumes the existing native admission
inventory. Its FUTURE Goal/Task checks remain future progress; the display does
not invent a second applicability policy or require future goals to be complete.
Per-execution native OCL rows retain their exact scopes. All retains original
results and historical failures. Diagnostic details/tooltips retain raw source
coverage, required evidence gaps, the admission inventory and the original trace.

Raw historical/global coverage is deliberately unchanged. A subsequently accepted
event-prefix cut without an inventory certificate cannot borrow the previous
frozen snapshot's proof. The final raw event-prefix result in this run therefore
still reports INCOMPLETE and may show the supporting compatibility aggregate as
SKIPPED. This differs from the eight verified frozen checkpoints, all of which
have a proved complete OrgContext population. The visible checkpoint history and
native hashes are retained; an unqualified later prefix is not rewritten.

The UI probe initially found the old coverage label inside a hidden legacy
container. The new labels were moved into visible footers of both actual live
verification facets. Final offscreen facet renders were inspected after this
layout correction. Native model/state diagrams and Goal/Task semantics are
unchanged.

## Models, scopes and lifecycle

The current Auction still has one shared group, one auctioneer, four participants
and two separately identified Auction/scheme executions. The two authored role
population constraints remain on auctionGroup. All does not add their counts or
invent a new system-wide upper bound. The authored CSL, iStar, XML and OCL bytes
are unchanged and their source/installed checksum checks pass.

WithinDeadline stays explicitly inactive by the user's prior decision. Timers,
operation admission, authenticated exact-token release, manual acknowledgement
and native assertion bodies were not altered by this change.

Lifecycle evidence distinguishes loss from authorized withdrawal. A new agent
without a published identity/state revokes source closure. A removed source agent
whose physical context has no admitted withdrawal mapping leaves that retained
context unqualified; the new certificate cannot hide it. It is not silently
deleted with a guessed cascade. Existing explicit logical-role reconciliation
still retires a removed role from a complete roster. Tests verify both behaviors
and savepoint rollback. Arbitrary unmanaged/distributed producers remain outside
the existing admitted local execution profile.

## Executed evidence

- 109 focused tests passed with zero failures, errors or skips in
  focused-build-current.log. This includes source inventory generation, strict
  contract decoding, mapped-output loss, stale/legacy certificates, rollback,
  actual native compatibility, source/role lifecycle, scope isolation, input hash
  guards, protocol authentication, missing-evidence holds, shared intentions,
  original failure retention, All and repeated review-button use.
- NativePopulationLimitTest materializes 301 actual native role objects and the
  native OCL upper-bound predicate returns FAIL. An authoritative complete roster
  subsequently withdraws one role, leaving 300, and the same predicate returns
  PASS. No expected result was injected into the engine.
- After the final footer correction, 22 focused UI tests were rerun successfully
  in ui-build.log. These overlap the preceding suite and are not advertised as
  22 additional distinct tests.
- One fresh unchanged external Auction run passed:
  20261106-004545-260-fe60069f. Eight checkpoints were presented and all eight
  have producer automatic-release receipts; both source-defined executions reach
  settled boundaries. No required unknown/error check or manual override occurred.
  This natural run happened to have no failed bid branch. Negative-domain and
  repeated-review behavior are covered by the focused tests, not a fabricated
  failure in this run.
- Each frozen checkpoint has native CslV4RoleCompatibility PASS with
  QUANTIFIED_NATIVE_INVARIANT;POPULATION_COMPLETE. All eight retain their original
  raw source coverage INCOMPLETE. The unchanged-source check passed.
- Independent producer-ledger reconciliation verifies the 8 release receipts
  against exact token/run/session/generation/revision/policy/scope, command IDs,
  accepted state hashes, native result hashes and combined inventory hashes.
  Every presented barrier is closed, drained and clock-frozen.
- Offscreen replay of those authentic cuts verifies COMPLETE data coverage at
  every checkpoint, both individual execution views and All. The final All and
  both execution verdicts are COMPLETE_OBSERVED in this successful run. The UI
  retains 8 audit cuts while presenting 7 grouped States. Both facet PNGs are
  offscreen evidence, not fresh manual desktop/Computer Use acceptance.

The first extended build caught a test method's missing checked-exception
declaration. A subsequent lifecycle test exposed accidental coupling between new
certificate qualification and the existing retirement flag. These were corrected;
the native count/removal assertions were retained. Earlier failed logs remain
local and are excluded from acceptance.

## Runnable package and delivery

The final source JARs are installed in use-assembly/target/new. Windows initially
locked a contract JAR. At the user's explicit request, the residual installed
JaCaMo producer was identified by its launcher and matching owned-runtime
manifest, then stopped through its existing stop.flag protocol. No force kill,
pause substitution, source edit or log/replay deletion was used. Packaging then
completed with staged atomic JAR replacements and retained backups.

package-receipt.json loads fourteen native/runtime classes, including the new
inventory contract, mapping certificate resolver and coverage presenter. It checks
their actual installed CodeSource paths and JAR SHA-256 digests. The loaded
JaCaMo is bridge/runtime/jacamo-1.3.1-checkpoint-SNAPSHOT.jar with SHA-256
aca1163c73b854123526f52c22c0ea8c6f9bc772376c5fb73d20a1aa4c6af155.
The batch launcher -nogui/quit exits 0.

Launcher:
`D:/_CODE_BANK/Project_/08_Thesis/use/use-assembly/target/new/bin/start_use.bat`.

The distribution remains the documented incremental source-JAR refresh. The three
user-deleted legacy clean-release inputs are not restored, and no clean release
ZIP is claimed. No full clean verify or unrelated live case was run.

Compact evidence is in summary.json, presentation-receipt.json, package-receipt.json
and the two facet PNGs. Large gzip cuts, raw producer/spool data, failed-attempt
logs, runtime work and unrelated Draw.io backups remain local. No transport
credentials are included in this report or the committed receipts.

Source and compact evidence delivery: commit
39318f4bfaa1d9045d0451e361de2ddd6c93a570 was pushed to use origin/main and the remote
head matched the local hash. The final checklist update is documentation only.
The sibling jacamo source required no new changes in this follow-up.
