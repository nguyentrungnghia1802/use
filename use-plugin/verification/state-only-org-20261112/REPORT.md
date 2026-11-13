# Organizational CSL correction

The later four-actor iStar correction and current live evidence are in
[`../state-only-istar-20261112/REPORT.md`](../state-only-istar-20261112/REPORT.md).
This report records the preceding organizational CSL revision.

This supersedes the flattened Auction CSL used by the earlier state-only demo.
The native model now retains AuctionOrg, its nested auctionGroup, declared
auctioneer/participant roles, Auction entities, and one auction_capabilities
context for each physical agent. OrgContext generalizes RuntimePlayer; it is
eligible to enact a role without being converted into an AtomicAgent.

Attribute mapping has eight entries: three identity attributes for organization,
group and agent-program types, plus the previous five Auction attributes.
Population and Plays/InContext links are reconstructed from observed physical
identities and the current Moise GroupBoard roster, not the JCM players list.
The current JaCaMo producer resolves roster players to live Jason agents;
collective players are supported by the neutral snapshot/native projection and
tested there, but are not invented by the JaCaMo adapter.

Verification completed:

- Eleven focused native/mapping/wire tests passed after the final source edit.
  The real two-JVM Counter gate test also passed in the preceding full run:
  twelve distinct tests, zero failures/errors.
- Organization tests use a different Warehouse model and identifiers. They
  verify Organization playing member in Federation, two physical agent players,
  nested organization scopes, and role removal causing both CSL invariant FAIL
  and iStar Maintain VIOLATED. Unresolved players and ambiguous entity scopes
  are rejected before state mutation.
- Real Auction ran for 127 checkpoints, all APPROVE. Final native population:
  one AuctionOrg, one auctionGroup, five AtomicAgent, five capability contexts,
  one auctioneer, four participant occurrences and two Auction objects.
  Both AuctionCompleted goals and both RolesConsistent goals were FULFILLED.
- INITIAL has no adopted roles because agent startup plans have not executed.
  The AuctioneerPopulation invariant requires at most one auctioneer during
  formation and exactly one once Auction objects exist. Each operational
  Auction also has an iStar Maintain condition on its group's role population.
- The final package build passed. The last local-path parsing edit also supports
  plain paths containing spaces; the tested Auction uses JaCaMo's opaque file URI.

`receipt.json` records populations, source enactments, markings, tests and current
model hashes. `last-checkpoint.json` contains the final full native result.
Protocol secrets are excluded. The runtime work directory is recorded in receipt.

Run from the repository root with:

```bash
bash use-plugin/tools/run-state-only.sh
```

Entity scope inference currently requires exactly one context of the declared
type in the Artifact workspace. Multiple candidate groups require additional
source scope evidence; the verifier holds instead of selecting one arbitrarily.
