# V2 input inventory

Active specification: WORKING_BASELINE. Not a freeze or production-readiness claim.
Exact input bytes/hashes: [v2-input-files.json](v2-input-files.json).

| File under Core | Role | Evidence |
|---|---|---|
| Metamodel/version-2/jacamo_v2_complete.ecore | Canonical semantic source | Only Ecore candidate; mapping embeds its exact matching SHA-256 |
| Mapping/version-2/jacamo-use-mapping-v2.json | Canonical structural mapping candidate | Internal mappingId JaCaMo-agentmetamodel-v2__to__USE-v2.1; schemaVersion 2.1.0 |
| Mapping/version-2/jacamo-use-mapping-v2.schema.json | Canonical candidate schema | Supplied closed Draft 2020-12 schema |
| Mapping/version-2/METAMODEL-MAPPING-V2-AUDIT.md | Supplied audit reference | Discusses internally versioned 2.1 mapping using an older filename; actual supplied filename is v2.json |
| Metamodel/version-2/jacamo_v2_AZ_audit.md | Historical design/provenance reference | Reviews attribute_only_researched/cleaned Ecore, not the final supplied complete bytes |

No duplicate Ecore, mapping JSON, schema, working manifest or freeze manifest was
found in these two folders. No generated model is supplied. Canonical selection
uses the unique candidate AND matching source fingerprint/package, not filename
alone. EPackage name/nsPrefix: `agentmetamodel`; nsURI:
`http://www.example.org/agentmetamodel`. No explicit EPackage version annotation.

The mapping embeds status REVIEWED_CANDIDATE_V2. This is its supplied audit maturity;
WORKING_BASELINE is the project's active-development policy. Neither means FROZEN.
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
