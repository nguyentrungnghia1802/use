# Phase 4 — Hello acceptance and shadow comparison

Status: PASS.

The byte-identical Hello JCM (`c81d15c9...b4101`) is loaded through the official
JCM/Jason/Moise objects, converted to a deterministic ModelSnapshot, adapted to
native IR, projected through frozen V2/Mapping V2.2, compiled by USE, traced and
verified with both core OCL and the authored Hello profile. The selected
transport proof supplies a RuntimeSnapshot and RuntimeEvent in a separate JVM;
the mirror replay fingerprint is deterministic.

`ShadowSemanticComparator` compares canonical identity plus provenance digest,
never display name. Every difference must receive one of `ADAPTER_BUG`,
`LEGACY_LIMITATION`, `UNSUPPORTED_FACT`, `REPRESENTATION_LOSS`, or
`INTENTIONAL_CORRECTION`; a null classification is rejected. Its tests prove
stable diff ordering/fingerprint and mutation sensitivity. Historical goldens
were not changed.

The canonical Hello register has fingerprint
`789657d98e2f3d6090ea2131b0cff3a375154aebd59e4f9f2ec7cc6fc51d8ca2`:
official-to-Bridge has zero differences, official-to-legacy has eight classified
legacy limitations, official-to-V2 has reviewed OrderEntry representation rows,
and V2-plan-to-USE has zero backend differences. The machine summary is
`phase04-shadow-register.json`; the test emits the exact per-row register.

The three historical baseline failures were reproduced unchanged. They remain
the legacy constraint-identity and command/trace/golden mismatches recorded in
Phase 0; Bridge acceptance does not suppress or rewrite them.
