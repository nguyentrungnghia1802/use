# DSL source layout

The DSL implementation is owned by `org.vnu.sme.goal.dsl`:

```text
org.vnu.sme.goal.dsl
├── csl
│   ├── ast       Concrete syntax records and source locations
│   ├── parser    Generated parser, visitors, compiler, frontend and syntax checks
│   ├── mm        CSL definitions, compatibility interfaces and validation
│   ├── view      Diagram nodes, edges, layout and specification rendering
│   └── form      CSL panel, host placement and window lifecycle
├── istar
│   ├── ast       Concrete syntax records
│   ├── parser    Generated parser, visitors, compiler and frontend admission
│   ├── mm        Actor/intentional-element definitions and validation
│   ├── view      Diagram nodes, edges, layout, badges and specification rendering
│   └── form      iStar panel, host placement and window lifecycle
├── csl_marking
│   ├── mm        Accepted OAL objects/links, legacy OAL DTOs, OCL state protocol
│   │             and versioned presentation snapshot serialization
│   └── view      OAL diagram and specification rendering
└── istar_marking
    ├── mm        Goal/Task/Quality marking state, status enums, scenario/instance
    │             definitions, topology and evaluation results
    ├── semantics Existing propagation algorithms and contribution polarity rules
    ├── parser    Existing scenario-to-instance translator
    └── view      Scenario context selection and marking presentation
```

`feature` remains the plugin menu/entry-point layer. `verify` retains native USE
model binding, OCL compilation/evaluation, evidence classification and runtime
verification. DSL frontends reuse the existing diagnostic/profile APIs. Semantic
code remains independent of Swing, bridge execution and source frameworks;
only `view` and `form` may import the host GUI.

`NativeOclEvaluator` belongs to `csl.parser` as the existing legacy syntax/subset
implementation; its evaluation API is retained. CSL v4 still uses the host OCL
grammar. `NativeOclBackend` remains in `verify.use75` and still evaluates against
the authoritative USE states. No evaluator or runtime state was replaced.

This relocation consolidates 70 classes without compatibility wrapper copies.
The marking package root contains no Java classes: marking values live in `mm`,
and propagation/polarity rules live in `semantics`.
Legacy metamodel interfaces and deprecated OAL DTOs are retained because they
have distinct contracts and remain part of the existing implementation.
Identical class names or similar fields alone are not evidence of equivalent
semantics.

`META-INF/dsl-class-relocations.properties` records the previous and current
class identities for provenance; production code does not consult this table.
The provenance manifests retain upstream paths/hashes and record current target
paths/hashes. The pinned oracle archive is unchanged and compiles under its
original packages. Tests canonicalize only recorded class names when comparing
the two implementations. The source provenance test reverses the recorded
package/import changes before applying its existing byte/patch assertions.

## Validation

From the repository root:

```powershell
mvn -pl use-plugin clean
mvn package
```

The module-only clean removes stale binary names from previous local builds.
`DslPackageLayoutTest` checks the four DSL roots, the five language subpackages,
source path/declaration consistency, absence of legacy production references,
and linkage of every moved class without old binary resources. Existing parser
parity, marking recurrence, native OCL, runtime lifecycle and Swing tests retain
their behavior assertions. Automated Swing tests do not establish manual
desktop acceptance.

Validated on 2026-11-17 on Windows/JDK 21:

- Baseline: all existing tests passed (150 in `use-plugin`).
- Focused relocation/parity/provenance checks: 76 passed.
- Full `mvn package`: all five reactor modules succeeded; 168 tests passed
  (14 core, 1 GUI architecture, 153 plugin), with no failures, errors or skips.
- Before/after source comparison: all 242 production classes retained their
  nonblank body lines, including literal whitespace, after reversing only
  package/import declarations, line endings and recorded qualified class names.
- The existing automated Swing and Auction/lifecycle tests passed. No fresh
  manual desktop acceptance was performed.
- Cold JVM smoke against `use-assembly/target/new/lib/plugins/` passed the
  installation guard, all 70 relocated class lookups, CSL frontend compilation
  and the existing initial Goal marking recurrence. The built JAR, both release
  ZIPs and runnable installation contain identical plugin bytes and current
  provenance metadata; no legacy class resources remain in the plugin JAR.
