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
    public static final String FORMATTED_RECIPE="JCM_FORMAT_AND_OPTIONAL_BRIDGE/1.0";
    private static final Pattern BOOTSTRAP=Pattern.compile("    platform: org\\.jacamo\\.bridge\\.adapter\\.JaCaMoBridgePlatform\\(\"port=([0-9]{1,5})\", \"secretFile=([^\"\\r\\n\\\\]+)\", \"jcmFile=([^\"\\r\\n\\\\]+)\", \"distributionSha256=([0-9a-f]{64})\"\\)\\r?\\n");
    private BridgeJcmDerivation(){ }
    public static byte[] canonical(byte[] derived,String canonicalHash,Path selected) {
        return canonical(derived,canonicalHash,selected,"");
    }
    public static byte[] canonical(byte[] derived,String canonicalHash,Path selected,String layoutHash) {
        byte[] withoutBootstrap=removeBootstrap(derived,selected);
        if(hash(withoutBootstrap).equals(canonicalHash))return withoutBootstrap;
        if(!layoutHash.isBlank()&&lineLayoutDigest(withoutBootstrap).equals(layoutHash))return withoutBootstrap;
        throw invalid("SOURCE_CHECKSUM_MISMATCH");
    }
    /** JCM_LINE_LAYOUT/1.0: ignore empty lines and non-literal line terminators.
     * Nonempty line contents, comments and quoted literal bytes stay exact. */
    public static String lineLayoutDigest(byte[] bytes) {
        String text=decode(bytes);var normalized=new StringBuilder();char quote=0;
        boolean block=false,escape=false;
        for(int start=0;start<text.length();) {
            int end=start;while(end<text.length()&&text.charAt(end)!='\r'&&text.charAt(end)!='\n')end++;
            int next=end;if(next<text.length()&&text.charAt(next++)=='\r'&&next<text.length()&&text.charAt(next)=='\n')next++;
            String line=text.substring(start,end);boolean literalLine=quote!=0;
            for(int i=0;i<line.length();i++) {
                char c=line.charAt(i),following=i+1<line.length()?line.charAt(i+1):0;
                if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
                if(block){if(c=='*'&&following=='/'){block=false;i++;}continue;}
                if(c=='/'&&following=='/')break;
                if(c=='/'&&following=='*'){block=true;i++;continue;}
                if(c=='\''||c=='"')quote=c;
            }
            if(literalLine||quote!=0||!line.isBlank()) {
                normalized.append(line);
                normalized.append(quote!=0?text.substring(end,next):"\n");
            }
            start=next;
        }
        if(quote!=0)throw invalid("UNTERMINATED_LITERAL");
        return hash(normalized.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static String recipe(byte[] derived,byte[] canonical) {
        byte[] stripped=removeBootstrap(derived,null);
        return !Arrays.equals(stripped,derived)&&Arrays.equals(stripped,canonical)?RECIPE:FORMATTED_RECIPE;
    }
    private static String hash(byte[] bytes){return TraceComplement.Asset.of("check","bytes",bytes).sha256();}
    private static String decode(byte[] bytes) {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException error){throw invalid("UTF8_REQUIRED");}
    }
    static byte[] removeBootstrap(byte[] derived,Path selected) {
        try {
            String text=decode(derived);
            var match=BOOTSTRAP.matcher(text);
            if(!match.find())return derived;
            int start=match.start(),end=match.end(),port=Integer.parseInt(match.group(1));
            Path secret=Path.of(match.group(2)),declared=Path.of(match.group(3));
            if(port<1||port>65535||!secret.isAbsolute()||!declared.isAbsolute()
                    ||selected!=null&&!declared.toRealPath().equals(selected.toRealPath()))throw invalid("BOOTSTRAP_PARAMETER_OR_PATH");
            if(end!=text.lastIndexOf('}')||match.find())throw invalid("EXACTLY_ONE_FINAL_INSERTION_REQUIRED");
            return (text.substring(0,start)+text.substring(end)).getBytes(StandardCharsets.UTF_8);
        }catch(IOException error){throw new IllegalArgumentException("XML_JCM_DERIVATION_UNREADABLE",error);}
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("XML_JCM_DERIVATION_"+reason);}
}
