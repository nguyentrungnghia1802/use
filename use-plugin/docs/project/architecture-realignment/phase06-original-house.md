# Phase 6 — original House-Building supported scope

Status: SUPPORTED_SCOPE_PASS.

The upstream JCM hash is `c14ae629...5fef08`. Official JCM/Jason loading expands
the six declarations to the exact configured total of 22 agent instances and
resolves official include semantics. The deterministic model revision is
`e2a4700b...ec1f4`; the generic pipeline compiles 592 semantic elements into
1024 USE objects. No case name or case dispatch exists in production code.

The original JCM creates its organization dynamically, so the static project
snapshot correctly contains no invented organization. Loading the exact
`house-os.xml` through `OS.loadOSFromURI` yields 11 roles, one group, one scheme,
10 missions, 13 goals, three plans and 10 norms. Nine exact group-role
cardinality tuples are retained; `bricklayer` is `1..2` and the other concrete
roles are `1..1`.

The selected production transport carries House through the same separate-JVM
model/snapshot/event pipeline with bounded buffers and stale identity checks.
The eight contracting AuctionArt instances, simulator House artifact,
winner-driven role changes and organization phase transitions were not observed
in an original autonomous Bridge run in this phase and are therefore not
fabricated: they remain runtime evidence requirements, not PASS claims. Existing
source inventory and reduced real-API controls are retained as separately
labelled evidence.
