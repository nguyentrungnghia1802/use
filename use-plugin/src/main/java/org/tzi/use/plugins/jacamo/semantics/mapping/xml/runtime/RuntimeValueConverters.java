package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.uml.ocl.value.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
/** Strict shared M0 codecs. No clamping, sentinel/default substitution or Boolean coercion. */
public final class RuntimeValueConverters {
    public static final class Unavailable extends RuntimeException {public Unavailable(String reason){super(reason);}}
    private RuntimeValueConverters(){ }
    public static Value decode(Node converter,Object value) {
        try {
            return switch(converter.attr("algorithm")) {
                case "identity" -> {if(!(value instanceof Boolean bool))throw new Unavailable("BOOLEAN_REQUIRED");yield BooleanValue.get(bool);}
                case "preserve-exact-content" -> {if(!(value instanceof String text))throw new Unavailable("STRING_REQUIRED");yield new StringValue(text);}
                case "preserve-exact-integer" -> {
                    if(!(value instanceof Number number))throw new Unavailable("INTEGER_REQUIRED");
                    try{yield IntegerValue.valueOf(new java.math.BigDecimal(number.toString()).intValueExact());}
                    catch(ArithmeticException invalid){throw new Unavailable("INTEGER_RANGE_OR_FRACTION_UNSUPPORTED");}
                }
                case "extract-Atom-name-without-term-pretty-print-quotes" -> new StringValue(atomName(value));
                case "preserve-the-finite-source-value-with-binary64-round-trip-or-exact-real-decoding" -> new RealValue(binary64(value));
                default -> throw converter.location().error("M0_CONVERTER_ALGORITHM_UNSUPPORTED");
            };
        }catch(Unavailable incomplete){throw incomplete;}catch(IllegalArgumentException|org.jacamo.bridge.contract.ContractException malformed){throw new Unavailable("TYPED_CONVERSION_UNAVAILABLE:"+converter.attr("id")+":"+malformed.getMessage());}
    }
    public static String atomName(Object value) {
        var data=CanonicalJson.object(value);
        if(!"JASON_TERM".equals(data.get("kind"))||!"jason.asSyntax.Atom".equals(data.get("javaType"))||!"JASON_ATOM_GET_FUNCTOR".equals(data.get("atomNameAuthority"))||!(data.get("atomName") instanceof String text))throw new Unavailable("PRODUCER_ATOM_NAME_REQUIRED");
        var atom=CanonicalJson.object(CanonicalJson.object(data.get("term")).get("key"));
        if(!"LITERAL".equals(atom.get("kind"))||!Objects.equals(text,atom.get("functor"))||!Boolean.FALSE.equals(atom.get("negative"))||!List.of().equals(atom.get("terms"))
                ||!Boolean.TRUE.equals(CanonicalJson.object(data.get("term")).get("supported")))throw new Unavailable("PRODUCER_ATOM_PROOF_MISMATCH");
        return text;
    }
    public static double binary64(Object value) {
        var data=CanonicalJson.object(value);
        if(!"NUMBER".equals(data.get("kind"))||!"java.lang.Double".equals(data.get("javaType"))||!"IEEE754_BINARY64".equals(data.get("encoding"))||!(data.get("rawBits") instanceof String bits)||!bits.matches("[0-9a-f]{16}")||!(data.get("value") instanceof String text))throw new Unavailable("FINITE_BINARY64_PROOF_REQUIRED");
        double number=Double.longBitsToDouble(Long.parseUnsignedLong(bits,16));
        if(!Double.isFinite(number)||!Double.toString(number).equals(text))throw new Unavailable("BINARY64_VALUE_BITS_MISMATCH");
        return number;
    }
    public static void validate(Node converter) {
        if(!converter.attr("onMismatch").equals("incomplete"))throw converter.location().error("M0_CONVERTER_FAILURE_POLICY_UNSUPPORTED");
        String pair=converter.attr("inputType")+"->"+converter.attr("outputType");
        String algorithm=switch(pair) {
            case "Boolean->Boolean" -> "identity";
            case "String->String" -> "preserve-exact-content";
            case "Integer->Integer" -> "preserve-exact-integer";
            case "JasonAtom->String" -> "extract-Atom-name-without-term-pretty-print-quotes";
            case "JavaBinary64->Real" -> "preserve-the-finite-source-value-with-binary64-round-trip-or-exact-real-decoding";
            default -> throw converter.location().error("M0_CONVERTER_TYPE_UNSUPPORTED");
        };
        if(!algorithm.equals(converter.attr("algorithm")))throw converter.location().error("M0_CONVERTER_ALGORITHM_UNSUPPORTED");
        if(pair.equals("JavaBinary64->Real")&&(!converter.attr("onOverflowOrUnsupportedBackend").equals("incomplete-do-not-clamp")||!converter.attr("sentinelReplacement").equals("forbidden")))throw converter.location().error("M0_BINARY64_POLICY_UNSUPPORTED");
    }
}
