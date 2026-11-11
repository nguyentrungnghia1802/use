package org.vnu.sme.goal.trace.cslTrace.nativecsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.vnu.sme.goal.dsl.csl.ocl.CslOclState;

/** Side-effect-free OCL subset evaluated directly over an CSL object state. */
public final class NativeOclEvaluator {
    private NativeOclEvaluator() {}

    /** Parses one native CSL/OCL expression without requiring a system state. */
    public static void validate(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("CSL/OCL expression must not be empty");
        }
        new Parser(source).parse();
    }

    public static boolean evaluate(String source, CslOclState snapshot, List<?> context) {
        if (source == null || source.isBlank()) return false;
        Map<String, Object> variables = new LinkedHashMap<>();
        // Context paths are stored from the outermost occurrence to the innermost
        // occurrence (for example [Initiator, Organizer, Participant]).  CSL/iStar
        // OCL, however, defines self as the current/innermost occurrence.
        if (!context.isEmpty()) variables.put("self", context.get(context.size() - 1));
        for (int depth = 1; depth < context.size(); depth++) {
            variables.put("$outer" + depth, context.get(context.size() - 1 - depth));
        }
        return Boolean.TRUE.equals(new Parser(source).parse().eval(new Env(snapshot, variables)));
    }

    private record Env(CslOclState snapshot, Map<String,Object> vars) {
        Env with(String name, Object value) {
            Map<String,Object> copy = new LinkedHashMap<>(vars); copy.put(name, value);
            return new Env(snapshot, copy);
        }
    }
    @FunctionalInterface private interface Expr { Object eval(Env env); }
    private enum T { ID, STRING, NUMBER, TRUE, FALSE, AND, OR, NOT, IMPLIES,
                     EQ, NE, LT, LE, GT, GE, DOT, ARROW,
                     LP, RP, BAR, HASH, COLON2, EOF }
    private record Token(T type, String text) {}

    private static final class Lexer {
        private final String input; private int at;
        Lexer(String input) { this.input = input; }
        List<Token> tokens() { List<Token> out = new ArrayList<>(); Token t; do { out.add(t=next()); } while(t.type()!=T.EOF); return out; }
        private Token next() {
            while (at<input.length() && Character.isWhitespace(input.charAt(at))) at++;
            if (at>=input.length()) return new Token(T.EOF,"");
            char c=input.charAt(at);
            if(c=='\''){int s=++at; while(at<input.length()&&input.charAt(at)!='\'')at++; String v=input.substring(s,at); if(at<input.length())at++; return new Token(T.STRING,v);}
            if(Character.isDigit(c)||(c=='-'&&at+1<input.length()&&Character.isDigit(input.charAt(at+1)))){int s=at++;while(at<input.length()&&(Character.isDigit(input.charAt(at))||input.charAt(at)=='.'))at++;return new Token(T.NUMBER,input.substring(s,at));}
            if(Character.isLetter(c)||c=='_'){int s=at++; while(at<input.length()&&(Character.isLetterOrDigit(input.charAt(at))||input.charAt(at)=='_'))at++; String id=input.substring(s,at); return new Token(switch(id.toLowerCase()){case"true"->T.TRUE;case"false"->T.FALSE;case"and"->T.AND;case"or"->T.OR;case"not"->T.NOT;case"implies"->T.IMPLIES;default->T.ID;},id);}
            at++;
            return switch(c){case'.'->new Token(T.DOT,".");case'('->new Token(T.LP,"(");case')'->new Token(T.RP,")");case'|'->new Token(T.BAR,"|");case'#'->new Token(T.HASH,"#");case'='->new Token(T.EQ,"=");
                case'<'->{if(at<input.length()&&input.charAt(at)=='>'){at++;yield new Token(T.NE,"<>");}if(at<input.length()&&input.charAt(at)=='='){at++;yield new Token(T.LE,"<=");}yield new Token(T.LT,"<");}
                case'>'->{if(at<input.length()&&input.charAt(at)=='='){at++;yield new Token(T.GE,">=");}yield new Token(T.GT,">");}
                case'-'->{if(at<input.length()&&input.charAt(at)=='>')at++;yield new Token(T.ARROW,"->");}
                case':'->{if(at<input.length()&&input.charAt(at)==':'){at++;yield new Token(T.COLON2,"::");}throw error("unexpected ':'");}
                default->throw error("unexpected character '"+c+"'");};
        }
        private IllegalArgumentException error(String m){return new IllegalArgumentException("native OCL at column "+at+": "+m);}
    }

    private static final class Parser {
        private final List<Token> tokens; private int at;
        Parser(String source){tokens=new Lexer(source).tokens();}
        Expr parse(){Expr e=implies();expect(T.EOF);return e;}
        private Expr implies(){Expr left=or();if(accept(T.IMPLIES)){Expr right=implies();return e->!bool(left.eval(e))||bool(right.eval(e));}return left;}
        private Expr or(){Expr l=and();while(accept(T.OR)){Expr a=l,b=and();l=e->bool(a.eval(e))||bool(b.eval(e));}return l;}
        private Expr and(){Expr l=equality();while(accept(T.AND)){Expr a=l,b=equality();l=e->bool(a.eval(e))&&bool(b.eval(e));}return l;}
        private Expr equality(){Expr l=comparison();while(peek(T.EQ)||peek(T.NE)){T op=take().type();Expr a=l,b=comparison();l=e->{boolean same=same(a.eval(e),b.eval(e),e.snapshot());return op==T.EQ?same:!same;};}return l;}
        private Expr comparison(){Expr l=unary();while(peek(T.LT)||peek(T.LE)||peek(T.GT)||peek(T.GE)){T op=take().type();Expr a=l,b=unary();l=e->compare(a.eval(e),b.eval(e),op);}return l;}
        private Expr unary(){if(accept(T.NOT)){Expr x=unary();return e->!bool(x.eval(e));}return navigation(primary());}
        private Expr primary(){
            if(accept(T.TRUE))return e->true;if(accept(T.FALSE))return e->false;
            if(peek(T.STRING)){String v=take().text();return e->v;}
            if(peek(T.NUMBER)){String v=take().text();return e->v.contains(".")?Double.parseDouble(v):Long.parseLong(v);}
            if(accept(T.HASH)){String v=expect(T.ID).text();return e->v;}
            if(accept(T.LP)){Expr x=implies();expect(T.RP);return x;}
            String id=expect(T.ID).text();
            if(peek(T.COLON2)){take();String literal=expect(T.ID).text();return e->literal;}
            if(id.equals("self")){int depth=0;while(peek(T.DOT)&&peek(1,T.ID)&&tokens.get(at+1).text().equals("outer")){take();take();depth++;}String name=depth==0?"self":"$outer"+depth;return e->e.vars().get(name);}
            return e->e.vars().getOrDefault(id,id);
        }
        private Expr navigation(Expr base){Expr value=base;while(true){
            if(accept(T.DOT)){String property=expect(T.ID).text();Expr old=value;value=e->e.snapshot().property(old.eval(e),property);continue;}
            if(!accept(T.ARROW))return value;String op=expect(T.ID).text();expect(T.LP);
            if(op.equals("isEmpty")||op.equals("notEmpty")||op.equals("size")){expect(T.RP);Expr old=value;value=e->{int size=list(old.eval(e)).size();return op.equals("size")?size:op.equals("isEmpty")?size==0:size>0;};continue;}
            if(op.equals("includes")){Expr item=implies();expect(T.RP);Expr old=value;value=e->list(old.eval(e)).stream().anyMatch(x->same(x,item.eval(e),e.snapshot()));continue;}
            if(op.equals("forAll")||op.equals("exists")){String var=expect(T.ID).text();expect(T.BAR);Expr body=implies();expect(T.RP);Expr old=value;value=e->{List<?> xs=list(old.eval(e));if(op.equals("forAll")){for(Object x:xs)if(!bool(body.eval(e.with(var,x))))return false;return true;}for(Object x:xs)if(bool(body.eval(e.with(var,x))))return true;return false;};continue;}
            throw new IllegalArgumentException("unsupported native OCL operation '"+op+"'");
        }}
        private boolean peek(T t){return tokens.get(at).type()==t;} private boolean peek(int d,T t){return at+d<tokens.size()&&tokens.get(at+d).type()==t;}
        private boolean accept(T t){if(!peek(t))return false;at++;return true;} private Token take(){return tokens.get(at++);}
        private Token expect(T t){Token x=take();if(x.type()!=t)throw new IllegalArgumentException("native OCL: expected "+t+" but found '"+x.text()+"'");return x;}
    }

    private static boolean bool(Object v){return Boolean.TRUE.equals(v);}
    private static boolean compare(Object left,Object right,T operation){
        int result;
        if(left instanceof Number a&&right instanceof Number b)result=Double.compare(a.doubleValue(),b.doubleValue());
        else result=String.valueOf(normalize(left)).compareTo(String.valueOf(normalize(right)));
        return switch(operation){case LT->result<0;case LE->result<=0;case GT->result>0;case GE->result>=0;default->false;};
    }
    private static List<?> list(Object v){return v instanceof List<?> xs?xs:v==null?List.of():List.of(v);}
    private static boolean same(Object a,Object b,CslOclState state){
        if(a!=null&&b!=null&&a.getClass().equals(b.getClass())&&!isLiteral(a))
            return state.identity(a).equals(state.identity(b));
        return Objects.equals(normalize(a),normalize(b));
    }
    private static boolean isLiteral(Object value){return value instanceof String||value instanceof Number||value instanceof Boolean||value instanceof Enum<?>;}
    private static Object normalize(Object v){return v==null?null:String.valueOf(v).replaceFirst("^.*::","");}
}
