package org.tzi.use.plugins.jacamo.evidence;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** Exact frozen JCM from read-only Git; copies dependencies into USE target, preserving user's reference checkout. */
public final class CanonicalReferenceFixture {
    private static Path auction;
    private CanonicalReferenceFixture(){}
    public static synchronized Path auction()throws Exception {
        if(auction!=null && Files.isRegularFile(auction))return auction;
        Path repo=Path.of("..","..","jacamo").toAbsolutePath().normalize();String relative="src/test/resources/auction-reference/auction.jcm";String expected="c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb";
        Process read=new ProcessBuilder("git","show","HEAD:"+relative).directory(repo.toFile()).start();byte[] bytes=read.getInputStream().readAllBytes();String error=new String(read.getErrorStream().readAllBytes(),StandardCharsets.UTF_8);if(read.waitFor()!=0)throw new IllegalStateException("CANONICAL_REFERENCE_UNAVAILABLE:"+error);
        if(!ExternalOclConstraintService.sha256(bytes).equals(expected)){byte[] crlf=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace("\n","\r\n").getBytes(StandardCharsets.UTF_8);if(!ExternalOclConstraintService.sha256(crlf).equals(expected))throw new IllegalStateException("CANONICAL_REFERENCE_CHECKSUM_MISMATCH");bytes=crlf;}
        Files.createDirectories(Path.of("target"));Path copy=Files.createTempDirectory(Path.of("target"),"canonical-auction-").toAbsolutePath();Path original=repo.resolve("src/test/resources/auction-reference/src");try(var paths=Files.walk(original)){for(var path:paths.toList()){if(Files.isSymbolicLink(path))throw new IllegalArgumentException("CANONICAL_REFERENCE_SYMLINK_UNSUPPORTED");Path target=copy.resolve("src").resolve(original.relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);}}
        auction=copy.resolve("auction.jcm");Files.write(auction,bytes);return auction;
    }
}
