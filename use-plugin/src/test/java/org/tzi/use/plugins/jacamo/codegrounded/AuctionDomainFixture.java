package org.tzi.use.plugins.jacamo.codegrounded;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.CartagoSnapshotSource;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;

/** Runs the unmodified reference artifact through official CArtAgO APIs. */
final class AuctionDomainFixture implements AutoCloseable {
    final CodeGroundedNativePipeline.Result pipeline;
    final NativeRuntimeProjector projector;
    final CartagoBasicContext context;
    final ArtifactId artifact;
    private final CartagoEnvironment environment;
    private final CartagoSnapshotSource source;
    private long sequence;
    AuctionDomainFixture() throws Exception {
        this(Path.of("../../jacamo/examples/auction/auction.jcm"),"src/env/auction_env/AuctionArtifact.java","auction_env.AuctionArtifact");
    }
    AuctionDomainFixture(Path entry,String artifactSource,String artifactClass,Object... arguments) throws Exception {
        entry=entry.toAbsolutePath().normalize();
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
        if(compiler==null) throw new IllegalStateException("JDK compiler required for original Auction acceptance");
        var diagnostics=new java.io.ByteArrayOutputStream();
        int exit=compiler.run(null,diagnostics,diagnostics,"-classpath",System.getProperty("java.class.path"),
            "-d",Path.of("target/test-classes").toAbsolutePath().toString(),entry.getParent().resolve(artifactSource).toString());
        if(exit!=0) throw new IllegalStateException(diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8));
        environment=CartagoEnvironment.getInstance(); environment.init();
        String suffix=UUID.randomUUID().toString().replace("-", "");
        context=new CartagoBasicContext("auctionObserver"+suffix);
        artifact=context.makeArtifact(context.getJoinedWspId("main"),"a1"+suffix,artifactClass,arguments);
        source=new CartagoSnapshotSource(environment,List.of("/main"),"auction-domain-session");
        pipeline=new CodeGroundedNativePipeline().build(DomainRuntimeProjectionTest.load(entry));
        projector=new NativeRuntimeProjector(pipeline,"auction-domain-session",1,pipeline.source().revision());
        refresh();
    }
    void refresh() {
        var all=source.capture();
        var target=all.stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT && artifact.getId().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow();
        String semanticId=target.values().get("semanticId").toString(),workspace=target.values().get("workspaceSemanticId").toString();
        var facts=all.stream().filter(f->f==target || (f.kind()==RuntimeFactKind.WORKSPACE && workspace.equals(f.values().get("semanticId")))
            || (f.kind()==RuntimeFactKind.PROPERTY && semanticId.equals(f.values().get("artifactSemanticId")))
            || (f.kind()==RuntimeFactKind.OPERATION && f.values().get("operationDescriptor") instanceof Map<?,?> descriptor
                && semanticId.equals(descriptor.get("artifactSemanticId")))).toList();
        var watermarks=Map.of("cartago",new SourceWatermark("cartago",sequence));
        projector.applySnapshot(new RuntimeSnapshot("auction-observed-"+sequence++,pipeline.source().revision(),Instant.now(),Instant.now(),
            watermarks,watermarks,1,facts,Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE,"moise",Completeness.COMPLETE),"observed-original-artifact"));
    }
    @Override public void close() throws Exception {
        projector.close(); source.close(); environment.getController("/main").removeArtifact(artifact.getName());
    }
}
