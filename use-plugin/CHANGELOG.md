# Changelog

## V2 working migration — unreleased

- Default to Metamodel V2 / Mapping 2.2.0 with exact fingerprints and descriptor-backed IR.
- Preserve independent opposite orders through target-only ranks, shared text/direct plans and exact trace.
- Migrate extraction, verification projections, OCL and runtime target contracts; preserve historical V1 evidence.
- Add exact V2 trace/binding identity, collision-safe OCL IDs, Runtime Mapping V2 validation and mirror correctness gates.
- Verify Auction and CounterTeam through the same production pipeline without case-specific core dispatch.
- Package one canonical V2 namespace and bound runtime correlation/report/trace diagnostic retention.
- Add determinism, stale-reference, security and stage-by-stage performance evidence gates.
- Produce a distinctly named V2 working ZIP, without a release tag or freeze claim.
- Full clean-build acceptance PASS: 350/350; see Phase 35 evidence.

## 1.0.1 - 2026-09-18

- Rebind LIVE runtime mutation and verification on workspace replacement, preserving exact trace aliases.
- Load and validate project-root binding.json in production import.
- Add regression coverage for LIVE rebuild, OCL profile load, reimport, failed rebuild, valid binding,
  invalid binding, and stale binding paths.
- Pin archive output timestamps and verify two 27-entry release ZIPs are byte-identical for the recorded
  source/dependency/JDK/Maven environment; this is not a cross-toolchain reproducibility claim.
- Pass 271/271 tests: 13 `use-core`, 130 `use-gui`, and 128 `use-plugin` (125 unit/component plus 3 release integration).
- Preserve the supported boundary: real in-process Jason/CArtAgO/Moise APIs are exercised, not a standalone
  external `.jcm` launcher; runtime verification observes and reports but does not enforce JaCaMo behavior.
- See [the hotfix record](release/HOTFIX-1.0.1.md),
  [the validation report](../docs/report/report.md), and
  [known limitations](KNOWN-LIMITATIONS.md).

## 1.0.0 - 2026-09-16

- Added deterministic `.jcm`, Jason, CArtAgO and Moise static import with provenance.
- Added frozen Ecore-to-USE Mapping V1 validation, USE model/state generation and
  conservative OCL translation.
- Added trace, explicit binding, offline verification and report export workflows.
- Added live Jason/CArtAgO/Moise connectors, ordered mirroring, operation contracts,
  drift detection and reconnect/full-resync.
- Added the USE workbench UI, Auction positive/negative/live acceptance evidence and
  reproducible release packaging.
- Embedded byte-verified canonical Ecore, mapping/schema/freeze data, compatibility
  metadata, and release manifest in the plugin JAR; verified release ZIP contents
  and plugin discovery from the packaged JAR.
- Bundled the JSON Schema validator and its runtime dependencies for installed
  mapping import; aligned Maven artifact metadata with plugin release 1.0.0.


### Historical Phase 26 runtime contract

Final metamodel remains unchanged canonical V1. Runtime Mapping V1 is frozen
with schema 2.0.0 and exact resource hashes; legacy draft schema 1.0.0 is rejected
with RUNTIME_MAPPING_VERSION_UNSUPPORTED. See docs/project/phase26-runtime-mapping-audit.md.
Supported runtime semantics and standalone/NPL limitations are unchanged.
