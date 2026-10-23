package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.jar.*;
import jason.runtime.SourcePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClosedSourcePathResolverTest {
    @TempDir Path temp;
    @Test void closesFileAndJarProbesAndPreservesDeclaredSearchPrecedence()throws Exception {
        Path first=Files.createDirectory(temp.resolve("first")),second=Files.createDirectory(temp.resolve("second"));
        Path a=Files.writeString(first.resolve("worker.asl"),"+!one <- true."),b=Files.writeString(second.resolve("worker.asl"),"+!two <- true.");
        var paths=new SourcePath();paths.addPath(first.toUri().toString());paths.addPath(second.toUri().toString());
        assertEquals(first.toUri()+"worker.asl",ClosedSourcePathResolver.resolve(paths,"worker.asl"));
        assertEquals(b.toUri().toString(),ClosedSourcePathResolver.resolve(paths,b.toUri().toString()));
        assertEquals("missing.asl",ClosedSourcePathResolver.resolve(paths,"missing.asl"));
        Files.delete(a);Files.delete(b); // Windows refuses deletion while a successful URL probe is open.
        Path jar=temp.resolve("fixture.jar");
        try(var output=new JarOutputStream(Files.newOutputStream(jar))){output.putNextEntry(new JarEntry("inc.asl"));output.write("+!ok <- true.".getBytes(java.nio.charset.StandardCharsets.UTF_8));output.closeEntry();}
        String uri="jar:"+jar.toUri()+"!/inc.asl";
        assertEquals(uri,ClosedSourcePathResolver.resolve(paths,uri));Files.delete(jar);
    }
}
