# 1.0.1 - Correctness hotfix (unreleased)

- Rebind LIVE runtime mutation and verification on workspace replacement, preserving exact trace aliases.
- Load and validate project-root binding.json in production import.
- Pin archive output timestamps and correct plugin-checkout audit instructions.
- See release/HOTFIX-1.0.1.md and the repository report.md for evidence and limitations.

# Changelog

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
