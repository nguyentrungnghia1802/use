package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.semantic.CartagoSemanticContract;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.jacamo.bridge.contract.semantic.SourceEvidence;

import java.util.*;

/**
 * Catalogue indexing source symbols from parsed JaCaMo contracts.
 * Resolves SourceReferences with disambiguation, owner validation, and boundary verification.
 */
public final class SourceSymbolCatalog {

    private final Map<String, SourceSymbol> symbolsByQualified = new LinkedHashMap<>();
    private final Map<String, List<SourceSymbol>> symbolsBySimple = new HashMap<>();
    private final Map<String, Set<String>> artifactMembers = new HashMap<>(); // className -> memberNames
    private final Map<String, Set<String>> orgMembers = new HashMap<>(); // org -> Set of "kind:name"
    private final Map<String, List<PlanInfo>> plansByPath = new HashMap<>(); // aslPath -> List<PlanInfo>
    private final Map<String, String> programHashes = new HashMap<>();
    private final Map<String, List<SourceSymbol>> operationOverloads = new HashMap<>();
    private JacamoSemanticSnapshot snapshot;

    public record PlanInfo(String trigger, String label, int stepCount,String context,String sourceHash) {
        public PlanInfo(String trigger,String label,int stepCount){this(trigger,label,stepCount,"","");}
    }
    public record SelectedPlan(String path,PlanInfo plan) { }

    public SourceSymbolCatalog() {}

