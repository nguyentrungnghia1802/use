package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Qualified framework invocation receipts. Operation selection, actor credit and admin policy stay in XML. */
public final class OperationHistoryCutDecoder {
    private static final String VERSION="CARTAGO_OPERATION_HISTORY/1.0",FRAMEWORK="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private final ObservationCapabilityRequest request;
    private final String type,hash;
    private final Set<String> operations;
    public record Result(Completeness quality,Map<String,Object> values,String cutId,String diagnostic) {
        public Result {values=SemanticJson.object(values);}
    }
    public OperationHistoryCutDecoder(ObservationCapabilityRequest request) {
        this.request=request;
        need(request.provider().equals("cartago.operation-history.v1")&&Set.of("class","hash","sourceRef","sourceHash","sourcePath","operations").containsAll(request.parameters().keySet()),"REQUEST_UNSUPPORTED");
        Object raw=request.parameters().getOrDefault("operations",List.of());need(raw instanceof List<?>,"OPERATION_SELECTION_INVALID");
        var names=(List<?>)raw;need(names.size()<=32&&(!request.parameters().containsKey("operations")||!names.isEmpty()),"OPERATION_SELECTION_INVALID");
        var selected=new TreeSet<String>();for(Object value:names){need(value instanceof String name&&name.matches("[A-Za-z_$][A-Za-z0-9_$]*"),"OPERATION_SELECTION_INVALID");need(selected.add((String)value),"OPERATION_SELECTION_DUPLICATE");}operations=Set.copyOf(selected);
        type=text(request.parameters(),"class");hash=text(request.parameters(),"hash");need(hash.matches("[0-9a-f]{64}"),"BINARY_PIN_REQUIRED");
    }
    public Result decode(Object raw,RuntimeFact artifact,RuntimeFact workspace,RuntimeSnapshot cut,String run,long generation) {
        try {
            var receipt=CapabilityObservation.require(raw,request);var scope=receipt.scope();var data=receipt.data();
            need(receipt.quality()==Completeness.COMPLETE,"INCOMPLETE:"+receipt.diagnostic());
            need(run.equals(scope.run())&&generation==scope.generation()&&cut.modelRevision().equals(scope.revision())&&scope.sourceId().equals("cartago")&&new SourceWatermark("cartago",scope.sourceSequence()).equals(cut.endWatermarks().get("cartago")),"RECEIPT_SCOPE_MISMATCH");
            need(faithful(artifact,RuntimeFactKind.ARTIFACT)&&faithful(workspace,RuntimeFactKind.WORKSPACE)&&cut.facts().contains(artifact)&&cut.facts().contains(workspace),"PUBLIC_OBJECT_UNAVAILABLE");
            need(type.equals(artifact.values().get("artifactTypeJavaClassName"))&&artifact.id().incarnation().equals(artifact.values().get("uuid"))&&workspace.id().incarnation().equals(workspace.values().get("uuid"))&&Objects.equals(artifact.values().get("workspaceSemanticId"),workspace.values().get("semanticId")),"PHYSICAL_OBJECT_MISMATCH");
            need(data.keySet().equals(Set.of("version","status","run","generation","revision","javaClass","classHash","resource","provider","frameworkArtifactHash","artifact","artifactUuid","workspaceUuid","lastSourceSequence","fromCreation","historyQuality","scopeMeaning","events","hash")),"FIELDS_UNSUPPORTED");
            need(VERSION.equals(data.get("version"))&&"COMPLETE".equals(data.get("status"))&&run.equals(data.get("run"))&&generation==integer(data.get("generation"))&&cut.modelRevision().equals(data.get("revision")),"SOURCE_SCOPE_OR_VERSION");
            var actual=CanonicalJson.object(artifact.values().get("artifactProviderObservation"));
            need("COMPLETE".equals(actual.get("status"))&&hash.equals(actual.get("classHash"))&&type.equals(actual.get("javaClass"))&&artifact.id().incarnation().equals(actual.get("artifactUuid"))
                    &&type.equals(data.get("javaClass"))&&hash.equals(data.get("classHash"))&&Objects.equals(actual.get("resource"),data.get("resource"))&&Objects.equals(actual.get("provider"),data.get("provider"))&&FRAMEWORK.equals(data.get("frameworkArtifactHash")),"ACTUAL_PROVIDER_BINARY_MISMATCH");
            need(artifact.id().canonical().equals(data.get("artifact"))&&artifact.id().incarnation().equals(data.get("artifactUuid"))&&workspace.id().incarnation().equals(data.get("workspaceUuid"))&&scope.sourceSequence()==integer(data.get("lastSourceSequence")),"PHYSICAL_CUT_CROSSWALK_MISMATCH");
            need((operations.isEmpty()?"ICARTAGOLOGGER_OBSERVED_START_TERMINAL_PREFIX_SUSPEND_RESUME_UNSUPPORTED":"ICARTAGOLOGGER_SELECTED_START_TERMINAL_PREFIX_SUSPEND_RESUME_UNSUPPORTED").equals(data.get("scopeMeaning"))&&data.get("fromCreation") instanceof Boolean&&Set.of("COMPLETE","INCOMPLETE").contains(data.get("historyQuality")),"HISTORY_SCOPE_UNSUPPORTED");
            boolean continuous=Boolean.TRUE.equals(data.get("fromCreation"))&&data.get("historyQuality").equals("COMPLETE");need(!data.get("historyQuality").equals("COMPLETE")||continuous,"FALSE_HISTORY_COMPLETENESS");
            var unsigned=new LinkedHashMap<>(data);Object digest=unsigned.remove("hash");need(SemanticJson.hash(unsigned).equals(digest),"HASH_MISMATCH");
            need(data.get("events") instanceof List<?> values&&values.size()<=512,"EVENT_CAPACITY");
            var rows=new ArrayList<Map<String,Object>>();var pending=new HashMap<String,RuntimeEvent>();var starts=new HashMap<String,Map<String,Object>>();var ended=new HashSet<String>();var ids=new HashSet<String>();long previous=0;
            for(Object value:(List<?>)data.get("events")) {
                var event=ContractPayloads.event(CanonicalJson.object(value));var after=event.after();
                need(event.entityId()!=null&&event.entityId().equals(artifact.id())&&event.factKind()==RuntimeFactKind.ARTIFACT&&run.equals(event.sessionId())&&generation==event.generation()&&cut.modelRevision().equals(event.modelRevision())&&event.sourceId().equals("cartago")&&event.subsystem().equals("cartago"),"EVENT_SOURCE_OR_IDENTITY_MISMATCH");
                need(event.completeness()==Completeness.COMPLETE&&Set.of(RuntimeEventKind.STARTED,RuntimeEventKind.SUCCEEDED,RuntimeEventKind.FAILED).contains(event.kind())&&!event.correlationId().isBlank()&&event.correlationId().equals(after.get("opId"))&&"CARTAGO_OPID_AGENT_BODY_ID".equals(after.get("performerAuthority")),"EVENT_PERFORMER_OR_CORRELATION_UNAVAILABLE");
                need(ids.add(event.eventId())&&event.sourceSequence()>previous&&event.sourceSequence()<=scope.sourceSequence()&&event.watermark().equals(new SourceWatermark("cartago",event.sourceSequence())),"EVENT_ORDER_OR_DUPLICATE");previous=event.sourceSequence();
                need(Objects.equals(artifact.values().get("semanticId"),after.get("artifactSemanticId")),"EVENT_ARTIFACT_ALIAS_MISMATCH");
                var descriptors=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.OPERATION)&&Objects.equals(f.values().get("semanticId"),after.get("operationDescriptorId"))).toList();
                need(descriptors.size()==1,"EXACT_PUBLIC_OPERATION_REQUIRED");var descriptor=descriptors.getFirst();
                var backing=SemanticContractCodec.backingOperationFromTree(descriptor.values().get("backingOperation"));var operation=SemanticContractCodec.operationFromTree(descriptor.values().get("operationDescriptor"));
                var signature=List.of(backing.declaringClass(),backing.methodName(),backing.parameterTypes(),backing.returnType());
                need(operations.isEmpty()||operations.contains(backing.methodName()),"OPERATION_OUTSIDE_REQUEST_SELECTION");
                need(!backing.varArgs()&&!operation.dynamic()&&!operation.internal()&&Objects.equals(artifact.values().get("semanticId"),operation.artifactSemanticId())&&backing.operationDescriptorId().equals(operation.metadata().semanticId())&&signature.equals(after.get("operationSignature")),"PUBLIC_OPERATION_SIGNATURE_MISMATCH");
                String performer=text(after,"performerSemanticId"),name=text(after,"performerName");need(!performer.isBlank()&&!name.isBlank(),"PERFORMER_REQUIRED");
                var agents=cut.facts().stream().filter(f->faithful(f,RuntimeFactKind.AGENT)&&Objects.equals(f.values().get("semanticId"),performer)&&f.id().scope().equals(artifact.id().scope())).toList();need(agents.size()<=1,"PERFORMER_IDENTITY_AMBIGUOUS");
                var row=new LinkedHashMap<String,Object>();row.put("eventId",event.eventId());row.put("invocation",event.correlationId());row.put("outcome",event.kind().name());row.put("performerName",name);row.put("performerSemanticId",performer);
                row.put("declaringClass",backing.declaringClass());row.put("operation",backing.methodName());row.put("parameterTypes",backing.parameterTypes());row.put("returnType",backing.returnType());row.put("sourceSequence",event.sourceSequence());row.put("raw",ContractPayloads.event(event));
                if(!agents.isEmpty()){need(name.equals(agents.getFirst().values().get("name")),"PERFORMER_NAME_IDENTITY_CONFLICT");row.put("performerId",agents.getFirst().id().canonical());row.put("performerKnown",true);}
                String invocation=event.correlationId();
                if(event.kind()==RuntimeEventKind.STARTED) {
                    need("started".equals(after.get("phase"))&&!ended.contains(invocation)&&pending.putIfAbsent(invocation,event)==null,"STARTED_INVOCATION_CONFLICT");starts.put(invocation,row);
                }else {
                    need(Objects.equals(after.get("phase"),event.kind()==RuntimeEventKind.SUCCEEDED?"completed":"failed")&&ended.add(invocation),"TERMINAL_INVOCATION_CONFLICT");
                    var start=pending.remove(invocation);
                    if(start==null)need(!continuous,"FALSE_COMPLETE_TERMINAL_WITHOUT_START");
                    else {need(Objects.equals(start.after().get("performerSemanticId"),performer)&&Objects.equals(start.after().get("operationSignature"),signature),"INVOCATION_ACTOR_OR_SIGNATURE_CHANGED");row.put("correlated",true);starts.get(invocation).put("correlated",true);}
                }
                rows.add(row);
            }
            return new Result(Completeness.COMPLETE,Map.of("continuous",continuous,"events",rows,"fromCreation",data.get("fromCreation"),"pendingInvocations",pending.keySet().stream().sorted().toList(),"suspendResume","UNSUPPORTED"),(String)digest,"");
        }catch(IllegalArgumentException|ContractException unavailable){return new Result(Completeness.UNAVAILABLE,Map.of(),"",unavailable.getMessage());}
    }
    private static boolean faithful(RuntimeFact fact,RuntimeFactKind kind){return fact!=null&&fact.kind()==kind&&fact.id().authority().equals("cartago")&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private static String text(Map<String,Object> values,String key){need(values.get(key) instanceof String,"TEXT_REQUIRED:"+key);return (String)values.get(key);}
    private static long integer(Object value){try{need(value instanceof Number,"INTEGER_REQUIRED");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException("OPERATION_HISTORY_INTEGER_REQUIRED");}}
    private static void need(boolean value,String diagnostic){if(!value)throw new IllegalArgumentException("OPERATION_HISTORY_CUT:"+diagnostic);}
}
