package org.tzi.use.plugins.jacamo.semantics.auction;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.api.*;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.binding.UseProjectionBinding;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import static org.junit.jupiter.api.Assertions.*;

class AuctionObserverSchemaTest {
    private static final AuctionCaseBundle BUNDLE=AuctionCaseBundle.read(Path.of("examples/auction"));
    private static AuctionMappingSpecification specification(){return new AuctionMappingXmlLoader().load(BUNDLE);}
    private static Use75ModelBinding compile(){
        var csl=new CslFrontend().compile(new String(BUNDLE.require("target-csl").bytes(),StandardCharsets.UTF_8),"auction.csl").requireModel();
        return new AuctionUseModelAdapter().compile(csl,specification());
    }
    @Test void nativeHelpersComeFromXmlWithTypedSetsReferencesAndNoExtraCanonicalEntities() {
        var binding=compile();var model=binding.model();
        assertEquals(1,binding.cslModel().entities().size());assertEquals(5,binding.classifiers().size());
        assertEquals("478ce44d3226039d7b5dafe372f0d74e3cca2e04f35d67ac870e7ec66f2bc720",CslModelFingerprint.of(binding.cslModel()));
        var views=new AuctionObserverUseBinding(specification());
        assertEquals(Set.of("view-monitor","view-obligation"),views.classViews().keySet());
        assertEquals(Set.of("enabled","succeeded"),views.valueType("view-step").getParts().keySet());
        assertNull(model.getClass("step"));
        assertEquals(TypeFactory.mkSet(TypeFactory.mkString()),model.getClass("AuctionMonitor").attribute("bidDoneBy",true).type());
        assertNotNull(model.getClass("AuctionObligation").navigableEnds().get("monitor"));
        for(var view:views.classViews().entrySet()) {
            var cls=model.getClass(view.getValue());assertNotNull(cls);
            assertEquals(BUNDLE.require("case-rules").sha256(),cls.getAnnotationValue(AuctionObserverUseBinding.ANNOTATION,"mappingHash"));
            assertFalse(cls.getAnnotationValue(Use75CslModelBackend.HELPER,"id64").isBlank());
            for(var attribute:cls.attributes())assertNotNull(attribute.getAnnotation(AuctionObserverUseBinding.ANNOTATION));
        }
        var inventory=UseProjectionBinding.of(model).inventory();
        assertEquals(2,inventory.types().stream().filter(t->t.semanticKind().equals("EXTERNAL_OBSERVER_VIEW")).count());
    }
    @Test void existingNativeStateBuilderSessionMutationEngineAndOclShareOneSystem() throws Exception {
        var binding=compile();var trace=new CodeGroundedTraceCollector();
        var schema=new NativeUseModelBuilder().buildCsl(binding,trace,NativeProjectionMode.AUTO);
        Path jcm=Path.of("examples/auction/auction.jcm").toAbsolutePath();
        var source=new JacamoSpecificationModel(new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm).semanticContract());
        var state=new NativeUseStateBuilder().build(source,schema,trace);
        var session=new Session();session.setSystem(state.system());
        var engine=new NativeRuntimeMutationEngine(state.system(),state.semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        assertSame(binding.model(),schema.model());assertSame(state.system(),session.system());assertSame(session.system(),engine.system());
        assertSame(engine.system(),UseStateReadView.pin(null,engine.system().state()).after().system());
        assertTrue(engine.system().state().allObjects().isEmpty(),"No configured player, timestamp, flag or observer instance is fabricated at import");
        var api=UseSystemApi.create(engine.system(),false);api.createObject("AuctionMonitor","m");
        assertSame(session.system().model().getClass("AuctionMonitor"),session.system().state().objectByName("m").cls());
        assertTrue(session.system().state().objectByName("m").state(session.system().state()).attributeValue("adminBidOverride").isUndefined());
    }
    @Test void allThreeExternalInvariantBodiesCompileUnchangedOnHelperSchema() throws Exception {
        var binding=compile();var api=new UseModelApi(binding.model());
        String source=new String(BUNDLE.require("input-ocl").bytes(),StandardCharsets.UTF_8);
        var names=List.of("SchemeOrder","BidEvidence","WithinDeadline");
        for(String name:names) {
            int start=source.indexOf("inv "+name+":")+("inv "+name+":").length();
            int end=source.indexOf("\ncontext ",start);if(end<0)end=source.length();
            String body=source.substring(start,end).replaceAll("(?m)--[^\\r\\n]*","");
            String owner=name.equals("WithinDeadline")?"AuctionObligation":"AuctionMonitor";
            var expression=NativeOclCompilation.predicate(binding.model(),binding.model().getClass(owner),name,body);
            api.createInvariantEx(name,owner,expression,false);
        }
        // The existing observed-player backend also installs its native M0
        // compatibility invariant; it is separate from the two authored CSL ones.
        assertEquals(Set.of("AuctioneerPopulation","ParticipantPopulation","CslV4RoleCompatibility",
                "SchemeOrder","BidEvidence","WithinDeadline"),binding.model().classInvariants().stream()
                .map(i->i.name()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void unsupportedSchemaAndCollisionsFailBeforeInstallingAnyHelper() throws Exception {
        String original=new String(BUNDLE.require("case-rules").bytes(),StandardCharsets.UTF_8);
        for(String changed:List.of(original.replace("type=\"Set(String)\"","type=\"Set(Integer)\""),original.replace("formatVersion=\"1.0\"","formatVersion=\"99\"")))
            assertThrows(IllegalArgumentException.class,()->new AuctionObserverUseBinding(new AuctionMappingXmlLoader().parse(changed.getBytes(StandardCharsets.UTF_8),"changed.xml")));
        var csl=new CslFrontend().compile(new String(BUNDLE.require("target-csl").bytes(),StandardCharsets.UTF_8),"auction.csl").requireModel();
        var binding=new AuctionUseModelAdapter().compile(csl);var api=new UseModelApi(binding.model());api.createClass("AuctionMonitor",false);
        int before=binding.model().classes().size();
        assertThrows(IllegalArgumentException.class,()->new AuctionObserverUseBinding(specification()).install(api,binding));
        assertEquals(before,binding.model().classes().size());assertNull(binding.model().getClass("AuctionObligation"));
    }
    @Test void helperTypesTraceAndNavigationSurviveNativeExportAndRecompile() throws Exception {
        var binding=compile();var text=new StringWriter();binding.model().processWithVisitor(new Use75ModelPrinter(new PrintWriter(text,true)));
        var errors=new StringWriter();var model=USECompiler.compileSpecification(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)),"auction.use",URI.create("memory:/auction.use"),new PrintWriter(errors,true),new ModelFactory());
        assertNotNull(model,errors.toString());
        for(String name:List.of("AuctionMonitor","AuctionObligation")) {
            for(var annotation:binding.model().getClass(name).getAllAnnotations().entrySet())
                assertEquals(annotation.getValue().getValues(),model.getClass(name).getAnnotation(annotation.getKey()).getValues());
            assertEquals(binding.model().getClass(name).attributes().stream().map(a->a.name()+":"+a.type()).toList(),model.getClass(name).attributes().stream().map(a->a.name()+":"+a.type()).toList());
        }
        assertNotNull(model.getClass("AuctionObligation").navigableEnds().get("monitor"));
    }
}
