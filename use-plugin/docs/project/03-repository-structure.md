# Repository Structure

This document describes the checked-in active V2 layout. Build output under
target is derived material and is not a source of truth.

## Reactor

    use/
      pom.xml
      use-core/
      use-gui/
      use-plugin/

The JaCaMo integration lives in use-plugin. Its parent reactor and sibling
modules provide the USE runtime used by the plugin tests and distribution.

## Plugin module

    use-plugin/
      Core/
        Metamodel/
          version-1/                 historical reproducibility baseline
          version-2/
            jacamo_v2_complete.ecore active metamodel
        Mapping/
          version-1/                 historical reproducibility baseline
          version-2/
            jacamo-use-mapping-v2.json
            jacamo-use-mapping-v2.schema.json
      docs/
        agent/task.md                current execution tracker
        project/                     specifications and acceptance evidence
        research/                    retained upstream research evidence
        superpowers/plans/           retained release-referenced evidence
      release/
        v2-freeze-manifest.json      active frozen-baseline manifest
        historical/                  retained historical release records
      src/
        main/
        test/

## Active baseline

The active structural contract is V2:

- Core/Metamodel/version-2/jacamo_v2_complete.ecore;
- Core/Mapping/version-2/jacamo-use-mapping-v2.json and its schema;
- release/v2-freeze-manifest.json; and
- the V2 resources loaded through ActiveBaseline and RuntimeMappingLoader.

Version-1 inputs remain checked in only for historical/reproducibility
evidence. They are not a runtime fallback for the active baseline.

## Relevant source areas

| Area | Purpose |
| --- | --- |
| src/main/java/org/tzi/use/plugins/jacamo | plugin entry points, import pipeline, project loading, mapping and runtime adapters |
| src/main/resources/org/tzi/use/plugins/jacamo | shipped mapping, schema and runtime resource copies |
| src/test/java/org/tzi/use/plugins/jacamo | structural, importer, runtime and release-hardening tests |
| src/test/resources | canonical cases and fixture projects used by the tests |

## Evidence and release records

Phase 29 onward records the V2 migration and acceptance evidence. Phase 44 is
the frozen-baseline closure. Earlier Phase 16–28 material is retained only
where it provides release, compatibility or original-Auction semantic evidence;
it must not be read as the active V2 architecture.

The current tracker is docs/agent/task.md. Retired task plans, stale manifests
and one-off documentation sync records are intentionally absent from this
layout.
