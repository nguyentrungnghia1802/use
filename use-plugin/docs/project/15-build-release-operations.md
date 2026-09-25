# Build, Release and Operations

> Current frozen candidate: `use-jacamo-plugin-1.0.1-v2-frozen.zip` plus SHA-256
> sidecar. It includes canonical V2 versioned Ecore/Mapping/schema, V2 profile/OCL
> plus its manifest, runtime Mapping V2/schema 3.0.0, compatibility metadata and the
> unified V2 freeze manifest. Manifest status is FROZEN_V2_RELEASE_CANDIDATE and
> gitTag is null. Binary descriptor remains 1.0.1; no published Git release is implied.
> The old manifest is preserved under release/historical. Historical release
> commands and evidence below apply to their recorded V1 revision.

## 1. Build

Use Maven structure compatible với USE repository/version được pin.

Required build outputs:
- plugin JAR;
- test reports;
- packaged resources;
- optional distribution zip.

---

## 2. Resource packaging

Plugin JAR phải contain/version:
- canonical metamodel;
- mapping schema/default mapping;
- core OCL;
- plugin metadata;
- version manifest.

For the V2 frozen package, the only active structural baseline is under
`org/tzi/use/plugins/jacamo/canonical/version-2/` inside the built JAR. Core OCL,
the V2 verification profile and Runtime Mapping V2 remain in their active component
namespaces. `release/release-manifest.json`, `release/compatibility.json` and
`release/v2-freeze-manifest.json` are embedded under the plugin namespace.

V1 structural, OCL, verification-profile and runtime-target resources are retained
only for explicit reproducibility under `org/tzi/use/plugins/jacamo/historical/version-1/`.
The binding, trace and runtime-event schemas whose filenames contain `v1` remain
active wire-format compatibility schemas; they are not a structural target baseline.
The ZIP exposes only canonical V2 target resources, while the JAR retains the V1
bytes solely in that historical namespace.

`ReleasePackageIT` checks the exact ZIP inventory, compares every ZIP entry with its
source, compares active and historical JAR resource bytes with their declared sources,
rejects V1 target resources in active namespaces, validates the ZIP SHA-256 sidecar,
and asks USE to load the JAR extracted from that ZIP. The ZIP includes canonical V2
files as readable copies for inspection.

Không bundle case-specific Auction OCL như global default.

---

## 3. Versioning

Track:
- plugin version;
- metamodel baseline version;
- mapping schema version;
- mapping baseline version;
- runtime protocol version;
- trace schema version.

Breaking schema changes require major bump tương ứng.

The manifest's plugin release tag name is `use-jacamo-plugin-v1.0.1`; no tag with
that exact name exists in the current checkout. An annotated `v1.0.1` tag exists at
`7f77b1f4`, one documentation-only commit behind the audit baseline. The artifact is
`use-jacamo-plugin-1.0.1.zip`. The release manifest records the package inventory,
compatibility versions, and known limits. The package does not redistribute
Jason/CArtAgO/Moise dependencies; supply them on the USE host classpath.
The plugin Maven module has its own `1.0.1` version; USE remains the `7.5.0`
parent and provided API dependency. The plugin JAR embeds its JSON Schema
validator/runtime dependencies and ships Apache/MIT license texts. The isolated
release smoke starts a child JVM with the USE distribution JAR and installed ZIP
contents only, then loads the canonical mapping without Maven's test classpath.

---

## 4. Release checklist

- clean checkout;
- build;
- tests;
- mapping audit;
- plugin loads in target USE;
- import Auction;
- full offline verification;
- runtime smoke test;
- docs packaged;
- changelog;
- tag;
- push.

---

## 5. CI

CI nên:
- validate JSON schemas;
- parse Ecore;
- audit mapping;
- compile;
- unit/integration tests;
- golden outputs;
- Auction offline E2E;
- optionally runtime synthetic E2E.

Live JaCaMo runtime test có thể chạy riêng nếu environment nặng.

---

## 6. Logging

Structured logs:
- phase;
- project ID;
- trace ID;
- runtime event ID;
- diagnostic code.

Không log secrets/path-sensitive content không cần thiết.

---

## 7. Backward compatibility

Plugin khi load mapping/project cache cũ:
- compare schema/version/hash;
- migrate only with explicit migration code;
- otherwise reject with actionable message.

## v1.0.1 reproducibility and audit

Run `mvn --batch-mode clean verify` from the repository root with JDK 21 and Maven.
All paths resolve from the checkout/module; no original mapping repository is needed.
See `Core/Mapping/README.md` for the current mapping gate. Historical external Python/EMF
records are retained as provenance, not advertised as commands available here.

`project.build.outputTimestamp` pins Maven JAR/assembly timestamps. This is necessary but
not sufficient to promise byte reproducibility of a shaded JAR across toolchains.
The hotfix evidence records separate-build archive hashes and extracted-entry comparisons.
Use the same source bytes, dependency artifacts, JDK and Maven versions when comparing.
The immutable v1.0.0 tag remains untouched. Current documentation must distinguish
the existing generic `v1.0.1` tag from the absent manifest-named
`use-jacamo-plugin-v1.0.1` tag and from any remote publication claim.

The historical v1.0.1 hotfix total was 271/271: 13 `use-core`, 130 `use-gui`, and 128
`use-plugin` tests (125 unit/component plus 3 release integration). Two 27-entry
ZIPs were byte-identical in the recorded identical source/dependency/JDK/Maven
environment. This does not promise cross-toolchain or cross-platform identity.


## Phase 26 package contract

The candidate package exposes Runtime Mapping V2 and schema 3.0.0 as readable
`runtime/` entries and embeds byte-identical resources plus the final freeze manifest
in the JAR. Installed smoke loads the frozen contracts. No release tag is created;
package coordinates remain the existing 1.0.1 candidate coordinates. The exact
source revision and V2 suffix distinguish this candidate from historical 1.0.1 bytes.
See the Phase 44 closure and evidence bundle for current tests.

## Current engineering closure

See [Phase 27 hardening](phase27-hardening-audit.md) and the
[final acceptance matrix](phase28-project-closure.md) for current scope and evidence.
Earlier phase test totals and draft/temporary-target descriptions are historical.
That final-target statement belongs to the historical Phase 28 V1 package. The
current package contains one active canonical V2 baseline with one final freeze
manifest. Phase 44 records the frozen release candidate; publishing a Git release
remains separate from autonomous engineering verification.

## Phase 44 frozen candidate verification

The frozen source revision `7c435addcc91d7bbe7928953a11778f1b8e32d73`
passes the 154-test focused semantic gate, the 231-test plugin module gate, the
374-test clean full reactor and a separate relocated-clone 374-test clean reactor.
Every gate has zero failures, errors and skips. The installed archive gate loads the
plugin JAR and both frozen mapping contracts without Maven's test classpath, and
checks all 30 declared ZIP entries byte-for-byte against their sources. Durable
machine-readable records and the final bundle are under
`docs/project/evidence/v2-final/`; publishing a tag remains a separate action.
