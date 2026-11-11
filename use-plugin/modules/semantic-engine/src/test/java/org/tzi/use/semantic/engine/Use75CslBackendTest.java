package org.tzi.use.semantic.engine;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.vnu.sme.goal.dsl.csl.mm.*;
import static org.junit.jupiter.api.Assertions.*;

class Use75CslBackendTest {
    @Test void equalAnnotationValuesHavePortableTextWithoutMutatingNativeModels() {
        var first=compile("csl v4.0 Portable { entity Counter { p : Integer; } }",false);
        var second=compile("csl v4.0 Portable { entity Counter { p : Integer; } }",false);
        var ascending=new LinkedHashMap<String,String>();var descending=new LinkedHashMap<String,String>();
        for(String key:List.of("alpha","beta","zeta"))ascending.put(key,key);
        for(String key:List.of("zeta","beta","alpha"))descending.put(key,key);
        for(var binding:List.of(first,second)) {
            var values=binding==first?ascending:descending;
            binding.model().addAnnotation(new MElementAnnotation("PortableOrder",values));
            binding.classifier("Counter").addAnnotation(new MElementAnnotation("PortableOrder",values));
            binding.classifier("Counter").attribute("p",true).addAnnotation(new MElementAnnotation("PortableOrder",values));
        }
        var a=new StringWriter();var b=new StringWriter();
        first.model().processWithVisitor(new Use75ModelPrinter(new PrintWriter(a,true)));
        second.model().processWithVisitor(new Use75ModelPrinter(new PrintWriter(b,true)));
        assertEquals(a.toString(),b.toString());
        assertEquals(List.of("zeta","beta","alpha"),new ArrayList<>(second.model().getAnnotation("PortableOrder").getValues().keySet()));
    }
    private Use75ModelBinding compile(String text, boolean players) {
        return new Use75CslModelBackend().compile(new CslFrontend().compile(text,"backend.csl").requireModel(),
                players ? M0ExtensionSpec.observedPlayers() : M0ExtensionSpec.schemaOnly());
    }
    @Test void originalV4ProposalModelBuildsAllInvariantsAndRoundTripsThroughNativePrinter() throws Exception {
        String source = Files.readString(Path.of("src/test/resources/oracle/csl/proposal_review.csl"));
        var binding = compile(source, false); var model = binding.model();
        assertEquals(11, model.classInvariants().size());
        assertEquals(7, model.associations().size());
        assertEquals("ProposalReviewCase", binding.declarationOwners().get("Proposal"));
        assertNull(model.getAssociation("ProposalReviewCase_contains_Proposal"));
        var buffer = new StringWriter(); model.processWithVisitor(new Use75ModelPrinter(new PrintWriter(buffer, true)));
        var errors = new StringWriter();
        var recompiled = USECompiler.compileSpecification(new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)),
                "roundtrip.use", URI.create("memory:/roundtrip.use"), new PrintWriter(errors,true), new ModelFactory());
        assertNotNull(recompiled, errors.toString());
        assertEquals(model.classes().size(), recompiled.classes().size());
        assertEquals(model.associations().size(), recompiled.associations().size());
        assertEquals(model.classInvariants().size(), recompiled.classInvariants().size());
        for (var cls : model.classes()) {
            var target = recompiled.getClass(cls.name()); assertNotNull(target);
            assertEquals(cls.attributes().stream().map(a -> a.name()+":"+a.type()).sorted().toList(),
                    target.attributes().stream().map(a -> a.name()+":"+a.type()).sorted().toList());
            assertEquals(cls.getAllAnnotations().keySet(), target.getAllAnnotations().keySet());
        }
    }
    @Test void roleInheritanceUsesOneIdentityAndDefaultsDoNotPopulateObservedState() throws Exception {
        var binding = compile("csl v4.0 Roles { role Parent { p : Integer mutable default 7; } orgContext Outer { orgContext Inner { role Child extends Parent; } } }",true);
        assertFalse(binding.classifier("Parent").isAbstract());
        assertFalse(binding.classifier("Child").isAbstract());
        assertTrue(binding.classifier("Child").isSubClassifierOf(binding.classifier("Parent")));
        assertEquals("Inner", binding.declarationOwners().get("Child"));
        assertEquals("Outer", binding.declarationOwners().get("Inner"));
        assertEquals(2, binding.model().associations().size()); // Explicit M0 extension only.
        assertTrue(binding.classifier("Parent").attribute("p",true).getInitExpression().isPresent() == false);
        var system = new MSystem(binding.model()); assertTrue(system.state().allObjects().isEmpty());
        var api = UseSystemApi.create(system,false); var role = api.createObject("Child","role");
        assertTrue(role.state(system.state()).attributeValue("p").isUndefined());
        assertEquals(1, system.state().allObjects().size());
        assertSame(role, system.state().objectsOfClassAndSubClasses(binding.classifier("Parent")).iterator().next());
        assertNotEquals(binding.classifier("Parent"), binding.model().getClass("RuntimePlayer"));
        assertFalse(binding.classifier("Parent").isSubClassifierOf(binding.model().getClass("RuntimePlayer")));
        assertTrue(binding.classifier("Inner").isSubClassifierOf(binding.model().getClass("RuntimePlayer")));
    }
    @Test void preservesEnumDatatypesModifiersAssociationOrderAndNarySchemaAdmission() throws Exception {
        var binding = compile("csl v4.0 Typed { enum Status { A, B } datatype Money; entity Item { s : Status optional mutable default A; price : Money optional; } entity Other; role Owner; association N { Item [*] role items; Other [0..1] role other; Owner [1] role owner; } }",false);
        assertNotNull(binding.model().enumType("Status"));
        assertEquals(1,binding.model().dataTypes().size());
        var attribute = binding.attributes().get("Item::s").specification();
        assertTrue(attribute.optional()); assertTrue(attribute.mutable()); assertEquals("A",attribute.defaultValue().orElseThrow());
        assertEquals(List.of("items","other","owner"),binding.associationEndRoles().get("N"));
        assertEquals(List.of("Item","Other","Owner"),binding.model().getAssociation("N").associationEnds().stream().map(e->e.cls().name()).toList());
        var codec = new Use75ValueCodec();
        assertTrue(binding.liveDiagnostics(codec.supportedTypes(binding.cslModel()),false).stream().anyMatch(d->d.code().equals("NARY_RUNTIME_WRITER_REQUIRED")));
        assertTrue(binding.liveDiagnostics(codec.supportedTypes(binding.cslModel()),true).stream().anyMatch(d->d.code().equals("EXACT_RUNTIME_CODEC_REQUIRED")));
        assertThrows(IllegalArgumentException.class,()->binding.requireLiveCompatible(codec.supportedTypes(binding.cslModel()),true));
        assertEquals("Status::B",codec.decode(attribute.type(),"B",binding.model()).toString());
        assertThrows(IllegalArgumentException.class,()->codec.decode(attribute.type(),"Missing",binding.model()));
        assertThrows(IllegalArgumentException.class,()->codec.decode(CslPrimitiveType.BOOLEAN,"false",binding.model()));
        assertThrows(ArithmeticException.class,()->codec.decode(CslPrimitiveType.INTEGER,Long.MAX_VALUE,binding.model()));
        assertThrows(IllegalArgumentException.class,()->codec.decode(CslPrimitiveType.REAL,Double.NaN,binding.model()));
        assertThrows(IllegalArgumentException.class,()->codec.decode(CslPrimitiveType.REAL,9007199254740993L,binding.model()));
    }
    @Test void rejectsInvalidSemanticsReservedNamesAndNativeOclTypeErrorsBeforeRuntimeExists() {
        for (String invalid : List.of("csl v4.0 Bad { role R; entity E extends R; }", "csl v4.0 Bad { entity A extends B; entity B extends A; }",
                "csl v4.0 Bad { entity A; entity B; association N { A [3..1]; B [*]; } }", "csl v4.0 Bad { entity String; }",
                "csl v4.0 Bad { entity E; context E inv InvalidType: 1; }", "csl v4.0 Bad { entity E; context E inv InvalidPath: self.unknown = 1; }")) {
            assertThrows(IllegalArgumentException.class,()->compile(invalid,false),invalid);
        }
        assertThrows(IllegalArgumentException.class,()->compile("csl v4.0 Bad { role RuntimePlayer; }",true));
        var inconsistent = new CslModel("v4.0","Bad",List.of(),List.of(new CslEntity("A",List.of()),new CslEntity("B",Optional.of("A"),List.of())),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->new Use75CslModelBackend().compile(inconsistent));
    }
    @Test void compatibilityIsSymmetricMetadataAndAnExplicitM0NativeInvariant() {
        var binding = compile("csl v4.0 Compat { role A; role B; orgContext Team {} A compatible B; }",true);
        assertNotNull(binding.model().getAnnotation("OriginalCslCompatibility_0"));
        var invariant = binding.model().classInvariants().stream().filter(i->i.name().equals("CslV4RoleCompatibility")).findFirst().orElseThrow();
        assertTrue(invariant.bodyExpression().toString().contains("A"));assertTrue(invariant.bodyExpression().toString().contains("B"));
        assertNull(binding.model().getAssociation("Compatibility"));
    }
    @Test void namedDatatypeDeclarationAndAttributeSurviveNativeRoundTrip() throws Exception {
        var binding = compile("csl v4.0 OpaqueSchema { datatype Money; entity Item { price : Money optional; } }",false);
        var output = new StringWriter(); binding.model().processWithVisitor(new Use75ModelPrinter(new PrintWriter(output,true)));
        var errors = new StringWriter();
        var recompiled = USECompiler.compileSpecification(new ByteArrayInputStream(output.toString().getBytes(StandardCharsets.UTF_8)),
                "opaque.use",URI.create("memory:/opaque.use"),new PrintWriter(errors,true),new ModelFactory());
        assertNotNull(recompiled,errors.toString());
        assertEquals(1,recompiled.dataTypes().size());
        assertEquals("Money",recompiled.getClass("Item").attribute("price",true).type().toString());
    }
    @Test void compatibilityDependsOnExactPlayerAndRuntimeContext() throws Exception {
        var binding = compile("csl v4.0 CompatRuntime { role A; role B; role C; role Child extends A; orgContext Team {} A compatible B; }",true);
        var system = new MSystem(binding.model()); var api = UseSystemApi.create(system,false);
        api.createObject("AtomicAgent","player");api.createObject("Team","first");api.createObject("Team","second");
        for (String[] occurrence : List.of(new String[]{"A","a","first"},new String[]{"B","b","first"},new String[]{"Child","child","first"},new String[]{"C","c","second"})) {
            api.createObject(occurrence[0],occurrence[1]);api.createLink("Plays","player",occurrence[1]);api.createLink("InContext",occurrence[2],occurrence[1]);
        }
        var invariant = binding.model().classInvariants().stream().filter(i->i.name().equals("CslV4RoleCompatibility")).findFirst().orElseThrow();
        assertEquals(BooleanValue.TRUE,new org.tzi.use.uml.ocl.expr.Evaluator().eval(invariant.expandedExpression(),system.state()));
        api.deleteLink("InContext",new String[]{"second","c"});api.createLink("InContext","first","c");
        assertEquals(BooleanValue.FALSE,new org.tzi.use.uml.ocl.expr.Evaluator().eval(invariant.expandedExpression(),system.state()));
    }
}
