package org.tzi.use.plugins.jacamo.semantics.xml;

import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeStateMapping;
import org.tzi.use.plugins.jacamo.ui.SemanticEvidencePanel;
import static org.junit.jupiter.api.Assertions.*;

class GenericMappingArchitectureTest {
    @TempDir Path temp;

    private static final String AUCTION_PACKAGE = "org.tzi.use.plugins.jacamo.semantics.mapping.auction.";
    private static final String AUCTION_BYTECODE_PREFIX = "org/tzi/use/plugins/jacamo/semantics/mapping/auction/";
    private static final String AUCTION_ENCODER = "org.tzi.use.plugins.jacamo.semantics.evidence.AuctionEvidenceEncoder";
    private static final String AUCTION_ENCODER_BYTECODE = "org/tzi/use/plugins/jacamo/semantics/evidence/AuctionEvidenceEncoder";

    private static final List<String> PACKAGES_TO_SCAN = List.of(
            "org.tzi.use.plugins.jacamo.semantics.mapping.xml",
            "org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime",
            "org.tzi.use.plugins.jacamo.semantics.mapping.ocl"
    );

    @Test
    void genericMappingEngineAndXmlPackagesHaveZeroAuctionDependencies() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (String pkg : PACKAGES_TO_SCAN) {
            classes.addAll(classesInPackage(pkg));
        }
        classes.add(GenericMappingEngine.class);
        classes.add(SemanticEvidencePanel.class);

        assertFalse(classes.isEmpty(), "Must find classes to scan");

        for (Class<?> cls : classes) {
            // 1. Reflection check: superclass and interfaces
            if (cls.getSuperclass() != null) {
                assertNonAuctionType(cls, "superclass", cls.getSuperclass().getName());
            }
            for (Class<?> iface : cls.getInterfaces()) {
                assertNonAuctionType(cls, "interface", iface.getName());
            }

            // 2. Reflection check: fields
            for (Field field : cls.getDeclaredFields()) {
                assertNonAuctionType(cls, "field " + field.getName(), field.getType().getName());
            }

            // 3. Reflection check: constructors
            for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                for (Class<?> param : ctor.getParameterTypes()) {
                    assertNonAuctionType(cls, "constructor param", param.getName());
                }
            }

            // 4. Reflection check: methods
            for (Method method : cls.getDeclaredMethods()) {
                assertNonAuctionType(cls, "method return " + method.getName(), method.getReturnType().getName());
                for (Class<?> param : method.getParameterTypes()) {
                    assertNonAuctionType(cls, "method param " + method.getName(), param.getName());
                }
                for (Class<?> exc : method.getExceptionTypes()) {
                    assertNonAuctionType(cls, "method throws " + method.getName(), exc.getName());
                }
            }

