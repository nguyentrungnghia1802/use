package org.tzi.use.plugins.jacamo.semantics.roundtrip;

import java.util.*;

/** Exact source ownership for offline Ecore definitions. Does not describe live role acceptance or Java effects. */
public record SupportedEditProfile(String version,List<Owner> owners) {
    public static final String VERSION="ECORE_STRUCTURAL_LENS_V1";
    public enum Kind{CSL_ROLE_DEFINITION,CSL_CONTEXT_DECLARATION,ISTAR_OPLAN_DESCRIPTOR,ARTIFACT_INTERFACE,PROPERTY_INTERFACE,OPERATION_INTERFACE}
    public record Owner(String targetId,String sourceFragment,Kind kind){public Owner{if(targetId==null || targetId.isBlank() || sourceFragment==null || sourceFragment.isBlank())throw new IllegalArgumentException("LENS_OWNERSHIP_REQUIRED");Objects.requireNonNull(kind);}}
    public SupportedEditProfile{if(!VERSION.equals(version))throw new IllegalArgumentException("LENS_PROFILE_UNSUPPORTED");owners=List.copyOf(owners);if(owners.isEmpty())throw new IllegalArgumentException("LENS_OWNERSHIP_EMPTY");}
}
