package org.tzi.use.semantic.engine.oracle;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import javax.tools.*;

/** Compiles the pinned original sources, never the migrated originals/adapters. Test only. */
public final class PinnedOriginalOracle implements AutoCloseable {
    private final URLClassLoader loader;
    public PinnedOriginalOracle() throws Exception {
        Path root = Path.of("").toAbsolutePath(), output = root.resolve("target/oracle");
        Path archive = root.resolve("src/test/resources/oracle/pinned-original-core.zip");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
        if (!hash.equals("6eb6e573194dc1bfd83020e5787933eafa862c9f636cdd549e8546ea74b9e467"))
            throw new IllegalStateException("PINNED_ORIGINAL_ARCHIVE_TAMPERED");
        Path sources = output.resolve("original-source"), classes = output.resolve("original-classes");
        Files.createDirectories(classes);
        var javaSources = new ArrayList<Path>();
        try (var zip = new ZipFile(archive.toFile())) {
            for (var entry : zip.stream().filter(e -> e.getName().endsWith(".java") || e.getName().endsWith(".g4")).toList()) {
                Path target = sources.resolve(entry.getName()).normalize();
                if (!target.startsWith(sources)) throw new IllegalStateException("ORACLE_ARCHIVE_PATH_ESCAPE");
                Files.createDirectories(target.getParent());
                Files.write(target, zip.getInputStream(entry).readAllBytes());
                if (entry.getName().endsWith(".java")) javaSources.add(target);
            }
        }
        Path generatedPath = output.resolve("original-generated");
        Files.createDirectories(generatedPath);
        for (String grammar : List.of("CSL", "IStar")) {
            var tool = new org.antlr.v4.Tool(new String[]{"-visitor", "-listener", "-o", generatedPath.toString(),
                    sources.resolve("goal/src/main/resources/grammars/" + grammar + ".g4").toString()});
            tool.processGrammarsOnCommandLine();
            if (tool.getNumErrors() != 0) throw new AssertionError("ORIGINAL_GRAMMAR_GENERATION_FAILED:" + grammar);
        }
        try (var generated = Files.walk(generatedPath)) {
            javaSources.addAll(generated.filter(p -> p.toString().endsWith(".java")).toList());
        }
        if (javaSources.size() != 135) throw new IllegalStateException("ORIGINAL_CLOSURE_COUNT:" + javaSources.size());
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK21_COMPILER_REQUIRED");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(javaSources);
            var options = List.of("--release", "21", "-encoding", "UTF-8", "-cp", System.getProperty("java.class.path"), "-d", classes.toString());
            if (!compiler.getTask(null, manager, diagnostics, options, null, units).call())
                throw new AssertionError("ORIGINAL_SOURCE_COMPILE_FAILED:" + diagnostics.getDiagnostics());
        }
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("org.vnu.sme.goal.")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name); // Never fall back to a migrated core class.
                if (resolve) resolveClass(type);
                return type;
            }
        };
        Class<?> original = loader.loadClass("org.vnu.sme.goal.dsl.csl.parser.CslCompiler");
        if (original == org.vnu.sme.goal.dsl.csl.parser.CslCompiler.class)
            throw new AssertionError("ORACLE_CLASSLOADER_NOT_ISOLATED");
    }
    public Object csl(String text, String name) throws Exception {
        return loader.loadClass("org.vnu.sme.goal.dsl.csl.parser.CslCompiler")
                .getMethod("compile", String.class, String.class).invoke(null, text, name);
    }
    public Object istar(String text) throws Exception {
        return loader.loadClass("org.vnu.sme.goal.dsl.istar.parser.IStarCompiler")
                .getMethod("compile", String.class).invoke(null, text);
    }
    public Object istarAst(String text) throws Exception {
        var lexer=(org.antlr.v4.runtime.Lexer)loader.loadClass("org.vnu.sme.goal.dsl.istar.parser.IStarLexer")
                .getConstructor(org.antlr.v4.runtime.CharStream.class).newInstance(org.antlr.v4.runtime.CharStreams.fromString(text));
        var parser=loader.loadClass("org.vnu.sme.goal.dsl.istar.parser.IStarParser")
                .getConstructor(org.antlr.v4.runtime.TokenStream.class).newInstance(new org.antlr.v4.runtime.CommonTokenStream(lexer));
        var tree=parser.getClass().getMethod("model").invoke(parser);
        return loader.loadClass("org.vnu.sme.goal.dsl.istar.parser.IStarBuildingVisitor").getMethod("build",tree.getClass()).invoke(null,tree);
    }
    public List<String> goalTrace(String type,List<boolean[]> inputs) throws Exception {
        Class<?> goalType=loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalType");
        @SuppressWarnings({"unchecked","rawtypes"}) Object value=Enum.valueOf((Class)goalType,type);
        Class<?> marking=loader.loadClass("org.vnu.sme.goal.verify.conformance.semantics.GoalMarking");
        Object current=marking.getMethod("initial",goalType).invoke(null,value);var result=new ArrayList<String>();
        for(boolean[] input:inputs){current=marking.getMethod("update",boolean.class,boolean.class).invoke(current,input[0],input[1]);result.add(normalize(current));}
        return result;
    }
    public List<String> taskTrace(List<boolean[]> inputs) throws Exception {
        Class<?> marking=loader.loadClass("org.vnu.sme.goal.verify.conformance.semantics.TaskMarking");
        Object current=marking.getMethod("initial").invoke(null);var result=new ArrayList<String>();
        for(boolean[] input:inputs){current=marking.getMethod("update",boolean.class,boolean.class,boolean.class).invoke(current,input[0],input[1],input[2]);result.add(normalize(current));}
        return result;
    }
    public Object scenario(String source,Map<String,String> cast,Map<String,String> contexts,List<String[]> assignments) throws Exception {
        Object model=property(istar(source),"model");
        Class<?> instanceType=loader.loadClass("org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance");var instances=new ArrayList<Object>();
        for(var entry:cast.entrySet())instances.add(instanceType.getConstructor(String.class,String.class).newInstance(entry.getKey(),entry.getValue()));
        Class<?> assign=loader.loadClass("org.vnu.sme.goal.dsl.iscn.mm.ScenarioStmt$Assign");var statements=new ArrayList<Object>();
        for(String[] assignment:assignments)statements.add(assign.getConstructor(String.class,String.class,String.class).newInstance((Object[])assignment));
        Class<?> scenarioType=loader.loadClass("org.vnu.sme.goal.dsl.iscn.mm.IStarScenarioModel");
        Object scenario=scenarioType.getConstructor(String.class,String.class,List.class,List.class).newInstance("live","<in-memory>",instances,statements);
        Class<?> matcher=loader.loadClass("org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator$InstanceContextMatcher");
        Object proxy=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{matcher},(p,m,args)-> {
            if(m.getName().equals("sharesContext"))return contexts.containsKey(args[1])&&Objects.equals(contexts.get(args[1]),contexts.get(args[3]));
            if(m.getName().equals("toString"))return "original-test-context-matcher";
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            if(m.getName().equals("equals"))return p==args[0];throw new UnsupportedOperationException(m.getName());
        });
        return loader.loadClass("org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator")
                .getMethod("evaluate",model.getClass(),scenarioType,matcher).invoke(null,model,scenario,proxy);
    }
    public static Object property(Object value, String method) throws Exception {
        return value.getClass().getMethod(method).invoke(value);
    }
    /** The original diagnostic renders Set<GoalType>; Set iteration depends on enum
     * identities in each loader. Normalize only that explicitly unordered suffix.
     * All diagnostic wording, IDs, error-list order and model fields remain exact.
     */
    public static List<String> canonicalIStarDiagnostics(List<?> errors) {
        var output=new ArrayList<String>();String marker="allowed child goal types are [";
        for(Object raw:errors) {
            String error=raw.toString();int index=error.lastIndexOf(marker);
            if(index>=0&&error.endsWith("]")) {
                String[] types=error.substring(index+marker.length(),error.length()-1).split(", ");
                for(String type:types)if(!Set.of("ACHIEVE","MAINTAIN","SUSTAIN","NONE").contains(type))
                    throw new AssertionError("UNKNOWN_DIAGNOSTIC_SET_MEMBER:"+type);
                Arrays.sort(types);error=error.substring(0,index)+marker+String.join(", ",types)+"]";
            }
            output.add(error);
        }
        return List.copyOf(output);
    }
    /** Reflects full stored M1/AST fields without invoking legacy derived accessors. */
    public static String normalize(Object value) throws Exception {
        if (value == null) return "null";
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)
            return value.getClass().getName() + ":" + value;
        if (value instanceof Optional<?> optional) return "Optional(" + normalize(optional.orElse(null)) + ")";
        if (value instanceof OptionalInt optional) return "OptionalInt(" + (optional.isPresent() ? optional.getAsInt() : "empty") + ")";
        if (value instanceof Collection<?> collection) {
            var items = new ArrayList<String>(); for (var item : collection) items.add(normalize(item));
            if (value instanceof Set<?>) Collections.sort(items);
            return "[" + String.join(",", items) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            var items = new TreeMap<String, String>();
            for (var entry : map.entrySet()) items.put(normalize(entry.getKey()), normalize(entry.getValue()));
            return items.toString();
        }
        var fields = new TreeMap<String, String>();
        for (Class<?> type = value.getClass(); type != null && type.getName().startsWith("org.vnu.sme.goal."); type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                field.setAccessible(true); fields.put(type.getName() + "." + field.getName(), normalize(field.get(value)));
            }
        }
        if (fields.isEmpty()) throw new AssertionError("NO_ORIGINAL_MODEL_FIELDS:" + value.getClass());
        return value.getClass().getName() + fields;
    }
    @Override public void close() throws IOException { loader.close(); }
}
