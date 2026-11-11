from pathlib import Path
p = Path('use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/xml/GenericMappingEngine.java')
s = p.read_text(encoding='utf-8')
a = s.index('        // Ensure all CSL entities, roles, and orgContexts')
b = s.index('        // 4.2 iStar', a)
s = s[:a] + '''        // Unmapped declarations remain model definitions. They do not acquire
        // a source object, role, group or runtime population by position/name.
        String projectNodeId = findProjectNodeId(source);

''' + s[b:]
a = s.index('            String actorSourceId = ib != null ?')
b = s.index('            actorTemplates.put(', a)
s = s[:a] + '''            ActorRuntimeBinding ar = explicitActorRuntimes.get(actor.name());
            if (ib == null) throw new IllegalArgumentException("COMPACT_ACTOR_SOURCE_MAPPING_REQUIRED:" + actor.name());
            String actorSourceId = resolveSourceNodeId(source, ib.sourceRef(), SemanticBindingIndex.Kind.ACTOR, new TreeMap<>());
            String roleSourceId = ib.sourceRef() instanceof SourceReference.MoiseRoleRef ? actorSourceId : "";
            String contextSourceId = "";
            String activationPolicy = ar != null ? ar.activationPolicy() : "EXPLICIT_ENACTMENT";
            String episodePolicy = ar != null ? ar.episodePolicy() : "STATIC_LIFETIME";
''' + s[b:]
a = s.index('        String defaultSelfName = ')
b = s.index('            for (IntentionalElement elem : actor.elements())', a)
s = s[:a] + '''        var elementTemplates = new TreeMap<String, GoalRuntimeBindingPlan.ElementTemplate>();
        for (Actor actor : istarModel.getActors()) {
            String actorSelf = compactSpec.actorRuntimes().stream().filter(a -> a.actor().equals(actor.name()))
                    .map(a -> a.attributes().getOrDefault("self", "")).findFirst().orElse("");
''' + s[b:]
a = s.index('                elementTemplates.putIfAbsent(elemId,')
b = s.index('                IStarBinding eb = ', a)
s = s[:a] + '''                String selfDecl = compactSpec.istarMappings().stream()
                        .filter(c -> targetCatalog.resolve(TargetReference.parse(c.target()), "ISTAR").name().equals(elemId))
                        .map(c -> c.attributes().getOrDefault("self", actorSelf)).findFirst().orElse(actorSelf);
                if (!structural && !contractExpr.isBlank() && selfDecl.isBlank())
                    throw new IllegalArgumentException("COMPACT_EXPLICIT_NATIVE_SELF_REQUIRED:" + elemId);
                String selfKind = selfDecl.isBlank() ? "" : cslModel.findEntity(selfDecl).isPresent() ? "ENTITY"
                        : cslModel.findRole(selfDecl).isPresent() ? "ROLE" : cslModel.findOrgContext(selfDecl).isPresent() ? "ORG_CONTEXT" : "";
                if (!selfDecl.isBlank() && selfKind.isBlank()) throw new IllegalArgumentException("COMPACT_NATIVE_SELF_NOT_FOUND:" + selfDecl);
                final String receiver = selfDecl;
                String selfSourceId = selfDecl.isBlank() ? "" : bindings.stream()
                        .filter(b -> Set.of(SemanticBindingIndex.Kind.ENTITY, SemanticBindingIndex.Kind.ROLE, SemanticBindingIndex.Kind.ORG_CONTEXT).contains(b.kind()) && b.targetId().equals(receiver))
                        .map(SemanticBindingIndex.Binding::sourceId).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("COMPACT_NATIVE_SELF_SOURCE_REQUIRED:" + receiver));
                elementTemplates.put(elemId, new GoalRuntimeBindingPlan.ElementTemplate(
                        elemId, actor.name(), structural, selfDecl, selfSourceId, selfKind, contractExpr));

''' + s[b:]
a = s.index('    private static String findProjectNodeId(')
s = s[:a] + '''    private static String findProjectNodeId(NormalizedSourceModel source) {
        return uniqueSource(source.nodes().stream().filter(n -> "JACAMO_PROJECT".equals(n.sourceKind())).toList(), "project");
    }
    private static String feature(NormalizedSourceModel.Node node,String key) {
        var value=node.features().get(key); return value != null && value.value() instanceof String text ? text : "";
    }
    private static String uniqueSource(List<NormalizedSourceModel.Node> candidates,String reference) {
        if(candidates.size()!=1) throw new IllegalArgumentException("COMPACT_SOURCE_NODE_UNAVAILABLE_OR_AMBIGUOUS:"+reference+":"+candidates.stream().map(NormalizedSourceModel.Node::id).toList());
        return candidates.getFirst().id();
    }
    private static boolean organization(NormalizedSourceModel source,NormalizedSourceModel.Node node,String name) {
        var visited=new HashSet<String>();
        while(node!=null && visited.add(node.id())) {
            if(node.sourceKind().equals("MOISE_ORGANIZATION")) return name.equals(feature(node,"name"));
            String parent=node.containerId();
            node=source.nodes().stream().filter(n -> n.id().equals(parent)).findFirst().orElse(null);
        }
        return false;
    }
    private static String resolveSourceNodeId(NormalizedSourceModel source, SourceReference ref,
            SemanticBindingIndex.Kind targetKind, Map<String,String> meta) {
        String type=switch(ref) {
            case SourceReference.ArtifactRef r -> r.className();
            case SourceReference.ArtifactPropertyRef r -> r.className();
            case SourceReference.ArtifactFieldRef r -> r.className();
            case SourceReference.ArtifactOpRef r -> r.className();
            default -> "";
        };
        if(!type.isBlank()) {
            String id=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.artifactId(type);
            return uniqueSource(source.nodes().stream().filter(n -> n.id().equals(id)).toList(), ref.raw());
        }
        var candidates=source.nodes().stream().filter(n -> switch(ref) {
            case SourceReference.MoiseGroupRef r -> n.concept().equals("Group") && r.groupName().equals(feature(n,"groupId")) && organization(source,n,r.organization());
            case SourceReference.MoiseRoleRef r -> n.concept().equals("Role") && r.roleName().equals(feature(n,"roleId")) && organization(source,n,r.organization());
            case SourceReference.MoiseSchemeRef r -> n.sourceKind().equals("MOISE_SCHEME") && r.schemeName().equals(feature(n,"schemeId")) && organization(source,n,r.organization());
            case SourceReference.MoiseGoalRef r -> n.sourceKind().equals("MOISE_GOAL") && r.goalName().equals(feature(n,"goalId")) && organization(source,n,r.organization());
            case SourceReference.AgentRef r -> n.sourceKind().equals("JACAMO_AGENT_DECLARATION") && r.agentOrProgram().equals(feature(n,"name"))
                    || n.sourceKind().equals("JASON_AGENT_PROGRAM") && programPath(feature(n,"sourceUri"), r.agentOrProgram());
            case SourceReference.AslGoalRef r -> n.sourceKind().equals("JASON_AGENT_PROGRAM") && programPath(feature(n,"sourceUri"),r.aslPath());
            case SourceReference.AslPlanStepRef r -> n.sourceKind().equals("JASON_AGENT_PROGRAM") && programPath(feature(n,"sourceUri"),r.aslPath());
            default -> false;
        }).toList();
        return uniqueSource(candidates, ref.raw());
    }
    private static boolean programPath(String actual,String selected) {
        String normalized=actual.replace('\\\\','/').replaceFirst("^project:/", "");
        String path=selected.replace('\\\\','/');
        if(!path.endsWith(".asl")) path += ".asl";
        return normalized.equals(path) || !path.contains("/") && normalized.endsWith("/"+path);
    }
}
'''
p.write_text(s, encoding='utf-8')
