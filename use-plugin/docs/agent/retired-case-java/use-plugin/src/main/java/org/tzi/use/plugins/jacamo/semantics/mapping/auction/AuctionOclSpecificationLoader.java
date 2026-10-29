package org.tzi.use.plugins.jacamo.semantics.mapping.auction;
import java.util.*;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader;

/** Legacy XML policy adapter. Parsing and native compilation have one shared implementation. */
public final class AuctionOclSpecificationLoader extends AuthoredOclSpecificationLoader {
    public Specification load(AuctionCaseBundle bundle){return parse(bundle.require("input-ocl").bytes(),"input-ocl");}
    @Override protected String sourceAnnotation(){return "AuctionOclSource";}
    public Prepared compile(Specification source,Use75ModelBinding binding,AuctionMappingSpecification mapping) {
        return super.compile(source,binding,mapping.requireId("input-ocl").attr("sha256"),bindings(mapping));
    }
    private static Map<String,Context> bindings(AuctionMappingSpecification mapping) {
        var expected=new LinkedHashMap<String,Context>();
        var bindings=mapping.root().child("verificationBindings");
        for(var node:bindings.children("operationBinding")) {
            var context=parseContext("context "+node.attr("oclContext"));
            if(context.operation().isEmpty()||!context.owner().equals(node.attr("cslStateOwner"))||!context.operation().equals(node.attr("sourceOperation")))
                throw node.location().error("OCL_OPERATION_HEADER_BINDING_MISMATCH");
            for(String name:node.attr("clauses").trim().split("\\s+"))if(expected.putIfAbsent(name,context)!=null)throw node.location().error("OCL_CLAUSE_BINDING_DUPLICATE");
        }
        for(var node:bindings.children("externalInvariant")) {
            var view=mapping.requireId(node.attr("observerViewRef"));
            if(expected.putIfAbsent(node.attr("name"),new Context(view.attr("name"),"",List.of(),""))!=null)throw node.location().error("OCL_CLAUSE_BINDING_DUPLICATE");
        }
        return Map.copyOf(expected);
    }
}
