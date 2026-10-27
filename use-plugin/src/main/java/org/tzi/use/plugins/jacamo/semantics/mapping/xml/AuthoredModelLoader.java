package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.List;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.*;

/** Compile explicitly selected owned bytes. No model discovery, generation or case graph builder. */
public final class AuthoredModelLoader {
    public CslRuleExecutor.Result csl(MappingInputs inputs) {
        requireAuthored(inputs);
        var declaration=inputs.specification().root().child("modelRules");
        String text=source(inputs,MappingInputs.AUTHORED_CSL);
        var model=new CslFrontend().compile(text,inputs.xml().id()+":"+MappingInputs.AUTHORED_CSL).requireModel();
        if(!model.name().equals(declaration.attr("targetModel"))||!model.version().equals(declaration.attr("targetVersion")))
            throw declaration.location().error("AUTHORED_CSL_MODEL_IDENTITY_MISMATCH");
        var omissions=inputs.specification().root().child("omittedConcepts").children("omit").stream()
                .map(n->new CslRuleExecutor.Omission(n.attr("id"),n.attr("concept"),n.optional("handledBy"),n.attr("reason"),"INTENTIONAL_M1_OMISSION_NOT_RUNTIME_VERDICT",n.children("trace"))).toList();
        return new CslRuleExecutor.Result(model,text,CslModelFingerprint.of(model),List.of(),omissions);
    }
    public IStarRuleExecutor.Result goals(MappingInputs inputs) {
        requireAuthored(inputs);
        var declaration=inputs.specification().root().child("istar");
        var profile=EngineSemanticsProfile.valueOf(inputs.specification().root().attr("engineProfile"));
        String text=source(inputs,MappingInputs.AUTHORED_ISTAR);
        var model=new IStarFrontend().compile(text,inputs.xml().id()+":"+MappingInputs.AUTHORED_ISTAR,profile).requireModel();
        if(!model.getName().equals(declaration.attr("name")))throw declaration.location().error("AUTHORED_ISTAR_MODEL_IDENTITY_MISMATCH");
        return new IStarRuleExecutor.Result(model,text,profile,List.of());
    }
    private static void requireAuthored(MappingInputs inputs) {
        if(!inputs.authoredModels())throw new IllegalArgumentException("EXPLICIT_AUTHORED_MODE_REQUIRED");
    }
    private static String source(MappingInputs inputs,String id) {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(inputs.require(id).bytes())).toString();}
        catch(CharacterCodingException invalid){throw new IllegalArgumentException("AUTHORED_MODEL_UTF8_REQUIRED:"+id,invalid);}
    }
}
