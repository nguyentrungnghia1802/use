package org.tzi.use.plugins.jacamo.ui;
import org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument;
import org.tzi.use.plugins.jacamo.dsl.istarscn.view.IStarScenarioView;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.dsl.oal.mm.AcceptedAolState;
import static org.junit.jupiter.api.Assertions.*;

class ModelStateDocumentTest {
    @TempDir Path directory;
    @Test void exportedStateReopensWithItsModelIdentityLinksAttributesAndSeparateExecutionMarkings()throws Exception {
        var rows=List.<Map<String,Object>>of(
            Map.of("object","subject","type","Team","_kind","GROUP","name","'Nhóm A & B'","_links",List.of(Map.of("relation","CslEntityScopes","ends",List.of("subject","artifact")))),
            Map.of("object","artifact","type","Auction","_kind","ENTITY","id","'a1'","running","true","_links",List.of(Map.of("relation","CslEntityScopes","ends",List.of("subject","artifact")))));
        var state=ModelStateDocument.objects(rows);assertEquals(1,state.associationLinks().size());
        var markings=List.of(new ModelStateDocument.Marking("a1","Participant","bid","FULFILLED"),new ModelStateDocument.Marking("a2","Participant","bid","PENDING"));
        for(var kind:ModelStateDocument.Kind.values()) {
            var original=new ModelStateDocument(kind,directory.resolve("auction.csl"),"42",state,markings);
            Path file=directory.resolve(kind+".xml");original.write(file);var read=ModelStateDocument.read(file);
            assertEquals(original,read);assertEquals("'Nhóm A & B'",read.state().objects().get("subject").attributes().get("name"));
        }
    }
    @Test void unresolvedLinksAndExternalEntitiesAreRejectedInsteadOfDisplayingInventedState()throws Exception {
        Path file=directory.resolve("bad.oal");
        Files.writeString(file,"<use-model-state version='1' kind='OAL' model='model.csl'><link relation='plays' source='missing' target='also_missing'/></use-model-state>");
        assertThrows(IllegalArgumentException.class,()->ModelStateDocument.read(file));
        Files.writeString(file,"<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///etc/passwd'>]><use-model-state version='1' kind='OAL' model='model.csl'>&external;</use-model-state>");
        assertThrows(Exception.class,()->ModelStateDocument.read(file));
    }
}
