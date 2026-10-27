package org.tzi.use.plugins.jacamo.semantics.xml;

import java.lang.management.ManagementFactory;
import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables;
import static org.junit.jupiter.api.Assertions.*;

/** Allocation, not elapsed time: enriching proofs must not repeatedly copy an
 * already-owned source history. Runs on the supported JDK21 HotSpot distribution. */
class RuntimeJoinAllocationTest {
    @Test void joinProofEnrichmentDoesNotMultiplyAnUnselectedImmutableHistory() {
        var joins=new StringBuilder();for(int i=0;i<24;i++)joins.append("<join tableRef=\"owners\" leftPath=\"owner\" rightPath=\"id\" as=\"join"+i+"\" cardinality=\"ONE\" required=\"true\"/>");
        String runtime="<runtime><tables><table id=\"owners\" dataset=\"owners-source\"><column name=\"id\" path=\"id\" required=\"true\"/></table>"
                +"<table id=\"selected\" dataset=\"source\">"+joins+"<column name=\"id\" path=\"id\" required=\"true\"/></table></tables></runtime>";
        var tables=new RuntimeTables(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime)));
        var history=new ArrayList<Map<String,Object>>();for(int i=0;i<4096;i++)history.add(Map.of("sequence",i,"value","observed-source-entry"));
        var mutable=new LinkedHashMap<String,Object>();mutable.put("id","item");mutable.put("owner","owner");mutable.put("history",history);
        var owned=new RuntimeTables.Row("item",mutable,Completeness.COMPLETE,Map.of("source-cut","cut"),Set.of("item"));
        var owner=new RuntimeTables.Row("owner",Map.of("id","owner"),Completeness.COMPLETE,Map.of("source-cut","cut","physical-identity","physical-owner"),Set.of("owner"));
        var data=Map.of("owners-source",new RuntimeTables.Dataset(List.of(owner),Completeness.COMPLETE),"source",new RuntimeTables.Dataset(List.of(owned),Completeness.COMPLETE));
        // Row ownership still freezes caller data; internal proof sharing cannot leak mutation.
        mutable.put("id","changed");history.clear();assertEquals("item",owned.values().get("id"));
        assertEquals(4096,((List<?>)owned.values().get("history")).size());
        var bean=ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
        assertNotNull(bean);assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        for(int i=0;i<3;i++)tables.execute(data);
        long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
        var selected=tables.execute(data).get("selected");long allocated=bean.getThreadAllocatedBytes(id)-before;
        System.out.println("XML_JOIN_PROOF_ALLOCATED_BYTES="+allocated+" joins=24 historyEntries=4096");
        assertEquals(Completeness.COMPLETE,selected.populationQuality());assertEquals(Map.of("id","item"),selected.rows().getFirst().values());
        var proofs=selected.rows().getFirst().proofs();assertEquals("cut",proofs.get("source-cut"));
        for(int i=0;i<24;i++){assertEquals("cut",proofs.get("join:join"+i+":source-cut"));assertEquals("physical-owner",proofs.get("join:join"+i+":physical-identity"));}
        assertThrows(UnsupportedOperationException.class,()->proofs.put("fake","proof"));
        assertTrue(allocated<4L*1024*1024,"Proof-only joins must stay below4MiB instead of copying a4096-entry payload24 times; actual="+allocated);
    }
}
