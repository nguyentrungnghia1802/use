# P-ACTOR-01: explicit generic intentional actor

The project now accepts `actor Name { ... }` in addition to `agent` and `role`.
This is an explicit project extension, not a claim that the pinned upstream
iStar 2.0 metamodel already had a concrete generic Actor.

`Actor` remains the sealed supertype. New `GenericActor` implements its common
intentional-element API without claiming Agent or Role specialization. The
factory and both scenario topology implementations preserve that type.
Existing Agent/Role syntax, Goal/Task recurrence and dependency propagation are
unchanged. State verification explicitly selects the existing unary-realization
admission profile and single-owner context topology.

The patch in `patches/P-ACTOR-01.diff` records changes to selected upstream
sources. The provenance manifest retains all original source hashes and records
the patched target hashes. `EngineExtractionTest` reverses only the exact actor
additions before checking the remaining pinned bytes; private-algorithm mutation
checks remain active. The deployment guard records the new grammar hash and
checks the new concrete class's origin.

`GenericActorTest` verifies the type through both instance topologies and actual
propagation through a role into an organizational Agent. Existing frontend
parity fixtures continue to run against the independent pinned-source oracle.
