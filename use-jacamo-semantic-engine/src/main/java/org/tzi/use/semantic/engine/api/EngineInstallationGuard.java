package org.tzi.use.semantic.engine.api;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Validates deployment ownership before original parsers/backend are used.
 * No plugin/GUI/framework dependency, runtime state, transport or global cache. */
public final class EngineInstallationGuard {
    private static final List<String> CLASSES=List.of(
            "org.vnu.sme.goal.dsl.csl.parser.CslCompiler","org.vnu.sme.goal.dsl.csl.mm.CslModel",
            "org.vnu.sme.goal.dsl.istar.parser.IStarCompiler","org.vnu.sme.goal.dsl.istar.mm.GoalModel",
            "org.vnu.sme.goal.dsl.istar.mm.GoalModelValidator","org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy",
            "org.tzi.use.semantic.engine.api.EngineSemanticsProfile","org.tzi.use.semantic.engine.runtime.RuntimeModelAdmission",
            "org.vnu.sme.goal.verify.conformance.semantics.GoalMarking",
            "org.vnu.sme.goal.verify.conformance.semantics.TaskMarking",
            "org.vnu.sme.goal.verify.conformance.semantics.IStarMarking",
            "org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation",
            "org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator");
    private static final Map<String,String> GRAMMARS=Map.of(
            "grammars/CSL.g4","5bca5c44933f2619eeac837279e09a995a293383ebe3795d058d7e5319d3d224",
            "grammars/IStar.g4","177eabd15e56c644c98bfa4e7a6f2b2e0e18b67bdfe68ac01d872a13ea72d503");
    private EngineInstallationGuard() { }
    public static Map<String,String> verify(){return verify(EngineInstallationGuard.class.getClassLoader());}
    /** Also supports an explicitly selected deployment resolver. */
    public static Map<String,String> verify(ClassLoader resolver) {
        Objects.requireNonNull(resolver);var evidence=new TreeMap<String,String>();
        try {
            Class<?> options=Class.forName("org.tzi.use.config.Options",false,resolver);
            String host=(String)options.getField("RELEASE_VERSION").get(null);
            if(!"7.5.0".equals(host))throw failure("HOST_VERSION_UNSUPPORTED",host);
            Class<?> runtime=Class.forName("org.antlr.v4.runtime.RuntimeMetaData",false,resolver);
            String antlr=(String)runtime.getField("VERSION").get(null);
            if(!"4.9.3".equals(antlr))throw failure("ANTLR_VERSION_UNSUPPORTED",antlr);
            unique(resolver,"org/antlr/v4/runtime/RuntimeMetaData.class");
            URL origin=origin(EngineInstallationGuard.class);evidence.put("engineOrigin",origin.toExternalForm());
            evidence.put("hostVersion",host);evidence.put("antlrVersion",antlr);
            // USE's global loader can be replaced after another plugin is registered.
            // Inspect installed siblings too; an older resolver cannot see a later URL.
            Path own=Path.of(origin.toURI()).toRealPath();
            if(Files.isRegularFile(own)&&own.getFileName().toString().endsWith(".jar")
                    &&own.getParent().getFileName().toString().equalsIgnoreCase("plugins")) {
                try(var siblings=Files.list(own.getParent())) {
                    for(Path sibling:siblings.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                        if(Files.isSameFile(own,sibling))continue;
                        try(var jar=new ZipFile(sibling.toFile())) {
                            if(jar.stream().anyMatch(e->e.getName().endsWith(".class")
                                    &&(e.getName().startsWith("org/vnu/sme/goal/")||e.getName().startsWith("org/tzi/use/semantic/engine/"))))
                                throw failure("DUPLICATE_INSTALLED_ENGINE",sibling.getFileName().toString());
                        }
                    }
                }
            }
            for(String name:CLASSES) {
                Class<?> type=Class.forName(name,false,resolver);
                if(!origin.equals(origin(type))||type.getClassLoader()!=EngineInstallationGuard.class.getClassLoader())
                    throw failure("CLASS_ORIGIN_MISMATCH",name+":"+origin(type));
                String resource=name.replace('.','/')+".class";URL url=unique(resolver,resource);
                evidence.put(name,hash(read(url)));
            }
            for(var grammar:GRAMMARS.entrySet()) {
                String actual=hash(read(unique(resolver,grammar.getKey())));
                if(!grammar.getValue().equals(actual))throw failure("GRAMMAR_HASH_MISMATCH",grammar.getKey());
                evidence.put(grammar.getKey(),actual);
            }
            byte[] manifest=read(unique(resolver,"META-INF/use4goal-provenance.json"));
            if(!new String(manifest,StandardCharsets.UTF_8).contains("6f3e73801239fd8bfb94ca8d04ba531b903c0ac9"))
                throw failure("PROVENANCE_UNSUPPORTED","upstream commit");
            evidence.put("provenanceHash",hash(manifest));
            read(unique(resolver,"META-INF/licenses/use4Goal-GPL-3.0.txt"));
            return Collections.unmodifiableMap(evidence);
        }catch(IllegalStateException error){throw error;}
        catch(ClassNotFoundException|LinkageError error){throw failure("DEPENDENCY_MISSING",error.toString());}
        catch(Exception error){throw failure("DEPLOYMENT_CHECK_FAILED",error.toString());}
    }
    private static URL origin(Class<?> type) {
        var domain=type.getProtectionDomain();var source=domain==null?null:domain.getCodeSource();
        if(source==null||source.getLocation()==null)throw failure("CLASS_ORIGIN_UNAVAILABLE",type.getName());
        return source.getLocation();
    }
    private static URL unique(ClassLoader resolver,String path) throws IOException {
        var urls=Collections.list(resolver.getResources(path));var distinct=new LinkedHashMap<String,URL>();
        for(URL url:urls)distinct.put(url.toExternalForm(),url);
        if(distinct.size()!=1)throw failure(distinct.isEmpty()?"RESOURCE_MISSING":"DUPLICATE_RESOURCE",path);
        return distinct.values().iterator().next();
    }
    private static byte[] read(URL url) throws IOException {
        var connection=url.openConnection();connection.setUseCaches(false);
        try(var input=connection.getInputStream()) {
            byte[] bytes=input.readNBytes(4*1024*1024+1);
            if(bytes.length>4*1024*1024)throw failure("RESOURCE_TOO_LARGE",url.toExternalForm());
            return bytes;
        }
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static IllegalStateException failure(String code,String detail){return new IllegalStateException("ENGINE_INSTALLATION_"+code+":"+detail);}
}
