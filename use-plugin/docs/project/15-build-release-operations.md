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
