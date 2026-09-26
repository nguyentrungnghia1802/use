# Metamodel Mapping Contract

## Active structural mapping

The active mapping contract is the V2 pair:

- Core/Mapping/version-2/jacamo-use-mapping-v2.json; and
- Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json.

It maps the V2 JaCaMo Ecore baseline to USE and is frozen with the V2 release
manifest. The manifest records mapping version 2.2.0, schema version 3.0.0 and
the following structural inventory.

| Contract item | Frozen V2 value |
| --- | --- |
| Enumerations mapped | 7 |
| Classes mapped | 21 |
| Attributes mapped | 48 |
| References mapped | 37 |
| Inheritance mappings | 0 |
| Target-only projections | 7 |

The mapping JSON and schema are authoritative for the exact qualified
identities, type rules, association ends and target-only projections.

## Runtime mapping is separate

Structural mapping must not be conflated with the runtime adapter mapping.
RuntimeMappingLoader loads the versioned V2 runtime mapping and schema from
the plugin resources. Its supported runtime targets and acceptance evidence are
documented in the Phase 35 and Phase 44 records.

## Authority and validation

The implementation selects V2 through ActiveBaseline and validates the shipped
resources against the V2 schemas. Tests cover the frozen baseline, mapping
integrity and relocated resource behavior. The release/v2-freeze-manifest.json
is the compact machine-readable record that ties those checks to the frozen
artifact set.

## Historical Version 1 material

Version-1 mapping files and older Phase 16–28 discussion records are retained
only where they support reproducibility, release evidence or compatibility
analysis. They are not specifications for new implementation work and are not
an active runtime fallback.

## Change rule

The V2 mapping is frozen. A semantic mapping change requires a new versioned
mapping/schema pair, migration evidence, acceptance tests and a new freeze
manifest rather than an in-place edit to V2.