    public static SourceSymbolCatalog fromSnapshot(JacamoSemanticSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SourceSymbolCatalog catalog = new SourceSymbolCatalog();
        catalog.snapshot=snapshot;
        if(snapshot.project()!=null) {
            var project=snapshot.project();
            catalog.register(new SourceSymbol(SourceReference.Kind.PROJECT,project.name(),null,project.name(),"JCM_PROJECT",project.sourceDigest(),null));
            catalog.register(new SourceSymbol(SourceReference.Kind.WORKSPACE,"*",project.name(),"*","WORKSPACE_INVENTORY/1.0",project.sourceDigest(),null));
        }
        for(var workspace:snapshot.workspaceDeclarations())catalog.register(new SourceSymbol(SourceReference.Kind.WORKSPACE,workspace.name(),null,workspace.name(),"JCM_WORKSPACE",null,null));
        for(var organization:snapshot.organizationDeployments())catalog.register(new SourceSymbol(SourceReference.Kind.ORGANIZATION,organization.name(),null,organization.name(),"JCM_ORGANIZATION",null,null));

        Map<String, String> artifactSemanticIdToClass = new LinkedHashMap<>();
        Set<String> indexedArtifactClasses = new LinkedHashSet<>();

        // Index Agent Declarations
        if (snapshot.agentDeclarations() != null) {
            for (var agent : snapshot.agentDeclarations()) {
                catalog.register(SourceSymbol.agent(agent.name()));
            }
        }

        // Index JCM artifact declarations
        if (snapshot.artifactDeclarations() != null) {
            for (var artDecl : snapshot.artifactDeclarations()) {
                if (artDecl.javaClass() != null && !artDecl.javaClass().isBlank()) {
                    catalog.register(SourceSymbol.artifact(artDecl.javaClass(), null));
                    if (artDecl.metadata() != null && artDecl.metadata().semanticId() != null) {
                        artifactSemanticIdToClass.put(artDecl.metadata().semanticId(), artDecl.javaClass());
                    }
                    if (artDecl.metadata() != null && artDecl.metadata().evidence() != null) {
                        for (SourceEvidence ev : artDecl.metadata().evidence()) {
                            catalog.indexArtifactClassEvidence(artDecl.javaClass(), ev, indexedArtifactClasses);
                        }
                    }
                }
            }
        }

        // Index CArtAgO Environments
        if (snapshot.cartagoEnvironments() != null) {
            for (var env : snapshot.cartagoEnvironments()) {
                Map<String, String> typeIdToClass = new LinkedHashMap<>();
                if (env.artifactTypes() != null) {
                    for (var at : env.artifactTypes()) {
                        String semId = at.metadata() != null ? at.metadata().semanticId() : null;
                        catalog.register(SourceSymbol.artifact(at.javaClassName(), null));
                        if (semId != null) {
                            typeIdToClass.put(semId, at.javaClassName());
                            artifactSemanticIdToClass.put(semId, at.javaClassName());
                        }
                    }
                }
                if (env.artifacts() != null) {
                    for (var art : env.artifacts()) {
                        String cls = typeIdToClass.get(art.artifactTypeSemanticId());
                        if (cls != null && art.metadata() != null) {
                            artifactSemanticIdToClass.put(art.metadata().semanticId(), cls);
                        }
                    }
                }
                if (env.operations() != null) {
                    for (var op : env.operations()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(op.artifactSemanticId(), op.artifactSemanticId());
                        catalog.register(SourceSymbol.operation(ownerClass, op.name(), op.name() + "/" + op.arity()));
                    }
                }
                if (env.backingOperations() != null) {
                    for (var bop : env.backingOperations()) {
                        if (bop.declaringClass() != null && !bop.declaringClass().isBlank()) {
                            catalog.register(SourceSymbol.operation(bop.declaringClass(), bop.methodName(),
                                    bop.methodName() + "(" + String.join(",", bop.parameterTypes()) + ")"));
                        }
                    }
                }
                if (env.liveProperties() != null) {
                    for (var prop : env.liveProperties()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(prop.artifactSemanticId(), prop.artifactSemanticId());
                        catalog.register(SourceSymbol.property(ownerClass, prop.name()));
                    }
                }
                if (env.propertySnapshots() != null) {
                    for (var prop : env.propertySnapshots()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(prop.artifactSemanticId(), prop.artifactSemanticId());
                        catalog.register(SourceSymbol.property(ownerClass, prop.name()));
                    }
                }
            }
        }

        // Index Moise Organizations
        if (snapshot.moiseOrganizations() != null) {
            for (var org : snapshot.moiseOrganizations()) {
                String orgName = org.name();
                if (org.structuralSpecification() != null) {
                    if (org.structuralSpecification().roles() != null) {
                        for (var role : org.structuralSpecification().roles()) {
                            catalog.register(SourceSymbol.moiseRole(orgName, role.roleId()));
                        }
                    }
                    if (org.structuralSpecification().groups() != null) {
                        for (var group : org.structuralSpecification().groups()) {
                            catalog.register(SourceSymbol.moiseGroup(orgName, group.groupId()));
                        }
                    }
                }
                if (org.functionalSpecification() != null && org.functionalSpecification().schemes() != null) {
                    for (var scheme : org.functionalSpecification().schemes()) {
                        catalog.register(SourceSymbol.moiseScheme(orgName, scheme.schemeId()));
                        if (scheme.goals() != null) {
                            for (var goal : scheme.goals()) {
                                catalog.register(SourceSymbol.moiseGoal(orgName, goal.goalId()));
                            }
                        }
                    }
                }
            }
        }

        // Index Jason Programs
        if (snapshot.jasonPrograms() != null) {
            for (var prog : snapshot.jasonPrograms()) {
                String path = normalizePath(prog.sourceUri());
                if (prog.declarationId() != null && !prog.declarationId().isBlank()) {
                    catalog.register(SourceSymbol.agent(prog.declarationId()));
                }
                String base = basenameWithoutExt(path);
                if (!base.isBlank()) {
                    catalog.register(new SourceSymbol(SourceReference.Kind.AGENT, path, path, base, path, prog.sourceDigest(), prog.sourceUri()));
                }
                String previousHash = catalog.programHashes.putIfAbsent(path, prog.sourceDigest());
                if (previousHash != null && !previousHash.equals(prog.sourceDigest()))
                    throw new IllegalArgumentException("SOURCE_PROGRAM_HASH_CONFLICT:" + path);
                boolean firstProgram = previousHash == null;
                if (firstProgram && prog.goals() != null) {
                    for (var g : prog.goals()) {
                        catalog.register(SourceSymbol.aslGoal(path, g.literal()));
                    }
                }
                if (firstProgram && prog.planLibrary() != null && prog.planLibrary().plans() != null) {
                    for (var plan : prog.planLibrary().plans()) {
                        String triggerStr = plan.trigger() != null ? plan.trigger().literal() : "";
                        int bodySize = plan.body() != null ? plan.body().size() : 0;
                        catalog.registerPlan(path, triggerStr, plan.label(), bodySize,plan.context(),prog.sourceDigest());
                        if (triggerStr.startsWith("+!") || triggerStr.startsWith("-!") || triggerStr.startsWith("!")) {
                            catalog.register(SourceSymbol.aslGoal(path, triggerStr));
                        }
                    }
                }
                if (prog.actions() != null) {
                    for (var action : prog.actions()) {
                        if (action.metadata() != null && action.metadata().evidence() != null) {
                            for (SourceEvidence ev : action.metadata().evidence()) {
                                if (ev.sourceJavaFqcn() != null && !ev.sourceJavaFqcn().isBlank()) {
                                    catalog.indexArtifactClassEvidence(ev.sourceJavaFqcn(), ev, indexedArtifactClasses);
                                }
                            }
                        }
                    }
                }
            }
        }

        for(var symbol:catalog.allSymbols())if(symbol.kind()==SourceReference.Kind.ARTIFACT_FIELD&&symbol.sourceDigest()!=null) {
            var owner=catalog.symbolsByQualified.get(SourceReference.Kind.ARTIFACT+":"+symbol.owner());
            if(owner==null||!symbol.sourceDigest().equals(owner.sourceDigest())||!Objects.equals(symbol.sourcePosition(),owner.sourcePosition()))
                throw new IllegalArgumentException("SOURCE_FIELD_CLASS_PROOF_MISMATCH:"+symbol.qualifiedName());
        }
        return catalog;
    }

