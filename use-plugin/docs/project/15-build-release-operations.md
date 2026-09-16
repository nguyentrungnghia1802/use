# Build, Release and Operations

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

For release 1.0.0, the canonical Ecore and mapping JSON/schema/freeze manifest
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

The plugin release tag is `use-jacamo-plugin-v1.0.0`; the artifact is
`use-jacamo-plugin-1.0.0.zip`. The release manifest records the package inventory,
compatibility versions, and known limits. The package does not redistribute
Jason/CArtAgO/Moise dependencies; supply them on the USE host classpath.
The plugin Maven module has its own `1.0.0` version; USE remains the `7.5.0`
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
