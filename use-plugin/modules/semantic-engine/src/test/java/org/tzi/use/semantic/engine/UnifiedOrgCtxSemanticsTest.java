package org.tzi.use.semantic.engine;

import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.sys.MSystem;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedOrgCtxSemanticsTest {
    private Use75ModelBinding model(){return new Use75CslModelBackend().compile(new CslFrontend().compile("""
        csl v4.0 Unified {
          orgContext Team { role worker; entity Document; }
          orgContext Person { attribute name : String; }
          orgContext Company { role supplier; }
        }
        ""","unified.csl").requireModel(),M0ExtensionSpec.unifiedContexts());}
    @Test void oneSubjectCanBeMemberAndPlayerIndependentlyAtMultipleOrganizationalLevels()throws Exception {
        var binding=model();assertNull(binding.model().getClass("AtomicAgent"));
        var api=UseSystemApi.create(new MSystem(binding.model()),false);
        api.createObject("Person","bob");api.createObject("Team","team");api.createObject("Company","company");
        api.createObject("Company","market");api.createObject("worker","job");api.createObject("supplier","contract");
        api.createLink("CslMembership","bob","company");
        api.createLink("Plays","bob","job");api.createLink("InContext","team","job");
        api.createLink("CslMembership","team","company");
        api.createLink("Plays","team","contract");api.createLink("InContext","market","contract");
        assertEquals(BooleanValue.TRUE,api.evaluate("bob.memberOf = Set{company} and bob.roleContexts = Set{team}"));
        assertEquals(BooleanValue.TRUE,api.evaluate("company.members = Set{bob,team} and team.rolePlayers = Set{bob}"));
        assertEquals(BooleanValue.TRUE,api.evaluate("team.memberOf = Set{company} and team.roleContexts = Set{market}"));
        api.createLink("CslMembership","bob","team");
        assertEquals(BooleanValue.TRUE,api.evaluate("team.participants->size() = 1 and bob.memberOf->size() = 2"));
        api.deleteObject("job");
        assertEquals(BooleanValue.TRUE,api.evaluate("team.rolePlayers->isEmpty() and team.members = Set{bob}"));
    }
    @Test void anEntityCannotBeAPlayerOrAnOrganizationalMemberAndKernelNamesAreProtected()throws Exception {
        var binding=model();var api=UseSystemApi.create(new MSystem(binding.model()),false);
        api.createObject("Team","team");api.createObject("Document","document");api.createObject("worker","job");
        assertThrows(UseApiException.class,()->api.createLink("Plays","document","job"));
        assertThrows(UseApiException.class,()->api.createLink("CslMembership","document","team"));
        var clash=new CslFrontend().compile("csl v4.0 Clash { orgContext Team { attribute members : String; } }","clash.csl").requireModel();
        assertThrows(IllegalArgumentException.class,()->new Use75CslModelBackend().compile(clash,M0ExtensionSpec.unifiedContexts()));
        assertNotNull(new Use75CslModelBackend().compile(clash,M0ExtensionSpec.observedPlayers()).model().getClass("AtomicAgent"));
    }
}
