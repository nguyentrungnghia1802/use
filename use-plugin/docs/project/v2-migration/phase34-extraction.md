# Phase 34 — source extraction into V2 IR

> Current acceptance: downstream consumer gates PASS after **350/350 clean reactor**
> and **11/11 Python tests**. Earlier run counts/OPEN descriptions below retain
> their historical stage. See [final acceptance](phase35-acceptance.md); phase
> merge/post-merge/push closure remains pending. V2 remains WORKING_BASELINE.

Status: focused component regression PASS; downstream transformation/OCL/runtime
consumer migration and full phase closure OPEN. Ecore and Mapping JSON/schema
remain unchanged at working contract 2.2.0.

## Controlled parser migration

The existing discovery graph, JCM lexer, bounded Jason parser, parse-only JDK
Java extractor and secure XML DOM parser are reused. The default importer now
constructs the descriptor-validated V2 IR, not the historical MAS-root constructor.

| Source | Active V2 output | Preserved outside metamodel |
|---|---|---|
| JCM mas | ProjectDeclaration metadata | platform, paths and project configuration |
| JCM workspace/artifact | Environment.workspaces, Workspace.artifacts; name/type | constructor parameters, confirmed Java type evidence |
| JCM agents | Agent, exact roles/workspaces/artifacts refs | raw configuration and role qualification |
| Jason | Belief.literal, AGoal, Plan.context, Event, ordered Action list | rules, original body/term positions, unsupported body terms and internal call syntax |
| Java | Property(name, arity), Operation(name, arity), Signal | signatures, guard source/expression, await/port/initial-value evidence |
| Moise | Organization/groups/schemes/norms; group-owned Role occurrences; Link endpoints; nested OGoal.plan/subGoals | global role-definition XML, JCM instance configuration, unowned plans |

No Rule/Body/Context/Message/GuardOperation/FormationConstraints EClass is invented.
An internal `.send` is represented as Action(INTERNAL), preserving its source
expression. Goal/belief update body terms stay source-only rather than becoming
external actions. Event operator/type preserve the supported trigger's source
symbols (`+`, `!`); the literal retains the trigger expression.

Enum source spelling is decoded through Mapping's exact source name/literal table;
case folding or guessed uppercase conversion is not accepted. Cardinality values
are EString in V2, so extraction preserves strings instead of forcing the V1 integer
datatype. Domain-specific cardinality interpretation is a separate verification gate.

## Identity, ownership and unsupported boundaries

Existing six-part IDs are reused where source meaning/ownership is unchanged.
The historical `MAS` owner-path segment is retained as an opaque project scope
token, not an active EClass. Changed kinds use V2 names. Role occurrences have
group-qualified IDs, so two JCM group instances of one specification do not share
one contained Role. Belief/AGoal local IDs retain the complete literal, so
`value(1)` and `value(2)` cannot collapse through their shared display functor. Global role definitions remain source facts. Unqualified
references with multiple exact role occurrences remain ambiguous.

Nested source plans have explicit OGoal ownership. A scheme-level sibling plan
does not establish such ownership and is preserved as `unownedPlan@...` with
MOISE_PLAN_OWNER_UNRESOLVED. The original Auction fixture exercises this boundary;
no self-referencing plan or natural-language deadline semantics are claimed as
fully translated. Norm timeConstraint is preserved text, not invented OCL.

## Order evidence

Exact eOpposite membership is completed generically from Mapping aliases. That
logical implication does not establish independent order. Empty/singleton lists
have only one possible order. For a larger inferred inverse list, sourceFacts
records `orderUnresolved:<feature>` and a located ORDER_SOURCE_UNRESOLVED diagnostic.
InstancePlanner rejects that unknown order before rank projection. Supplied valid
V2 ordered instances remain supported; the importer does not invent missing facts
from object iteration, source name sorting or USE insertion order.

## Source preservation and security

Each source is read into an owned UTF-8 snapshot and checked against the discovery
hash before parsing. XML source spans are attached using a second secure SAX pass
over that same text and DOM traversal order, not a name/ID search. Raw XML slices
are preserved. Java method/property/signal spans use JDK source offsets and original
text. No project classes execute. DTDs and external entities remain disabled.

Historical V1 static regression now replays the retained immutable model.use,
initial-state.cmd and trace.json after checking their existing manifest hashes.
It does not run a parallel V1 parser or rewrite any retained digest. The historical
profile test consumes a historical structural plan directly.

## Evidence and remaining work

34 focused tests PASS: StaticProjectImporterTest (9), V2ExtractionTest (5),
V2SemanticModelTest (7), OrderProjectionTest (8), V2OppositeOrderingAuditTest (1),
PreMigrationBaselineTest (2), VerificationSemanticLayerTest (2). Controls include
both case studies, malformed/partial input, exact ambiguity/no fuzzy names,
shared specification/multiple group instances, exact XML spans, unsupported plan
ownership, enum spelling, unprovable inverse order and static execution safety.

Next: adapt projection/constraint/runtime consumers to the V2 attributes and
sourceFacts boundary, materialize explicit Ecore defaults with provenance and
requiredness checks, update reviewed V2 output only after parity/trace/OCL gates.
Whole-pipeline acceptance and P29-P33 dependent gates remain OPEN.

Post-parser plugin run: 197 executed, 8 failures, 59 errors (67 failing identities).
Compared with the pre-migration 7F/65E snapshot, five fewer failing identities.
Three ConstraintClosureTest cases reappear versus the intermediate profile run: their
unchanged consumer searches for removed GuardOperation/context attributes, so no
guard constraint is extracted. Inspection confirms this is the pending sourceFacts
consumer migration, not a weakened guard-translation contract. See phase34-regression.json.
