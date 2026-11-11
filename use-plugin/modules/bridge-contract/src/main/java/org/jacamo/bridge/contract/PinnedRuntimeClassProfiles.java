package org.jacamo.bridge.contract;

import java.util.Properties;

/** Reviewed private read layout hashes; generated patched profile stays exact. */
public final class PinnedRuntimeClassProfiles {
    private static final String ORIGINAL="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private static final Properties patched=new Properties();
    static {
        try(var stream=PinnedRuntimeClassProfiles.class.getResourceAsStream("/META-INF/checkpoint-class-profiles.properties")){
            if(stream!=null)patched.load(stream);
        }catch(java.io.IOException failed){throw new ExceptionInInitializerError(failed);}
    }
    public static boolean supportsCartagoArtifactHash(Object hash){return hash instanceof String value&&(ORIGINAL.equals(value)||value.equals(patched.getProperty("cartago.Artifact")));}
    public static boolean supportsPatchedWorkspaceHash(Object hash){return hash instanceof String value&&value.equals(patched.getProperty("cartago.Workspace"));}
    public static boolean supportsNplNormInstanceHash(Object hash){return hash instanceof String value&&("1a14085372cf52cc96b49f8835f6b5ac987763c0d3b240b3bf6424d86be38e43".equals(value)||value.equals(patched.getProperty("npl.NormInstance")));}
    /** Explicit source patch lineage: never trust a self-declared binary hash. */
    public static boolean matchesNplBinary(String requested,Object actual){return requested!=null&&(requested.equals(actual)||requested.equals("956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332")&&actual instanceof String value&&value.equals(patched.getProperty("npl.jar")));}
    private PinnedRuntimeClassProfiles(){ }
    /** Versioned NPL observer contract, including the reviewed patched lineage. */
    public static String nplObservationContractHash(){return "956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332";}
}
