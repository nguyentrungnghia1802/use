# Plugin-owned reactor modules

These modules are declared by the USE root reactor; the plugin itself remains
`use-plugin`. Build from the repository root with `-pl use-plugin -am`.

| Directory | Artifact ID | Ownership |
| --- | --- | --- |
| `bridge-contract` | `jacamo-bridge-contract` | Neutral authenticated bridge protocol, identities and evidence contracts. |
| `bridge-jacamo` | `jacamo-bridge-jacamo` | Official JaCaMo/Jason/CArtAgO/MOISE/NPL adapters and producer lifecycle. |
| `semantic-engine` | `use-jacamo-semantic-engine` | Pinned original CSL/iStar semantics and neutral native USE adapter. |

Source organization does not change installed JAR names, public packages, native
semantic assertions, or runtime admission policies. See
`../docs/WORKSPACE-LAYOUT.md` for the complete ownership map.
