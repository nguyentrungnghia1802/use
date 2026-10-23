package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;

/** Proof of the launcher's one permitted insertion, not a relaxed source checksum policy. */
public final class AuctionJcmDerivation {
    private static final Pattern BOOTSTRAP=Pattern.compile("    platform: org\\.jacamo\\.bridge\\.adapter\\.JaCaMoBridgePlatform\\(\"port=([0-9]{1,5})\", \"secretFile=([^\"\\r\\n\\\\]+)\", \"jcmFile=([^\"\\r\\n\\\\]+)\", \"distributionSha256=([0-9a-f]{64})\"\\)\\r?\\n");
    private final String bundleHash,originalHash,derivedHash,recipe;
    private final Map<String,String> parameters;
    private final TraceComplement.Asset derived;
    private AuctionJcmDerivation(AuctionCaseBundle bundle,byte[] bytes,String recipe,Map<String,String> parameters) {
        this.bundleHash=bundle.hash();this.originalHash=bundle.require("src-jcm").sha256();this.derivedHash=ExternalOclConstraintService.sha256(bytes);
        this.recipe=recipe;this.parameters=Map.copyOf(parameters);this.derived=bundle.derive("src-jcm",recipe,bytes).derived();
    }
    public static AuctionJcmDerivation original(AuctionCaseBundle bundle,ModelSnapshot source) {
        var receipt=new AuctionJcmDerivation(bundle,bundle.require("src-jcm").bytes(),"ORIGINAL_FROZEN_JCM/1.0",Map.of());
        receipt.requireOwnership(bundle,source);return receipt;
    }
    public static AuctionJcmDerivation read(AuctionCaseBundle bundle,ModelSnapshot source,Path selectedJcm) {
        try {
            Path file=selectedJcm.toRealPath();
            if(!Files.isRegularFile(file)||Files.size(file)>1024*1024)throw invalid("FILE_OR_CAPACITY");
            return fromBytes(bundle,source,Files.readAllBytes(file),file);
        }catch(IOException error){throw new IllegalArgumentException("AUCTION_JCM_DERIVATION_UNREADABLE",error);}
    }
    private static AuctionJcmDerivation fromBytes(AuctionCaseBundle bundle,ModelSnapshot source,byte[] actual,Path liveFile)throws IOException {
            if(actual==null||actual.length>1024*1024)throw invalid("FILE_OR_CAPACITY");
            byte[] original=bundle.require("src-jcm").bytes();
            if(Arrays.equals(actual,original))return original(bundle,source);
            String baseline=utf8(original),text=utf8(actual);int closing=baseline.lastIndexOf('}');
            String prefix=baseline.substring(0,closing),suffix=baseline.substring(closing);
            if(!text.startsWith(prefix)||!text.endsWith(suffix)||text.length()<baseline.length())throw invalid("BUSINESS_SOURCE_CHANGED");
            String insertion=text.substring(prefix.length(),text.length()-suffix.length());var matcher=BOOTSTRAP.matcher(insertion);
            if(!matcher.matches())throw invalid("BOOTSTRAP_INSERTION_UNSUPPORTED");
            int port=Integer.parseInt(matcher.group(1));if(port<1||port>65535)throw invalid("PORT");
            Path declared=Path.of(matcher.group(3));
            if(!declared.isAbsolute()||liveFile!=null&&!declared.toRealPath().equals(liveFile)||!Path.of(matcher.group(2)).isAbsolute())throw invalid("BOOTSTRAP_PATH_MISMATCH");
            var receipt=new AuctionJcmDerivation(bundle,actual,"BRIDGE_PLATFORM_INSERTION/1.0",Map.of("port",matcher.group(1),"secretFile",matcher.group(2),"jcmFile",matcher.group(3),"distributionSha256",matcher.group(4)));
            receipt.requireOwnership(bundle,source);return receipt;
    }
    public static AuctionJcmDerivation fromFrozen(AuctionCaseBundle bundle,ModelSnapshot source,TraceComplement.Asset asset,Map<String,Object> evidence) {
        try{var restored=fromBytes(bundle,source,asset.bytes(),null);
            if(!restored.derived.equals(asset)||!org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(restored.evidence()).equals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(evidence)))throw invalid("FROZEN_RECIPE_MISMATCH");return restored;}
        catch(IOException error){throw new IllegalArgumentException("AUCTION_JCM_DERIVATION_FROZEN_UNREADABLE",error);}
    }
    public void requireOwnership(AuctionCaseBundle bundle,ModelSnapshot source) {
        if(!bundle.hash().equals(bundleHash)||!bundle.require("src-jcm").sha256().equals(originalHash)||!source.semanticContract().project().sourceDigest().equals(derivedHash))throw invalid("SOURCE_OR_BUNDLE_MISMATCH");
    }
    public String derivedHash(){return derivedHash;}
    public TraceComplement.Asset derived(){return derived;}
    public Map<String,Object> evidence(){return Map.of("bundleHash",bundleHash,"originalHash",originalHash,"derivedHash",derivedHash,"recipe",recipe,"parameters",parameters);}
    private static String utf8(byte[] bytes) {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException error){throw invalid("UTF8_REQUIRED");}
    }
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("AUCTION_JCM_DERIVATION_"+reason);}
}
