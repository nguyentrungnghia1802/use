package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SourceArtifactTypeReferences;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;

/** Pure case crosswalk over an already admitted cut and its accepted event prefix.
 * Owns no runtime, history, model, network connection or framework class loader. */
public final class AuctionArtifactBindingResolver {
    public record Run(String id,long generation,String modelRevision) {
        public Run {required(id,"RUN");required(modelRevision,"REVISION");if(generation<0)throw invalid("GENERATION");}
    }
    public enum Availability { INCOMPLETE, UNSUPPORTED }
    public record Diagnostic(String schemeIdentity,Availability availability,String code) { }
    public record Binding(Run run,String organizationDeclaration,String organizationWorkspace,String organizationWorkspaceUuid,
            String schemeIdentity,String schemeUuid,String artifactIdentity,String artifactUuid,String workspaceIdentity,String workspaceUuid,
            String creator,String auctionId,String idConversion,String service,String serviceConversion,String witnessEvent,String episode,String cutHash) { }
    public record Resolution(List<Binding> bindings,List<Diagnostic> diagnostics) {
        public Resolution {bindings=List.copyOf(bindings);diagnostics=List.copyOf(diagnostics);}
    }
    private record Candidate(RuntimeFact artifact,RuntimeFact workspace,RuntimeEvent witness,String creator) { }
    private record Argument(String value,String conversion) { }
    private static final List<?> CREATE_SIGNATURE=List.of("cartago.WorkspaceArtifact","makeArtifact",
            List.of("java.lang.String","java.lang.String","[Ljava.lang.Object;","cartago.OpFeedbackParam"),"void");
    private static final List<?> LOOKUP_SIGNATURE=List.of("cartago.WorkspaceArtifact","lookupArtifact",
            List.of("java.lang.String","cartago.OpFeedbackParam"),"void");
    public static boolean discoverySignature(Object value){return CREATE_SIGNATURE.equals(value)||LOOKUP_SIGNATURE.equals(value);}
    private final String revision,javaClass,classHash,provider,resource,organizationSpec,schemeSpec;

