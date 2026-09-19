package org.tzi.use.plugins.jacamo.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small deterministic parser for the explicitly supported boolean/arithmetic subset. */
public final class ConstraintExpressionParser {
    public Result parse(String source, TypeEnvironment environment) {
        try {
            Parser parser = new Parser(source, environment);
            Expression expression = parser.expression();
            if (!parser.peek().equals("<eof>")) return unsupported(source, "unexpected token " + parser.peek());
            validate(expression);
            return new Result(expression, TranslationStatus.EXACT, List.of());
        } catch (IllegalArgumentException exception) {
            return unsupported(source, exception.getMessage());
        }
    }

    /** Validates the complete tree: unknown children must never hide behind a Boolean parent. */
    public static Expression.ValueType validate(Expression expression) {
        if (expression instanceof Expression.Unknown unknown) throw new IllegalArgumentException(unknown.reason());
        if (expression instanceof Expression.Literal literal) return literal.type();
        if (expression instanceof Expression.VariableRef variable) {
            if (variable.type()==Expression.ValueType.UNKNOWN) throw new IllegalArgumentException("unknown variable type");
            return variable.type();
        }
        if (expression instanceof Expression.PropertyRef property) { validate(property.receiver()); return property.type(); }
        if (expression instanceof Expression.UnaryOp unary) {
            var operand=validate(unary.operand());
            if (unary.operator().equals("not") && operand==Expression.ValueType.BOOLEAN) return Expression.ValueType.BOOLEAN;
            if (unary.operator().equals("-") && operand==Expression.ValueType.INTEGER) return operand;
            throw new IllegalArgumentException("unsupported unary operand type");
        }
        if (expression instanceof Expression.BinaryOp binary) {
            var left=validate(binary.left());var right=validate(binary.right());
            if (left==Expression.ValueType.UNKNOWN || right==Expression.ValueType.UNKNOWN || left!=right)
                throw new IllegalArgumentException("incompatible operand types");
            switch(binary.operator()) {
                case "and", "or" -> { if(left==Expression.ValueType.BOOLEAN) return Expression.ValueType.BOOLEAN; }
                case "=", "<>" -> { if(left==Expression.ValueType.BOOLEAN || left==Expression.ValueType.INTEGER || left==Expression.ValueType.STRING) return Expression.ValueType.BOOLEAN; }
                case ">", "<", ">=", "<=" -> { if(left==Expression.ValueType.INTEGER) return Expression.ValueType.BOOLEAN; }
                case "+", "-", "*" -> { if(left==Expression.ValueType.INTEGER) return Expression.ValueType.INTEGER; }
                default -> throw new IllegalArgumentException("operator semantics unsupported: "+binary.operator());
            }
            throw new IllegalArgumentException("unsupported binary operand type");
        }
        throw new IllegalArgumentException("unsupported expression semantics");
    }

    private Result unsupported(String source, String reason) {
        return new Result(new Expression.Unknown(source, reason), TranslationStatus.UNSUPPORTED, List.of(reason));
    }

    public record Result(Expression expression, TranslationStatus status, List<String> unsupportedReasons) {
        public Result { unsupportedReasons = List.copyOf(unsupportedReasons); }
    }

