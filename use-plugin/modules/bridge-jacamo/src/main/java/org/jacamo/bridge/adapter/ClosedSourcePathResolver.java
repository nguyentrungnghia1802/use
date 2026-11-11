package org.jacamo.bridge.adapter;

import java.io.File;
import java.net.URL;
import jason.runtime.SourcePath;
import jason.util.Config;

/** Jason 3.3.2 SourcePath.fixPath search order, with ownership of existence probes.
 * Its private testURLSrc leaves successful streams open, locking ASL on Windows.
 * This adapter does not change source precedence or patch the framework. */
final class ClosedSourcePathResolver {
    private ClosedSourcePathResolver() { }
    static String resolve(SourcePath paths,String reference) {
        if(reference==null||reference.isEmpty())return reference;
        if(exists(reference))return reference;
        if(new File(reference).exists())return "file:"+reference;
        if(reference.startsWith("$")&&reference.indexOf('/')>1) {
            String name=reference.substring(1,reference.indexOf('/'));
            var archive=Config.get().getPackage(name);
            if(archive!=null) {
                String candidate="jar:file:"+archive.getAbsolutePath()+"!"+reference.substring(reference.indexOf('/'));
                if(exists(candidate))return candidate;
            }
        }
        String relative=reference.startsWith("file:")?reference.substring(5):reference;
        for(String path:paths.getPaths()) {
            String candidate=(path+"/"+relative).replaceAll("\\./","");
            if(exists(candidate))return candidate;
        }
        return reference;
    }
    private static boolean exists(String source) {
        try {
            URL url=source.startsWith(SourcePath.CRPrefix)?SourcePath.class.getResource(source.substring(SourcePath.CRPrefix.length())):new URL(source);
            if(url==null)return false;
            var connection=url.openConnection();connection.setUseCaches(false);
            try(var input=connection.getInputStream()){return true;}
        }catch(Exception unavailable){return false;}
    }
}
