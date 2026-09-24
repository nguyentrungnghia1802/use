# Build, Release and Operations

> Current working package: `use-jacamo-plugin-1.0.1-v2-working.zip` plus SHA-256
> sidecar. It includes canonical V2 versioned Ecore/Mapping/schema, V2 profile/OCL
> and runtime Mapping V2/schema 3.0.0. Manifest status is WORKING_V2_NOT_RELEASED,
> gitTag is null. Binary descriptor remains 1.0.1; no V2 release/freeze is implied.
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

For release 1.0.1, the canonical Ecore and mapping JSON/schema/freeze manifest
are under `org/tzi/use/plugins/jacamo/canonical/` inside the built JAR. The
descriptor is `useplugin.xml`; core OCL and its manifest are under `ocl/`;
`release/release-manifest.json` and `release/compatibility.json` are embedded
under the plugin namespace. `ReleasePackageIT` checks the real ZIP inventory,
compares each ZIP entry with its source, compares these JAR resource bytes with
their canonical sources, validates the ZIP SHA-256 sidecar, and asks USE to load
the JAR extracted from that ZIP. The ZIP includes the same canonical files as
readable copies for inspection.

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

Current candidate package adds runtime mapping V1, schema 2.0.0 and freeze manifest
as three readable runtime/ entries (30 ZIP entries total), with identical embedded
JAR resources. Installed smoke loads the final runtime contract. No new release tag
is created; package name/version remain the existing 1.0.1 candidate coordinates.
The exact source revision distinguishes this candidate from historical 1.0.1 bytes.
See phase26-runtime-mapping-audit.md and final closure evidence for current tests.

## Current engineering closure

See [Phase 27 hardening](phase27-hardening-audit.md) and the
[final acceptance matrix](phase28-project-closure.md) for current scope and evidence.
Earlier phase test totals and draft/temporary-target descriptions are historical.
The final structural target is unchanged V1; Runtime Mapping V1 is frozen.
Final user acceptance remains separate from autonomous engineering verification.