    public AuctionArtifactBindingResolver(ModelSnapshot source,AuctionSelectorResult selectors) {
        if(!selectors.sourceHash().equals(new org.tzi.use.plugins.jacamo.semantics.mapping.SourceModelNormalizer().normalize(source).sourceHash()))
            throw invalid("SELECTOR_SOURCE_MISMATCH");
        revision=source.modelRevision();javaClass=selectors.require("sel-create").getFirst().values().get("className");
        var references=SourceArtifactTypeReferences.fromActions(source).stream().filter(r->r.javaClass().equals(javaClass)).toList();
        if(references.isEmpty())throw invalid("SOURCE_TYPE_PROOF_REQUIRED");
        var proof=references.getFirst().classProof();classHash=proof.sourceDigest();resource=proof.sourceUri();
        provider=proof.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).findFirst().orElseThrow().substring(9);
        if(references.stream().anyMatch(r->!r.classProof().sourceDigest().equals(classHash)||!r.classProof().sourceUri().equals(resource)))
            throw invalid("CONFLICTING_SOURCE_PROVIDERS");
        // Names here belong to the reviewed Auction specification, not generic JaCaMo rules.
        var organizations=source.semanticContract().moiseOrganizations().stream().filter(o->o.name().equals("auction")).toList();
        if(organizations.size()!=1)throw invalid("SOURCE_ORGANIZATION_REQUIRED");
        var organization=organizations.getFirst();organizationSpec=organization.metadata().semanticId();
        var schemes=organization.functionalSpecification().schemes().stream().filter(s->s.schemeId().equals("doAuction")).toList();
        if(schemes.size()!=1||schemes.getFirst().goals().stream().noneMatch(g->g.goalId().equals("auction")))throw invalid("SOURCE_SCHEME_REQUIRED");
        schemeSpec=schemes.getFirst().metadata().semanticId();
    }

    public Resolution resolve(Run run,RuntimeSnapshot cut,List<RuntimeEvent> acceptedEvents) {
        if(!revision.equals(run.modelRevision())||!revision.equals(cut.modelRevision()))throw invalid("MIXED_REVISION");
        if(!cut.startWatermarks().equals(cut.endWatermarks()))throw invalid("UNSTABLE_CUT");
        if(cut.facts().size()>16384||acceptedEvents.size()>8192)throw invalid("CUT_CAPACITY");
        var diagnostics=new ArrayList<Diagnostic>();var result=new ArrayList<Binding>();
        var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&faithful(f)).toList();
        var workspaces=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&faithful(f)).toList();
        var agents=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.AGENT&&faithful(f)).toList();
        var witnesses=admittedWitnesses(run,cut,acceptedEvents);
        var schemes=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD).toList();
        if(schemes.size()>256)throw invalid("SCHEME_CAPACITY");
        for(var fact:schemes) {
            String scope=fact.id().canonical();
            if(!schemeSpec.equals(fact.values().get("schemeSpecSemanticId")))continue;
            try {
                need(faithful(fact),"SCHEME_CUT_INCOMPLETE");
                var observed=FunctionalObservation.decode(fact.id(),fact.values());
                need(organizationSpec.equals(observed.organization().metadata().semanticId()),"ORGANIZATION_DEFINITION_MISMATCH");
                need(fact.id().incarnation().equals(run.id()+":"+observed.artifactUuid()),"SCHEME_RUN_MISMATCH");
                need("1.0.0".equals(fact.values().get("schemeArgumentObservationVersion")),"TYPED_ARGUMENTS_UNAVAILABLE");
                var idArgument=argument(fact,"Id");var serviceArgument=argument(fact,"Service");
                String auction=idArgument.value();
                var board=one(artifacts.stream().filter(a->observed.artifactUuid().equals(a.values().get("uuid"))).toList(),"SCHEME_ARTIFACT_UNRESOLVED");
                var orgWorkspace=workspace(board,workspaces);
                String schemeCreator=text(board.values(),"creatorAgentSemanticId");
                var candidates=new ArrayList<Candidate>();
                for(var artifact:artifacts) {
                    if(!javaClass.equals(artifact.values().get("artifactTypeJavaClassName"))||!auction.equals(artifact.values().get("name")))continue;
                    var ownWorkspace=workspace(artifact,workspaces);
                    String creator=text(artifact.values(),"creatorAgentSemanticId");
                    if(!samePlayer(creator,schemeCreator,agents))continue;
                    if(!providerMatches(artifact))continue;
                    String identity=text(artifact.values(),"semanticId");
                    boolean focused=cut.facts().stream().filter(AuctionArtifactBindingResolver::faithful).anyMatch(f->
                            f.kind()==RuntimeFactKind.RELATION_STATE&&"SET_CARTAGO_FOCUS".equals(f.values().get("normalizedEventKind"))
                            &&identity.equals(f.values().get("artifactSemanticId"))&&creator.equals(f.values().get("agentSemanticId"))
                            &&Boolean.TRUE.equals(f.values().get("focused")));
                    if(!focused)continue;
                    var evidence=witnesses.stream().filter(e->matches(e,artifact,ownWorkspace,artifacts,creator,auction)).toList();
                    if(!evidence.isEmpty())candidates.add(new Candidate(artifact,ownWorkspace,evidence.getLast(),creator));
                }
                var candidate=one(candidates,candidates.isEmpty()?"EXACT_ARTIFACT_WITNESS_UNAVAILABLE":"ARTIFACT_BINDING_AMBIGUOUS");
                String identity=text(candidate.artifact().values(),"semanticId"),uuid=uuid(candidate.artifact().values(),"uuid");
                String episode="auction-episode:"+SemanticJson.hash(List.of(run.id(),run.generation(),scope,identity,uuid));
                result.add(new Binding(run,observed.organizationIdentity(),text(orgWorkspace.values(),"semanticId"),uuid(orgWorkspace.values(),"uuid"),
                        scope,observed.artifactUuid(),identity,uuid,text(candidate.workspace().values(),"semanticId"),uuid(candidate.workspace().values(),"uuid"),
                        candidate.creator(),auction,idArgument.conversion(),serviceArgument.value(),serviceArgument.conversion(),candidate.witness().eventId(),episode,
                        SemanticJson.hash(List.of(observed.cutHash(),cut.stateFingerprint(),identity,candidate.witness().after()))));
            } catch(Unavailable unavailable) {diagnostics.add(new Diagnostic(scope,unavailable.availability,unavailable.getMessage()));}
            catch(IllegalArgumentException|ContractException malformed) {diagnostics.add(new Diagnostic(scope,Availability.UNSUPPORTED,"MALFORMED_BINDING_EVIDENCE:"+malformed.getMessage()));}
        }
        // One physical incarnation cannot silently become self for two independent schemes.
        var counts=new HashMap<String,Integer>();result.forEach(b->counts.merge(b.artifactIdentity(),1,Integer::sum));
        result.removeIf(b->{if(counts.get(b.artifactIdentity())==1)return false;
            diagnostics.add(new Diagnostic(b.schemeIdentity(),Availability.INCOMPLETE,"ARTIFACT_SHARED_BY_AMBIGUOUS_SCHEMES"));return true;});
        result.sort(Comparator.comparing(Binding::schemeIdentity));diagnostics.sort(Comparator.comparing(Diagnostic::schemeIdentity).thenComparing(Diagnostic::code));
        return new Resolution(result,diagnostics);
    }

    private List<RuntimeEvent> admittedWitnesses(Run run,RuntimeSnapshot cut,List<RuntimeEvent> events) {
        var unique=new LinkedHashMap<String,RuntimeEvent>();
        var candidates=new ArrayList<>(events);
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.ARTIFACT&&faithful(fact)&&fact.values().containsKey("auctionDiscoveryWitness")) {
            var receipt=ContractPayloads.event(CanonicalJson.object(fact.values().get("auctionDiscoveryWitness")));
            if(receipt.after().get("typedArgumentValues") instanceof List<?> arguments&&!arguments.isEmpty()) {
                var feedback=CanonicalJson.object(arguments.getLast());var value=CanonicalJson.object(feedback.get("value"));
                if(!Objects.equals(fact.values().get("uuid"),value.get("uuid"))||!Objects.equals(fact.values().get("semanticId"),value.get("semanticId")))throw invalid("EMBEDDED_DISCOVERY_INCARNATION_MISMATCH");
            }else throw invalid("EMBEDDED_DISCOVERY_ARGUMENTS_UNAVAILABLE");
            candidates.add(receipt);
        }
        for(var event:candidates) {
            if(!run.id().equals(event.sessionId())||run.generation()!=event.generation()||!revision.equals(event.modelRevision()))continue;
            if(!"cartago".equals(event.subsystem())||event.kind()!=RuntimeEventKind.SUCCEEDED||event.completeness()!=Completeness.COMPLETE)continue;
            var watermark=cut.endWatermarks().get(event.sourceId());
            if(watermark==null||event.sourceSequence()>watermark.sequence()||!event.sourceId().equals(event.watermark().sourceId())
                    ||event.sourceSequence()!=event.watermark().sequence())continue;
            if(!CREATE_SIGNATURE.equals(event.after().get("operationSignature"))&&!LOOKUP_SIGNATURE.equals(event.after().get("operationSignature")))continue;
            if(event.factKind()!=RuntimeFactKind.ARTIFACT||event.entityId()==null||event.correlationId().isBlank()
                    ||!event.correlationId().equals(event.after().get("opId")))continue;
            var previous=unique.putIfAbsent(event.eventId(),event);
            if(previous!=null&&!previous.equals(event))throw invalid("EVENT_ID_CONFLICT");
        }
        return unique.values().stream().sorted(Comparator.comparingLong(RuntimeEvent::sourceSequence).thenComparing(RuntimeEvent::eventId)).toList();
    }
    private boolean matches(RuntimeEvent event,RuntimeFact artifact,RuntimeFact workspace,List<RuntimeFact> artifacts,String creator,String auction) {
        var data=event.after();
        if(!"1.0.0".equals(data.get("argumentObservationVersion"))||!"CARTAGO_OPID_AGENT_BODY_ID".equals(data.get("performerAuthority"))
                ||!creator.equals(data.get("performerSemanticId"))||!"completed".equals(data.get("phase")))return false;
        if(!(data.get("typedArgumentValues") instanceof List<?> arguments))return false;
        var invoked=artifacts.stream().filter(f->f.id().equals(event.entityId())).toList();
        if(invoked.size()!=1||!"cartago.WorkspaceArtifact".equals(invoked.getFirst().values().get("artifactTypeJavaClassName"))
                ||!Objects.equals(invoked.getFirst().values().get("semanticId"),data.get("artifactSemanticId"))
                ||!Objects.equals(invoked.getFirst().values().get("workspaceSemanticId"),workspace.values().get("semanticId")))return false;
        boolean creation=CREATE_SIGNATURE.equals(data.get("operationSignature"));
        if(arguments.size()!=(creation?4:2))return false;
        try {
            if(!auction.equals(javaString(arguments.getFirst())))return false;
            if(creation&&(!javaClass.equals(javaString(arguments.get(1)))||!"ARRAY".equals(object(arguments.get(2)).get("kind"))
                    ||!List.of().equals(object(arguments.get(2)).get("values"))))return false;
            var feedback=object(arguments.get(creation?3:1));
            if(!"FEEDBACK".equals(feedback.get("kind"))||!Boolean.TRUE.equals(feedback.get("available")))return false;
            var reference=object(feedback.get("value"));
            return "ARTIFACT_ID".equals(reference.get("kind"))&&text(artifact.values(),"semanticId").equals(reference.get("semanticId"))
                    &&uuid(artifact.values(),"uuid").equals(reference.get("uuid"))&&auction.equals(reference.get("name"))
                    &&javaClass.equals(reference.get("typeHint"))&&uuid(workspace.values(),"uuid").equals(reference.get("workspaceUuid"))
                    &&text(workspace.values(),"fullName").equals(reference.get("workspaceName"));
        } catch(IllegalArgumentException|Unavailable malformed){return false;}
    }
    private boolean providerMatches(RuntimeFact artifact) {
        try {
            var proof=object(artifact.values().get("artifactProviderObservation"));
            return "1.0.0".equals(proof.get("version"))&&"COMPLETE".equals(proof.get("status"))&&javaClass.equals(proof.get("javaClass"))
                    &&classHash.equals(proof.get("classHash"))&&provider.equals(proof.get("provider"))&&resource.equals(proof.get("resource"))
                    &&uuid(artifact.values(),"uuid").equals(proof.get("artifactUuid"));
        }catch(IllegalArgumentException|Unavailable malformed){return false;}
    }
    private static Argument argument(RuntimeFact fact,String name) {
        var roots=object(fact.values().get("schemeArgumentTerms"));var goal=object(roots.get("auction"));var value=object(goal.get(name));
        need(Boolean.TRUE.equals(value.get("supported"))&&Boolean.TRUE.equals(value.get("ground")),"ARGUMENT_UNKNOWN:"+name);
        var key=object(value.get("key"));
        var raw=object(object(fact.values().get("schemeArguments")).get("auction"));
        need(text(value,"raw").equals(raw.get(name)),"ARGUMENT_CUT_MISMATCH:"+name);
        if("STRING".equals(key.get("kind")))return new Argument(text(key,"value"),"JASON_STRING_VALUE");
        // JaCa 3.1 CAgentArch.parseOp delegates to JavaLibrary.termToObject:
        // strings lose their quotes, then SchemeBoard$4 parses them as Terms.
        // A later CArtAgO call renders a ground structure/ordinary atom as its
        // canonical syntax. Preserve that exact producer rendering, not a
        // consumer parser/unquote guess or a static configured value.
        need("LITERAL".equals(key.get("kind"))&&"default".equals(key.get("namespace"))&&Boolean.FALSE.equals(key.get("negative"))
                &&key.get("terms") instanceof List<?>,"ARGUMENT_NOT_STRING_OR_CANONICAL_TERM:"+name);
        var terms=(List<?>)key.get("terms");
        need(!name.equals("Id")||terms.isEmpty(),"ID_MUST_BE_SCALAR");
        String syntax=text(value,"raw");
        if(terms.isEmpty())need(!Set.of("true","false").contains(key.get("functor")),"BOOLEAN_ATOM_NOT_STRING");
        return new Argument(syntax,terms.isEmpty()?"JACA_3_1_ATOM_SYNTAX":"JACA_3_1_GROUND_STRUCTURE_SYNTAX");
    }
    private static RuntimeFact workspace(RuntimeFact artifact,List<RuntimeFact> workspaces) {
        String id=text(artifact.values(),"workspaceSemanticId");
        var found=one(workspaces.stream().filter(w->id.equals(w.values().get("semanticId"))).toList(),"WORKSPACE_IDENTITY_UNRESOLVED");
        uuid(found.values(),"uuid");return found;
    }
    private static boolean samePlayer(String first,String second,List<RuntimeFact> agents) {
        var left=agents.stream().filter(a->first.equals(a.values().get("semanticId"))).toList();
        var right=agents.stream().filter(a->second.equals(a.values().get("semanticId"))).toList();
        if(left.size()!=1||right.size()!=1)return false;
        String a=text(left.getFirst().values(),"globalId"),b=text(right.getFirst().values(),"globalId");
        Object l=left.getFirst().values().get("agentRuntimeSemanticId"),r=right.getFirst().values().get("agentRuntimeSemanticId");
        return a.equals(b)&&(l==null&&r==null||l!=null&&l.equals(r));
    }
    private static boolean faithful(RuntimeFact fact){return fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private static String javaString(Object value){var map=object(value);need("STRING".equals(map.get("kind"))&&"java.lang.String".equals(map.get("javaType")),"JAVA_STRING_REQUIRED");return text(map,"value");}
    private static Map<String,Object> object(Object value){need(value instanceof Map<?,?>,"TYPED_OBJECT_UNAVAILABLE");return CanonicalJson.object(value);}
    private static String text(Map<String,Object> values,String key){Object value=values.get(key);if(!(value instanceof String s)||s.isBlank())throw new Unavailable(Availability.INCOMPLETE,"VALUE_UNAVAILABLE:"+key);return s;}
    private static String uuid(Map<String,Object> values,String key){String value=text(values,key);UUID.fromString(value);return value;}
    private static <T>T one(List<T> values,String error){need(values.size()==1,error);return values.getFirst();}
    private static void need(boolean condition,String code){if(!condition)throw new Unavailable(Availability.INCOMPLETE,code);}
    private static void required(String value,String code){if(value==null||value.isBlank())throw invalid(code);}
    private static IllegalArgumentException invalid(String code){return new IllegalArgumentException("AUCTION_BINDING_"+code);}
    private static final class Unavailable extends RuntimeException {
        final Availability availability;
        Unavailable(Availability availability,String code){super(code);this.availability=availability;}
    }
}