            // 5. Bytecode constant pool scan: ensure no string/classref points to auction mapping
            String resourcePath = "/" + cls.getName().replace('.', '/') + ".class";
            try (InputStream in = cls.getResourceAsStream(resourcePath)) {
                assertNotNull(in, "Class resource must exist: " + resourcePath);
                byte[] bytecode = in.readAllBytes();
                String raw = new String(bytecode, java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(raw.contains(AUCTION_BYTECODE_PREFIX),
                        cls.getName() + " bytecode contains reference to auction package: " + AUCTION_BYTECODE_PREFIX);
                assertFalse(raw.contains(AUCTION_ENCODER_BYTECODE),
                        cls.getName() + " bytecode contains reference to AuctionEvidenceEncoder");
            }
        }
    }

    private static void assertNonAuctionType(Class<?> origin, String location, String typeName) {
        assertFalse(typeName.startsWith(AUCTION_PACKAGE),
                origin.getName() + " (" + location + ") depends on auction package: " + typeName);
        assertNotEquals(AUCTION_ENCODER, typeName,
                origin.getName() + " (" + location + ") depends on AuctionEvidenceEncoder");
    }

    private static List<Class<?>> classesInPackage(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        var url = GenericMappingArchitectureTest.class.getClassLoader().getResource(path);
        if (url == null) return List.of();
        Path dir = Paths.get(url.toURI());
        List<Class<?>> result = new ArrayList<>();
        try (var stream = Files.walk(dir, 1)) {
            for (Path p : stream.filter(p -> p.toString().endsWith(".class")).toList()) {
                String fileName = p.getFileName().toString();
                String className = packageName + "." + fileName.substring(0, fileName.length() - ".class".length());
                result.add(Class.forName(className));
            }
        }
        return result;
    }

    @Test
    void vocabularyHasNoHardcodedCaseKeywords() throws Exception {
        // Inspect MappingVocabulary class bytecode and string constants
        Class<?> vocabCls = Class.forName("org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingVocabulary");
        String resourcePath = "/" + vocabCls.getName().replace('.', '/') + ".class";
        try (InputStream in = vocabCls.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "MappingVocabulary bytecode must exist");
            byte[] bytecode = in.readAllBytes();
            String raw = new String(bytecode, java.nio.charset.StandardCharsets.ISO_8859_1);
            for (String caseKeyword : List.of("auction_env", "AuctionArtifact", "doAuction", "d_bid", "start_t1", "start_t2", "seller_sch", "buyer_sch")) {
                assertFalse(raw.contains(caseKeyword),
                        "MappingVocabulary contains hardcoded case keyword: " + caseKeyword);
            }
        }
    }

    @Test
    void arbitraryCaseBRequiresZeroAuctionJavaClasses() throws Exception {
        // Arbitrary Case B domain: Warehouse Inspection (Pallet / Supervisor / Inspector)
        Path jcm = GenericMappingEngineTest.JCM;
        Path ocl = Files.writeString(temp.resolve("warehouse.ocl"),
                "context Pallet inv NonnegativeWeight:\n" +
                "    self.weight >= 0\n");

        String xmlContent = """
                <mapping xmlns="urn:use:jacamo-mapping:2" version="2.0" id="warehouse-case-b" engineProfile="USE4GOAL_OBSERVED_V1" jcmRef="project">
                  <sources>
                    <file id="project" kind="JCM" path="%s" sha256="%s"/>
                  </sources>
                  <ocl sha256="%s"/>
                  <selectors>
                    <select id="agent-a" sourceRef="project" syntax="regex" expectedCount="1">
                      <pattern>agent\\s+(?P&lt;name&gt;a)</pattern>
                    </select>
                  </selectors>
                  <modelRules targetVersion="v4.0" targetModel="WarehouseModel">
                    <rule id="pallet-entity" selectRef="agent-a">
                      <createClassifier kind="Entity" name="Pallet"/>
                      <createAttribute name="weight" owner="Pallet" type="Integer" optional="false" mutable="true"/>
                    </rule>
                  </modelRules>
                  <istar name="WarehouseRequirements">
                    <actor id="supervisor-actor" name="Supervisor" kind="role">
                      <goal id="quality-goal" name="EnsureQuality" type="Achieve">
                        <condition>self.weight >= 0</condition>
                      </goal>
                    </actor>
                  </istar>
                  <runtime/>
                  <observers/>
                  <bindings>
                    <static id="pallet-binding" kind="ENTITY" target="Pallet" sourceKind="DERIVED_ARTIFACT_CLASS_ALIAS" feature="javaClass" value="org.tzi.use.plugins.jacamo.semantics.CslCounterArtifact" reason="Domain entity"/>
                    <static id="supervisor-binding" kind="ACTOR" target="Supervisor" sourceKind="MOISE_ROLE" feature="roleId" value="worker" reason="Intentional role"/>
                    <actorRuntime actor="Supervisor" bindingRef="supervisor-binding" roleBindingRef="supervisor-binding" activationPolicy="ENACTMENT_INTERVAL" episodePolicy="ENACTMENT_INCARNATION"/>
                    <elementRuntime target="EnsureQuality" actor="Supervisor" self="Pallet" derived="false" selfKind="ARTIFACT" reason="World condition"/>
                    <oclClause name="NonnegativeWeight" context="context Pallet"/>
                  </bindings>
                  <requirements/>
                  <omittedConcepts/>
                </mapping>
                """.formatted(jcm.getFileName(), hash(jcm), hash(ocl));

        Path xml = Files.writeString(temp.resolve("warehouse-mapping.xml"), xmlContent);

        ModelSnapshot snapshot = GenericMappingEngineTest.official(jcm);
        MappingInputs inputs = MappingInputs.read(jcm, xml, ocl);

        var program = new GenericMappingEngine().map(snapshot, inputs);

        assertNotNull(program.xmlPlan(), "Must generate xmlPlan");
        assertNull(program.casePlan(), "Must have zero legacy casePlan");
        assertTrue(program.cslModel().findEntity("Pallet").isPresent(), "Pallet entity must exist in CSL model");
        assertTrue(program.goalModel().findElement("EnsureQuality").isPresent(), "EnsureQuality must exist in goal model");
        assertEquals("warehouse-case-b", program.xmlPlan().inputs().specification().root().attr("id"));
    }

    @Test
    void runtimeIsolationBetweenDeclarativeAndLegacyCase() throws Exception {
        var helper = new GenericMappingEngineTest();
        helper.temp = temp;
        helper.input();
        var xml = temp.resolve("mapping.xml");
        Files.writeString(xml, Files.readString(xml).replace("</bindings>", "<oclClause name=\"Nonnegative\" context=\"context Counter\"/></bindings>"));
        var source = GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var inputs = MappingInputs.read(GenericMappingEngineTest.JCM, xml, temp.resolve("requirements.ocl"));
        var program = new GenericMappingEngine().map(source, inputs);

        assertNotNull(program.xmlPlan());
        assertNull(program.casePlan());

        var built = new CodeGroundedNativePipeline().buildCanonicalProgram(source, NativeProjectionMode.AUTO, program);
        var engine = new NativeRuntimeMutationEngine(
                built.nativeResult().state().system(),
                built.nativeResult().state().semanticObjectIndex(),
                new CodeGroundedRuntimeRuleRegistry(),
                program);

        assertTrue(engine.xmlCase(), "Must report xmlCase as true");
        var caseMapping = new org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoRuntimeStateMapping(program, engine.system().model()).caseMapping(engine.system().model());
        assertInstanceOf(XmlRuntimeStateMapping.class, caseMapping, "Runtime state mapping must be XmlRuntimeStateMapping");
        assertFalse(caseMapping instanceof org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionRuntimeStateMapping,
                "Case mapping must NOT be AuctionRuntimeStateMapping");
    }

    private static String hash(Path path) throws Exception {
        return TraceComplement.Asset.of("check", "bytes", Files.readAllBytes(path)).sha256();
    }
}
