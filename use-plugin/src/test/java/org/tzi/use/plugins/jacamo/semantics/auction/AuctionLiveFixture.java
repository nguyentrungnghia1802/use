package org.tzi.use.plugins.jacamo.semantics.auction;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.tools.ToolProvider;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import jason.asSyntax.ASSyntax;
import ora4mas.nopl.SchemeBoard;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** In-JUnit real CArtAgO/MOISE fixture, not the external-JVM/TCP E01 acceptance. */
final class AuctionLiveFixture implements AutoCloseable {
    final AuctionCaseBundle bundle=AuctionCaseBundle.read(Path.of("examples/auction"));
    final AuctionMappingSpecification specification=new AuctionMappingXmlLoader().load(bundle);
    final ClassLoader previous=Thread.currentThread().getContextClassLoader();
    final URLClassLoader loader;
    final CartagoEnvironment environment=CartagoEnvironment.getInstance();
    final ModelSnapshot source;
    final AuctionSelectorResult selectors;
    final AuctionArtifactBindingResolver.Run run;
    final CartagoBasicContext actor;
    final WorkspaceId workspace;
    final CartagoSnapshotSource cartago;
    final NplBoardSnapshotSource npl;
    final MoiseBoardSnapshotSource moise;
    final List<RuntimeEvent> events=new CopyOnWriteArrayList<>();
    volatile java.util.function.Consumer<RuntimeEvent> eventHook=event->{};
    final List<ArtifactId> artifacts=new ArrayList<>();
    final List<SchemeBoard> boards=new ArrayList<>();
    final List<WorkspaceId> joined=new ArrayList<>();
    final String previousRevision=System.getProperty("jacamo.bridge.modelRevision");
    final String previousGeneration=System.getProperty("jacamo.bridge.generation");
    final String previousInspector=jason.util.Config.get().getProperty(jason.util.Config.START_WEB_OI);
    private final String factoryName="pinned-auction-test:"+UUID.randomUUID();
    int cuts;
    AuctionLiveFixture()throws Exception {this(false);}
    AuctionLiveFixture(boolean fieldProbe)throws Exception {this(fieldProbe,false);}
    AuctionLiveFixture(boolean fieldProbe,boolean normative)throws Exception {
        Path classes=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-live")),"classes-").toAbsolutePath();
        Path input=input("src-artifact");
        assertArrayEquals(bundle.require("src-artifact").bytes(),Files.readAllBytes(input));
        var diagnostics=new ByteArrayOutputStream();
        String classpath=String.join(File.pathSeparator,Path.of(Artifact.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Path.of(jason.asSyntax.Atom.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,diagnostics,"-proc:none","--release","21","-encoding","UTF-8", "-cp",classpath,"-d",classes.toString(),input.toString()),diagnostics.toString());
        // Legacy tests may have compiled the same FQCN into target/test-classes.
        // Isolate only this pinned application type; frameworks retain their host identity.
        var frameworks=new ClassLoader(previous) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.equals("auction_env.AuctionArtifact"))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);
            }
            @Override public URL getResource(String name){return name.equals("auction_env/AuctionArtifact.class")?null:super.getResource(name);}
            @Override public Enumeration<URL> getResources(String name)throws IOException{return name.equals("auction_env/AuctionArtifact.class")?Collections.emptyEnumeration():super.getResources(name);}
        };
        loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},frameworks);Thread.currentThread().setContextClassLoader(loader);
        Path jcm=Path.of("examples/auction/auction.jcm").toRealPath();source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        selectors=new AuctionSourceSelector().execute(specification,bundle,source);
        run=new AuctionArtifactBindingResolver.Run(UUID.randomUUID().toString(),7,source.modelRevision());
        System.setProperty("jacamo.bridge.generation",Long.toString(run.generation()));System.setProperty("jacamo.bridge.modelRevision",run.modelRevision());
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_OI,"false");environment.init();
        installFactory("/main");
        actor=new CartagoBasicContext("auctionBinding"+UUID.randomUUID().toString().replace("-",""));workspace=actor.getJoinedWspId("main");joined.add(workspace);
        npl=normative?new NplBoardSnapshotSource(run.id(),()->List.copyOf(boards)):null;if(npl!=null)npl.attach(events::add);
        cartago=fieldProbe?new CartagoSnapshotSource(environment,List.of(),run.id(),source,npl==null?null:npl::artifactCreated):new CartagoSnapshotSource(environment,List.of(),run.id());cartago.attach(event->{events.add(event);eventHook.accept(event);});
        moise=new MoiseBoardSnapshotSource("main",run.id(),List::of,()->List.copyOf(boards),source.semanticContract(),Path.of("examples/auction").toRealPath());
    }
    private void installFactory(String path)throws Exception {
        environment.addArtifactFactory(path,new ArtifactFactory(factoryName) {
            @Override public Artifact createArtifact(String name)throws CartagoException {
                if(!name.equals("auction_env.AuctionArtifact"))throw new CartagoException("NOT_SELECTED_TEST_TYPE");
                try{return (Artifact)Class.forName(name,false,loader).getDeclaredConstructor().newInstance();}
                catch(ReflectiveOperationException failure){throw new CartagoException(failure.toString());}
            }
        });
    }
    ArtifactId scheme(String name,String auction,String service)throws Exception {
        return scheme(name,auction,service,false);
    }
    ArtifactId scheme(String name,String auction,String service,boolean throughJaca)throws Exception {
        return scheme(name,auction,service,throughJaca,workspace);
    }
    ArtifactId scheme(String name,String auction,String service,boolean throughJaca,WorkspaceId target)throws Exception {
        var output=new OpFeedbackParam<ArtifactId>();
        actor.doAction(new Op("makeArtifact",name,"ora4mas.nopl.SchemeBoard",new Object[]{input("src-os").toString(),"doAuction"},output),target,5000);
        var id=output.get();assertNotNull(id);artifacts.add(id);
        boards.add((SchemeBoard)environment.resolveWSP(target.getFullName()).getWorkspace().getArtifactDescriptor(name).getArtifact());
        var jaca=new jaca.JavaLibrary();
        actor.doAction(id,new Op("setArgumentValue","auction","Id",throughJaca?jaca.termToObject(ASSyntax.createString(auction)):ASSyntax.createString(auction)),5000);
        actor.doAction(id,new Op("setArgumentValue","auction","Service",throughJaca?jaca.termToObject(ASSyntax.createString(service)):ASSyntax.createString(service)),5000);
        return id;
    }
    ArtifactId auction(String name)throws Exception {
        return auction(name,workspace);
    }
    ArtifactId auction(String name,WorkspaceId target)throws Exception {
        var output=new OpFeedbackParam<ArtifactId>();actor.doAction(new Op("makeArtifact",name,"auction_env.AuctionArtifact",new Object[0],output),target,5000);
        var id=output.get();assertNotNull(id);artifacts.add(id);actor.doAction(new Op("focus",id),target,5000);return id;
    }
    WorkspaceId otherWorkspace()throws Exception {
        return otherWorkspace("other"+UUID.randomUUID().toString().replace("-",""));
    }
    WorkspaceId otherWorkspace(String name)throws Exception {
        return otherWorkspace(name,workspace);
    }
    WorkspaceId otherWorkspace(String name,WorkspaceId parent)throws Exception {
        try {actor.doAction(new Op("createWorkspace",name),parent,5000);}
        catch(cartago.util.agent.ActionFailedException failure){throw new IllegalStateException("createWorkspace "+parent.getFullName()+"/"+name+": "+failure.getFailureMsg()+" "+failure.getFailureDescr(),failure);}
        // CArtAgO 3.1 AgentSessionArtifact exposes /2; BasicContext's /3
        // convenience method does not match that actual operation interface.
        var output=new OpFeedbackParam<WorkspaceId>();actor.doAction(new Op("joinWorkspace",parent.getFullName()+"/"+name,output),workspace,5000);
        var target=output.get();assertNotNull(target);joined.add(target);
        installFactory(target.getFullName());cartago.capture();return target;
    }
    void dispose(ArtifactId id)throws Exception {actor.doAction(new Op("disposeArtifact",id),id.getWorkspaceId(),5000);artifacts.remove(id);}
    RuntimeSnapshot cut()throws Exception {
        var facts=new ArrayList<>(moise.capture());facts.addAll(cartago.capture());
        for(var fact:facts)if(fact.kind()==RuntimeFactKind.SCHEME_BOARD)assertEquals(ProjectionStatus.MATERIALIZED_FAITHFULLY,fact.projectionStatus(),fact.values().toString());
        var watermarks=Map.of(cartago.sourceId(),cartago.watermark(),moise.sourceId(),moise.watermark());
        var now=Instant.now();return new RuntimeSnapshot("binding-cut-"+(++cuts),source.modelRevision(),now,now,watermarks,watermarks,1,facts,
                Map.of(cartago.sourceId(),cartago.completeness(),moise.sourceId(),moise.completeness()),"sequential-fixture-cut-"+cuts);
    }
    @Override public void close()throws Exception {
        try {
            moise.close();if(npl!=null)npl.close();cartago.close();
            for(var board:boards)board.getNormativeEngine().stop();
            for(var id:artifacts)environment.getController(id.getWorkspaceId().getFullName()).removeArtifact(id.getName());
            CslRuntimeTestRegistry.closeCartagoContext(actor);
            for(var target:joined)environment.resolveWSP(target.getFullName()).getWorkspace().removeArtifactFactory(factoryName);
            // CArtAgO 3.1 shutdown leaves child descriptors in the parent map.
            // Its public getChildWSPs() is the actual values view (verified in
            // the pinned binary). Retire only this fixture's exact UUID after
            // stopping its controllers; preserve the shared workspace/others.
            for(var target:joined.reversed())if(!target.equals(workspace)) {
                var descriptor=environment.resolveWSP(target.getFullName());descriptor.getWorkspace().shutdown();
                var parent=descriptor.getParentInfo().getWorkspace();
                synchronized(parent){parent.getChildWSPs().removeIf(child->child==descriptor&&child.getId().equals(target));}
            }
        } finally {
            environment.shutdown();Thread.currentThread().setContextClassLoader(previous);loader.close();
            restore("jacamo.bridge.modelRevision",previousRevision);restore("jacamo.bridge.generation",previousGeneration);
            if(previousInspector==null)jason.util.Config.get().remove(jason.util.Config.START_WEB_OI);else jason.util.Config.get().setProperty(jason.util.Config.START_WEB_OI,previousInspector);
        }
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    private Path input(String id)throws IOException{return Path.of("examples/auction").resolve(bundle.inputs().get(id).path()).toRealPath();}
}
