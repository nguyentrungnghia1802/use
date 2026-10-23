package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionCaseBundle;

/** Separately authored E02 input variant; never modifies the pinned canonical case. */
final class AuctionControlledCase {
    static Path prepare(Path original,Path work,boolean lateRole)throws Exception {
        var source=AuctionCaseBundle.read(original);Path target=Files.createDirectory(work.resolve("case-variant"));
        for(var input:source.inputs().values()) {Path file=target.resolve(input.path());Files.createDirectories(file.getParent());Files.write(file,source.require(input.id()).bytes());}
        var changed=new TreeMap<String,String>();
        Path ocl=target.resolve(source.inputs().get("input-ocl").path());
        String originalOcl=Files.readString(ocl),variantOcl=once(originalOcl,"post Opened: self.running and self.task = service","post Opened: not self.running@pre and self.running and self.task = service");
        Files.writeString(ocl,variantOcl);changed.put("input-ocl",sha(Files.readAllBytes(ocl)));
        if(lateRole) {
            Path jcm=target.resolve(source.inputs().get("src-jcm").path());
            Files.writeString(jcm,once(Files.readString(jcm),"                     giacomo  participant","                     // E02: giacomo adopts participant later through actual GroupBoard API"));
            changed.put("src-jcm",sha(Files.readAllBytes(jcm)));
        }
        Path rules=target.resolve(source.inputs().get("case-rules").path());String xml=Files.readString(rules);
        for(var entry:changed.entrySet())xml=once(xml,source.require(entry.getKey()).sha256(),entry.getValue());
        xml=xml.replace("profile-auction","profile-auction-e02-"+(lateRole?"late-":"")+"native-pre-v1");
        if(lateRole) {
            xml=once(xml,"id=\"sel-players\" sourceRef=\"src-jcm\" syntax=\"regex\" expectedCount=\"5\"","id=\"sel-players\" sourceRef=\"src-jcm\" syntax=\"regex\" expectedCount=\"4\"");
            xml=once(xml,"Five configured players; actual membership must still be observed","Four configured players; giacomo adopts participant later under the explicit E02 fixture");
        }
        Files.writeString(rules,xml);changed.put("case-rules",sha(Files.readAllBytes(rules)));
        var manifest=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(source.manifest().bytes())));var assets=new ArrayList<Object>();
        for(Object raw:(List<?>)manifest.get("assets")) {
            var asset=new TreeMap<>(CanonicalJson.object(raw));String id=(String)asset.get("id");
            if(changed.containsKey(id)){asset.put("sha256",changed.get(id));asset.put("origin","E02 explicit test input variant of pinned Auction; original remains unchanged");asset.put("archiveSha256","");asset.put("archiveEntry","");}
            assets.add(asset);
        }
        manifest.put("assets",assets);Files.write(target.resolve(AuctionCaseBundle.MANIFEST),CanonicalJson.encode(manifest));
        AuctionCaseBundle.read(target);
        Files.write(work.resolve("control-profile.json"),CanonicalJson.encode(Map.of("profile","AUCTION_DETERMINISTIC_CONTROLS/1.0","canonicalManifestSha256",sha(source.manifest().bytes()),
                "variantManifestSha256",sha(Files.readAllBytes(target.resolve(AuctionCaseBundle.MANIFEST))),"changedAssets",changed,"lateRole",lateRole,
                "expectedDifference","test-authored start postcondition uses native @pre; optional giacomo role is enacted after managed Start, not configured at bootstrap","canonicalSourceUnchanged",source.hash().equals(AuctionCaseBundle.read(original).hash()))));
        return target;
    }
    private static String once(String source,String from,String to) {int index=source.indexOf(from);if(index<0||source.indexOf(from,index+from.length())>=0)throw new IllegalArgumentException("E02_EXPECTED_SOURCE_FRAGMENT_NOT_UNIQUE:"+from);return source.substring(0,index)+to+source.substring(index+from.length());}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
