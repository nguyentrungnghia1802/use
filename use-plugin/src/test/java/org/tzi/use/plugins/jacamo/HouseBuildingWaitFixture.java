package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** User-approved sparse test fixture. Never modifies the reference tree. */
final class HouseBuildingWaitFixture {
    private static final Path RESOURCES=Path.of("src/test/resources/house-building-condition-wait-v1");
    static final String PROFILE="HOUSE_BUILDING_CONDITION_WAIT_V1";
    private HouseBuildingWaitFixture() { }
    static Map<String,Object> apply(Path stage,Path evidence) {
        try {
            var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(RESOURCES.resolve("manifest.json"))));
            if(!PROFILE.equals(manifest.get("profile")))throw new IllegalStateException("FIXTURE_PROFILE");
            var root=stage.toRealPath();var target=root.resolve("src/agt/giacomo.asl").toRealPath();
            if(!target.startsWith(root))throw new IllegalStateException("FIXTURE_OUTSIDE_STAGE");
            var original=Files.readAllBytes(target);var replacement=Files.readAllBytes(RESOURCES.resolve("giacomo.asl"));
            if(!hash(original).equals(manifest.get("originalSha256"))||!hash(replacement).equals(manifest.get("fixtureSha256")))throw new IllegalStateException("FIXTURE_SOURCE_HASH");
            var text=new String(original,java.nio.charset.StandardCharsets.UTF_8);String from=manifest.get("original").toString(),to=manifest.get("replacement").toString();
            if(text.indexOf(from)<0||text.indexOf(from)!=text.lastIndexOf(from)||!Arrays.equals(text.replace(from,to).getBytes(java.nio.charset.StandardCharsets.UTF_8),replacement))throw new IllegalStateException("FIXTURE_PATCH_SCOPE");
            Files.write(target,replacement);
            var proof=new TreeMap<String,Object>(manifest);proof.put("manifestSha256",hash(Files.readAllBytes(RESOURCES.resolve("manifest.json"))));
            proof.put("stagedPath",target.toString());proof.put("sourceUnchanged",true);
            Files.write(evidence.resolve("fixture-manifest.json"),CanonicalJson.encode(proof));return Map.copyOf(proof);
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static String hash(byte[] bytes){return ExternalOclConstraintService.sha256(bytes);}
}
