package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import jason.asSyntax.*;
import jason.asSemantics.Unifier;
import npl.*;
import org.junit.jupiter.api.Test;

class NplActivationCrosswalkTest {
    static NormInstance instance(String modality) {
        var literal=ASSyntax.createLiteral(modality,ASSyntax.createAtom("alice"),Literal.LTrue,ASSyntax.createAtom("done"),new TimeTerm(new Date(System.currentTimeMillis()+10000)));var norm=new Norm("n",literal,Literal.LTrue);var value=new NormInstance(literal,new Unifier(),norm);value.setActive();return value;
    }
    @Test void copiedTerminalInstancesMatchPublicCreatedCrosswalkAndDuplicateCreationIsAmbiguous() {
        var value=instance("obligation");var registry=new NplActivationCrosswalk();String uuid=UUID.randomUUID().toString();var created=registry.observe(uuid,value.copy(),"LISTENER_CREATED","ACTIVE");assertEquals("BOUND_PUBLIC_CREATED_ANNOTATION",created.get("activationIdentityStatus"));
        var terminal=value.copy();terminal.setFulfilled();var fulfilled=registry.observe(uuid,terminal,"LISTENER_TERMINAL","FULFILLED");assertEquals(created.get("activationKey"),fulfilled.get("activationKey"));assertEquals("CREATED_EVENT_OBSERVED",fulfilled.get("history"));assertEquals("ABSOLUTE_NPL_TIME",fulfilled.get("deadlineKind"));
        var ambiguous=registry.observe(uuid,value.copy(),"LISTENER_CREATED","ACTIVE");assertEquals("AMBIGUOUS_NORM_INSTANCE",ambiguous.get("activationIdentityStatus"));assertNotEquals(created.get("activationKey"),new NplActivationCrosswalk().observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE").get("activationKey"));
    }
    @Test void permissionIsAnObservedModalityAndMissingCreationOrStateDeadlineIsExplicit() {
        var value=instance("permission");var registry=new NplActivationCrosswalk();var observed=registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("PERMISSION",observed.get("modality"));assertEquals("ACTIVATION_HISTORY_UNPROVEN",observed.get("history"));
        value.setTerm(3,ASSyntax.createAtom("finished"));observed=registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("STATE",observed.get("deadlineKind"));assertEquals("",observed.get("deadline"));value.delAnnot(value.getAnnot("created"));assertEquals("ACTIVATION_ORIGIN_UNAVAILABLE",registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE").get("activationIdentityStatus"));
    }
}
