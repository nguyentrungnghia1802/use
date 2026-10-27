package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;

/** Verifies the launcher's exact insertion. Canonical domain bytes stay pinned by XML. */
public final class BridgeJcmDerivation {
    public static final String RECIPE="BRIDGE_PLATFORM_INSERTION/1.0";
    private static final Pattern BOOTSTRAP=Pattern.compile("    platform: org\\.jacamo\\.bridge\\.adapter\\.JaCaMoBridgePlatform\\(\"port=([0-9]{1,5})\", \"secretFile=([^\"\\r\\n\\\\]+)\", \"jcmFile=([^\"\\r\\n\\\\]+)\", \"distributionSha256=([0-9a-f]{64})\"\\)\\r?\\n");
    private BridgeJcmDerivation(){ }
    public static byte[] canonical(byte[] derived,String canonicalHash,Path selected) {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(derived)).toString();
            var match=BOOTSTRAP.matcher(text);
            if(!match.find())throw invalid("BOOTSTRAP_REQUIRED");
            int start=match.start(),end=match.end(),port=Integer.parseInt(match.group(1));
            Path secret=Path.of(match.group(2)),declared=Path.of(match.group(3));
            if(port<1||port>65535||!secret.isAbsolute()||!declared.isAbsolute()
                    ||selected!=null&&!declared.toRealPath().equals(selected.toRealPath()))throw invalid("BOOTSTRAP_PARAMETER_OR_PATH");
            if(end!=text.lastIndexOf('}')||match.find())throw invalid("EXACTLY_ONE_FINAL_INSERTION_REQUIRED");
            byte[] canonical=(text.substring(0,start)+text.substring(end)).getBytes(StandardCharsets.UTF_8);
            if(!TraceComplement.Asset.of("check","bytes",canonical).sha256().equals(canonicalHash))throw invalid("CANONICAL_CHECKSUM_MISMATCH");
            return canonical;
        }catch(IOException error){throw new IllegalArgumentException("XML_JCM_DERIVATION_UNREADABLE",error);}
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("XML_JCM_DERIVATION_"+reason);}
}
