package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;

/** Additional authority for a previously validated, pinned MOISE public-state cut.
 * A timestamp denotes its CArtAgO publication callback, never internal truth change.
 * Creation establishes state only; it does not supply a transition timestamp. */
public final class MoisePublicConfirmation {
    public static final String VERSION="MOISE_PUBLIC_CONFIRMATION/1.0";
    private MoisePublicConfirmation(){ }
    public static boolean clockQualified(Object raw) {
        return raw instanceof Map<?,?> proof&&proof.keySet().equals(Set.of("version","workspaceClassSha256","status","unit","authority"))
                &&"CARTAGO_PUBLICATION_CLOCK/1.0".equals(proof.get("version"))&&"COMPLETE".equals(proof.get("status"))
                &&"0578556e6977dd23d04de4b5f55a293be9ba7da0fa14d16254ae804b10af4798".equals(proof.get("workspaceClassSha256"))
                &&"EPOCH_MILLISECONDS".equals(proof.get("unit"))&&"WORKSPACE_NEW_PERCEPT_LOGGER_SYSTEM_CURRENT_TIME_MILLIS".equals(proof.get("authority"));
    }
    public static boolean complete(boolean continuous,List<Map<String,Object>> transitions) {
        if(!continuous||transitions.isEmpty())return false;
        boolean baseline=false;long previous=0;
        for(var transition:transitions) {
            if("CREATION_BASELINE".equals(transition.get("kind"))) {
                if(!"ARTIFACT_CREATED".equals(transition.get("origin"))||previous>0)return false;
                baseline=true;continue;
            }
            if(!baseline||!"STATE_CHANGE".equals(transition.get("kind"))||!"PROPERTY_PUBLICATION".equals(transition.get("origin")))return false;
            if(!(transition.get("publicationEpochMillis") instanceof String text))return false;
            try {long time=Long.parseLong(text);if(time<=0||time<previous)return false;previous=time;}
            catch(NumberFormatException missing){return false;}
        }
        return baseline;
    }
}