    private void indexArtifactClassEvidence(String fqcn, SourceEvidence ev, Set<String> indexedArtifactClasses) {
        if (fqcn == null || fqcn.isBlank()) return;
        if (ev.authority() != org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API
                || ev.fidelity() != org.jacamo.bridge.contract.semantic.Fidelity.EXACT
                || ev.capabilityStatus() != org.jacamo.bridge.contract.CapabilityStatus.COMPLETE
                || !fqcn.equals(ev.sourceJavaFqcn()) || !ev.sourceDigest().matches("[0-9a-f]{64}")) return;
        if (ev.diagnostics().stream().anyMatch(t -> t.endsWith("CARTAGO_ARTIFACT_APPLICATION_CLASS"))) {
            register(new SourceSymbol(SourceReference.Kind.ARTIFACT,fqcn,null,fqcn,fqcn,ev.sourceDigest(),ev.sourceUri()));
        }
        if(ev.diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_FIELD") || ev.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_FIELD")) {
            var signatures=ev.diagnostics().stream().filter(t->t.startsWith("FIELD_SIGNATURE:")).toList();
            if(signatures.size()!=1||!ev.diagnostics().contains("OWNER:"+fqcn))throw new IllegalArgumentException("SOURCE_FIELD_INTERFACE_PROOF_REQUIRED:"+fqcn);
            Object value=org.jacamo.bridge.contract.CanonicalJson.decode(signatures.getFirst().substring("FIELD_SIGNATURE:".length()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!(value instanceof List<?> parts)||parts.size()!=3||!fqcn.equals(parts.get(0))||!(parts.get(1) instanceof String name)||!(parts.get(2) instanceof String type))
                throw new IllegalArgumentException("SOURCE_FIELD_INTERFACE_INVALID:"+fqcn);
            register(new SourceSymbol(SourceReference.Kind.ARTIFACT_FIELD,fqcn+"/"+name,fqcn,name,type,ev.sourceDigest(),ev.sourceUri()));
        }
        for (String diagnostic : ev.diagnostics()) if (diagnostic.startsWith("SIGNATURE:")) {
            Object value = org.jacamo.bridge.contract.CanonicalJson.decode(diagnostic.substring(10).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!(value instanceof List<?> signature) || signature.size() != 4 || !fqcn.equals(signature.get(0))
                    || !(signature.get(1) instanceof String name) || !(signature.get(2) instanceof List<?>))
                throw new IllegalArgumentException("SOURCE_OPERATION_SIGNATURE_INVALID:" + fqcn);
            register(new SourceSymbol(SourceReference.Kind.ARTIFACT_OPERATION, fqcn + "/" + name, fqcn, name,
                    diagnostic.substring(10), ev.sourceDigest(), ev.sourceUri()));
        }
    }

