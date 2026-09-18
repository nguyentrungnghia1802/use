# JaCaMo Runtime Research Pack

Purpose: provide a source-backed runtime inventory that can be imported into the USE-JaCaMo thesis project and used as the **input evidence** for designing `Runtime Mapping V1`.

This pack intentionally does **not** declare the final JaCaMo -> USE runtime mapping as settled fact. It separates:

- **FACT**: directly supported by JaCaMo/Jason/CArtAgO/Moise source/API inspected.
- **INFERENCE**: reasonable normalization proposed for the plugin, but not an upstream event name.
- **OPEN**: requires verification against the current USE-side metamodel/trace model or a live execution probe.

## Research baseline

- JaCaMo repository: `jacamo-lang/jacamo`
- JaCaMo `main` commit audited: `3866858a7ebf6be85d9199c13a09cf4bfb8191be` (2026-09-16)
- JaCaMo build version: `1.3.1`
- Java toolchain: 21
- Dependencies declared by JaCaMo main:
  - Jason interpreter `3.3.2`
  - CArtAgO `3.1`
  - JaCa `3.1`
  - Moise `1.1`
  - NPL `0.6.1`

Important compatibility note for the thesis repository: existing USE-JaCaMo documentation previously pinned Jason `3.3.0`, while current JaCaMo `main` now declares `3.3.2`. Do not silently update the plugin pin. Treat this pack as an **upstream-current audit** and add a compatibility reconciliation task before implementation.

## Files

1. `00_RESEARCH_BASELINE.md` — source scope, evidence policy, audited repositories/versions.
2. `01_JACAMO_RUNTIME_ARCHITECTURE.md` — what actually exists and runs at JaCaMo runtime.
3. `02_RUNTIME_CAPABILITY_MATRIX.md` — observable runtime entities/events, APIs, identity, payload, confidence.
4. `03_RUNTIME_IDENTITY_MODEL.md` — proposed identity chains required before mutation.
5. `04_USE_MAPPING_CANDIDATES.md` — runtime facts/events that should later be compared with USE targets.
6. `05_AUCTION_RUNTIME_WALKTHROUGH.md` — Auction-specific execution walkthrough using the official example.
7. `runtime-capabilities-v1.json` — machine-readable inventory for later validator/mapping work.
8. `runtime-mapping-candidate-v0.json` — **DRAFT ONLY**, a candidate dispatch table to compare against the USE side.

## Core conclusion

JaCaMo is not one monolithic runtime state API. It orchestrates three principal runtime semantic dimensions:

```text
Jason runtime       -> agent mind / goals / events / intentions / actions / messages
CArtAgO runtime     -> workspaces / artifacts / observable properties / operation instances / focus / lifecycle
Moise runtime       -> organisational entity / agents / role players / groups / schemes / missions / goals / permissions/obligations
```

JaCaMo itself provides the integration and startup semantics that bind these runtimes together. Runtime Mapping V1 therefore must be designed **per source runtime capability**, then normalized into one plugin event model before any USE mutation is chosen.
