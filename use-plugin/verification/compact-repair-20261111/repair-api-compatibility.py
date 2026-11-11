"""Mechanical compatibility repairs; no runtime XML generation or case templates."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'src/main/java/org/tzi/use/plugins/jacamo'
def edit(relative,changes):
 p=root/relative
 s=p.read_text(encoding='utf-8')
 for a,b in changes:
  if a not in s: raise ValueError((relative,a))
  s=s.replace(a,b)
 p.write_text(s,encoding='utf-8')
edit('semantics/mapping/xml/GenericMappingEngine.java',[
 ('r.parent().id().equals(elemId)','r.parent().equals(elemId)'),
 ('d.dependerElement().id()','d.dependerElmt()'),
 ('g.condition().isPresent()','!g.conditions().isEmpty()'),
 ('g.condition().get().expression()','g.oclSource()'),
 ('t.postCondition().or(t::preCondition).map(OclExpression::expression).orElse("")','t.postconditions().stream().findFirst().map(IStarOclConstraint::oclBody).orElse("")'),
 ('if (gr.actor().equals(actor.name())) {','if (gr.target().equals(actor.name())) {'),
 ('selfDecl = gr.rootClass();','throw new IllegalArgumentException("COMPACT_ACTOR_SELF_MUST_USE_TYPED_RUNTIME_BINDING");')
])
edit('semantics/mapping/compact/CompactNativePreparation.java',[
 ('var scopes = new CslScopeModelAdapter().compile(program.cslModel());','new CslScopeModelAdapter().prepare(new UseModelApi(binding.model()), binding);'),
 ('AuthoredOclSpecificationLoader.compileInvariant(\n                                    binding.model(), clause.context().owner(), clause.body(), clause.name())','compiler.compile(new OclContractSpec("NATIVE:"+clause.name(), OclContractSpec.Kind.INVARIANT, binding.model().getClass(clause.context().owner()), null, clause.body())).expression()'),
 ('AuthoredOclSpecificationLoader.compileOperationClause(\n                                    binding.model(), op, clause.body(), pre, clause.name())','compiler.compile(new OclContractSpec("NATIVE:"+clause.name(), pre ? OclContractSpec.Kind.TASK_PRE : OclContractSpec.Kind.TASK_POST, (MClass)op.cls(), op, clause.body())).expression()'),
 ('op.cls(), op, clause.body()','(MClass)op.cls(), op, clause.body()'),
 ('goal.condition().isPresent()','!goal.conditions().isEmpty()'),
 ('g.condition().isPresent()','!g.conditions().isEmpty()'),
 ('g.condition().get().expression()','g.oclSource()'),
 ('t.preCondition().ifPresent(e -> allExpressions.add(Map.entry("", e.expression())));','t.preconditions().forEach(e -> allExpressions.add(Map.entry("", e.oclBody())));'),
 ('t.postCondition().ifPresent(e -> allExpressions.add(Map.entry("", e.expression())));','t.postconditions().forEach(e -> allExpressions.add(Map.entry("", e.oclBody())));'),
 ('context.returnType()','context.resultType()')
])
p=root/'semantics/mapping/compact/CompactNativePreparation.java'
s=p.read_text(encoding='utf-8')
a=s.index('            binding.model().addAnnotation(new MElementAnnotation("CslContextScopes",')
b=s.index('            binding.model().addAnnotation(new MElementAnnotation("XmlNativeContractPreparation"',a)
s=s[:a]+s[b:]
# Operation-to-task credit needs source action identity; do not guess functors/arity.
a=s.index('        for (var window : compactPlan.stepWindows())')
b=s.index('        return result;',a)
s=s[:a]+s[b:]
p.write_text(s,encoding='utf-8')
