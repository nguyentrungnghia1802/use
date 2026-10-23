package org.jacamo.bridge.adapter;
import cartago.*;
import java.util.*;
import ora4mas.nopl.SchemeBoard;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
/** Shared pinned, lock-coherent public MOISE read. No case goals, aggregation or semantic verdict. */
final class MoiseSchemeRead {
    static final String MOISE_SHA="2b7d0415c5ec0bcf060a430c7d12753aa02bdf769172922d10ff2909870161a8";
    final CartagoEnvironment environment;
    final SchemeSemantic definition;
    final String osHash;
    private final java.nio.file.Path projectRoot;
    private final String projectKey;
    private final Object expectedScheme;
    record Raw(MoiseGoalObservation.Cut cut,List<Map<String,Object>> done,List<Map<String,String>> commitments,Map<String,Object> commitmentBindings,boolean held) { }
    @FunctionalInterface interface Reader<T>{T read(Raw raw)throws Exception;}
    MoiseSchemeRead(CartagoEnvironment environment,ModelSnapshot source,String organization,String scheme) {
        this(environment,source,organization,scheme,null);
    }
    MoiseSchemeRead(CartagoEnvironment environment,ModelSnapshot source,String organization,String scheme,java.nio.file.Path projectRoot) {
        this.environment=Objects.requireNonNull(environment);
        this.projectRoot=projectRoot;projectKey=source.semanticContract().project().name();
        var definitions=source.semanticContract().moiseOrganizations().stream().filter(o->o.name().equals(organization)).toList();
        if(definitions.size()!=1)throw new IllegalArgumentException("MOISE_SCHEME_SOURCE_ORGANIZATION_NOT_UNIQUE");
        var os=definitions.getFirst();var schemes=os.functionalSpecification().schemes().stream().filter(s->s.schemeId().equals(scheme)).toList();
        if(schemes.size()!=1)throw new IllegalArgumentException("MOISE_SCHEME_SOURCE_DEFINITION_NOT_UNIQUE");definition=schemes.getFirst();
        expectedScheme=schemeShape(org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationToTree(os),scheme);
        osHash=os.metadata().evidence().stream().map(e->e.sourceDigest()).distinct().reduce((a,b)->{if(!a.equals(b))throw new IllegalArgumentException("MOISE_SCHEME_SOURCE_HASH_AMBIGUOUS");return a;}).orElseThrow();
        try {var path=java.nio.file.Path.of(SchemeBoard.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(!java.nio.file.Files.isRegularFile(path)||!MOISE_SHA.equals(AdapterEvidence.digest(java.nio.file.Files.readAllBytes(path))))throw new IllegalArgumentException("MOISE_SCHEME_BINARY_UNSUPPORTED");
        }catch(Exception unavailable){throw new IllegalArgumentException("MOISE_SCHEME_BINARY_PROOF_UNAVAILABLE",unavailable);}
    }
    <T> T read(ArtifactId identity,Reader<T> reader)throws Exception {
        return CartagoArtifactReadScope.read(environment,identity,SchemeBoard.class,(board,descriptor,held)->{
            if(!board.getSpec().getId().equals(definition.schemeId()))throw new IllegalArgumentException("SCHEME_DEFINITION_UNSUPPORTED");
            if(projectRoot!=null) {
                var actual=new OfficialMoiseAdapter().capture(board.getSpec().getFS().getOS(),projectRoot,projectKey).organization();
                if(!expectedScheme.equals(schemeShape(org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationToTree(actual),definition.schemeId())))throw new IllegalArgumentException("SCHEME_LIVE_DEFINITION_DIFFERS_FROM_SOURCE");
            }
            var properties=descriptor.getAdapter().readProperties();
            var info=new ArtifactInfo(null,identity,List.of(),properties,List.of(),null);var cut=MoiseGoalObservation.decode(info,identity.getName());
            if(!new TreeSet<>(cut.goals().stream().map(MoiseGoalObservation.Goal::id).toList()).equals(new TreeSet<>(definition.goals().stream().map(OrganizationalGoalSemantic::goalId).toList())))throw new IllegalArgumentException("SCHEME_GOAL_POPULATION_UNAVAILABLE");
            var done=board.getSchState().getDoneGoals().stream().map(JasonStructuredTerms::term).sorted(Comparator.comparing(m->new String(CanonicalJson.encode(m),java.nio.charset.StandardCharsets.UTF_8))).toList();
            var commitments=board.getSchState().getPlayers().stream().map(p->Map.of("agent",p.getAg(),"mission",p.getTarget())).sorted(Comparator.comparing(m->m.get("agent")+":"+m.get("mission"))).toList();
            if(done.size()>4096||commitments.size()>4096)throw new IllegalArgumentException("SCHEME_EVIDENCE_CAPACITY");
            return reader.read(new Raw(cut,done,commitments,commitmentBindings(commitments),held));
        }).value();
    }
    /** Registration identity sampled under the same Artifact read scope. It does
     * not assert who originally committed a mission before this capture. */
    private Map<String,Object> commitmentBindings(List<Map<String,String>> commitments) {
        synchronized(BridgeRuntimeRegistry.class){return capturedBindings(commitments);}
    }
    private Map<String,Object> capturedBindings(List<Map<String,String>> commitments) {
        var rows=new ArrayList<Map<String,Object>>();boolean complete=true;
        for(var commitment:commitments) {
            String agent=commitment.get("agent");var before=BridgeRuntimeRegistry.agentIdentity(agent);
            var projected=BridgeRuntimeRegistry.projectedAgentId(agent);var after=BridgeRuntimeRegistry.agentIdentity(agent);
            boolean known=before.isPresent()&&before.equals(after)&&projected.isPresent();
            if(known){var id=before.orElseThrow();known=id.authority().equals("jason")&&id.dimension().equals("agent")&&id.kind().equals("runtime-agent")&&id.scope().equals(projectKey)&&id.localId().equals(agent)&&!id.incarnation().isBlank();}
            if(!known)complete=false;
            rows.add(Map.of("agent",agent,"mission",commitment.get("mission"),"sourcePlayer",known?projected.orElseThrow():"","runtimePlayer",known?before.orElseThrow().canonical():"","quality",known?"COMPLETE":"INCOMPLETE"));
        }
        return JasonStructuredTerms.immutable(Map.of("complete",complete,"rows",rows,"authority","BRIDGE_REGISTERED_AGENT_AT_SCHEME_CAPTURE"));
    }
    private static Object schemeShape(Object organization,String id) {
        var fs=CanonicalJson.object(CanonicalJson.object(organization).get("functionalSpecification"));
        var rows=((List<?>)fs.get("schemes")).stream().map(CanonicalJson::object).filter(s->id.equals(s.get("schemeId"))).toList();
        if(rows.size()!=1)throw new IllegalArgumentException("SCHEME_SHAPE_NOT_UNIQUE");return withoutMetadata(rows.getFirst());
    }
    private static Object withoutMetadata(Object value) {
        if(value instanceof Map<?,?> raw){var result=new TreeMap<String,Object>();raw.forEach((k,v)->{if(!k.equals("metadata"))result.put((String)k,withoutMetadata(v));});return result;}
        if(value instanceof List<?> list)return list.stream().map(MoiseSchemeRead::withoutMetadata).toList();return value;
    }
}
