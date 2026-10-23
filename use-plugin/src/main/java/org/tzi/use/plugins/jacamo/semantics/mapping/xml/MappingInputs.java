package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;

/** Frozen production inputs. No optional oracle discovery, manifest, URL or reference-repo fallback. */
public final class MappingInputs {
    public static final String SPECIFICATION_PROPERTY="use.jacamo.mapping.xml",OCL_PROPERTY="use.jacamo.mapping.ocl";
    private static final int MAX_FILE=8*1024*1024, MAX_TOTAL=32*1024*1024;
    private final MappingSpecification specification;
    private final Map<String,TraceComplement.Asset> sources;
    private final TraceComplement.Asset xml,ocl;
    private MappingInputs(MappingSpecification spec,Map<String,TraceComplement.Asset> sources,byte[] xml,byte[] ocl) {
        specification=spec;this.sources=Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        this.xml=TraceComplement.Asset.of("mapping-specification","JACAMO_MAPPING_XML/2.0",xml);
        this.ocl=TraceComplement.Asset.of("authored-ocl","NATIVE_USE_OCL",ocl);
    }
    public static MappingInputs read(Path jcm,Path xml,Path ocl) {
        try {
            Path project=jcm.toRealPath(),root=project.getParent();
            byte[] specification=bounded(xml,MappingXmlReader.MAX_BYTES),authored=bounded(ocl,1024*1024);
            var spec=new MappingXmlReader().parse(specification,xml.toString());
            if(!spec.root().child("ocl").attr("sha256").equals(TraceComplement.Asset.of("check","bytes",authored).sha256()))
                throw spec.root().child("ocl").location().error("OCL_CHECKSUM_MISMATCH");
            var sources=new LinkedHashMap<String,TraceComplement.Asset>();var paths=new HashSet<Path>();int total=specification.length+authored.length;
            for(var file:spec.root().child("sources").children("file")) {
                Path candidate=root.resolve(MappingVocabulary.relative(file.attr("path"),file)).normalize();
                if(!candidate.startsWith(root)||Files.isSymbolicLink(candidate))throw file.location().error("SOURCE_PATH_UNSAFE");
                Path actual=candidate.toRealPath();
                if(!actual.startsWith(root)||!paths.add(actual))throw file.location().error("SOURCE_OUTSIDE_PROJECT_OR_DUPLICATE");
                if(file.attr("id").equals(spec.root().attr("jcmRef"))&&!actual.equals(project))throw file.location().error("SELECTED_JCM_MISMATCH");
                byte[] bytes=bounded(actual,MAX_FILE);total=Math.addExact(total,bytes.length);
                if(total>MAX_TOTAL)throw file.location().error("INPUT_TOTAL_CAPACITY");
                var asset=TraceComplement.Asset.of(file.attr("id"),file.attr("kind"),bytes);
                if(!asset.sha256().equals(file.attr("sha256")))throw file.location().error("SOURCE_CHECKSUM_MISMATCH");
                sources.put(file.attr("id"),asset);
            }
            return new MappingInputs(spec,sources,specification,authored);
        }catch(IOException|ArithmeticException error){throw new IllegalArgumentException("MAPPING_INPUT_UNREADABLE_OR_CAPACITY",error);}
    }
    private static byte[] bounded(Path path,int limit)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>limit)throw new IllegalArgumentException("MAPPING_FILE_CAPACITY_OR_TYPE:"+path);
        try(var input=Files.newInputStream(path)){byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new IllegalArgumentException("MAPPING_FILE_CAPACITY:"+path);return bytes;}
    }
    public MappingSpecification specification(){return specification;}
    /** Portable replay reconstructs from owned bytes only; never reopens diagnostic source paths. */
    public static MappingInputs fromFrozen(byte[] xml,byte[] ocl,Map<String,TraceComplement.Asset> sources,String location) {
        if(xml.length>MappingXmlReader.MAX_BYTES||ocl.length>1024*1024||location==null||location.length()>4096)throw new IllegalArgumentException("MAPPING_FROZEN_CAPACITY");
        var spec=new MappingXmlReader().parse(xml,location);var owned=new LinkedHashMap<String,TraceComplement.Asset>();long total=(long)xml.length+ocl.length;
        if(!spec.root().child("ocl").attr("sha256").equals(TraceComplement.Asset.of("check","bytes",ocl).sha256()))throw new IllegalArgumentException("MAPPING_FROZEN_OCL_DIGEST_MISMATCH");
        for(var file:spec.root().child("sources").children("file")) {
            var asset=sources.get(file.attr("id"));
            if(asset==null||!asset.format().equals(file.attr("kind"))||!asset.sha256().equals(file.attr("sha256"))||asset.bytes().length>MAX_FILE)throw file.location().error("MAPPING_FROZEN_SOURCE_MISMATCH");
            total+=asset.bytes().length;owned.put(asset.id(),asset);
        }
        if(total>MAX_TOTAL||!owned.keySet().equals(sources.keySet()))throw new IllegalArgumentException("MAPPING_FROZEN_ASSETS_OR_CAPACITY");
        return new MappingInputs(spec,owned,xml,ocl);
    }
    public Map<String,TraceComplement.Asset> sources(){return sources;}
    public TraceComplement.Asset xml(){return xml;}
    public TraceComplement.Asset ocl(){return ocl;}
    public TraceComplement.Asset require(String id){var asset=sources.get(id);if(asset==null)throw new IllegalArgumentException("MAPPING_SOURCE_REQUIRED:"+id);return asset;}
}
