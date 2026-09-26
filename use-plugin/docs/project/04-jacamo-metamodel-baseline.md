# JaCaMo Metamodel Baseline

## Active baseline

The checked-in active metamodel is
Core/Metamodel/version-2/jacamo_v2_complete.ecore. Its frozen identity,
inventory and checksums are recorded in release/v2-freeze-manifest.json.
The implementation selects this baseline through ActiveBaseline; it does not
silently fall back to Version 1.

| Contract item | Frozen V2 value |
| --- | --- |
| Ecore version | V2 |
| Enumerations | 7 |
| Classes | 21 |
| Attributes | 48 |
| References | 37 |
| Opposite reference pairs | 3 |

The Ecore file and freeze manifest are authoritative for names, types,
containment and opposites. This overview deliberately does not duplicate their
full element inventory.

## Scope

The structural baseline defines the JaCaMo concepts that the plugin can map to
USE. Runtime launch behavior, source-extraction coverage and original Auction
semantics have separate contracts and acceptance evidence; structural freezing
does not imply that every upstream runtime behavior is supported.

## Mapping relationship

The corresponding structural mapping is
Core/Mapping/version-2/jacamo-use-mapping-v2.json, validated by its adjacent
schema. Runtime mapping is a separate V2 resource loaded by
RuntimeMappingLoader. See 05-metamodel-mapping-contract.md for the mapping
boundary and the Phase 44 closure evidence for the final acceptance state.

## Historical Version 1 material

Version-1 Ecore and mapping inputs remain in their versioned directories for
reproducibility, compatibility analysis and retained release evidence. They do
not define the currently loaded schema or active mapping contract.

## Change rule

The V2 baseline is frozen. Any future structural change requires a new
versioned Ecore and mapping, explicit migration/acceptance evidence and an
updated freeze manifest; editing V2 in place would invalidate the frozen
contract.
