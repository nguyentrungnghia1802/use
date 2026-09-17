# Full Project Roadmap

> **HISTORICAL_EVIDENCE:** This phase plan records how v1.0.0 was developed. It is
> not the current completion/status source for v1.0.1. See `00-README.md`,
> `17-end-to-end-acceptance.md`, and the v1.0.1 synchronization report instead.

## Phase 0 — Baseline Freeze
Outputs:
- audited Ecore;
- mapping schema;
- Mapping V1 audit;
- freeze manifests;
- docs baseline.

Gate:
- canonical structural coverage complete.

## Phase 1 — Plugin Skeleton
Outputs:
- module;
- plugin metadata;
- menu/command;
- service wiring;
- CI build.

Gate:
- plugin loads in USE.

## Phase 2 — Semantic IR and Project Discovery
Outputs:
- `.jcm` loader;
- project graph;
- IR;
- source index;
- diagnostics.

Gate:
- minimal multi-dimension project represented without USE dependency.

## Phase 3 — Dimension Parsers
Outputs:
- Jason parser;
- CArtAgO extractor;
- Moise parser;
- cross-file resolver.

Gate:
- Auction semantic model complete enough for transform.

## Phase 4 — Mapping Engine and USE Structural Transformation
Outputs:
- mapping loader/validator;
- transformation plan;
- `.use` generation/direct `MModel`;
- trace.

Gate:
- generated model compiles.

## Phase 5 — Instance Materialization
Outputs:
- objects;
- attributes;
- links;
- `.cmd`/`MSystemState`;
- initial check.

Gate:
- initial snapshot matches semantic model.

## Phase 6 — Constraint Translation and OCL Profiles
Outputs:
- constraint IR;
- supported translator;
- generated OCL;
- core OCL;
- case OCL loader.

Gate:
- generated/user OCL compile and test.

## Phase 7 — Resolver and Optional Binding
Outputs:
- owner-qualified resolution;
- binding schema;
- ambiguity UX;
- stale detection.

Gate:
- duplicate-name fixtures resolve/block correctly.

## Phase 8 — Verification Service and Reporting
Outputs:
- full/targeted checks;
- pre/post support;
- result model;
- source navigation;
- exports.

Gate:
- offline Auction positive/negative verification.

## Phase 9 — Runtime Adapter Foundation
Outputs:
- runtime connector abstraction;
- normalized event model;
- synthetic stream;
- state mutation engine;
- lifecycle.

Gate:
- synthetic live mirror works.

## Phase 10 — Live JaCaMo Connectors
Outputs:
- Jason connector;
- CArtAgO connector;
- Moise connector where supported;
- full sync/reconnect.

Gate:
- real JaCaMo Auction mirrors state.

## Phase 11 — Runtime Verification
Outputs:
- event-driven OCL;
- operation enter/exit;
- pre/post;
- incremental dependencies;
- violation correlation.

Gate:
- real runtime violations detected.

## Phase 12 — UI Completion
Outputs:
- import wizard;
- mapping/trace view;
- diagnostics;
- runtime view;
- verification dashboard.

Gate:
- full workflow accessible without shell/manual file editing.

## Phase 13 — Hardening
Outputs:
- performance;
- resilience;
- security/path safety;
- compatibility;
- regression suite.

Gate:
- stable repeated E2E runs.

## Phase 14 — Thesis/Release Package
Outputs:
- reproducible Auction experiment;
- reports;
- release JAR;
- documentation;
- thesis evidence package.

Gate:
- Definition of Done in `17-end-to-end-acceptance.md`.
