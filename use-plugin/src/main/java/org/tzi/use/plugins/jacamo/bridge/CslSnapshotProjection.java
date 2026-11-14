package org.tzi.use.plugins.jacamo.bridge;

import java.util.*;
import org.tzi.use.plugins.jacamo.verify.use75.*;
import org.tzi.use.uml.ocl.value.*;

/** Structural correspondence is type based; populations and enactments always come from M0 observations. */
final class CslSnapshotProjection {
    record ObjectRow(String identity,String type,Map<String,Value> values) { }
    record Link(String association,String left,String right) { }
    record Prepared(List<ObjectRow> objects,List<Link> links) { }
    private record Source(String id,String kind,String type,String name,String workspace,String parent,Map<String,Object> properties) { }
    private final Use75ModelBinding binding;
    private final AttributeMapping mapping;
    CslSnapshotProjection(Use75ModelBinding binding,AttributeMapping mapping) {
        this.binding=binding;this.mapping=mapping;
        if(!binding.cslModel().relations().isEmpty())throw new IllegalArgumentException("STATE_EXPLICIT_ASSOCIATION_PROVIDER_REQUIRED");
        for(var a:mapping.attributes()) {
            var declaration=binding.classifiers().get(a.targetClass());
            if(declaration==null)throw new IllegalArgumentException("MAPPING_TARGET_MUST_BE_CSL_DECLARATION");
            var expected=a.sourceCategory().equals("artifact")?Use75ModelBinding.Kind.ENTITY:Use75ModelBinding.Kind.ORG_CONTEXT;
            if(declaration.kind()!=expected)throw new IllegalArgumentException("STATE_SOURCE_CSL_KIND_MISMATCH:"+a.targetClass());
            if(binding.classifier(a.targetClass()).attribute(a.targetName(),true)==null||binding.classifier(a.targetClass()).attribute(a.targetName(),true).isDerived())throw new IllegalArgumentException("STATE_TARGET_ATTRIBUTE_INVALID:"+a.targetName());
        }
        for(var c:binding.classifiers().values())if(c.kind()!=Use75ModelBinding.Kind.ROLE&&mapping.attributes().stream().noneMatch(a->a.targetClass().equals(c.declaration())))throw new IllegalArgumentException("CSL_INSTANCE_SOURCE_REQUIRED:"+c.declaration());
    }
    Prepared prepare(CslStateVerifier.Snapshot snapshot) {
        var sources=new LinkedHashMap<String,Source>();
        for(var a:snapshot.artifacts())put(sources,new Source(a.identity(),"artifact",a.javaClass(),a.name(),a.workspace(),"",a.properties()));
        for(var n:snapshot.nodes())put(sources,new Source(n.identity(),n.kind(),n.type(),n.name(),n.workspace(),n.parent(),n.properties()));
        var rows=new LinkedHashMap<String,ObjectRow>();var bySource=new HashMap<String,List<ObjectRow>>();
        for(var source:sources.values()) {
            var targets=new TreeMap<String,Map<String,Value>>();
            for(var a:mapping.attributes())if(a.sourceCategory().equals(source.kind())&&a.sourceClass().equals(source.type())) {
                Object raw=a.sourceKind().equals("property")?source.properties().get(a.sourceName()):switch(a.sourceName()){case "name"->source.name();case "uuid"->source.id();case "workspace"->source.workspace();default->null;};
                var type=binding.classifier(a.targetClass()).attribute(a.targetName(),true).type().toString();
                targets.computeIfAbsent(a.targetClass(),k->new TreeMap<>()).put(a.targetName(),CslStateVerifier.value(raw,type,a.targetClass()+"."+a.targetName()));
            }
            if(!source.kind().equals("artifact")&&targets.size()>1)throw new IllegalArgumentException("ORGCTX_SOURCE_REQUIRES_ONE_CANONICAL_TYPE:"+source.id());
            for(var target:targets.entrySet()) {
                for(var attribute:binding.classifier(target.getKey()).allAttributes())if(!attribute.isDerived()&&!target.getValue().containsKey(attribute.name()))throw new IllegalArgumentException("REQUIRED_ATTRIBUTE_NOT_MAPPED:"+target.getKey()+"."+attribute.name());
                var row=new ObjectRow(key(snapshot,source.id(),target.getKey()),target.getKey(),Map.copyOf(target.getValue()));rows.put(row.identity(),row);bySource.computeIfAbsent(source.id(),k->new ArrayList<>()).add(row);
            }
        }
        var links=new LinkedHashSet<Link>();
        for(var artifact:snapshot.artifacts())for(String observer:artifact.observers()) {
            var source=sources.get(observer);if(source==null||!source.kind().equals("agent"))throw new IllegalArgumentException("FOCUS_OBSERVER_UNOBSERVED:"+observer);
            if(bySource.getOrDefault(observer,List.of()).isEmpty()&&binding.classifiers().values().stream().noneMatch(c->c.kind()==Use75ModelBinding.Kind.ORG_CONTEXT))continue;
            var player=exact(bySource.getOrDefault(observer,List.of()),"FOCUS_ORGCTX_UNRESOLVED:"+observer);
            for(var target:bySource.getOrDefault(artifact.identity(),List.of()))links.add(new Link("CslFocuses",player.identity(),target.identity()));
        }
        for(var source:sources.values())for(var row:bySource.getOrDefault(source.id(),List.of())) {
            String owner=binding.declarationOwners().get(row.type());if(owner==null)continue;
            // Lexical declaration nesting does not give an agent instance one physical owner.
            // Its runtime groups/roles are represented by explicit Membership/Enactment facts.
            if(source.kind().equals("agent")&&source.parent().isBlank())continue;
            List<ObjectRow> candidates;
            if(source.kind().equals("artifact")) {
                candidates=sources.values().stream().filter(s->s.workspace().equals(source.workspace())).flatMap(s->bySource.getOrDefault(s.id(),List.of()).stream()).filter(r->r.type().equals(owner)).toList();
            }else {
                if(!sources.containsKey(source.parent()))throw new IllegalArgumentException("CSL_CONTEXT_PARENT_UNOBSERVED:"+source.id());
                candidates=bySource.getOrDefault(source.parent(),List.of()).stream().filter(r->r.type().equals(owner)).toList();
            }
            var parent=exact(candidates,"CSL_DECLARATION_SCOPE_AMBIGUOUS_OR_MISSING:"+row.type());
            links.add(new Link(source.kind().equals("artifact")?"CslEntityScopes":"CslContextScopes",parent.identity(),row.identity()));
        }
        var memberships=new HashSet<CslStateVerifier.Membership>();
        for(var membership:snapshot.memberships()) {
            if(!memberships.add(membership))throw new IllegalArgumentException("DUPLICATE_MEMBERSHIP");
            var member=context(membership.member(),sources,bySource);
            var group=context(membership.group(),sources,bySource);
            links.add(new Link("CslMembership",member.identity(),group.identity()));
        }
        var enactments=new HashSet<CslStateVerifier.Enactment>();
        for(var e:snapshot.enactments()) {
            if(!enactments.add(e))throw new IllegalArgumentException("DUPLICATE_ROLE_ENACTMENT");
            var contextSource=sources.get(e.context());var playerSource=sources.get(e.player());
            if(contextSource==null||playerSource==null)throw new IllegalArgumentException("ROLE_ENDPOINT_UNOBSERVED");
            var contexts=bySource.getOrDefault(e.context(),List.of());if(contexts.isEmpty())continue;
            var context=exact(contexts,"ROLE_CONTEXT_AMBIGUOUS");var role=binding.classifiers().get(e.role());
            if(role==null||role.kind()!=Use75ModelBinding.Kind.ROLE||!context.type().equals(binding.declarationOwners().get(e.role())))throw new IllegalArgumentException("ROLE_NOT_DECLARED_IN_CSL_CONTEXT:"+e.role());
            if(!binding.classifier(e.role()).allAttributes().isEmpty())throw new IllegalArgumentException("ROLE_ATTRIBUTE_PROVIDER_REQUIRED:"+e.role());
            String player=context(e.player(),sources,bySource).identity();
            String id=key(snapshot,e.context()+":"+e.player(),e.role());
            if(rows.putIfAbsent(id,new ObjectRow(id,e.role(),Map.of()))!=null)throw new IllegalArgumentException("ROLE_IDENTITY_COLLISION");
            links.add(new Link("InContext",context.identity(),id));links.add(new Link("Plays",player,id));
        }
        validateContextCycles(snapshot.nodes());
        return new Prepared(List.copyOf(rows.values()),List.copyOf(links));
    }
    private ObjectRow context(String id,Map<String,Source> sources,Map<String,List<ObjectRow>> bySource) {
        var source=sources.get(id);if(source==null||source.kind().equals("artifact"))throw new IllegalArgumentException("ORGCTX_ENDPOINT_UNOBSERVED:"+id);
        var row=exact(bySource.getOrDefault(id,List.of()),"ORGCTX_ENDPOINT_AMBIGUOUS_OR_UNMAPPED:"+id);
        if(binding.classifiers().get(row.type()).kind()!=Use75ModelBinding.Kind.ORG_CONTEXT)throw new IllegalArgumentException("ORGCTX_ENDPOINT_REQUIRED");
        return row;
    }
    private static void validateContextCycles(List<CslStateVerifier.Node> nodes) {
        var parents=new HashMap<String,String>();for(var node:nodes)parents.put(node.identity(),node.parent());
        for(String id:parents.keySet()){var seen=new HashSet<String>();for(String current=id;current!=null&&!current.isBlank();current=parents.get(current))if(!seen.add(current))throw new IllegalArgumentException("PHYSICAL_CONTEXT_CYCLE");}
    }
    private static void put(Map<String,Source> sources,Source source){if(sources.putIfAbsent(source.id(),source)!=null)throw new IllegalArgumentException("DUPLICATE_SOURCE_IDENTITY");}
    private static String key(CslStateVerifier.Snapshot s,String id,String type){return s.run()+":"+id+":"+type;}
    private static ObjectRow exact(List<ObjectRow> values,String error){if(values.size()!=1)throw new IllegalArgumentException(error);return values.getFirst();}
}
