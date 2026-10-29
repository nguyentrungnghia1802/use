package org.tzi.use.plugins.jacamo.semantics.xml;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables;

class AuthoredAuctionContainmentTest {
    @Test void containmentUsesExactSchemeResponsibilityInsteadOfAssumingAUniqueGroupInAWorkspace()throws Exception {
        String xml=Files.readString(Path.of("src/test/resources/auction-reference/authored-v1/auction-mapping.xml"));
        int start=xml.indexOf("<table id=\"qualified-scheme-group\"");String actual=xml.substring(start,xml.indexOf("</table>",start)+8);
        String tables="<runtime><tables><table id=\"qualified-groups\" dataset=\"groups\"><column name=\"uuid\" path=\"identity\" required=\"true\"/><identity mode=\"PHYSICAL\" path=\"identity\"/></table>"+actual+"</tables></runtime>";
        var executor=new RuntimeTables(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",tables)));
        var groups=new RuntimeTables.Dataset(List.of(row("g1",Map.of("identity","g1")),row("g2",Map.of("identity","g2"))),Completeness.PARTIAL);
        var scheme=row("s1",Map.of("identity","s1","responsibilityComplete",true,"responsibleGroups",List.of("g2")));
        var good=executor.execute(Map.of("groups",groups,"source-scheme-history",new RuntimeTables.Dataset(List.of(scheme),Completeness.PARTIAL))).get("qualified-scheme-group");
        assertEquals(1,good.rows().size());assertEquals("g2",good.rows().getFirst().values().get("group"));assertEquals(Completeness.COMPLETE,good.rows().getFirst().quality());
        var ambiguous=row("s1",Map.of("identity","s1","responsibilityComplete",true,"responsibleGroups",List.of("g1","g2")));
        assertTrue(executor.execute(Map.of("groups",groups,"source-scheme-history",new RuntimeTables.Dataset(List.of(ambiguous),Completeness.PARTIAL))).get("qualified-scheme-group").rows().isEmpty());
        assertTrue(xml.contains("tableRef=\"qualified-scheme-group\" leftPath=\"scheme\" rightPath=\"scheme\""));
        assertFalse(xml.contains("leftPath=\"schemeWorkspaceIdentity\" rightPath=\"organization\""));
    }
    private static RuntimeTables.Row row(String id,Map<String,Object> values){return new RuntimeTables.Row(id,values,Completeness.COMPLETE,Map.of("source-cut","cut","physical-identity",id),Set.of(id));}
}
