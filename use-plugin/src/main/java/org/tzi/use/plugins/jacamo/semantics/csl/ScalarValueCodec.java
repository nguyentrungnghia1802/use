package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;
import org.tzi.use.uml.ocl.value.*;

/** Strict scalar projection. Observed Integer does not silently widen a declared Integer slot to Real. */
public final class ScalarValueCodec {
    private ScalarValueCodec() { }
    public static void requireType(String type) {
        if (!Set.of("Integer", "Real", "Boolean", "String").contains(type))
            throw new IllegalArgumentException("SCALAR_TYPE_UNSUPPORTED:" + type);
    }
    public static Value decode(String declared, List<?> values, List<?> javaTypes) {
        requireType(declared);
        if (values.size() != 1 || javaTypes.size() != 1)
            throw new IllegalArgumentException("OBS_PROPERTY_NON_SCALAR");
        if (!(values.getFirst() instanceof String text) || !(javaTypes.getFirst() instanceof String actual))
            throw new IllegalArgumentException("OBS_PROPERTY_TYPED_TEXT_REQUIRED");
        return switch (declared) {
            case "Integer" -> {
                if (!Set.of("java.lang.Byte", "java.lang.Short", "java.lang.Integer", "byte", "short", "int").contains(actual))
                    throw new IllegalArgumentException("OBS_PROPERTY_TYPE_MISMATCH:Integer:" + actual);
                yield IntegerValue.valueOf(Integer.parseInt(text));
            }
            case "Real" -> {
                if (!Set.of("java.lang.Float", "java.lang.Double", "float", "double").contains(actual))
                    throw new IllegalArgumentException("OBS_PROPERTY_TYPE_MISMATCH:Real:" + actual);
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("OBS_PROPERTY_REAL_NONFINITE");
                yield new RealValue(value);
            }
            case "Boolean" -> {
                if (!Set.of("java.lang.Boolean", "boolean").contains(actual) || !Set.of("true", "false").contains(text))
                    throw new IllegalArgumentException("OBS_PROPERTY_BOOLEAN_INVALID");
                yield BooleanValue.get(Boolean.parseBoolean(text));
            }
            case "String" -> {
                if (!Set.of("java.lang.String", "java.lang.Character", "char").contains(actual))
                    throw new IllegalArgumentException("OBS_PROPERTY_TYPE_MISMATCH:String:" + actual);
                yield new StringValue(text);
            }
            default -> throw new IllegalArgumentException("SCALAR_TYPE_UNSUPPORTED:" + declared);
        };
    }
}
