package org.tzi.use.semantic.engine.use75;

import java.math.*;
import java.util.*;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.*;
import org.vnu.sme.goal.dsl.csl.mm.*;

/** Exact scalar codecs. Observation quality belongs to the caller, not to null/default conversion. */
public final class Use75ValueCodec {
    public Set<String> supportedTypes(CslModel model) {
        var types = new HashSet<>(Set.of("String", "Integer", "Real", "Boolean"));
        model.enums().forEach(e -> types.add(e.name())); return Set.copyOf(types);
    }
    public Value decode(CslDataType type, Object observed, MModel model) {
        if (type instanceof CslNamedDataType) throw new IllegalArgumentException("UNSUPPORTED:EXACT_RUNTIME_CODEC_REQUIRED:" + type.sourceName());
        if (observed == null) return UndefinedValue.instance;
        if (type instanceof CslEnum enumeration) {
            if (!(observed instanceof String literal)) throw mismatch(type, observed);
            var nativeType = model.enumType(enumeration.name());
            if (nativeType == null) throw new IllegalArgumentException("ENUM_MODEL_IDENTITY_MISSING:" + enumeration.name());
            return new EnumValue(nativeType, literal);
        }
        if (!(type instanceof CslPrimitiveType primitive)) throw new IllegalArgumentException("UNSUPPORTED:CSL_VALUE_TYPE:" + type.sourceName());
        return switch (primitive) {
            case STRING -> { if (!(observed instanceof String text)) throw mismatch(type, observed); yield new StringValue(text); }
            case BOOLEAN -> { if (!(observed instanceof Boolean truth)) throw mismatch(type, observed); yield BooleanValue.get(truth); }
            case INTEGER -> {
                if (!(observed instanceof Byte || observed instanceof Short || observed instanceof Integer || observed instanceof Long || observed instanceof BigInteger)) throw mismatch(type, observed);
                long integer = observed instanceof BigInteger big ? big.longValueExact() : ((Number) observed).longValue();
                yield IntegerValue.valueOf(Math.toIntExact(integer));
            }
            case REAL -> {
                if (!(observed instanceof Number number)) throw mismatch(type, observed);
                double value = number.doubleValue();
                if (!Double.isFinite(value)) throw mismatch(type, observed);
                if (number instanceof BigDecimal decimal && BigDecimal.valueOf(value).compareTo(decimal) != 0)
                    throw new IllegalArgumentException("REAL_PRECISION_UNREPRESENTABLE:" + decimal);
                if (number instanceof BigInteger big && BigDecimal.valueOf(value).compareTo(new BigDecimal(big)) != 0)
                    throw new IllegalArgumentException("REAL_PRECISION_UNREPRESENTABLE:" + big);
                if (number instanceof Long integer && BigDecimal.valueOf(value).compareTo(BigDecimal.valueOf(integer)) != 0)
                    throw new IllegalArgumentException("REAL_PRECISION_UNREPRESENTABLE:" + integer);
                yield new RealValue(value);
            }
        };
    }
    private static IllegalArgumentException mismatch(CslDataType type, Object value) {
        return new IllegalArgumentException("CSL_TYPED_VALUE_MISMATCH:" + type.sourceName() + ":" + value.getClass().getName());
    }
}
