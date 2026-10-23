package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.antlr.runtime.*;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.parser.ParseErrorHandler;
import org.tzi.use.parser.generator.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStructure;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpInvalidException;

/** Located clause specification. The host lexer/parser/compiler owns all OCL expression semantics. */
public class AuthoredOclSpecificationLoader {
    public enum Kind { INVARIANT, PRE, POST }
    public record Parameter(String name,String type) { }
    public record Context(String owner,String operation,List<Parameter> parameters,String resultType) {
        public Context {parameters=List.copyOf(parameters);}
    }
    /** Offsets are UTF-16 indices in the unchanged Java source string, end exclusive. */
    public record Clause(String name,Kind kind,Context context,String body,int bodyStart,int bodyEnd,int line,int column) { }
    public record Specification(String sourceName,String sourceHash,String source,List<Clause> clauses) {
        public Specification {clauses=List.copyOf(clauses);}
        public List<Context> operations(){return clauses.stream().map(Clause::context).filter(c->!c.operation().isEmpty()).distinct().toList();}
    }
    public record CompiledClause(Clause specification,Expression expression,MClassInvariant invariant,MPrePostCondition condition) { }
    public static final class Prepared {
        private final MModel model;
        private final String modelHash;
        private final Specification source;
        private final List<CompiledClause> clauses;
        private final List<MClassInvariant> cslInvariants;
        private boolean installed;
        private Prepared(MModel model,Specification source,List<CompiledClause> clauses,List<MClassInvariant> cslInvariants) {
            this.model=model;this.modelHash=NativeUseStructure.sha256(model);this.source=source;
            this.clauses=List.copyOf(clauses);this.cslInvariants=List.copyOf(cslInvariants);
        }
        public Specification source(){return source;}
        public List<CompiledClause> clauses(){return clauses;}
        public List<MClassInvariant> cslInvariants(){return cslInvariants;}
        /** Construction candidate only: compile every clause before attaching any.
         * The caller publishes its existing candidate through the normal USE pipeline.
         * An unexpected model API failure means discard that unpublished candidate. */
        public synchronized void install(UseModelApi api) {
            if(installed||api.getModel()!=model||!modelHash.equals(NativeUseStructure.sha256(model)))
                throw new IllegalArgumentException("OCL_SPEC_PREPARED_MODEL_CHANGED_OR_ALREADY_INSTALLED");
            var invariantNames=new HashSet<String>();model.classInvariants().forEach(i->invariantNames.add(i.qualifiedName()));
            var conditionNames=new HashSet<String>();model.prePostConditions().forEach(c->conditionNames.add(conditionKey(c)));
            for(var row:clauses) {
                if(row.invariant()!=null&&!invariantNames.add(row.invariant().qualifiedName()))throw new IllegalArgumentException("OCL_SPEC_INVARIANT_COLLISION");
                if(row.condition()!=null&&!conditionNames.add(conditionKey(row.condition())))throw new IllegalArgumentException("OCL_SPEC_CONDITION_COLLISION");
            }
            try {
                for(var row:clauses)if(row.invariant()!=null)model.addClassInvariant(row.invariant());else model.addPrePostCondition(row.condition());
                installed=true;
            }catch(MInvalidModelException unexpected){throw new IllegalArgumentException("OCL_SPEC_UNPUBLISHED_CANDIDATE_REJECTED",unexpected);}
        }
        private static String conditionKey(MPrePostCondition c){return c.cls().name()+"::"+c.operation().name()+c.name();}
    }
    public Specification parse(byte[] bytes,String name) {
        if(bytes.length>1024*1024)throw new IllegalArgumentException("OCL_SPEC_SOURCE_CAPACITY");
        try {
            String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return parse(source,name);
        }catch(CharacterCodingException invalid){throw new IllegalArgumentException("OCL_SPEC_UTF8_REQUIRED:"+name,invalid);}
    }
    public Specification parse(String source,String name) {
        Objects.requireNonNull(source);Objects.requireNonNull(name);
        if(!StandardCharsets.UTF_8.newEncoder().canEncode(source))throw new IllegalArgumentException("OCL_SPEC_UTF8_REQUIRED:"+name);
        byte[] bytes=source.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>1024*1024)throw new IllegalArgumentException("OCL_SPEC_SOURCE_CAPACITY");
        var cursor=new Cursor(lex(source,name),name);var clauses=new ArrayList<Clause>();var names=new HashSet<String>();
        Context context=null;boolean needsClause=false;
        while(cursor.has()) {
            if(cursor.is("context")) {
                if(needsClause)throw cursor.error("EMPTY_CONTEXT");
                context=context(cursor);needsClause=true;continue;
            }
            if(context==null)throw cursor.error("CONTEXT_REQUIRED");
            var start=cursor.take();
            Kind kind=switch(start.getText()){case "inv"->Kind.INVARIANT;case "pre"->Kind.PRE;case "post"->Kind.POST;default->throw cursor.error("NAMED_CLAUSE_REQUIRED");};
            if((kind==Kind.INVARIANT)!=context.operation().isEmpty())throw cursor.error("CLAUSE_CONTEXT_KIND_MISMATCH");
            String id=cursor.identifier();
            if(!names.add(id))throw cursor.error("DUPLICATE_CLAUSE:"+id);
            var colon=cursor.expect(":");int bodyStart=colon.getStopIndex()+1;
            var stack=new ArrayDeque<String>();int count=0;
            while(cursor.has()) {
                if(stack.isEmpty()&&(cursor.is("context")||cursor.clauseStart()))break;
                String text=cursor.take().getText();
                if(++count>4096)throw cursor.error("EXPRESSION_TOKEN_CAPACITY");
                if(Set.of("(","[","{").contains(text)) {
                    if(stack.size()>=128)throw cursor.error("EXPRESSION_NESTING_CAPACITY");stack.push(text);
                } else if(Set.of(")","]","}").contains(text)) {
                    if(stack.isEmpty()||!matching(stack.pop(),text))throw cursor.error("UNBALANCED_EXPRESSION");
                }
            }
            if(count==0||!stack.isEmpty())throw cursor.error("EMPTY_OR_UNBALANCED_EXPRESSION");
            int bodyEnd=cursor.has()?cursor.peek().getStartIndex():source.length();
            String body=source.substring(bodyStart,bodyEnd);
            syntax(body,name+":"+start.getLine()+":"+id);
            clauses.add(new Clause(id,kind,context,body,bodyStart,bodyEnd,start.getLine(),start.getCharPositionInLine()+1));
            if(clauses.size()>64)throw cursor.error("CLAUSE_CAPACITY");needsClause=false;
        }
        if(clauses.isEmpty()||needsClause)throw cursor.error("NAMED_CLAUSE_REQUIRED");
        return new Specification(name,ExternalOclConstraintService.sha256(bytes),source,clauses);
    }
    public Prepared compile(Specification source,Use75ModelBinding binding,String expectedHash,Map<String,Context> expectedBindings) {
        try {return compileChecked(source,binding,expectedHash,Map.copyOf(expectedBindings));}
        catch(StackOverflowError capacity){throw new IllegalArgumentException("OCL_SPEC_NATIVE_COMPILATION_CAPACITY",capacity);}
    }
    private Prepared compileChecked(Specification source,Use75ModelBinding binding,String expectedHash,Map<String,Context> expectedBindings) {
        // Public value records are not a provenance attestation. Reparse the
        // frozen text, then enforce the XML input pin before compiling bodies.
        if(!source.equals(parse(source.source(),source.sourceName())))throw new IllegalArgumentException("OCL_SPEC_PARSED_SOURCE_MISMATCH");
        if(!source.sourceHash().equals(expectedHash))throw new IllegalArgumentException("OCL_SPEC_SOURCE_HASH_MISMATCH");
        validateBindings(source,expectedBindings);
        var model=binding.model();var rows=new ArrayList<CompiledClause>();var factory=new ModelFactory();
        for(var clause:source.clauses()) {
            var owner=model.getClass(clause.context().owner());
            if(owner==null)throw error(source,clause,"CONTEXT_UNRESOLVED");
            if(clause.kind()==Kind.INVARIANT) {
                var diagnostics=new StringWriter();
                var compiled=ASSLCompiler.compileInvariants(model,new ByteArrayInputStream(("context "+owner.name()+"\ninv "+clause.name()+":"+clause.body()).getBytes(StandardCharsets.UTF_8)),
                        source.sourceName()+":"+clause.line(),new PrintWriter(diagnostics,true));
                if(compiled==null||compiled.size()!=1||!diagnostics.toString().isBlank())throw error(source,clause,"NATIVE_INVARIANT_REJECTED:"+diagnostics);
                var invariant=compiled.iterator().next();
                if(!invariant.bodyExpression().type().isTypeOfBoolean())throw error(source,clause,"BOOLEAN_TYPE_REQUIRED");
                invariant.addAnnotation(metadata(source,clause));
                rows.add(new CompiledClause(clause,invariant.bodyExpression(),invariant,null));
            } else {
                var operation=owner.operation(clause.context().operation(),false);
                requireSignature(source,clause,operation);
                boolean pre=clause.kind()==Kind.PRE;
                if(!pre&&operation.isConstructor())throw error(source,clause,"CONSTRUCTOR_POST_UNSUPPORTED");
                var expression=NativeOclCompilation.operationPredicate(model,operation,clause.name(),clause.body(),pre);
                try {
                    var condition=factory.createPrePostCondition(clause.name(),operation,pre,expression);
                    condition.addAnnotation(metadata(source,clause));rows.add(new CompiledClause(clause,expression,null,condition));
                }catch(ExpInvalidException invalid){throw error(source,clause,"NATIVE_CONDITION_REJECTED:"+invalid.getMessage());}
            }
        }
        var csl=new ArrayList<MClassInvariant>();
        for(var invariant:binding.cslModel().invariants()) {
            var existing=model.getClassInvariant(invariant.contextType()+"::"+invariant.name());
            if(existing==null)throw new IllegalArgumentException("OCL_SPEC_CANONICAL_INVARIANT_MISSING:"+invariant.name());
            var expression=NativeOclCompilation.predicate(model,model.getClass(invariant.contextType()),invariant.name(),invariant.expression());
            if(!existing.bodyExpression().toString().equals(expression.toString()))throw new IllegalArgumentException("OCL_SPEC_CANONICAL_INVARIANT_CHANGED:"+invariant.name());
            csl.add(existing);
        }
        return new Prepared(model,source,rows,csl);
    }
    private static void validateBindings(Specification source,Map<String,Context> expected) {
        if(!expected.keySet().equals(source.clauses().stream().map(Clause::name).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("OCL_SPEC_CLAUSE_INVENTORY_MISMATCH");
        for(var clause:source.clauses())if(!clause.context().equals(expected.get(clause.name())))throw error(source,clause,"CONTEXT_BINDING_MISMATCH");
    }
    private static void requireSignature(Specification source,Clause clause,MOperation operation) {
        var context=clause.context();
        if(operation==null||operation.paramList().size()!=context.parameters().size()
                ||operation.hasResultType()!=!context.resultType().isEmpty()
                ||operation.hasResultType()&&!operation.resultType().toString().equals(context.resultType()))throw error(source,clause,"OPERATION_SIGNATURE_MISMATCH");
        for(int i=0;i<context.parameters().size();i++) {
            var parameter=context.parameters().get(i);var actual=operation.paramList().varDecl(i);
            if(!parameter.name().equals(actual.name())||!parameter.type().equals(actual.type().toString()))throw error(source,clause,"OPERATION_SIGNATURE_ALIAS_MISMATCH");
        }
    }
    protected String sourceAnnotation(){return "AuthoredOclSource";}
    public static Context parseContext(String header) {
        var cursor=new Cursor(lex(header,"mapping-header"),"mapping-header");
        var result=context(cursor);if(cursor.has())throw cursor.error("OCL_OPERATION_HEADER_UNSUPPORTED");return result;
    }
    private static Context context(Cursor cursor) {
        cursor.expect("context");String owner=cursor.identifier();
        if(!cursor.is("::"))return new Context(owner,"",List.of(),"");
        cursor.take();String operation=cursor.identifier();cursor.expect("(");
        var parameters=new ArrayList<Parameter>();var names=new HashSet<String>();
        if(!cursor.is(")"))do {
            String name=cursor.identifier();cursor.expect(":");String type=cursor.identifier();
            if(!names.add(name)||Set.of("self","result").contains(name))throw cursor.error("PARAMETER_IDENTITY_INVALID");
            parameters.add(new Parameter(name,type));if(parameters.size()>16)throw cursor.error("PARAMETER_CAPACITY");
            if(!cursor.is(","))break;cursor.take();
        }while(true);
        cursor.expect(")");String result="";if(cursor.is(":")){cursor.take();result=cursor.identifier();}
        return new Context(owner,operation,parameters,result);
    }
    private static List<CommonToken> lex(String source,String name) {
        var diagnostics=new StringWriter();var handler=new ParseErrorHandler(name,new PrintWriter(diagnostics,true));
        var lexer=new GeneratorLexer(new ANTLRStringStream(source));lexer.init(handler);
        var stream=new CommonTokenStream(lexer);stream.fill();
        if(handler.errorCount()!=0)throw new IllegalArgumentException("OCL_SPEC_LEXICAL_ERROR:"+name+":"+diagnostics);
        var tokens=new ArrayList<CommonToken>();
        for(Object value:stream.getTokens()) {
            var token=(CommonToken)value;if(token.getChannel()==Token.DEFAULT_CHANNEL)tokens.add(token);
            if(tokens.size()>16384)throw new IllegalArgumentException("OCL_SPEC_TOKEN_CAPACITY");
        }
        return List.copyOf(tokens);
    }
    private static void syntax(String body,String name) {
        var diagnostics=new StringWriter();var handler=new ParseErrorHandler(name,new PrintWriter(diagnostics,true));
        var lexer=new GeneratorLexer(new ANTLRStringStream(body));lexer.init(handler);
        var parser=new GeneratorParser(new CommonTokenStream(lexer));parser.init(handler);
        try {parser.expressionOnly();}
        catch(RecognitionException|StackOverflowError invalid){throw new IllegalArgumentException("OCL_SPEC_EXPRESSION_SYNTAX_OR_CAPACITY:"+name,invalid);}
        if(handler.errorCount()!=0)throw new IllegalArgumentException("OCL_SPEC_EXPRESSION_SYNTAX:"+name+":"+diagnostics);
    }
    private static boolean matching(String left,String right){return left.equals("(")&&right.equals(")")||left.equals("[")&&right.equals("]")||left.equals("{")&&right.equals("}");}
    private static IllegalArgumentException error(Specification source,Clause clause,String code){return new IllegalArgumentException("OCL_SPEC:"+source.sourceName()+":"+clause.line()+":"+clause.column()+":"+clause.name()+":"+code);}
    private MElementAnnotation metadata(Specification source,Clause clause) {
        return new MElementAnnotation(sourceAnnotation(),Map.of("sourceHash",source.sourceHash(),"source64",Use75CslModelBackend.encode(source.sourceName()),
                "clause64",Use75CslModelBackend.encode(clause.name()),"kind",clause.kind().name(),"line",Integer.toString(clause.line()),
                "bodyStart",Integer.toString(clause.bodyStart()),"bodyEnd",Integer.toString(clause.bodyEnd())));
    }
    private static final class Cursor {
        private final List<CommonToken> tokens;private final String name;private int at;
        Cursor(List<CommonToken> tokens,String name){this.tokens=tokens;this.name=name;}
        boolean has(){return at<tokens.size()&&peek().getType()!=Token.EOF;}
        CommonToken peek(){return tokens.get(Math.min(at,tokens.size()-1));}
        boolean is(String text){return has()&&peek().getText().equals(text);}
        CommonToken take(){if(!has())throw error("UNEXPECTED_EOF");return tokens.get(at++);}
        CommonToken expect(String text){if(!is(text))throw error("EXPECTED:"+text);return take();}
        String identifier(){var token=take();if(token.getType()!=GeneratorLexer.IDENT)throw error("IDENTIFIER_REQUIRED");return token.getText();}
        boolean clauseStart(){return at+2<tokens.size()&&Set.of("inv","pre","post").contains(peek().getText())
                &&tokens.get(at+1).getType()==GeneratorLexer.IDENT&&tokens.get(at+2).getText().equals(":");}
        IllegalArgumentException error(String code){return new IllegalArgumentException("OCL_SPEC:"+name+":"+peek().getLine()+":"+(peek().getCharPositionInLine()+1)+":"+code);}
    }
}
