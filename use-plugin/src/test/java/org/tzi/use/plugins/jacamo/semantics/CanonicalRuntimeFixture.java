package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;

/** Actual CArtAgO/MOISE fixture. It is not the separate-JVM JaCaMo acceptance test. */
public final class CanonicalRuntimeFixture implements AutoCloseable {
    final CartagoEnvironment environment;CartagoBasicContext alice,bob;ArtifactId artifact,board;
    private WorkspaceId counterWorkspace;
    ModelSnapshot model;SemanticProgram program;CodeGroundedNativePipeline.CanonicalBuildResult built;NativeRuntimeProjector projector;
    SnapshotCoordinator captures;final AtomicReference<NativeRuntimeProjector> target=new AtomicReference<>();final AtomicReference<Throwable> failure=new AtomicReference<>();
    private final List<CslGroupBoard> boards=new ArrayList<>();private final List<String> extraBoards=new ArrayList<>();
    String artifactId,groupId;final String aliceName,bobName,boardName;AutoCloseable aliceBinding,bobBinding;
    private final String previousRevision=System.getProperty("jacamo.bridge.modelRevision"),previousGeneration=System.getProperty("jacamo.bridge.generation");
    CanonicalRuntimeFixture() throws Exception {this(CslCounterArtifact.class,m->CanonicalCslMappingTest.rules(m,CanonicalCslMappingTest.csl()));}
    public CanonicalRuntimeFixture(Class<? extends Artifact> type,java.util.function.Function<ModelSnapshot,MappingRuleSet> configuration) throws Exception {
        this(type,configuration,1);
    }
    CanonicalRuntimeFixture(Class<? extends Artifact> type,java.util.function.Function<ModelSnapshot,MappingRuleSet> configuration,int contexts) throws Exception {
        if(contexts<1||contexts>2)throw new IllegalArgumentException("TEST_CONTEXT_BOUND");
        var path=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();var base=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
        String suffix=UUID.randomUUID().toString().replace("-","");aliceName="alice"+suffix;bobName="bob"+suffix;boardName="team"+suffix;
        environment=CartagoEnvironment.getInstance();
        try {environment.init();var root=environment.getRootWSP().getWorkspace();
        counterWorkspace=(root.getChildWSP("main").isPresent()?root.getChildWSP("main").orElseThrow():root.createWorkspace("main")).getId();
        // CartagoEnvironment.startSession prepends '/' to this constructor argument.
        String sessionWorkspace=counterWorkspace.getFullName().replaceFirst("^/","");
        alice=new CartagoBasicContext(aliceName,sessionWorkspace);bob=new CartagoBasicContext(bobName,sessionWorkspace);
        artifact=alice.makeArtifact(counterWorkspace,"counter",type.getName());
        board=alice.makeArtifact(counterWorkspace,boardName,CslGroupBoard.class.getName(),new Object[]{path.getParent().resolve("src/org/organization.xml").toString(),"team"});
        alice.doAction(board,new Op("adoptRole","worker"));bob.doAction(board,new Op("adoptRole","reviewer"));
        boards.add(CslGroupBoard.instance);
        for(int i=1;i<contexts;i++){String name=boardName+"_"+i;var other=alice.makeArtifact(counterWorkspace,name,CslGroupBoard.class.getName(),new Object[]{path.getParent().resolve("src/org/organization.xml").toString(),"team"});extraBoards.add(name);boards.add(CslGroupBoard.instance);alice.doAction(other,new Op("adoptRole","worker"));bob.doAction(other,new Op("adoptRole","reviewer"));}
        aliceBinding=CslRuntimeTestRegistry.bind(base.semanticContract(),aliceName,"alice");bobBinding=CslRuntimeTestRegistry.bind(base.semanticContract(),bobName,"bob");
        System.setProperty("jacamo.bridge.generation","1");var official=new OfficialCartagoAdapter().capture(environment);var source=base.semanticContract();
        var semantic=new JacamoSemanticSnapshot(source.contractVersion(),source.project(),source.agentDeclarations(),source.workspaceDeclarations(),source.artifactDeclarations(),source.organizationDeployments(),source.groupDeployments(),source.schemeDeployments(),source.institutionDeployments(),source.rawRoleTuples(),source.rawFocusTuples(),source.importProvenance(),source.jasonPrograms(),List.of(official),source.moiseOrganizations(),source.exactBindings(),source.diagnostics());
        model=new ModelSnapshot(base.modelRevision(),base.sources(),base.agentDeclarations(),base.workspaces(),base.configuredArtifacts(),base.organisationFacts(),base.groupRoleCardinalities(),base.parentSubGroupCardinalities(),base.crossDimensionalRelations(),base.unresolvedFacts(),base.projectionProvenance(),semantic);
        System.setProperty("jacamo.bridge.modelRevision",model.modelRevision());
        program=new JaCaMoStaticMappingAdapter().map(model,JaCaMoMappingProfile.observedV1(),configuration.apply(model));built=new CodeGroundedNativePipeline().buildCanonicalProgram(model,NativeProjectionMode.AUTO,program);
        projector=new NativeRuntimeProjector(built,"canonical-run",1,model.modelRevision(),Path.of("target/canonical-runtime-"+suffix));
        var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"canonical-run");var moise=new MoiseBoardSnapshotSource("org","canonical-run",()->List.copyOf(boards),List::of,base.semanticContract(),path.getParent());
        groupId=moise.capture().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow().id().canonical();
        artifactId=official.artifacts().stream().filter(a->a.uuid().equals(artifact.getId().toString())).findFirst().orElseThrow().metadata().semanticId();
        captures=new SnapshotCoordinator(List.of(cartago,moise),8192,event->{var target=this.target.get();if(target!=null)try{target.apply(event);}catch(Throwable error){failure.compareAndSet(null,error);}});
        target.set(projector);projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());
        } catch(Exception|AssertionError error) {try{close();}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public NativeRuntimeProjector projector(){return projector;}
    public MObject counter(){return projector.coordinator().read(()->projector.mutations().objectForSemanticId(artifactId));}
    String value(String name){return projector.coordinator().read(()->counter().state(projector.system().state()).attributeValue(name).toString());}
    void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()&&System.nanoTime()<end){if(failure.get()!=null)throw new AssertionError("Actual source callback failed",failure.get());Thread.sleep(10);}
        assertNull(failure.get());assertTrue(condition.getAsBoolean(),"Actual source observation timed out");
    }
    public void resync() throws Exception {target.set(null);projector.applySnapshot(captures.capture(model.modelRevision(),3).snapshot());target.set(projector);}
    public void reviewerEnactment(boolean enact) throws Exception {bob.doAction(board,new Op(enact?"adoptRole":"leaveRole","reviewer"));}
    public void settled() throws Exception {projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);}
    @Override public void close() throws Exception {
        target.set(null);
        try {if(captures!=null)captures.close();if(projector!=null)projector.close();if(aliceBinding!=null)aliceBinding.close();if(bobBinding!=null)bobBinding.close();
            if(artifact!=null)environment.getController(counterWorkspace.getFullName()).removeArtifact("counter");if(board!=null)environment.getController(counterWorkspace.getFullName()).removeArtifact(boardName);
            for(String name:extraBoards)environment.getController(counterWorkspace.getFullName()).removeArtifact(name);
            if(alice!=null)CslRuntimeTestRegistry.closeCartagoContext(alice);if(bob!=null)CslRuntimeTestRegistry.closeCartagoContext(bob);
        } finally {environment.shutdown();restore("jacamo.bridge.modelRevision",previousRevision);restore("jacamo.bridge.generation",previousGeneration);}
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
