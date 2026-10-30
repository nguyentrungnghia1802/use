package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.sys.MObject;

/** Explicit start of an invariant's operational scope. A qualified source row
 * activates one exact context incarnation once; later absence never deactivates it. */
public final class InvariantActivation {
    public static final String ANNOTATION="DeclarativeInvariantActivation",POLICY="LATCH_ONCE_QUALIFIED/1.0";
    private record Binding(MappingSpecification.Node node,MClassInvariant invariant) { }
    private final List<Binding> bindings;
    private final String program;
    public static void declare(MappingSpecification spec,MModel model,String program) {new InvariantActivation(spec,model,program,true);}
    public InvariantActivation(MappingSpecification spec,MModel model,String program) {this(spec,model,program,false);}
    private InvariantActivation(MappingSpecification spec,MModel model,String program,boolean declaration) {
        this.program=program;var result=new ArrayList<Binding>();var targets=new HashSet<String>();
        var sections=spec.root().child("runtime").children("invariantActivations");
        if(sections.size()>1)throw spec.root().location().error("INVARIANT_ACTIVATIONS_DUPLICATE");
        for(var section:sections)for(var node:section.children("activate")) {
            var inv=model.classInvariants().stream().filter(i->i.qualifiedName().equals(node.attr("target"))).findFirst().orElseThrow(()->node.location().error("ACTIVATION_INVARIANT_UNRESOLVED"));
            var table=spec.requireId(node.attr("tableRef"));
            if(!table.name().equals("table")||table.children("column").stream().noneMatch(c->c.attr("name").equals(node.attr("contextColumn")))||!POLICY.equals(node.attr("policy"))||!targets.add(inv.qualifiedName())
                    ||!inv.cls().isSubClassifierOf(model.getClass("OrgContext")))throw node.location().error("ACTIVATION_POLICY_TABLE_OR_CONTEXT_INVALID");
            var annotation=new MElementAnnotation(ANNOTATION,Map.of("program",program,"policy",POLICY,"id",node.attr("id"),"table",node.attr("tableRef")));
            if(inv.getAnnotation(ANNOTATION)!=null&&!program.equals(inv.getAnnotationValue(ANNOTATION,"program")))throw node.location().error("ACTIVATION_OWNER_CONFLICT");
            if(declaration)inv.addAnnotation(annotation);
            else if(inv.getAnnotation(ANNOTATION)==null||!node.attr("id").equals(inv.getAnnotationValue(ANNOTATION,"id"))||!node.attr("tableRef").equals(inv.getAnnotationValue(ANNOTATION,"table")))throw node.location().error("ACTIVATION_DECLARATION_REQUIRED");
            result.add(new Binding(node,inv));
        }
        bindings=List.copyOf(result);
    }
    private static String key(MClassInvariant invariant){return "xml:invariant:activation:"+SemanticJson.hash(invariant.qualifiedName());}
    public void apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Result> tables) {
        for(var binding:bindings) {
            var table=tables.get(binding.node().attr("tableRef"));
            if(table==null||table.populationQuality()!=Completeness.COMPLETE)continue;
            for(var row:table.rows()) {
                if(row.quality()!=Completeness.COMPLETE||!(row.values().get(binding.node().attr("contextColumn")) instanceof String id)||id.isBlank()||!row.proofs().containsKey("source-cut"))continue;
                var object=port.find(id);
                if(object==null||!object.cls().isSubClassifierOf(binding.invariant().cls())||!program.equals(port.metadata(object,"xml:m0:program"))||!epoch.equals(port.metadata(object,"xml:m0:epoch"))
                        ||!"COMPLETE".equals(port.metadata(object,"xml:m0:quality"))||!cut.equals(port.metadata(object,"xml:m0:cut"))
                        ||!"COMPLETE".equals(port.metadata(object,"xml:m0:roles-quality"))||!cut.equals(port.metadata(object,"xml:m0:roles-cut")))continue;
                if(!port.metadata(object,key(binding.invariant())).isBlank())continue;
                var proof=Map.of("policy",POLICY,"program",program,"epoch",epoch,"context",object.name(),"cut",cut,"table",binding.node().attr("tableRef"),"sourceProofs",row.proofs());
                var stored=new TreeMap<String,Object>(proof);stored.put("hash",SemanticJson.hash(proof));
                port.metadata(object,key(binding.invariant()),new String(CanonicalJson.encode(stored),StandardCharsets.UTF_8));
            }
        }
    }
    public static String evidence(MClassInvariant invariant,org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine engine,MObject object) {
        return invariant.getAnnotation(ANNOTATION)==null?"":engine.metadata(object,key(invariant));
    }
    public static String unavailable(MClassInvariant invariant,org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine engine,MObject object) {
        if(invariant.getAnnotation(ANNOTATION)==null)return "";
        if(!engine.canonicalProgramHash().equals(invariant.getAnnotationValue(ANNOTATION,"program")))return "ERROR:INVARIANT_ACTIVATION_OWNER_MISMATCH";
        String raw=engine.metadata(object,key(invariant));if(raw.isBlank())return "CONSTRAINT_AWAITING_QUALIFIED_ACTIVATION";
        try {
            var proof=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(raw.getBytes(StandardCharsets.UTF_8))));var hash=proof.remove("hash");
            if(!SemanticJson.hash(proof).equals(hash)||!POLICY.equals(proof.get("policy"))||!engine.canonicalProgramHash().equals(proof.get("program"))
                    ||!object.name().equals(proof.get("context"))||!engine.metadata(object,"xml:m0:epoch").equals(proof.get("epoch")))return "ERROR:INVARIANT_ACTIVATION_PROOF_INVALID";
            return "";
        }catch(RuntimeException invalid){return "ERROR:INVARIANT_ACTIVATION_PROOF_INVALID";}
    }
}
