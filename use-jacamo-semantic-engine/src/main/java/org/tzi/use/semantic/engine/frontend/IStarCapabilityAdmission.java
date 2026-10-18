package org.tzi.use.semantic.engine.frontend;

import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.*;
import org.tzi.use.semantic.engine.api.EngineDiagnostic;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.vnu.sme.goal.dsl.istar.parser.*;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Generated parse-tree admission before the original builder can discard or throw on a feature. */
public final class IStarCapabilityAdmission {
    /** Offsets use the ANTLR CharStream's Unicode code-point indexing, not UTF-16 indices. */
    public record SourceSpan(int startOffset, int endOffset, int line, int column, String sourceText) { }
    public record Finding(EngineDiagnostic diagnostic, SourceSpan span) { }
    public record Scan(IStarParser.ModelContext tree, List<EngineDiagnostic> diagnostics, List<Finding> findings) {
        public Scan { diagnostics=List.copyOf(diagnostics);findings=List.copyOf(findings); }
    }
    public Scan scan(String source, String sourceName) {
        var input=CharStreams.fromString(Objects.requireNonNull(source));
        Objects.requireNonNull(sourceName);
        var diagnostics=new ArrayList<EngineDiagnostic>();var findings=new ArrayList<Finding>();
        var listener=new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?,?> recognizer,Object token,int line,int column,String message,RecognitionException error) {
                diagnostics.add(new EngineDiagnostic(Stage.PARSE,Kind.ERROR,"ORIGINAL_ISTAR_SYNTAX",sourceName,line,column,
                        "line "+line+":"+column+" "+message));
            }
        };
        var lexer=new IStarLexer(input);lexer.removeErrorListeners();lexer.addErrorListener(listener);
        var parser=new IStarParser(new CommonTokenStream(lexer));parser.removeErrorListeners();parser.addErrorListener(listener);
        var tree=parser.model();
        if(diagnostics.isEmpty()) {
            walk(tree,input,sourceName,findings);
            if(findings.isEmpty())checkDefinitionBounds(tree,input,sourceName,findings);
        }
        findings.forEach(f->diagnostics.add(f.diagnostic()));
        return new Scan(tree,diagnostics,findings);
    }
    private void walk(ParseTree node,CharStream input,String name,List<Finding> output) {
        if(node instanceof IStarParser.ModelContext model && model.stringLit()!=null)
            reject(model.stringLit(),input,name,"UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE","Model header text is not stored by the original model",output);
        if(node instanceof IStarParser.ActorDefContext actor && actor.stringLit()!=null)
            reject(actor.stringLit(),input,name,"UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE","Actor header text is not stored by the original model",output);
        if(node instanceof IStarParser.DescPropertyContext context)
            reject(context,input,name,"UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE","Description is not stored by the original builder",output);
        if(node instanceof IStarParser.CondLangContext context)
            reject(context,input,name,"UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE","Explicit condition language is not stored; only the default OCL slot is admitted",output);
        if(node instanceof IStarParser.ActivationClauseContext context)
            reject(context,input,name,"DECLARED_GOAL_ACTIVATION","Declared activation is absent from the current original Goal implementation",output);
        if(node instanceof IStarParser.RelForAllContext context)
            reject(context,input,name,"FORALL_REFINEMENT","Original builder has no forall dispatch/model implementation",output);
        if(node instanceof IStarParser.RelPickContext context)
            reject(context,input,name,"PICK_REFINEMENT","Original builder has no pick dispatch/model implementation",output);
        if(node instanceof IStarParser.ActorAssociationStmtContext context)
            reject(context,input,name,"TOP_LEVEL_ACTOR_ASSOCIATION_NOT_BUILT","Original builder ignores top-level actor associations",output);
        if(node instanceof TerminalNode terminal && (terminal.getSymbol().getType()==IStarLexer.OCL_BLOCK || terminal.getSymbol().getType()==IStarLexer.OCL_CLAUSE)) {
            String raw=terminal.getText();String body;
            if(raw.startsWith("ocl:"))body=raw.substring(4,raw.length()-1).trim();
            else {int begin=raw.indexOf("{[");int end=raw.lastIndexOf("]}");body=begin>=0&&end>=begin?raw.substring(begin+2,end).trim():"";}
            if(body.isEmpty()) {
                var token=terminal.getSymbol();var diagnostic=new EngineDiagnostic(Stage.MODEL_VALIDATION,Kind.ERROR,"EMPTY_ISTAR_OCL_CONTRACT",name,
                        token.getLine(),token.getCharPositionInLine(),"Empty authored contract would be discarded by the original visitor");
                output.add(new Finding(diagnostic,span(token,token,input)));
            }
        }
        for(int i=0;i<node.getChildCount();i++)walk(node.getChild(i),input,name,output);
    }
    private void reject(ParserRuleContext context,CharStream input,String name,String code,String message,List<Finding> output) {
        var token=context.getStart();var location=span(token,context.getStop(),input);
        output.add(new Finding(new EngineDiagnostic(Stage.CAPABILITY,Kind.UNSUPPORTED,code,name,token.getLine(),token.getCharPositionInLine(),message),location));
    }
    private static SourceSpan span(Token start,Token stop,CharStream input) {
        int from=start.getStartIndex(),through=stop.getStopIndex();
        return new SourceSpan(from,through+1,start.getLine(),start.getCharPositionInLine(),input.getText(Interval.of(from,through)));
    }
    /** Protects the original validator's recursive DFS before its invocation. Small invalid
     * graphs still receive exact original diagnostics; oversized/unbounded inputs are gated.
     */
    private void checkDefinitionBounds(IStarParser.ModelContext tree,CharStream input,String name,List<Finding> output) {
        var limits=EngineSemanticsProfile.USE4GOAL_OBSERVED_V1;
        var nodes=new HashSet<String>();var edges=new HashMap<String,Set<String>>();int count=0,edgeCount=0;
        for(var actor:tree.actorDef()) {
            nodes.add("actor:"+actor.IDENT().getText());
            for(var body:actor.actorBody()) {
                if(body instanceof IStarParser.BodyIsAContext association) {
                    edges.computeIfAbsent("actor:"+association.IDENT(0).getText(),k->new HashSet<>()).add("actor:"+association.IDENT(1).getText());edgeCount++;
                } else if(body instanceof IStarParser.BodyParticipatesContext association) {
                    edges.computeIfAbsent("actor:"+association.IDENT(0).getText(),k->new HashSet<>()).add("actor:"+association.IDENT(1).getText());edgeCount++;
                } else {
                    count++;String child="element:"+body.getToken(IStarParser.IDENT,0).getText();nodes.add(child);
                    for(ParseTree node:body.children)if(node instanceof IStarParser.RelAndContext relation) {
                        edges.computeIfAbsent(child,k->new HashSet<>()).add("element:"+relation.target.getText());edgeCount++;
                    } else if(node instanceof IStarParser.RelOrContext relation) {
                        edges.computeIfAbsent(child,k->new HashSet<>()).add("element:"+relation.target.getText());edgeCount++;
                    }
                }
            }
        }
        if(count>limits.maxOccurrences()||tree.actorDef().size()>limits.maxOccurrences()||edgeCount>limits.maxDefinitionEdges()) {
            reject(tree,input,name,"ENGINE_GRAPH_SIZE_LIMIT","Definition exceeds the bounded validator profile",output);return;
        }
        var degree=new HashMap<String,Integer>();nodes.forEach(n->degree.put(n,0));edges.values().forEach(set->set.forEach(n->degree.merge(n,1,Integer::sum)));
        var queue=new ArrayDeque<String>();var depth=new HashMap<String,Integer>();degree.forEach((n,d)->{if(d==0)queue.add(n);});int seen=0;
        while(!queue.isEmpty()) {
            String node=queue.remove();seen++;int current=depth.getOrDefault(node,1);
            if(current>limits.maxDefinitionDepth()){reject(tree,input,name,"ENGINE_GRAPH_DEPTH_LIMIT","Original recursive validator depth is bounded",output);return;}
            for(String target:edges.getOrDefault(node,Set.of())){depth.merge(target,current+1,Math::max);if(degree.merge(target,-1,Integer::sum)==0)queue.add(target);}
        }
        if(degree.size()-seen>limits.maxDefinitionDepth())reject(tree,input,name,"ENGINE_GRAPH_DEPTH_LIMIT","Unprocessed cyclic validator graph exceeds the depth bound",output);
    }
}
