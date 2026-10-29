package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import cartago.Op;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline native export/replay comparisons; never a second live runtime. */
class AuctionNativeRoundTripTest {
    private static final String CSL_HASH="478ce44d3226039d7b5dafe372f0d74e3cca2e04f35d67ac870e7ec66f2bc720";

    @Test void originalMetadataAndNativeHelperSchemaSurviveOfficialExportWithoutChangingCanonicalM1()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var model=f.projector.system().model();var exported=new NativeUseExporter().export(model);
            assertEquals(CSL_HASH,CslModelFingerprint.of(f.program.cslModel()));
            assertEquals(exported.originalStructuralHash(),exported.recompiledStructuralHash());
            assertTrue(model.enumTypes().isEmpty());assertTrue(exported.recompiledModel().enumTypes().isEmpty());
            for(var attribute:model.getClass("Auction").attributes()) {
                var metadata=attribute.getAnnotation(Use75CslModelBackend.ATTRIBUTE);assertNotNull(metadata,attribute.name());
                assertEquals("false",metadata.getValues().get("optional"));
                assertEquals(metadata.getValues(),exported.recompiledModel().getClass("Auction").attribute(attribute.name(),true).getAnnotation(Use75CslModelBackend.ATTRIBUTE).getValues());
                assertEquals(!attribute.name().equals("id"),Boolean.parseBoolean(metadata.getValues().get("mutable")));
                assertFalse(metadata.getValues().get("id64").isBlank());
                assertEquals(Set.of("running","task","winner","currentWinner").contains(attribute.name()),Boolean.parseBoolean(metadata.getValues().get("hasDefault")));
            }
            assertEquals(conditions(model),conditions(exported.recompiledModel()));
            assertEquals(CSL_HASH,CslModelFingerprint.of(f.program.cslModel()));
        }
    }
    @Test void requiredAndDefaultAnnotationsNeverInventRuntimeValues()throws Exception {
        var source=new CslFrontend().compile(Files.readString(Path.of("examples/auction/auction_csl.csl")),"auction.csl").requireModel();
        var binding=new AuctionUseModelAdapter().compile(source);var system=new MSystem(binding.model());
        var object=UseSystemApi.create(system,false).createObject("Auction","unobserved");
        for(var attribute:object.cls().attributes())assertTrue(object.state(system.state()).attributeValue(attribute).isUndefined(),attribute.name());
        var replay=roundTrip(system);assertEquals(signature(system.state()),signature(replay.state()));
        for(var attribute:replay.model().getClass("Auction").attributes())assertTrue(replay.state().objectByName("unobserved").state(replay.state()).attributeValue(attribute).isUndefined());
        assertEquals(CSL_HASH,CslModelFingerprint.of(source));
    }
    @Test void twoActualAuctionsAndObserverValuesRoundTripIncludingMaximumRealAndQuotedService()throws Exception {
        try(var f=new AuctionCastFixture(1,1,true)) {
            var first=AuctionRuntimeProjectionTest.auction(f.projector,"castOne");
            var second=AuctionRuntimeProjectionTest.auction(f.projector,"castTwo");
            var initial=roundTrip(f.projector.system());
            assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE),bits(initial.state().objectByName(first.name()),initial.state()));
            assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE),bits(initial.state().objectByName(second.name()),initial.state()));
            f.org.live.actor.doAction(f.artifacts.getFirst(),new Op("start","service 'quoted' \\ route"),5000);
            f.org.live.actor.doAction(f.artifacts.getFirst(),new Op("bid",42.5),5000);f.resync();
            var system=f.projector.system();var before=signature(system.state());
            var metadata=f.projector.mutations().objectMetadata(first);var replay=roundTrip(system);
            assertEquals(before,signature(replay.state()));assertEquals(before,signature(system.state()));
            assertEquals(metadata,f.projector.mutations().objectMetadata(first));assertSame(system,f.projector.system());
            assertEquals(Double.doubleToRawLongBits(42.5),bits(replay.state().objectByName(first.name()),replay.state()));
            assertEquals(Double.doubleToRawLongBits(Double.MAX_VALUE),bits(replay.state().objectByName(second.name()),replay.state()));
        }
    }
    @Test void finiteBinary64CodecAndNativeSoilPreserveRawBitsWithoutClampingOrSentinels()throws Exception {
        var source=new CslFrontend().compile(Files.readString(Path.of("examples/auction/auction_csl.csl")),"auction.csl").requireModel();
        var binding=new AuctionUseModelAdapter().compile(source);var system=new MSystem(binding.model());var api=UseSystemApi.create(system,false);var codec=new Use75ValueCodec();
        double[] values={Double.MAX_VALUE,-Double.MAX_VALUE,Double.MIN_NORMAL,Double.MIN_VALUE,0.0,-0.0,42.5};
        for(int i=0;i<values.length;i++) {
            var object=api.createObject("Auction","number"+i);
            var value=codec.decode(org.vnu.sme.goal.dsl.csl.mm.CslPrimitiveType.REAL,values[i],binding.model());
            api.setAttributeValueEx(object,object.cls().attribute("best_bid",true),value);
        }
        var text=new NativeUseSoilExporter().export(system).commands();assertTrue(text.contains(Double.toString(Double.MAX_VALUE)));
        var replay=roundTrip(system);
        for(int i=0;i<values.length;i++)assertEquals(Double.doubleToRawLongBits(values[i]),bits(replay.state().objectByName("number"+i),replay.state()),"number"+i);
    }
    @Test void unavailableRequiredStateStaysUndefinedThroughExportAndCannotGainAuthoredDefaults()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var object=AuctionRuntimeProjectionTest.auction(f.projector,"castOne");
            var cut=AuctionRuntimeProjectionTest.mutate(f.cut(),fact->{
                if(fact.kind()!=RuntimeFactKind.ARTIFACT||!"castOne".equals(fact.values().get("name")))return fact;
                var values=new LinkedHashMap<>(fact.values());var observation=new LinkedHashMap<>(CanonicalJson.object(values.get("auctionStateObservation")));
                observation.put("fields",Map.of());values.put("auctionStateObservation",observation);
                return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());
            });
            f.projector.applySnapshot(cut,f.run().id(),f.run().generation());
            assertEquals("INCOMPLETE",f.projector.mutations().metadata(object,"auctionFullState"));
            assertTrue(object.state(f.projector.system().state()).attributeValue("currentWinner").isUndefined());
            var replay=roundTrip(f.projector.system());assertEquals(signature(f.projector.system().state()),signature(replay.state()));
            assertTrue(replay.state().objectByName(object.name()).state(replay.state()).attributeValue("currentWinner").isUndefined());
        }
    }
    @Test void recreatedArtifactHasFreshNativeIdentityAndStaleGenerationCannotAlterExportedState()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var old=AuctionRuntimeProjectionTest.auction(f.projector,"castOne");var stale=f.cut();
            f.disposeArtifact(0);f.resync();var freshSource=f.org.live.auction("castOne");f.resync();
            var fresh=AuctionRuntimeProjectionTest.auction(f.projector,"castOne");assertNotSame(old,fresh);assertNotEquals(old.name(),fresh.name());
            assertEquals(freshSource.getId().toString(),f.projector.mutations().metadata(fresh,"uuid"));
            var before=signature(f.projector.system().state());
            assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,()->f.projector.applySnapshot(stale,f.run().id(),f.run().generation()-1));
            assertEquals(before,signature(f.projector.system().state()));
            var replay=roundTrip(f.projector.system());assertNull(replay.state().objectByName(old.name()));assertNotNull(replay.state().objectByName(fresh.name()));assertEquals(before,signature(replay.state()));
        }
    }
    private static MSystem roundTrip(MSystem system) {
        var model=new NativeUseExporter().export(system.model());
        return new NativeUseSoilExporter().replay(model.recompiledModel(),new NativeUseSoilExporter().export(system).commands());
    }
    private static long bits(MObject object,MSystemState state){return Double.doubleToRawLongBits(((RealValue)object.state(state).attributeValue("best_bid")).value());}
    private static SortedMap<String,String> signature(MSystemState state) {
        var rows=new TreeMap<String,String>();
        for(var object:state.allObjects()) {
            rows.put("object:"+object.name(),object.cls().name());
            for(var attribute:object.cls().allAttributes())rows.put("value:"+object.name()+":"+attribute.name(),value(object.state(state).attributeValue(attribute)));
        }
        // Unqualified USE links may store [] or one empty list per end;
        // both have exactly zero qualifier values. Auction has no qualifiers.
        for(var link:state.allLinks())rows.put("link:"+link.association().name()+":"+link.linkedObjects().stream().map(MObject::name).toList(),link.getQualifier().stream().flatMap(List::stream).map(AuctionNativeRoundTripTest::value).toList().toString());
        return rows;
    }
    private static String value(Value value) {
        if(value instanceof RealValue real)return "Real:"+Long.toHexString(Double.doubleToRawLongBits(real.value()));
        if(value instanceof CollectionValue collection)return value.type()+":"+collection.collection().stream().map(AuctionNativeRoundTripTest::value).sorted().toList();
        if(value instanceof ObjectValue object)return "Object:"+object.value().name();
        return value.type()+":"+value;
    }
    private static List<String> conditions(org.tzi.use.uml.mm.MModel model) {
        return model.getClass("Auction").operations().stream().flatMap(op->java.util.stream.Stream.concat(op.preConditions().stream(),op.postConditions().stream()).map(c->op.signature()+":"+c.name()+":"+c.expression())).sorted().toList();
    }
}
