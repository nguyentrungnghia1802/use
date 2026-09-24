# Phase 42 UI, packaging and compatibility audit

Status: DONE. Component, module and full-reactor gates pass; post-merge evidence is
recorded separately after integration to main.
V2 remains WORKING_BASELINE and is not frozen or released.

## Workbench workflow

`DefaultJaCaMoFacade` obtains one `ActiveBaseline.Selection` and exposes the selected
Metamodel V2 version/hash plus Mapping V2 ID, schema version, status and hash through
the UI-facing summary. The Project tab displays those complete values. It continues
to expose import, diagnostics, V2 trace, OCL profile loading, offline verification,
runtime controls, live verification results and exact source navigation.

Runtime refresh now reads the latest runtime verification report when one exists, so
the Verification tab shows the result associated with the current mirrored event.
Before the first runtime result it continues to show the latest offline report. Swing
contains no mapping, transformation, instance, OCL-generation or mutation engine;
the executable UI regression rejects those production pipeline types in the panel.

## Exact compatibility identity

`compatibility.json` records plugin 1.0.1, Java 21, Maven 3.9.9, USE 7.5.0 at
`da3762efc7006b0c3096a2dd36566962e47fb74d`, JaCaMo 1.3.0, Jason 3.3.0,
CArtAgO 3.1 and Moise 1.1. The pinned Jason version remains deliberate even though
the upstream development branch may differ.

The active resources are pinned as follows:

| Resource | Status/version | SHA-256 |
| --- | --- | --- |
| Metamodel V2 | WORKING_BASELINE / V2 | `4ae51638a078f0a933982844063993084f17d420694ac8a868d95b9df063c35c` |
| Structural Mapping V2 | WORKING_BASELINE / schema 2.2.0 | `15d330c70400d16958559b83daf27c3755dc0cb28a7dffb8a08d94879d1ddf67` |
| Mapping V2 schema | 2.2.0 | `e9f555ad2e19cbf8382ff4c17d44179d3b47a13d03ee9a58c2b491f99b72c919` |
| Runtime Mapping V2 | WORKING provisional / mapping 2.0.0, schema 3.0.0 | `8daab614184d8fc9fb20e9fc13ab0e8731b76de1d42008890643738161801dec` |
| Runtime Mapping V2 schema | 3.0.0 | `be843872c909a8d44ca7c038f5896a8d829f07b068b4861f8169faf552f6a3bb` |
| Core OCL V2 | 2.0.0 | `a725c1fe0524f2b477327b58c54d317df693a0fbe37d3773bbe44372beeefab5` |
| Core OCL V2 manifest | 2.0.0 | `14b098508167dc5198fa60589d098d5251d808e3ed2e828376324c751a009917` |
| Verification profile V2 | ACTIVE / 2.0.0 | `91c85d42080b56754e8c2f5e9d37127e80c1483fdb4d71f2d61df7eab58fcab2` |

`CompatibilityManifestTest` recomputes every hash from the declared path and checks
the versions/statuses against the source documents. The separate
`v2-working-baseline-manifest.json` repeats the release-facing resource contract with
`freeze=false`; Phase 44 alone may replace that disposition with final freeze data.

## Package namespace and inventory

The ZIP contains canonical V2 Ecore, Structural Mapping and schemas, Runtime Mapping
and schema, core OCL plus manifest, verification profile, wire schemas, compatibility
metadata, working/release manifests, licenses and documentation. Its inventory is
exactly the machine-readable release manifest and every byte is compared with source.

Historical V1 structural resources remain under
`org/tzi/use/plugins/jacamo/historical/version-1`. Historical core OCL, verification
profile and runtime target mapping/schema/freeze bytes were moved from active
component namespaces into the same explicit historical tree. Explicit V1 loaders use
that tree. The `binding-v1`, `trace-v1` and `runtime-event-v1` files are active wire
protocol schemas, not competing target baselines. Tests reject any historical V1
target resource in an active JAR namespace and verify every retained historical byte.
The external ZIP publishes only the canonical V2 target baseline.

## Consistency and evidence

Canonical V2 model, mapping, semantic IR, transformation rules, trace/binding, OCL,
Runtime Mapping and target-only ordering semantics are unchanged. UI reads identity
from the same active selection used by transformation. Packaging changes only the
location of explicitly historical resources and keeps explicit historical loaders;
there is no V1 fallback.

- UI/facade focused gate: 22/22 PASS, 0 skipped.
- Historical namespace clean focused gate: 11/11 PASS, 0 skipped.
- Manifest/resource focused gate: 14/14 PASS, 0 skipped.
- Package/install focused gate: 23/23 unit + 3/3 integration PASS, 0 skipped.
- Final manifest/UI/package/install gate after evidence synchronization: 15/15 PASS,
  0 skipped (`phase42-focused.json`).
- Full plugin module verify: 219/219 PASS, 0 failures, 0 errors, 0 skipped
  (`phase42-module.json`).
- Full reactor phase gate: 362/362 PASS across USE core, GUI, assembly and plugin
  unit/integration suites, 0 failures, 0 errors, 0 skipped (`phase42-regression.json`).
