# Compact runtime completion before layout reorganization

Date: 2026-11-12. This supersedes the incomplete runtime status from 2026-11-11.

The compact workflow now uses typed source cuts, actual players/roles/schemes,
native context/resource projection, source-qualified Task demand and original
native PRE/POST/Goal/OCL evaluation. No legacy XML overlay or case-specific
production graph/verdict is used. Authored CSL/iStar/OCL and ASL bodies were not
changed to pass the migration.

Corrected integration defects found by the external Auction gate:

- Initial invocation graphs no longer require organizational actors before their
  actual source objects exist; later source membership rebuilds the topology.
- Execution identities are not physical object aliases.
- MOISE receipts derive their source-clock prerequisite.
- Explicit operational invariant activation is preserved and latched.
- Compact physical identities own native operation PRE/POST pairing.
- Task selections survive only qualified revalidation of the same episode and
  references; unrelated resources do not reset a Task.
- The adapter queries the actual expanded graph's derived-goal admission result.
- Known assignment evidence is supplied through the existing native contract.
- Reserved empty M0 helper bases have native writer closure evidence; an unmapped
  authored population is still unproven.
- Failed evaluation audit no longer dereferences a missing state image.

## Fresh evidence

| Gate | Evidence |
| --- | --- |
| Main source/native/provider/operation/history/import paths | `main-assignment.log`: 67 tests, zero failures/errors/skips. |
| Exact terminal acceptance plus checkpoint failure/review protocol | `terminal-acceptance.log`: 16 tests, zero failures/errors/skips. |
| Final population proof, rollback/history, Task and checkpoint checks | `population-acceptance.log`: 24 tests, zero failures/errors/skips. |
| Unmodified external Auction through actual TCP producer | `ACCEPTANCE.json`: two exact terminal scopes automatically APPROVED, zero manual acknowledgements. All five native invariant outcomes PASS in each frozen terminal cut. |
| Runnable dependency coherence | Validated updater compared 133 contract and 297 engine classes byte-for-byte; installed the reactor JARs into `use-assembly/target/new`. |
| Actual JaCaMo artifact | Source build, Maven dependency and installed runtime JAR share SHA-256 `4e446dbc8ea2fd41f98a398204fd2d5a9bbe694ab44493012246557c5838e27e`. |

The live source is
`D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples/auction/auction.jcm`.
Historical intermediate failures and their full producer/journal recordings are
retained locally. They are diagnostic evidence, not current acceptance.

These are service/TCP and native component tests, not manual desktop acceptance.
Between frozen checkpoints, an event cut may be incomplete while independent
sources publish; that does not upgrade its coverage or erase the accepted frozen
result. Unsupported policies still fail explicitly. See the supported boundary
in `docs/project/compact-runtime.md` under the plugin root.

The following organization pass must keep these semantics and evidence intact.
