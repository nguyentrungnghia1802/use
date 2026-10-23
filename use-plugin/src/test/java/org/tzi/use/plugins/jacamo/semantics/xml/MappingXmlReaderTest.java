package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingXmlReaderTest {
    static final String XML="""
        <mapping xmlns="urn:use:jacamo-mapping:2" version="2.0" id="sample" engineProfile="DERIVED_AND_RESOURCE_V1" jcmRef="project">
          <sources><file id="project" path="demo.jcm" sha256="%s" kind="JCM"/></sources>
          <ocl sha256="%s"/>
          <selectors/>
          <modelRules targetVersion="v4.0" targetModel="Sample"/>
          <istar name="SampleGoals"/>
          <runtime/><observers/><bindings/><requirements/><omittedConcepts/>
        </mapping>
        """.formatted("1".repeat(64),"2".repeat(64));
    static MappingSpecification parse(String xml){return new MappingXmlReader().parse(xml.getBytes(StandardCharsets.UTF_8),"spec.xml");}
    @Test void parsesNeutralLocatedVersionedSpecification() {
        var spec=parse(XML);assertEquals("sample",spec.root().attr("id"));assertEquals(2,spec.requireId("project").location().line());
        assertThrows(UnsupportedOperationException.class,()->spec.root().attributes().clear());
    }
    @Test void rejectsUnknownInstructionAttributeAndVersionWithoutFallback() {
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("<runtime/>","<runtime><executeScript/></runtime>")));
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("version=\"2.0\"","version=\"99.0\"")));
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("<runtime/>","<runtime ignored=\"true\"/>")));
    }
    @Test void rejectsUnknownReferenceDuplicateAndOracleSource() {
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("jcmRef=\"project\"","jcmRef=\"absent\"")));
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("<runtime/>","<runtime id=\"project\"/>")));
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("demo.jcm","model.istar")));
    }
    @Test void rejectsXxeWrongNamespaceAndOversize() {
        assertThrows(IllegalArgumentException.class,()->parse("<!DOCTYPE mapping [<!ENTITY x SYSTEM 'file:///C:/Windows/win.ini'>]>"+XML));
        assertThrows(IllegalArgumentException.class,()->parse(XML.replace("urn:use:jacamo-mapping:2","urn:other")));
        assertThrows(IllegalArgumentException.class,()->new MappingXmlReader().parse(new byte[1048577],"large"));
    }
}
