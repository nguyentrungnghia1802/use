grammar IStar;

@header { package org.vnu.sme.goal.dsl.istar.parser; }

// =====================================================================
//  iStar 2.0 — SD (Strategic Dependency) + SR (Strategic Rationale) views
//
//  Declaration-only body: only intentional elements (goal/task/resource/
//  quality) are declared. Every element-to-element relation is
//  written inline at the child's own declaration — '>' relation target —
//  the same way Java writes 'class Foo extends Bar' at Foo's declaration
//  instead of as a separate statement. AND-refinement is the only relation
//  with no keyword (the default): multiple children written '> SameParent'
//  are grouped into one AndRefinement by the factory. Every other relation
//  (or, forall, pick, make/help/hurt/break, qualifies, needed-by)
//  requires its keyword — see docs/model/istar.ecore.
//
//  Actor is abstract — only Role and Agent are concrete, both generalizing
//  to Actor.
//
//  istar ModelName {
//    role|agent ActorName {
//      goal     GoalId [: Achieve|Maintain|Sustain] [description STR] rel* [cond*]
//      task     TaskId                              [description STR] rel* [pre/post*]
//      resource ResourceId                          [description STR] rel*
//      quality  QualityId                           [description STR] rel*
//      // legacy in-actor form of ActorAssociation (still accepted):
//      ActorId is-a           SuperActorId
//      ActorId participates-in RoleId
//    }
//    // canonical, metamodel-faithful form: GoalModel.actorAssociations
//    is-a           SubActorId  SuperActorId
//    participates-in AgentId     RoleId
//    // dependency composition-owns one newly declared dependum intentional element;
//    // goal|task|resource|quality selects its concrete EClass. ".Elmt"
//    // on either end is the optional SD "boundary opening"
//    // (Dependency.dependerElement / dependeeElement) — the SR element inside
//    // that actor's boundary the dependency arrow visually attaches to.
//    depend DependerId['.'DependerElmt] -> goal|task|resource|quality DependumId -> DependeeId['.'DependeeElmt]
//  }
//
//  ─────────────────────────────────────────────────────────────────────
//  CHANGE LOG — this grammar is now a FAITHFUL concrete syntax of
//  docs/model/istar.ecore. Every production maps to an EClass/feature;
//  nothing here is an undocumented extension.
//  ─────────────────────────────────────────────────────────────────────
//  v2.1:
//   1. `description STRING` on goal/task/resource/quality (+ actor header,
//      doc-only) -> IntentionalElement.description.
//   2. `lang IDENT` before any OCL block -> Condition.condLang (default OCL).
//   3. Top-level `is-a` / `participates-in` as peers of `depend`
//      -> GoalModel.actorAssociations. The in-`actorBody` spelling is kept
//      as a concrete-syntax convenience: the builder lifts BOTH forms to a
//      model-owned ActorAssociation, so neither violates the ecore.
//  v2.2 (this pass — the ecore itself was completed to remove its own gaps):
//   4. `> forall <Param> <Child>` -> ForRefinement (newly defined EClass).
//   5. `> pick <Param> <Child>`   -> ParameterRefinement (newly defined).
//      Both were referenced by Goal's activation constraints but had no
//      class; now defined in istar.ecore. This also lets ojs.istar /
//      incident_response*.istar (which use `forall`/`pick`) parse.
//   6. `activation [lang X] {[ ... ]}` on a goal -> Goal.activation
//      (newly a real containment feature). Root-goal-only, per
//      Goal::RootGoalHasActivationSource / InheritedActivationIsNotRedeclared.
//   7. `satisfy` / `ensure` goal-condition aliases removed (no ecore basis;
//      `condition` is the feature name). `ocl {[…]}` / `ocl:…;` kept —
//      they are just spellings that fill Condition.condExpr.
//  Java to update: regenerate dsl.istar.parser; builder reads
//  `description` / `condLang` / `activation` / the `relForAll`+`relPick`
//  alternatives / top-level `actorAssociationStmt`; EMF codegen from the
//  updated istar.ecore (adds ForRefinement, ParameterRefinement,
//  Goal.activation).
// =====================================================================

