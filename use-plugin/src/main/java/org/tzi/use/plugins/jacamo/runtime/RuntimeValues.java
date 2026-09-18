package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.uml.ocl.value.*;

/** Lossless scalar conversion; malformed inputs never become plausible default values. */
public final class RuntimeValues {
    private RuntimeValues() { }
    public static Value convert(String type, Object raw) {
        if (type.equals("UNDEFINED")) return UndefinedValue.instance;
        if (raw == null) throw new IllegalArgumentException("RUNTIME_VALUE_NULL");
        return switch (type) {
            case "BOOLEAN", "Boolean" -> {
                if (raw instanceof Boolean value) yield BooleanValue.get(value);
                if (raw.equals("true") || raw.equals("false")) yield BooleanValue.get(Boolean.parseBoolean((String) raw));
                throw new IllegalArgumentException("RUNTIME_BOOLEAN_INVALID");
            }
            case "INTEGER", "Integer" -> IntegerValue.valueOf(new java.math.BigDecimal(raw.toString()).intValueExact());
            case "REAL", "Real" -> {
                double value = Double.parseDouble(raw.toString());
                if (!Double.isFinite(value)) throw new IllegalArgumentException("RUNTIME_REAL_INVALID");
                yield new RealValue(value);
            }
            case "STRING", "String" -> {
                if (!(raw instanceof String value)) throw new IllegalArgumentException("RUNTIME_STRING_INVALID");
                yield new StringValue(value);
            }
            default -> throw new IllegalArgumentException("RUNTIME_VALUE_TYPE_UNSUPPORTED:" + type);
        };
    }
}
