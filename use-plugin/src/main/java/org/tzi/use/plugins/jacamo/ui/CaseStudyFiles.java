package org.tzi.use.plugins.jacamo.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Exact, non-recursive selection of manually authored system specifications. */
record CaseStudyFiles(Path folder,Path csl,Path istar,Path mapping,Path ocl) {
    static CaseStudyFiles read(Path directory) {
        Path folder=directory.toAbsolutePath().normalize();
        if(!Files.isDirectory(folder))throw new IllegalArgumentException("CASE_FOLDER_NOT_FOUND: "+folder);
        var byType=new LinkedHashMap<String,List<Path>>();
        for(String extension:List.of("csl","istar","xml","ocl"))byType.put(extension,new ArrayList<>());
        try(var entries=Files.list(folder)) {
            for(Path file:entries.sorted().toList()) {
                String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
                int dot=name.lastIndexOf('.');if(dot<0)continue;
                var matches=byType.get(name.substring(dot+1));if(matches==null)continue;
                if(Files.isSymbolicLink(file))throw new IllegalArgumentException("CASE_FILE_SYMLINK_UNSUPPORTED: "+file.getFileName());
                if(Files.isRegularFile(file))matches.add(file);
            }
        }catch(IOException error){throw new IllegalArgumentException("CASE_FOLDER_UNREADABLE: "+folder,error);}
        for(var entry:byType.entrySet()) {
            if(entry.getValue().size()>1)throw new IllegalArgumentException("CASE_FOLDER_DUPLICATE_TYPE: ."+entry.getKey()+" — "+entry.getValue().stream().map(p->p.getFileName().toString()).toList());
            if(!entry.getKey().equals("ocl")&&entry.getValue().isEmpty())throw new IllegalArgumentException("CASE_FOLDER_MISSING_REQUIRED: ."+entry.getKey());
        }
        return new CaseStudyFiles(folder,byType.get("csl").getFirst(),byType.get("istar").getFirst(),
                byType.get("xml").getFirst(),byType.get("ocl").isEmpty()?null:byType.get("ocl").getFirst());
    }
}
