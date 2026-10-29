package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;

/** Frozen production inputs. No optional oracle discovery, manifest, URL or reference-repo fallback. */
public final class MappingInputs {
    public static final String SPECIFICATION_PROPERTY="use.jacamo.mapping.xml",OCL_PROPERTY="use.jacamo.mapping.ocl";
    public static final String CSL_PROPERTY="use.jacamo.mapping.csl",ISTAR_PROPERTY="use.jacamo.mapping.istar";
    public static final String AUTHORED_CSL="authored-csl",AUTHORED_ISTAR="authored-istar";
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
        return read(jcm,xml,ocl,null,null);
    }
    public static MappingInputs read(Path jcm,Path xml,Path ocl,Path csl,Path istar) {
        return readSelected(jcm,xml,ocl,csl,istar,null);
    }
    public static MappingInputs read(Path jcm,Path xml,Path ocl,Path csl,Path istar,boolean strict) {
        return readSelected(jcm,xml,ocl,csl,istar,strict?"PINNED":"VALIDATED_SOURCE_V1");
    }
    private static MappingInputs readSelected(Path jcm,Path xml,Path ocl,Path csl,Path istar,String policy) {
        try {
            Path project=jcm.toRealPath(),root=project.getParent();
            byte[] specification=bounded(xml,MappingXmlReader.MAX_BYTES),authored=bounded(ocl,1024*1024);
            if(policy!=null)specification=withPolicy(specification,policy);
            var spec=new MappingXmlReader().parse(specification,xml.toString());
            boolean validated=validated(spec);
            if(!spec.root().child("ocl").attr("sha256").equals(TraceComplement.Asset.of("check","bytes",authored).sha256()))
                throw spec.root().child("ocl").location().error("OCL_CHECKSUM_MISMATCH");
            var sources=new LinkedHashMap<String,TraceComplement.Asset>();var paths=new HashSet<Path>();int total=specification.length+authored.length;TraceComplement.Asset derived=null;
            for(var file:spec.root().child("sources").children("file")) {
                Path candidate=validated&&file.attr("id").equals(spec.root().attr("jcmRef"))?project:root.resolve(MappingVocabulary.relative(file.attr("path"),file)).normalize();
                if(!candidate.startsWith(root)||Files.isSymbolicLink(candidate))throw file.location().error("SOURCE_PATH_UNSAFE");
                Path actual=candidate.toRealPath();
                if(!actual.startsWith(root)||!paths.add(actual))throw file.location().error("SOURCE_OUTSIDE_PROJECT_OR_DUPLICATE");
                if(file.attr("id").equals(spec.root().attr("jcmRef"))&&!actual.equals(project))throw file.location().error("SELECTED_JCM_MISMATCH");
                byte[] bytes=bounded(actual,MAX_FILE);total=Math.addExact(total,bytes.length);
                if(total>MAX_TOTAL)throw file.location().error("INPUT_TOTAL_CAPACITY");
                var asset=TraceComplement.Asset.of(file.attr("id"),file.attr("kind"),bytes);
                if(validated&&file.attr("id").equals(spec.root().attr("jcmRef"))) {
                    byte[] canonical=BridgeJcmDerivation.removeBootstrap(bytes,project);
                    if(!Arrays.equals(canonical,bytes)) {
                        derived=TraceComplement.Asset.of(derivedId(spec),BridgeJcmDerivation.RECIPE,bytes);
                        total=Math.addExact(total,canonical.length);
                        asset=TraceComplement.Asset.of(file.attr("id"),file.attr("kind"),canonical);
                    }
                } else if(!validated&&!asset.sha256().equals(file.attr("sha256"))) {
                    if(file.attr("id").equals(spec.root().attr("jcmRef"))) {
                        byte[] canonical=BridgeJcmDerivation.canonical(bytes,file.attr("sha256"),project,file.optional("layoutSha256"));
                        derived=TraceComplement.Asset.of(derivedId(spec),BridgeJcmDerivation.recipe(bytes,canonical),bytes);
                        total=Math.addExact(total,canonical.length);
                        asset=TraceComplement.Asset.of(file.attr("id"),file.attr("kind"),canonical);
                    } else throw file.location().error("SOURCE_CHECKSUM_MISMATCH");
                }
                sources.put(file.attr("id"),asset);
            }
            if(total>MAX_TOTAL)throw new IllegalArgumentException("INPUT_TOTAL_CAPACITY");
            if(derived!=null&&sources.putIfAbsent(derived.id(),derived)!=null)throw new IllegalArgumentException("MAPPING_DERIVED_ASSET_COLLISION");
            boolean authoredMode=!spec.root().children("models").isEmpty();
            if(authoredMode) {
                if(csl==null||istar==null)throw spec.root().child("models").location().error("AUTHORED_MODEL_SELECTION_REQUIRED");
                var declaration=spec.root().child("models");
                var cslAsset=selectedModel(csl,AUTHORED_CSL,".csl","NATIVE_CSL/4.0",declaration.attr("cslSha256"));
                var goalAsset=selectedModel(istar,AUTHORED_ISTAR,".istar","NATIVE_ISTAR/1.0",declaration.attr("istarSha256"));
                for(var asset:List.of(cslAsset,goalAsset)) {
                    total=Math.addExact(total,asset.bytes().length);
                    if(sources.putIfAbsent(asset.id(),asset)!=null)throw declaration.location().error("AUTHORED_MODEL_ASSET_COLLISION");
                }
            } else if(csl!=null||istar!=null)throw new IllegalArgumentException("AUTHORED_MODEL_SELECTION_REQUIRES_EXPLICIT_MODE");
            if(total>MAX_TOTAL)throw new IllegalArgumentException("INPUT_TOTAL_CAPACITY");
            if(validated) {
                // Actual immutable revision, independently pinned from reference hashes in XML.
                var pins=new TreeMap<String,Object>();sources.forEach((id,asset)->pins.put(id,asset.sha256()));
                var manifest=TraceComplement.Asset.of("source-admission-manifest","VALIDATED_SOURCE_V1",org.jacamo.bridge.contract.CanonicalJson.encode(pins));
                if(sources.putIfAbsent(manifest.id(),manifest)!=null)throw new IllegalArgumentException("SOURCE_MANIFEST_COLLISION");
                if((long)total+manifest.bytes().length>MAX_TOTAL)throw new IllegalArgumentException("INPUT_TOTAL_CAPACITY");
                SourceSelections.execute(new MappingInputs(spec,sources,specification,authored));
            }
            return new MappingInputs(spec,sources,specification,authored);
        }catch(IOException|ArithmeticException error){throw new IllegalArgumentException("MAPPING_INPUT_UNREADABLE_OR_CAPACITY",error);}
    }
    private static TraceComplement.Asset selectedModel(Path path,String id,String extension,String format,String hash)throws IOException {
        if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))throw new IllegalArgumentException("AUTHORED_MODEL_EXTENSION_REQUIRED:"+extension);
        var asset=TraceComplement.Asset.of(id,format,bounded(path,MAX_FILE));
        if(!asset.sha256().equals(hash))throw new IllegalArgumentException("AUTHORED_MODEL_CHECKSUM_MISMATCH:"+id);
        return asset;
    }
    private static String derivedId(MappingSpecification spec){return spec.root().attr("jcmRef")+":bridge-derived";}
    public String projectDigest(){return sources.getOrDefault(derivedId(specification),require(specification.root().attr("jcmRef"))).sha256();}
    public Map<String,Object> projectDerivation(){
        var derived=sources.get(derivedId(specification));
        return derived==null?Map.of():Map.of("recipe",derived.format(),"canonicalHash",require(specification.root().attr("jcmRef")).sha256(),"derivedHash",derived.sha256());
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
            boolean exact=asset!=null&&(validated(spec)||asset.sha256().equals(file.attr("sha256")));
            boolean layout=asset!=null&&file.attr("kind").equals("JCM")&&!file.optional("layoutSha256").isBlank()
                    &&BridgeJcmDerivation.lineLayoutDigest(asset.bytes()).equals(file.attr("layoutSha256"));
            if(asset==null||!asset.format().equals(file.attr("kind"))||!(exact||layout)||asset.bytes().length>MAX_FILE)throw file.location().error("MAPPING_FROZEN_SOURCE_MISMATCH");
            total+=asset.bytes().length;owned.put(asset.id(),asset);
        }
        var derived=sources.get(derivedId(spec));
        if(derived!=null) {
            if(owned.containsKey(derived.id())||!Set.of(BridgeJcmDerivation.RECIPE,BridgeJcmDerivation.FORMATTED_RECIPE).contains(derived.format())||derived.bytes().length>MAX_FILE)throw new IllegalArgumentException("MAPPING_FROZEN_DERIVATION_TYPE_OR_CAPACITY");
            var project=owned.get(spec.root().attr("jcmRef"));
            BridgeJcmDerivation.canonical(derived.bytes(),project.sha256(),null);
            if(!derived.format().equals(BridgeJcmDerivation.recipe(derived.bytes(),project.bytes())))throw new IllegalArgumentException("MAPPING_FROZEN_DERIVATION_RECIPE_MISMATCH");
            owned.put(derived.id(),derived);total+=derived.bytes().length;
        }
        if(!spec.root().children("models").isEmpty()) {
            var declaration=spec.root().child("models");
            for(var model:List.of(List.of(AUTHORED_CSL,"NATIVE_CSL/4.0","cslSha256"),List.of(AUTHORED_ISTAR,"NATIVE_ISTAR/1.0","istarSha256"))) {
                var asset=sources.get(model.get(0));
                if(asset==null||!asset.format().equals(model.get(1))||!asset.sha256().equals(declaration.attr(model.get(2)))||asset.bytes().length>MAX_FILE
                        ||owned.putIfAbsent(asset.id(),asset)!=null)throw declaration.location().error("FROZEN_AUTHORED_MODEL_MISMATCH:"+model.get(0));
                total+=asset.bytes().length;
            }
        }
        if(validated(spec)) {
            var manifest=sources.get("source-admission-manifest");
            if(manifest==null||!manifest.format().equals("VALIDATED_SOURCE_V1"))throw new IllegalArgumentException("FROZEN_SOURCE_MANIFEST_REQUIRED");
            var pins=new TreeMap<String,Object>();owned.forEach((id,asset)->pins.put(id,asset.sha256()));
            if(!pins.equals(org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(manifest.bytes()))))throw new IllegalArgumentException("FROZEN_SOURCE_REVISION_MISMATCH");
            if(owned.putIfAbsent(manifest.id(),manifest)!=null)throw new IllegalArgumentException("SOURCE_MANIFEST_COLLISION");total+=manifest.bytes().length;
        }
        if(total>MAX_TOTAL||!owned.keySet().equals(sources.keySet()))throw new IllegalArgumentException("MAPPING_FROZEN_ASSETS_OR_CAPACITY");
        return new MappingInputs(spec,owned,xml,ocl);
    }
    private static boolean validated(MappingSpecification spec){return spec.root().optional("sourcePolicy").equals("VALIDATED_SOURCE_V1");}
    private static byte[] withPolicy(byte[] xml,String policy) {
        // Validate before parsing again; DTD/entities/unknown instructions are forbidden.
        var original=new MappingXmlReader().parse(xml,"selected-mapping");
        if(policy.equals("PINNED")&&!validated(original))return xml;
        try {
            var factory=javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));document.getDocumentElement().setAttribute("sourcePolicy",policy);
            var transformers=javax.xml.transform.TransformerFactory.newDefaultInstance();transformers.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");transformers.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            var output=new java.io.ByteArrayOutputStream();transformers.newTransformer().transform(new javax.xml.transform.dom.DOMSource(document),new javax.xml.transform.stream.StreamResult(output));
            return output.toByteArray();
        }catch(Exception error){throw new IllegalArgumentException("SOURCE_POLICY_ENCODING_FAILED",error);}
    }
    public Map<String,TraceComplement.Asset> sources(){return sources;}
    public boolean authoredModels(){return !specification.root().children("models").isEmpty();}
    public TraceComplement.Asset xml(){return xml;}
    public TraceComplement.Asset ocl(){return ocl;}
    public TraceComplement.Asset require(String id){var asset=sources.get(id);if(asset==null)throw new IllegalArgumentException("MAPPING_SOURCE_REQUIRED:"+id);return asset;}
}
