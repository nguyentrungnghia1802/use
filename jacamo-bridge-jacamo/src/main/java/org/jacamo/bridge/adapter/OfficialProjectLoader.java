package org.jacamo.bridge.adapter;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import jacamo.project.JaCaMoProject;
import jacamo.project.parser.JaCaMoProjectParser;
import jason.asSyntax.directives.DirectiveProcessor;
import jason.asSyntax.directives.Include;

/** Launcher-equivalent official parser entry point; imported projects are merged by JaCaMo itself. */
public final class OfficialProjectLoader {
    public JaCaMoProject load(Path jcm) throws Exception {
        // This is the same official configuration bootstrap used by the JaCaMo
        // launcher.  In particular it registers the $jacamo and $moise package
        // roots from the exact distribution jars on the runtime class path.
        jacamo.util.Config.get(true);
        Path exact = jcm.toAbsolutePath().normalize();
        try (Reader reader = Files.newBufferedReader(exact)) {
            var parser = new JaCaMoProjectParser(reader);
            JaCaMoProject project = parser.parse(exact.getParent().toString());
            project.setProjectFile(exact.toFile());
            project.setDirectory(exact.getParent().toString());
            project.getSourcePaths().setRoot(exact.getParent().toString());
            project.getOrgPaths().setRoot(exact.getParent().toString());
            project.getJavaSourcePaths().setRoot(exact.getParent().toString());
            project.addSourcePath(exact.getParent().toUri().toString());
            project.addSourcePath(exact.getParent().resolve("src/agt").toUri().toString());
            project.addSourcePath(exact.getParent().resolve("src/agt/inc").toUri().toString());
            project.addOrgSourcePath(exact.getParent().toUri().toString());
            project.addOrgSourcePath(exact.getParent().resolve("src/org").toUri().toString());
            project.addJavaSourcePath(exact.getParent().resolve("src").toUri().toString());
            project.setupDefault();
            project.registerDirectives();
            ((Include) DirectiveProcessor.getDirective("include")).setSourcePath(project.getSourcePaths());
            project.parserFinished();
            return project;
        }
    }
}