    public void register(SourceSymbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        String qKey = symbol.kind() + ":" + symbol.qualifiedName();
        if(symbol.kind()==SourceReference.Kind.ARTIFACT_OPERATION) {
            var overloads=operationOverloads.computeIfAbsent(symbol.qualifiedName(),k->new ArrayList<>());
            if(overloads.stream().anyMatch(s->s.equals(symbol)))return;
            var previousSignature=overloads.stream().filter(s->Objects.equals(s.signature(),symbol.signature())).findFirst();
            if(previousSignature.isPresent()) {
                var previous=previousSignature.get();
                if(previous.sourceDigest()!=null&&symbol.sourceDigest()!=null&&!previous.sourceDigest().equals(symbol.sourceDigest()))
                    throw new IllegalArgumentException("SOURCE_OPERATION_PROOF_CONFLICT:"+symbol.qualifiedName());
                if(previous.sourceDigest()!=null||symbol.sourceDigest()==null)return;
                overloads.remove(previous);
            }
            overloads.add(symbol);return;
        }
        var previous = symbolsByQualified.putIfAbsent(qKey, symbol);
        if (previous != null) {
            if (previous.equals(symbol)) return;
            if (previous.sourceDigest() == null && symbol.sourceDigest() != null) symbolsByQualified.put(qKey, symbol);
            else if (symbol.sourceDigest() == null) return;
            else throw new IllegalArgumentException("SOURCE_SYMBOL_CONFLICT:" + qKey);
        }
        var simple = symbolsBySimple.computeIfAbsent(symbol.simpleName(), k -> new ArrayList<>());
        if (previous != null) simple.remove(previous);
        simple.add(symbol);

        if (symbol.owner() != null) {
            if (symbol.kind() == SourceReference.Kind.ARTIFACT_PROPERTY
                    || symbol.kind() == SourceReference.Kind.ARTIFACT_FIELD
                    || symbol.kind() == SourceReference.Kind.ARTIFACT_OPERATION) {
                artifactMembers.computeIfAbsent(symbol.owner(), k -> new HashSet<>()).add(symbol.kind() + ":" + symbol.simpleName());
            } else if (symbol.kind() == SourceReference.Kind.MOISE_GROUP
                    || symbol.kind() == SourceReference.Kind.MOISE_ROLE
                    || symbol.kind() == SourceReference.Kind.MOISE_SCHEME
                    || symbol.kind() == SourceReference.Kind.MOISE_GOAL) {
                orgMembers.computeIfAbsent(symbol.owner(), k -> new HashSet<>()).add(symbol.kind() + ":" + symbol.simpleName());
            }
        }
    }

