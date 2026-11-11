package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.math.*;
import java.util.*;
import org.tzi.use.uml.ocl.value.*;

/** Explicit typed numeric widening. No clamping, stringification or sentinels. */
public final class CompactValueConverters {
    public static final String NUMBER_TO_REAL="JAVA_SCALAR_NUMBER_TO_REAL/1.0";
    public static Value real(List<?> values,List<?> types) {
        if(values.size()!=1||types.size()!=1||!(values.getFirst() instanceof String text)||!(types.getFirst() instanceof String type))throw new IllegalArgumentException("COMPACT_NUMERIC_SCALAR_REQUIRED");
        double value;
        switch(type) {
            case "java.lang.Byte","byte","java.lang.Short","short","java.lang.Integer","int","java.lang.Long","long" -> {
                var number=new BigInteger(text);
                if(!number.toString().equals(text))throw new IllegalArgumentException("COMPACT_NUMERIC_INTEGER_ENCODING_REQUIRED");
                int bits=switch(type){case "java.lang.Byte","byte"->8;case "java.lang.Short","short"->16;case "java.lang.Integer","int"->32;default->64;};
                if(number.bitLength()>bits-1)throw new IllegalArgumentException("COMPACT_NUMERIC_INTEGER_RANGE");
                value=number.doubleValue();if(!new BigDecimal(value).toBigIntegerExact().equals(number))throw new IllegalArgumentException("COMPACT_NUMERIC_WIDENING_NOT_EXACT");
            }
            case "java.lang.Float","float" -> {float raw=Float.parseFloat(text);if(!Float.toString(raw).equals(text))throw new IllegalArgumentException("COMPACT_BINARY32_ENCODING_REQUIRED");value=raw;}
            case "java.lang.Double","double" -> {value=Double.parseDouble(text);if(!Double.toString(value).equals(text))throw new IllegalArgumentException("COMPACT_BINARY64_ENCODING_REQUIRED");}
            default->throw new IllegalArgumentException("COMPACT_NUMERIC_JAVA_TYPE_UNSUPPORTED:"+type);
        }
        if(!Double.isFinite(value))throw new IllegalArgumentException("COMPACT_NUMERIC_FINITE_VALUE_REQUIRED");
        return new RealValue(value);
    }
    private CompactValueConverters(){ }
}
