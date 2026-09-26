# Phase 10 — legacy authority removal ledger

Status: PASS.

| Retired production capability | Exact replacement | Replacement proof | Release disposition |
|---|---|---|---|
| JCM grammar/discovery (`JcmLexer`, `JcmSemanticParser`, `JcmProjectLoader`) | official `JaCaMoProjectParser`/`JaCaMoProject` through `OfficialProjectAdapter` | `OfficialAdapterTest`, canonical Hello/Auction/House tests | source retained historical; classes absent from JAR |
| Jason source authority (`JasonSourceParser`) | official Jason `Agent`/plan AST through `OfficialJasonAdapter` | API-drift compilation, official fixtures, shadow register | source retained historical; class absent from JAR |
| CArtAgO source authority (`CartagoSourceExtractor`) | official artifact descriptors/runtime evidence, classified by projection status | adapter/runtime controls and projection review | source retained historical; class absent from JAR |
| Moise XML authority (`MoiseXmlParser`) | `OS.loadOSFromURI` object graph through `OfficialMoiseAdapter` | Auction/House structure and relation-cardinality tests | source retained historical; class absent from JAR |
| legacy resolver/import orchestration | contract validation plus `NativeSemanticAdapter` | malformed/reference/revision tests and facade import tests | implementation/support classes absent from JAR |
| Jason/CArtAgO/Moise in-process connectors and registry | official JaCaMo-side adapters plus neutral snapshots/events | adapter lifecycle, validated-cut, separate-JVM and transport tests | concrete connectors/registry absent from JAR |
| `CompositeRuntimeConnector` and old mirror/service SPI | Bridge client, `BridgeMirrorStateMachine`, `BridgeRuntimeProjector` | replay/gap/resync tests and separate-JVM transport | old composite/mirror/service classes absent from JAR |

No test, fixture, golden, or historical failure was deleted. The historical
source compiles and its focused tests remain runnable from the repository, while
the release boundary prevents those implementations from becoming production
authority. `LegacyAuthorityPackagingIT` also proves that Bridge/client classes
and formal foundations remain present, and that the JaCaMo-side adapter is not
accidentally shaded into the USE process.

Source scans find no case-specific production logic, no production caller of a
retired class, and only one `legacy-compatibility` occurrence: the explicit
removed-authority error. The foundations listed in the disposition document
remain intact. Frozen Ecore, Mapping V2.2, Runtime Mapping V2, OCL/profile,
goldens, and freeze manifest have no diff.