    public void registerPlan(String aslPath, String trigger, String label, int stepCount) {
        String normPath = normalizePath(aslPath);
        plansByPath.computeIfAbsent(normPath, k -> new ArrayList<>()).add(new PlanInfo(trigger, label, stepCount));
    }
    public void registerPlan(String path,String trigger,String label,int count,String context,String hash) {
        plansByPath.computeIfAbsent(normalizePath(path),k->new ArrayList<>()).add(new PlanInfo(trigger,label,count,context,hash));
    }
    public SelectedPlan selectedPlan(SourceReference.AslPlanStepRef reference) {
        resolve(reference);
        String path=normalizePath(reference.aslPath());
        if(!plansByPath.containsKey(path)) {
            final String requestedPath=path;
            var candidates=plansByPath.keySet().stream().filter(p->p.endsWith("/"+requestedPath)).toList();
            if(candidates.size()!=1)throw new IllegalArgumentException("ASL_SOURCE_PATH_NOT_UNIQUE:"+path);
            path=candidates.getFirst();
        }
        var matching=matchPlans(plansByPath.get(path),reference.planTrigger());
        if(matching.size()!=1)throw new IllegalArgumentException("ASL_SOURCE_PLAN_NOT_UNIQUE:"+reference.raw());
        var plan=matching.getFirst();
        if(!Objects.toString(plan.sourceHash(),"").matches("[0-9a-f]{64}"))throw new IllegalArgumentException("ASL_SOURCE_DIGEST_REQUIRED:"+reference.raw());
        return new SelectedPlan(path,plan);
    }
    /** An explicit runtime selector is not a claim that the property exists.
     * Its pinned class must exist; only an actual qualified property cut can
     * establish a value. Missing/ambiguous runtime members remain unavailable. */
    public SourceSymbol select(CompactMappingSpecification.Correspondence mapping) {
        var reference=SourceReference.parse(mapping.source());
        if(mapping.attributes().getOrDefault("policy","").equals("OBSERVED_PROPERTY/1.0")) {
            if(!(reference instanceof SourceReference.ArtifactPropertyRef property))throw mapping.location().error("OBSERVED_PROPERTY_POLICY_REQUIRES_PROPERTY_SELECTOR");
            var owner=resolve(new SourceReference.ArtifactRef(property.className(),"artifact:"+property.className()));
            if(!Objects.toString(owner.sourceDigest(),"").matches("[0-9a-f]{64}"))throw mapping.location().error("OBSERVED_PROPERTY_PINNED_CLASS_REQUIRED");
            return new SourceSymbol(SourceReference.Kind.ARTIFACT_PROPERTY_SELECTION,owner.qualifiedName()+"/"+property.propertyName(),owner.qualifiedName(),property.propertyName(),
                    "RUNTIME_PROPERTY_SELECTION/1.0",owner.sourceDigest(),owner.sourcePosition());
        }
        return resolve(reference);
    }
    public Map<String,Object> requestPin(SourceReference reference) {
        if(snapshot==null||snapshot.project()==null)throw new IllegalArgumentException("CAPABILITY_OFFICIAL_SOURCE_PIN_REQUIRED:"+reference.raw());
        List<SourceEvidence> evidence;
        if(reference instanceof SourceReference.MoiseSchemeRef selected) {
            var organizations=snapshot.moiseOrganizations().stream().filter(o->o.name().equals(selected.organization())).toList();
            if(organizations.size()!=1)throw new IllegalArgumentException("CAPABILITY_SOURCE_ORGANIZATION_NOT_UNIQUE");
            evidence=organizations.getFirst().metadata().evidence();
        } else evidence=snapshot.project().metadata().evidence();
        var pins=evidence.stream().filter(e->e.sourceUri().startsWith("project:/")&&e.sourceDigest().matches("[0-9a-f]{64}"))
                .map(e->Map.<String,Object>of("sourceRef",reference.raw(),"sourcePath",e.sourceUri().substring("project:/".length()),"sourceHash",e.sourceDigest())).distinct().toList();
        if(pins.size()!=1)throw new IllegalArgumentException("CAPABILITY_EXACT_PROJECT_SOURCE_PIN_REQUIRED:"+reference.raw());
        return pins.getFirst();
    }

    public List<SourceSymbol> allSymbols() {
        var all=new ArrayList<>(symbolsByQualified.values());operationOverloads.values().forEach(all::addAll);
        return all.stream().sorted(Comparator.comparing((SourceSymbol s)->s.kind().name()).thenComparing(SourceSymbol::qualifiedName)
                .thenComparing(s->Objects.toString(s.signature(),""))).toList();
    }

