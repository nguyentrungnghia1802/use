package org.tzi.use.plugins.jacamo.constraint;

import java.util.List;

/** Typed verification expression IR; it is independent of USE parser classes. */
public sealed interface Expression permits Expression.Literal, Expression.VariableRef, Expression.PropertyRef,
        Expression.UnaryOp, Expression.BinaryOp, Expression.Call, Expression.CollectionPredicate, Expression.Unknown {
    record Literal(String text, ValueType type) implements Expression { }
    record VariableRef(String name, ValueType type) implements Expression { }
    record PropertyRef(Expression receiver, String property, ValueType type, boolean pre) implements Expression { }
    record UnaryOp(String operator, Expression operand, ValueType type) implements Expression { }
    record BinaryOp(Expression left, String operator, Expression right, ValueType type) implements Expression { }
    record Call(String name, List<Expression> arguments, ValueType type) implements Expression {
        public Call { arguments = List.copyOf(arguments); }
    }
    record CollectionPredicate(Expression source, String operation, String variable, Expression body,
                               ValueType type) implements Expression { }
    record Unknown(String source, String reason) implements Expression { }
    enum ValueType { BOOLEAN, INTEGER, REAL, STRING, OBJECT, COLLECTION, UNKNOWN }
}