model
    : 'istar' IDENT stringLit? '{' actorDef* modelStatement* '}' EOF
    ;

// dependencies and actor associations may be freely interleaved after the
// actor definitions.
modelStatement
    : dependency
    | actorAssociationStmt
    ;

actorDef : actorKind IDENT stringLit? '{' actorBody* '}' ;

actorKind : 'role' | 'agent' ;

actorBody
    : 'goal'     IDENT goalType? descProperty? rel* goalCondition* activationClause? # bodyGoal
    | 'task'     IDENT           descProperty? rel* oclCondition*  # bodyTask
    | 'resource' IDENT           descProperty? rel*                # bodyResource
    | 'quality'  IDENT           descProperty? rel*                # bodyQuality
    | IDENT 'is-a'           IDENT                                 # bodyIsA
    | IDENT 'participates-in' IDENT                                # bodyParticipates
    ;

// Canonical ActorAssociation form (GoalModel.actorAssociations). `is-a`
// links Role->Role or Agent->Agent generalisation; `participates-in`
// links an Agent to a Role it plays.
actorAssociationStmt
    : 'is-a'            source=IDENT parent=IDENT   # assocIsA
    | 'participates-in' agent=IDENT  role=IDENT     # assocParticipatesIn
    ;

goalType : ':' goalTypeName ;
goalTypeName : 'Achieve' | 'Maintain' | 'Sustain' | 'None' ;

descProperty : 'description' stringLit ;

rel
    : '>' target=IDENT                            # relAnd
    | '>' 'or' target=IDENT                       # relOr
    | '>' 'forall' param=IDENT target=IDENT       # relForAll   // ForRefinement
    | '>' 'pick'   param=IDENT target=IDENT       # relPick     // ParameterRefinement
    | '>' contribType target=IDENT                # relContribute
    | '>' 'qualifies' target=IDENT                # relQualifies
    | '>' 'needed-by' target=IDENT                # relNeededBy
    ;

dependency : 'depend' depEnd '->' dependumRef '->' depEnd ;

dependumRef : dependumKind IDENT ;

dependumKind : 'goal' | 'task' | 'resource' | 'quality' ;

depEnd : IDENT ('.' IDENT)? ;

contribType
    : 'make'  # ctMake
    | 'help'  # ctHelp
    | 'hurt'  # ctHurt
    | 'break' # ctBreak
    ;

// Task owns pre/post contracts; the older single-line `ocl:` form stays.
// Optional `lang <Id>` selects Condition.condLang (default OCL).
oclCondition
    : ('pre' | 'post') condLang? OCL_BLOCK
    | OCL_CLAUSE
    ;

// Goal owns one condition contract (Goal.condition).
goalCondition
    : 'condition' condLang? OCL_BLOCK
    | OCL_CLAUSE
    ;

// Goal.activation: the OCL trigger of a ROOT goal (one not refined into and
// not a goal-dependency dependee). A non-root goal must not declare one.
activationClause : 'activation' condLang? OCL_BLOCK ;

condLang : 'lang' IDENT ;

stringLit : STRING ;

// ── Lexer ─────────────────────────────────────────────────────────────

OCL_CLAUSE
    : 'ocl' [ \t\r\n\f]* '{[' .*? ']}'
    | 'ocl:' (OCL_DQ_STRING | OCL_SQ_STRING | ~[;"'])* ';'
    ;

OCL_BLOCK : '{[' .*? ']}' ;

STRING : '"' ('\\' . | ~["\\\r\n])* '"' ;

fragment OCL_DQ_STRING
    : '"' ('\\' . | ~["\\\r\n])* '"'
    ;

fragment OCL_SQ_STRING
    : '\'' ('\\' . | ~['\\\r\n])* '\''
    ;

IDENT  : [a-zA-Z_][a-zA-Z0-9_]* ;

WS            : [ \t\r\n\f]+ -> skip ;
LINE_COMMENT  : '//' ~[\r\n]*  -> skip ;
BLOCK_COMMENT : '/*' .*? '*/'  -> skip ;