    public SourceSymbol resolve(SourceReference ref) {
        Objects.requireNonNull(ref, "ref");
        switch (ref) {
            case SourceReference.ProjectRef r -> {return requiredNamed(SourceReference.Kind.PROJECT,r.name());}
            case SourceReference.WorkspaceRef r -> {return requiredNamed(SourceReference.Kind.WORKSPACE,r.name());}
            case SourceReference.WorkspaceIdentityRef r -> {
                var owner=requiredNamed(SourceReference.Kind.WORKSPACE,r.name());
                return new SourceSymbol(SourceReference.Kind.WORKSPACE_IDENTITY,r.name()+"/identity:"+r.field(),owner.qualifiedName(),r.field(),"CARTAGO_WORKSPACE_IDENTITY/1.0",owner.sourceDigest(),owner.sourcePosition());
            }
            case SourceReference.OrganizationRef r -> {return requiredNamed(SourceReference.Kind.ORGANIZATION,r.name());}
            case SourceReference.ArtifactIdentityRef r -> {
                var owner=findArtifactOrThrow(r.className());
                return new SourceSymbol(SourceReference.Kind.ARTIFACT_IDENTITY,owner.qualifiedName()+"/identity:"+r.field(),owner.qualifiedName(),r.field(),"CARTAGO_PHYSICAL_IDENTITY/1.0",owner.sourceDigest(),owner.sourcePosition());
            }
            case SourceReference.ArtifactRef a -> {
                SourceSymbol s = findArtifact(a.className());
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_SYMBOL_NOT_FOUND: artifact '" + a.className() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactPropertyRef p -> {
                SourceSymbol art = findArtifactOrThrow(p.className());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_PROPERTY + ":" + p.className() + "/" + p.propertyName());
                if (s == null && art != null) {
                    s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_PROPERTY + ":" + art.qualifiedName() + "/" + p.propertyName());
                }
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_PROPERTY_NOT_FOUND: '" + p.className() + "/" + p.propertyName() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactFieldRef f -> {
                SourceSymbol art = findArtifactOrThrow(f.className());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_FIELD + ":" + f.className() + "/" + f.fieldName());
                if (s == null && art != null) {
                    s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_FIELD + ":" + art.qualifiedName() + "/" + f.fieldName());
                }
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_FIELD_NOT_FOUND: '" + f.className() + "/" + f.fieldName() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactOpRef o -> {
                SourceSymbol art = findArtifactOrThrow(o.className());
                var candidates=operationOverloads.getOrDefault(art.qualifiedName()+"/"+o.operationName(),List.of());
                if(o.raw().contains("("))candidates=candidates.stream().filter(s->operationParameters(s.signature()).equals(o.paramTypes())).toList();
                if(candidates.size()!=1)throw new IllegalArgumentException("SOURCE_OPERATION_NOT_FOUND_OR_AMBIGUOUS:"+o.raw());
                return candidates.getFirst();
            }
            case SourceReference.MoiseGroupRef g -> {
                checkOrgExists(g.organization());
                String qKey = SourceReference.Kind.MOISE_GROUP + ":" + g.organization() + "/" + g.groupName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_GROUP_NOT_FOUND: '" + g.organization() + "/" + g.groupName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseRoleRef r -> {
                checkOrgExists(r.organization());
                String qKey = SourceReference.Kind.MOISE_ROLE + ":" + r.organization() + "/" + r.roleName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_ROLE_NOT_FOUND: '" + r.organization() + "/" + r.roleName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseSchemeRef sc -> {
                checkOrgExists(sc.organization());
                String qKey = SourceReference.Kind.MOISE_SCHEME + ":" + sc.organization() + "/" + sc.schemeName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_SCHEME_NOT_FOUND: '" + sc.organization() + "/" + sc.schemeName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseGoalRef mg -> {
                checkOrgExists(mg.organization());
                String qKey = SourceReference.Kind.MOISE_GOAL + ":" + mg.organization() + "/" + mg.goalName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_GOAL_NOT_FOUND: '" + mg.organization() + "/" + mg.goalName() + "'");
                }
                return s;
            }
            case SourceReference.AgentRef ag -> {
                String qKey = SourceReference.Kind.AGENT + ":" + ag.agentOrProgram();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    List<SourceSymbol> list = symbolsBySimple.get(ag.agentOrProgram());
                    if (list != null && !list.isEmpty()) {
                        List<SourceSymbol> agentSymbols = list.stream().filter(x -> x.kind() == SourceReference.Kind.AGENT).toList();
                        if (agentSymbols.size() == 1) return agentSymbols.get(0);
                        if (agentSymbols.size() > 1) {
                            throw new IllegalArgumentException("AMBIGUOUS_AGENT_SYMBOL: '" + ag.agentOrProgram() + "' matches multiple candidates");
                        }
                    }
                    throw new IllegalArgumentException("SOURCE_AGENT_NOT_FOUND: '" + ag.agentOrProgram() + "'");
                }
                return s;
            }
            case SourceReference.AslGoalRef g -> {
                String norm = normalizePath(g.aslPath());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ASL_GOAL + ":" + norm + "/" + g.goalTrigger());
                if (s != null) return s;

                List<PlanInfo> plans = findPlansByPath(norm);
                if (plans != null && !plans.isEmpty()) {
                    List<PlanInfo> matching = matchPlans(plans, g.goalTrigger());
                    if (matching.size() == 1) {
                        return SourceSymbol.aslGoal(norm, g.goalTrigger());
                    }
                    if (matching.size() > 1) throw new IllegalArgumentException("AMBIGUOUS_ASL_GOAL_PLAN:" + g.raw());
                }
                throw new IllegalArgumentException("SOURCE_ASL_GOAL_NOT_FOUND: '" + g.raw() + "'");
            }
            case SourceReference.AslPlanStepRef ps -> {
                String norm = normalizePath(ps.aslPath());
                List<PlanInfo> plans = findPlansByPath(norm);
                if (plans == null || plans.isEmpty()) {
                    throw new IllegalArgumentException("SOURCE_ASL_PLAN_NOT_FOUND: no plans registered for '" + ps.aslPath() + "'");
                }
                List<PlanInfo> matching = matchPlans(plans, ps.planTrigger());
                if (matching.isEmpty()) {
                    throw new IllegalArgumentException("SOURCE_ASL_PLAN_NOT_FOUND: '" + ps.planTrigger() + "' in " + ps.aslPath());
                }
                if (matching.size() > 1) {
                    throw new IllegalArgumentException("AMBIGUOUS_PLAN_TRIGGER: '" + ps.planTrigger() + "' matches multiple plans in " + ps.aslPath());
                }
                PlanInfo p = matching.get(0);
                if (ps.firstIndex() < 0 || ps.afterIndex() <= ps.firstIndex() || ps.afterIndex() > p.stepCount()) {
                    throw new IllegalArgumentException("PLAN_STEP_OUT_OF_BOUNDS: [" + ps.firstIndex() + ".." + ps.afterIndex() + "] exceeds plan step count " + p.stepCount());
                }
                return SourceSymbol.aslPlanStep(norm, ps.planTrigger(), ps.stepName(), ps.firstIndex(), ps.afterIndex());
            }
        }
    }
    private SourceSymbol requiredNamed(SourceReference.Kind kind,String name) {
        var found=symbolsByQualified.get(kind+":"+name);
        if(found==null)throw new IllegalArgumentException("SOURCE_NAMED_SYMBOL_NOT_FOUND:"+kind+":"+name);
        return found;
    }

    private List<PlanInfo> findPlansByPath(String normPath) {
        List<PlanInfo> direct = plansByPath.get(normPath);
        if (direct != null) return direct;
        var candidates = plansByPath.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("/" + normPath) || normPath.endsWith("/" + entry.getKey()))
                .toList();
        if (candidates.size() > 1) throw new IllegalArgumentException("AMBIGUOUS_ASL_SOURCE_PATH:" + normPath);
        return candidates.isEmpty() ? null : candidates.getFirst().getValue();
    }

    private static List<PlanInfo> matchPlans(List<PlanInfo> plans, String requestedTrigger) {
        String reqStripped = stripTriggerPrefix(requestedTrigger);
        List<PlanInfo> exact = plans.stream()
                .filter(p -> (p.label() != null && p.label().equals(requestedTrigger))
                        || stripTriggerPrefix(p.trigger()).equals(reqStripped))
                .toList();
        if (!exact.isEmpty()) return exact;

        // Only an explicitly unqualified functor may select by functor. A wrong
        // argument list must not resolve to a different source plan.
        if (reqStripped.contains("(") || reqStripped.contains("[")) return List.of();

        String reqFunctor = extractFunctor(requestedTrigger);
        return plans.stream()
                .filter(p -> extractFunctor(p.trigger()).equals(reqFunctor)
                        || (p.label() != null && extractFunctor(p.label()).equals(reqFunctor)))
                .toList();
    }

    private static String stripTriggerPrefix(String trigger) {
        if (trigger == null) return "";
        String s = trigger.trim();
        while (!s.isEmpty() && ("+ - ! ?".indexOf(s.charAt(0)) >= 0)) {
            s = s.substring(1).trim();
        }
        return s;
    }

    private static List<String> operationParameters(String signature) {
        if(signature==null)return List.of("<unavailable>");
        if(signature.startsWith("[")) {
            Object raw=org.jacamo.bridge.contract.CanonicalJson.decode(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(raw instanceof List<?> parts&&parts.size()==4&&parts.get(2) instanceof List<?> params)
                return params.stream().map(String.class::cast).toList();
        }
        int first=signature.indexOf('('),last=signature.lastIndexOf(')');
        if(first>=0&&last==signature.length()-1) {
            String inside=signature.substring(first+1,last).trim();
            return inside.isEmpty()?List.of():Arrays.stream(inside.split(",")).map(String::trim).toList();
        }
        return List.of("<unavailable>");
    }

    private static String extractFunctor(String trigger) {
        String s = stripTriggerPrefix(trigger);
        int paren = s.indexOf('(');
        return paren >= 0 ? s.substring(0, paren).trim() : s;
    }

    private static String basenameWithoutExt(String path) {
        if (path == null || path.isBlank()) return "";
        String norm = normalizePath(path);
        int slash = norm.lastIndexOf('/');
        String name = slash >= 0 ? norm.substring(slash + 1) : norm;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private SourceSymbol findArtifact(String name) {
        String qKey = SourceReference.Kind.ARTIFACT + ":" + name;
        SourceSymbol s = symbolsByQualified.get(qKey);
        if (s != null) return s;

        List<SourceSymbol> matches = symbolsByQualified.values().stream()
                .filter(x -> x.kind() == SourceReference.Kind.ARTIFACT
                        && (x.qualifiedName().equals(name) || x.qualifiedName().endsWith("." + name)))
                .toList();
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) {
            throw new IllegalArgumentException("AMBIGUOUS_ARTIFACT_NAME: '" + name + "' matches multiple artifact classes");
        }
        return null;
    }

    private SourceSymbol findArtifactOrThrow(String className) {
        SourceSymbol s = findArtifact(className);
        if (s == null) {
            throw new IllegalArgumentException("SOURCE_ARTIFACT_NOT_FOUND: '" + className + "'");
        }
        return s;
    }

    private void checkOrgExists(String orgName) {
        if (!orgMembers.containsKey(orgName)) {
            boolean found = symbolsByQualified.values().stream().anyMatch(x -> Objects.equals(x.owner(), orgName));
            if (!found) {
                throw new IllegalArgumentException("SOURCE_ORGANIZATION_NOT_FOUND: '" + orgName + "'");
            }
        }
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String norm = path.replace('\\', '/').trim();
        if (norm.startsWith("project:/")) {
            norm = norm.substring("project:/".length());
        }
        return norm.replaceAll("^\\./", "");
    }
}
