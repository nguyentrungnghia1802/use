package org.jacamo.bridge.adapter;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jacamo.project.parser.JaCaMoProjectParserConstants;
import jacamo.project.parser.JaCaMoProjectParserTokenManager;
import jacamo.project.parser.SimpleCharStream;
import jacamo.project.parser.Token;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JcmSemanticContract.ImportProvenanceSemantic;

/**
 * J11 provenance collector. It consumes JaCaMo's generated token manager and deliberately does not
 * interpret declarations or reproduce {@code JaCaMoProject.importProject} merge semantics.
 */
public final class OfficialImportGraphCollector {
    public List<ImportProvenanceSemantic> collect(Path entry, Path projectRoot) throws Exception {
        var result=new ArrayList<ImportProvenanceSemantic>();
        collect(entry.toAbsolutePath().normalize(),projectRoot.toAbsolutePath().normalize(),new HashSet<>(),result,new int[]{0});
        return List.copyOf(result);
    }

    private void collect(Path importer, Path projectRoot, Set<Path> visited,
                         List<ImportProvenanceSemantic> result, int[] ordinal) throws Exception {
        Path canonical=importer.toAbsolutePath().normalize();
        if(!visited.add(canonical) || !Files.isRegularFile(canonical))return;
        var importerEvidence=AdapterEvidence.file("jacamo-generated-jcm-token-manager",projectRoot,canonical,
                "J11 import provenance; official parser retains merge authority");
        for(TokenPath tokenPath:directImports(canonical)){
            Path resolved=resolveLikeOfficialImport(canonical.getParent(),tokenPath.spelling());
            boolean available=Files.isRegularFile(resolved);
            String digest=available?AdapterEvidence.digest(Files.readAllBytes(resolved)):"";
            CapabilityStatus status=available?CapabilityStatus.COMPLETE:CapabilityStatus.UNAVAILABLE;
            List<String> diagnostics=available?List.of():List.of("UNRESOLVED_PROVENANCE");
            String id="jcm-import:"+AdapterEvidence.digest((canonical+"|"+tokenPath.spelling()+"|"+ordinal[0])
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var metadata=SemanticEvidence.metadata(id,"JCM_IMPORT_PROVENANCE",
                    "jacamo.project.parser.JaCaMoProjectParserTokenManager",EvidenceAuthority.OFFICIAL_GENERATED_LEXER,
                    Fidelity.PROVENANCE_ONLY,status,importerEvidence,tokenPath.line(),tokenPath.line(),diagnostics);
            result.add(new ImportProvenanceSemantic(metadata,importer.toUri().toString(),tokenPath.spelling(),
                    resolved.toString(),digest,ordinal[0]++,status));
            if(available)collect(resolved,projectRoot,visited,result,ordinal);
        }
    }

    private List<TokenPath> directImports(Path file) throws Exception {
        var tokens=new ArrayList<Token>();
        try(Reader reader=Files.newBufferedReader(file)){
            var manager=new JaCaMoProjectParserTokenManager(new SimpleCharStream(reader));
            for(Token token=manager.getNextToken();token.kind!=JaCaMoProjectParserConstants.EOF;token=manager.getNextToken()){
                tokens.add(token);
                if("{".equals(token.image))break; // only the grammar's top-level MAS header can declare imports
            }
        }
        int uses=-1; for(int i=0;i<tokens.size();i++)if(tokens.get(i).kind==JaCaMoProjectParserConstants.USES){uses=i;break;}
        if(uses<0)return List.of();
        var result=new ArrayList<TokenPath>(); var current=new StringBuilder(); int line=0;
        for(int i=uses+1;i<tokens.size();i++){
            Token token=tokens.get(i); if("{".equals(token.image)){addPath(result,current,line);break;}
            if(",".equals(token.image)){addPath(result,current,line);current.setLength(0);line=0;continue;}
            if(line==0)line=token.beginLine; current.append(token.image);
        }
        return result;
    }

    private void addPath(List<TokenPath> result,StringBuilder spelling,int line){
        String value=spelling.toString().strip();
        if(value.length()>=2&&((value.startsWith("\"")&&value.endsWith("\""))
                ||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
        if(!value.isBlank())result.add(new TokenPath(value,line));
    }

    private Path resolveLikeOfficialImport(Path directory,String spelling){
        String file=spelling.endsWith(".jcm")?spelling:spelling+".jcm";
        Path direct=Path.of(file); if(Files.exists(direct))return direct.toAbsolutePath().normalize();
        return directory.resolve(file).toAbsolutePath().normalize();
    }

    private record TokenPath(String spelling,int line){ }
}
