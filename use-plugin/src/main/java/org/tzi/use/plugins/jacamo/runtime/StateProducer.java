package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.plugins.jacamo.bridge.SourceSnapshot;
import org.tzi.use.plugins.jacamo.bridge.mapping.AttributeMapping;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import org.tzi.use.plugins.jacamo.bridge.protocol.CheckpointProtocol;
import cartago.*;
import jacamo.infra.JaCaMoLauncher;
import jason.asSyntax.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import org.tzi.use.plugins.jacamo.runtime.JaCaMoRuntime.Checkpoint;

/** Child JVM entry point. Captures frozen JaCaMo state and exchanges it with the USE session. */
public final class StateProducer {
    public static void main(String[] args)throws Exception {
        if (args.length != 6) throw new IllegalArgumentException("project mapping port secret-file runtime-directory model-revision");
        Path project=Path.of(args[0]).toAbsolutePath(),work=Path.of(args[4]).toAbsolutePath();Files.createDirectories(work);
        var mapping=AttributeMapping.read(Path.of(args[1]));byte[] secret=HexFormat.of().parseHex(Files.readString(Path.of(args[3])).trim());
        for(String type:mapping.sourceClasses())if(!Artifact.class.isAssignableFrom(Class.forName(type,false,StateProducer.class.getClassLoader())))throw new IllegalArgumentException("MAPPED_SOURCE_NOT_ARTIFACT:"+type);
        String revision = CheckpointProtocol.inputRevision(args[5], project, mapping);
        var bridge = new Connection(mapping, Integer.parseInt(args[2]), secret, work, revision);
        var runtime=new JaCaMoRuntime(bridge.run,bridge::send);
        bridge.launcher=runtime.launcher();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close,"stop-source-interface"));
        runtime.initialize(project);runtime.create();
        System.out.println("STATE_PRODUCER_READY run="+bridge.run+" agents="+bridge.launcher.getAgs().size());
        runtime.start();
        new CountDownLatch(1).await();
    }
    private static final class Connection {
        private final String run=UUID.randomUUID().toString();
        private final AttributeMapping mapping;
        private final int port;
        private final byte[] secret;
        private final Path work;
        private final String revision;
        private long sequence;
        private JaCaMoLauncher launcher;
        private final Map<jason.infra.local.LocalAgArch,String> agentIdentities=new IdentityHashMap<>();
        Connection(AttributeMapping mapping, int port, byte[] secret, Path work, String revision) {
            this.mapping = mapping;
            this.port = port;
            this.secret = secret;
            this.work = work;
            this.revision = revision;
        }

        boolean send(Checkpoint checkpoint, ExecutionBarrier.Proof proof) throws Exception {
            if (!run.equals(proof.run()) || !proof.admissionClosed() || !proof.clockFrozen()
                    || !proof.active().isEmpty() || proof.pending() != 0) {
                throw new IOException("STATE_CUT_NOT_FROZEN");
            }
            var cut = CheckpointProtocol.Cut.local(run, proof.epoch(), revision);
            var snapshot=capture(++sequence,checkpoint.complete());
            var metadata=Map.<String,Object>of("boundary",checkpoint.boundary(),"invocation",checkpoint.invocation(),
                    "intention",checkpoint.intention(),"mergedPlans",checkpoint.mergedPlans(),"messages",checkpoint.messages());
            var request=Map.<String,Object>of("version",CheckpointProtocol.VERSION,"token",checkpoint.token(),"run",run,"agent",checkpoint.agent(),
                    "plan",checkpoint.plan(),"outcome",checkpoint.outcome(),"checkpoint",metadata,"cut",cut.payload(),
                    "snapshot",CheckpointProtocol.encodeSnapshot(snapshot),"sourceState",sourceState());
            String requestHash=CheckpointProtocol.digest(request);
            try(var socket=new Socket(InetAddress.getLoopbackAddress(),port)) {
                socket.setSoTimeout(30000);CheckpointProtocol.write(socket.getOutputStream(),request,secret);
                var response=CheckpointProtocol.read(socket.getInputStream(),secret);
                boolean approved = CheckpointProtocol.approved(response, run, checkpoint.token(), requestHash, cut);
                Files.writeString(work.resolve("checkpoint-"+sequence+".json"),new String(CanonicalJson.encode(Map.of("request",request,"response",response)),StandardCharsets.UTF_8));
                System.out.println("STATE_CHECKPOINT sequence="+sequence+" agent="+checkpoint.agent()+" plan="+checkpoint.plan()+" outcome="+checkpoint.outcome()+" boundary="+checkpoint.boundary()+" merged="+checkpoint.mergedPlans()+" decision="+response.get("decision"));
                return approved;
            }
        }
        /** Inspector data is audit/presentation only. It never becomes a Task receipt or an OCL input. */
        private Map<String,Object> sourceState() {
            var agents=new ArrayList<Map<String,Object>>();
            for(var local:launcher.getAgs().values()) {
                var beliefs=new ArrayList<String>();var plans=new ArrayList<String>();var intentions=new ArrayList<String>();
                for(var belief:local.getTS().getAg().getBB()){if(beliefs.size()>=1000)break;beliefs.add(preview(belief));}
                for(var plan:local.getTS().getAg().getPL()){if(plans.size()>=500)break;plans.add(preview(plan));}
                var all=local.getTS().getC().getAllIntentions();
                while(all.hasNext()&&intentions.size()<200)intentions.add(preview(all.next()));
                agents.add(Map.of("name",local.getAgName(),"source",local.getTS().getAg().getASLSrc(),"beliefs",beliefs,"plans",plans,"intentions",intentions));
            }
            return Map.of("agents",agents,"scope","Frozen checkpoint inspector; presentation limits: 1000 beliefs, 500 plans, 200 intentions per agent; 4000 characters per entry");
        }
        private static String preview(Object value){String text=String.valueOf(value);return text.length()>4000?text.substring(0,4000)+"…":text;}
        private SourceSnapshot capture(long sequence,boolean complete)throws Exception {
            var artifacts=new ArrayList<SourceSnapshot.Artifact>();var environment=CartagoEnvironment.getInstance();
            var nodes=new ArrayList<SourceSnapshot.Node>();var enactments=new ArrayList<SourceSnapshot.Enactment>();
            var memberships=new ArrayList<SourceSnapshot.Membership>();
            var groups=new LinkedHashMap<String,ora4mas.nopl.GroupBoard>();var organizations=new LinkedHashMap<String,ora4mas.nopl.OrgBoard>();var locations=new HashMap<String,String>();
            var agents=new HashMap<String,String>();
            for(var local:launcher.getAgs().values()) {
                String identity=agentIdentities.computeIfAbsent(local,k->UUID.randomUUID().toString());
                String source=local.getTS().getAg().getASLSrc().replace('\\','/');source=source.substring(source.lastIndexOf('/')+1).replaceFirst("\\.asl$","");
                agents.put(local.getAgName(),identity);
                nodes.add(new SourceSnapshot.Node(identity,"agent",source,local.getAgName(),"","",Map.of()));
            }
            var root=environment.getRootWSP();if(root!=null)capture(root,artifacts,groups,organizations,locations,agents);
            var orgIds=new HashMap<String,String>();
            for(var entry:organizations.entrySet()) {
                var board=entry.getValue();String type=organizationType(board);
                if(orgIds.putIfAbsent(board.getOEId(),entry.getKey())!=null)throw new IllegalStateException("ORGANIZATION_NAME_AMBIGUOUS");
                nodes.add(new SourceSnapshot.Node(entry.getKey(),"organization",type,board.getOEId(),locations.get(entry.getKey()),"",Map.of()));
            }
            for(var entry:groups.entrySet()) {
                var board=entry.getValue();var state=board.getGrpState().clone();String parent;
                if(state.getParentGroup().equals("root"))parent=orgIds.get(board.getOEId());
                else {
                    var candidates=groups.entrySet().stream().filter(e->e.getValue().getOEId().equals(board.getOEId())&&e.getValue().getArtId().equals(state.getParentGroup())).toList();
                    if(candidates.size()!=1)throw new IllegalStateException("MOISE_PARENT_GROUP_UNRESOLVED");parent=candidates.getFirst().getKey();
                }
                if(parent==null)throw new IllegalStateException("MOISE_ORGANIZATION_UNOBSERVED");
                nodes.add(new SourceSnapshot.Node(entry.getKey(),"group",board.getSpec().getId(),board.getArtId(),locations.get(entry.getKey()),parent,Map.of("wellFormed",board.isWellFormed())));
                memberships.add(new SourceSnapshot.Membership(entry.getKey(),parent));
                for(var player:state.getPlayers()) {
                    String id=agents.get(player.getAg());if(id==null)throw new IllegalStateException("MOISE_PLAYER_UNOBSERVED:"+player.getAg());
                    enactments.add(new SourceSnapshot.Enactment(entry.getKey(),id,player.getTarget()));
                }
            }
            return new SourceSnapshot(run,sequence,complete,artifacts,nodes,enactments,memberships);
        }
        private String organizationType(ora4mas.nopl.OrgBoard board)throws Exception {
            // Read only the local specification identity; role populations come from the live GroupBoard.
            String file=board.getOSFile();Path path;
            if(file.startsWith("file:")){var uri=java.net.URI.create(file);path=uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);}
            else path=Path.of(file);
            var factory=javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            String type=factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement().getAttribute("id");
            if(type.isBlank())throw new IllegalStateException("ORGANIZATION_SPEC_ID_REQUIRED");return type;
        }
        private void capture(WorkspaceDescriptor descriptor,List<SourceSnapshot.Artifact> result,Map<String,ora4mas.nopl.GroupBoard> groups,Map<String,ora4mas.nopl.OrgBoard> organizations,Map<String,String> locations,Map<String,String> agents)throws Exception {
            if(!descriptor.isLocal())throw new IllegalStateException("REMOTE_WORKSPACE_NOT_COVERED");
            var workspace=descriptor.getWorkspace();var controller=workspace.getController();
            for(var id:controller.getCurrentArtifacts()) {
                var actual=workspace.getArtifactDescriptor(id.getName()).getArtifact();String uuid=id.getId().toString();
                if(actual instanceof ora4mas.nopl.GroupBoard group){groups.put(uuid,group);locations.put(uuid,descriptor.getId().getFullName());}
                if(actual instanceof ora4mas.nopl.OrgBoard organization){organizations.put(uuid,organization);locations.put(uuid,descriptor.getId().getFullName());}
                if(!mapping.sourceClasses().contains(id.getArtifactType()))continue;
                var info=controller.getArtifactInfo(id.getName());var properties=new TreeMap<String,Object>();
                for(var property:info.getObsProperties())if(property.getValues().length==1) {
                    Object value=property.getValue();if(value instanceof Atom atom) {
                        if(atom.getArity()!=0||atom.negated()||atom.hasAnnot())throw new IllegalArgumentException("STRUCTURED_PROPERTY_REQUIRES_TYPED_CONVERTER:"+property.getName());
                        value=atom.getFunctor();
                    }
                    if(value instanceof String||value instanceof Boolean||value instanceof Number)properties.put(property.getName(),value);
                }
                if(info.getObservers()==null)throw new IllegalStateException("ARTIFACT_FOCUS_OBSERVATION_UNAVAILABLE");
                var observers=new HashSet<String>();
                for(var observer:info.getObservers()){String agent=agents.get(observer.getAgentId().getAgentName());if(agent!=null)observers.add(agent);}
                result.add(new SourceSnapshot.Artifact(id.getId().toString(),id.getArtifactType(),id.getName(),descriptor.getId().getFullName(),properties,observers));
            }
            for(var child:workspace.getChildWSPs())capture(child,result,groups,organizations,locations,agents);
        }
    }
}
