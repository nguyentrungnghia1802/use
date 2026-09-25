# V2 input inventory

This file preserves the Phase 29 intake inventory. The exact supplied Ecore remains
the Phase 44 frozen Metamodel V2; Mapping V2 subsequently advanced to schema 2.2.0
and is frozen separately. Current authoritative bytes and hashes are in
[`release/v2-freeze-manifest.json`](../../../release/v2-freeze-manifest.json).
Phase 29 input bytes/hashes remain in [v2-input-files.json](v2-input-files.json).

| File under Core | Role | Evidence |
|---|---|---|
| Metamodel/version-2/jacamo_v2_complete.ecore | Canonical semantic source | Only Ecore candidate; mapping embeds its exact matching SHA-256 |
| Mapping/version-2/jacamo-use-mapping-v2.json | Canonical structural mapping candidate | Internal mappingId JaCaMo-agentmetamodel-v2__to__USE-v2.1; schemaVersion 2.1.0 |
| Mapping/version-2/jacamo-use-mapping-v2.schema.json | Canonical candidate schema | Supplied closed Draft 2020-12 schema |
| Mapping/version-2/METAMODEL-MAPPING-V2-AUDIT.md | Supplied audit reference | Discusses internally versioned 2.1 mapping using an older filename; actual supplied filename is v2.json |
| Metamodel/version-2/jacamo_v2_AZ_audit.md | Historical design/provenance reference | Reviews attribute_only_researched/cleaned Ecore, not the final supplied complete bytes |

At intake, no duplicate Ecore, mapping JSON, schema or working manifest was found
in these two folders. No generated model was supplied. Canonical selection uses
the unique candidate and matching source fingerprint/package, not filename alone.
EPackage name/nsPrefix: `agentmetamodel`; nsURI:
`http://www.example.org/agentmetamodel`. No explicit EPackage version annotation.

The supplied mapping embedded status REVIEWED_CANDIDATE_V2. That was its intake
maturity; it is retained here only as historical provenance.
The AZ audit's old counts and recommendations must not override the actual Ecore.
Its reviewed filename is absent, so it cannot establish exact current-byte validity.

Read-only intake generates [metamodel-v2-inventory.json](metamodel-v2-inventory.json)
and [mapping-v2-source-audit.json](mapping-v2-source-audit.json). Preliminary source
coverage/fingerprint PASS does not substitute for JSON Schema validation, EMF-level
validation, USE compilation, projection or runtime tests.

Reproduce from root:
`python use-plugin/tools/v2_baseline_audit.py --output use-plugin/docs/project/v2-migration`.
Existing `metamodel_diff.py` is reused for qualified identity diff; supplementary
details preserve enum, default, ordering and namespace changes. No rename is accepted.
