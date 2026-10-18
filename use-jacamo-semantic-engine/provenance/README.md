# Selected use4Goal engine provenance

Pinned upstream: `6f3e73801239fd8bfb94ca8d04ba531b903c0ac9`. The canonical per-file
manifest is `src/main/resources/META-INF/use4goal-provenance.json`. Original
FQCNs, exact source bytes, GPL license and attribution are retained. Generated
CSL/IStar parsers are regenerated using ANTLR 4.9.3; upstream target directories
are never copied. See the manifest for the compatibility-only native evaluator
closure, which is not the production OCL backend.

`inventory_upstream.py --verify --self-test` checks the read-only reference and
extraction. `--extract` refuses to overwrite a differing target. The pinned
test-only archive is independent of the sibling checkout and excluded from
the production JAR. `run_original_oracle.py` regenerates/compiles the isolated
original implementation; evidence is archived under
`use-plugin/verification/semantic-migration/phase-1`.

## Observed original behavior, not a correction to the engine

The original contextual dependency fixture creates both `requested[bob]` and
`requested[bob,alice]`. Only the bound dependency occurrence is fulfilled;
the standalone occurrence remains UNKNOWN. The first oracle assertion assumed
there was only one occurrence and failed. That failed output is preserved.
Source tracing and the subsequent oracle assert the exact three-node topology
and both distinct results. This distinction is a required parity assertion,
not a new expected difference from the original engine.

Runtime occurrence identity must include the complete structured binding stack.
The new profile must reject ambiguous implicit owner fallback and unproven
contexts before calling the original translator. Its default context matcher
and first-owner fallback are not safe substitutes for observed bindings.
P-CTX-01 may expose the existing topology/dependency seam in our copy, without
changing the original evaluate behavior. Every applied patch must have source
hash, patch inventory and original-oracle parity evidence.
