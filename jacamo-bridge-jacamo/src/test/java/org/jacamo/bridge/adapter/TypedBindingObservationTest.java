package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypedBindingObservationTest {
    @Test void moiseArgumentsRetainActualTermTypesRawPairAndUnknownInsteadOfStringGuesses()throws Exception {
        var id=new ArtifactId("sch_a1",UUID.randomUUID(),"ora4mas.nopl.SchemeBoard",new WorkspaceId("/main/aorg"),null);
        var properties=List.of(
                new ArtifactObsProperty("goalArgument",1,"goalArgument",ASSyntax.createAtom("sch_a1"),ASSyntax.createAtom("auction"),ASSyntax.createString("Id"),ASSyntax.createString("a1")),
                new ArtifactObsProperty("goalArgument",2,"goalArgument",ASSyntax.createAtom("sch_a1"),ASSyntax.createAtom("auction"),ASSyntax.createString("Other"),ASSyntax.createAtom("a1")),
                new ArtifactObsProperty("goalArgument",3,"goalArgument",ASSyntax.createAtom("sch_a1"),ASSyntax.createAtom("auction"),ASSyntax.createString("Unknown"),ASSyntax.parseTerm("X")));
        var cut=MoiseGoalObservation.decode(new ArtifactInfo(null,id,List.of(),properties,List.of(),null),"sch_a1");
        var value=cut.structuredArguments().get("auction").get("Id");
        assertEquals(cut.arguments().get("auction").get("Id"),value.get("raw"));assertEquals(Boolean.TRUE,value.get("supported"));
        assertEquals(Map.of("kind","STRING","value","a1"),value.get("key"));
        assertEquals("LITERAL",CanonicalJson.object(cut.structuredArguments().get("auction").get("Other").get("key")).get("kind"));
        assertEquals(Boolean.FALSE,cut.structuredArguments().get("auction").get("Unknown").get("supported"));
        assertThrows(UnsupportedOperationException.class,()->cut.structuredArguments().get("auction").clear());
        assertThrows(UnsupportedOperationException.class,()->cut.arguments().get("auction").clear());
    }
    @Test void feedbackArtifactIdentityAndBinary64AreCopiedThroughActualApisWithoutToStringFallback()throws Exception {
        var artifact=new ArtifactId("a1",UUID.randomUUID(),"auction_env.AuctionArtifact",new WorkspaceId("/main"),null);
        var feedback=new OpFeedbackParam<ArtifactId>();feedback.set(artifact);
        var encoded=CartagoArgumentObservation.value(feedback,"env");
        assertEquals("FEEDBACK",encoded.get("kind"));assertEquals(Boolean.TRUE,encoded.get("available"));
        var ref=CanonicalJson.object(encoded.get("value"));assertEquals("ARTIFACT_ID",ref.get("kind"));
        assertEquals(artifact.getId().toString(),ref.get("uuid"));assertEquals(artifact.getWorkspaceId().getUUID().toString(),ref.get("workspaceUuid"));
        assertEquals(OfficialCartagoAdapter.artifactId("env",artifact),ref.get("semanticId"));
        assertEquals("7fefffffffffffff",CartagoArgumentObservation.value(Double.MAX_VALUE,"env").get("rawBits"));
        assertEquals("8000000000000000",CartagoArgumentObservation.value(-0.0,"env").get("rawBits"));
        assertEquals("UNSUPPORTED",CartagoArgumentObservation.value(Double.NaN,"env").get("kind"));
        Object hostile=new Object(){@Override public String toString(){throw new AssertionError("Must not call arbitrary user methods");}};
        assertEquals("UNSUPPORTED",CartagoArgumentObservation.value(hostile,"env").get("kind"));
        var pending=CartagoArgumentObservation.value(new OpFeedbackParam<>(),"env");assertEquals(Boolean.FALSE,pending.get("available"));
    }
}