    private static final class Parser {
        private final List<String> tokens;
        private final TypeEnvironment environment;
        private int cursor;
        Parser(String source, TypeEnvironment environment) { this.tokens = tokenize(source); this.environment = environment; }
        String peek() { return cursor < tokens.size() ? tokens.get(cursor) : "<eof>"; }
        String take() { String value = peek(); cursor++; return value; }
        boolean accept(String... values) {
            for (String value : values) if (peek().equals(value)) { cursor++; return true; }
            return false;
        }
        Expression expression() { return logicalOr(); }
        Expression logicalOr() {
            Expression left = logicalAnd();
            while (accept("|", "||", "or")) left = new Expression.BinaryOp(left, "or", logicalAnd(), Expression.ValueType.BOOLEAN);
            return left;
        }
        Expression logicalAnd() {
            Expression left = comparison();
            while (accept("&", "&&", "and")) left = new Expression.BinaryOp(left, "and", comparison(), Expression.ValueType.BOOLEAN);
            return left;
        }
        Expression comparison() {
            Expression left = additive();
            if (List.of("=", "==", "!=", "<>", ">", "<", ">=", "<=").contains(peek())) {
                String op = take(); if (op.equals("==")) op = "="; if (op.equals("!=")) op = "<>";
                return new Expression.BinaryOp(left, op, additive(), Expression.ValueType.BOOLEAN);
            }
            return left;
        }
        Expression additive() {
            Expression left = multiplicative();
            while (peek().equals("+") || peek().equals("-"))
                left = new Expression.BinaryOp(left, take(), multiplicative(), Expression.ValueType.INTEGER);
            return left;
        }
        Expression multiplicative() {
            Expression left = unary();
            while (peek().equals("*") || peek().equals("/"))
                left = new Expression.BinaryOp(left, take(), unary(), Expression.ValueType.INTEGER);
            return left;
        }
        Expression unary() {
            if (accept("not", "!")) return new Expression.UnaryOp("not", unary(), Expression.ValueType.BOOLEAN);
            if (accept("-")) return new Expression.UnaryOp("-", unary(), Expression.ValueType.INTEGER);
            return primary();
        }
        Expression primary() {
            if (accept("(")) { Expression value = expression(); if (!accept(")")) throw new IllegalArgumentException("missing )"); return value; }
            String token = take();
            if (token.equals("<eof>")) throw new IllegalArgumentException("unexpected end of expression");
            if (token.matches("-?\\d+")) return new Expression.Literal(token, Expression.ValueType.INTEGER);
            if (token.equals("true") || token.equals("false")) return new Expression.Literal(token, Expression.ValueType.BOOLEAN);
            if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'")))
                return new Expression.Literal(token, Expression.ValueType.STRING);
            if (accept("(")) {
                List<Expression> args = new ArrayList<>();
                if (!accept(")")) { do { args.add(expression()); } while (accept(",")); if (!accept(")")) throw new IllegalArgumentException("missing )"); }
                return new Expression.Unknown(token, "call semantics are not bound: " + token);
            }
            if (environment.variables().containsKey(token) && environment.properties().containsKey(token))
                throw new IllegalArgumentException("ambiguous variable/property binding: "+token);
            TypeEnvironment.PropertyBinding property = environment.properties().get(token);
            if (property != null) return navigation(property);
            Expression.ValueType type = environment.variables().get(token);
            if (type != null) return new Expression.VariableRef(token, type);
            return new Expression.Unknown(token, "unresolved identifier: " + token);
        }
        private Expression navigation(TypeEnvironment.PropertyBinding property) {
            String[] parts = property.oclNavigation().split("\\.");
            Expression value = new Expression.VariableRef(parts[0], Expression.ValueType.OBJECT);
            for (int i = 1; i < parts.length; i++)
                value = new Expression.PropertyRef(value, parts[i], i == parts.length - 1 ? property.type() : Expression.ValueType.OBJECT, false);
            return value;
        }
        private static List<String> tokenize(String source) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < source.length();) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '\'' || c == '"') {
                    int end = i + 1; while (end < source.length() && source.charAt(end) != c) end++;
                    if (end >= source.length()) throw new IllegalArgumentException("unterminated string");
                    result.add(source.substring(i, ++end)); i = end; continue;
                }
                if (Character.isLetter(c) || c == '_') {
                    int end = i + 1; while (end < source.length() && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) end++;
                    result.add(source.substring(i, end)); i = end; continue;
                }
                if (Character.isDigit(c)) {
                    int end = i + 1; while (end < source.length() && Character.isDigit(source.charAt(end))) end++;
                    result.add(source.substring(i, end)); i = end; continue;
                }
                String pair = i + 1 < source.length() ? source.substring(i, i + 2) : "";
                if (List.of(">=", "<=", "!=", "==", "<>", "&&", "||").contains(pair)) { result.add(pair); i += 2; }
                else { result.add(Character.toString(c)); i++; }
            }
            return result;
        }
    }
}
