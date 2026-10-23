package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeValueConverters;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeValueConvertersTest {
    private MappingSpecification.Node converter(String in,String out,String algorithm) {
        return new MappingSpecification.Node("converter",Map.of("id","codec","inputType",in,"outputType",out,"algorithm",algorithm,"onMismatch","incomplete","onOverflowOrUnsupportedBackend","incomplete-do-not-clamp","sentinelReplacement","forbidden"),"",List.of(),new MappingSpecification.Location("test.xml",1,1,"codec"));
    }
    @Test void exactBinary64AndScalarKindsAreSharedWithoutFalseOrSentinelFallback() {
        var real=converter("JavaBinary64","Real","preserve-the-finite-source-value-with-binary64-round-trip-or-exact-real-decoding");RuntimeValueConverters.validate(real);
        for(double value:new double[]{Double.MAX_VALUE,Double.MIN_VALUE,Double.MIN_NORMAL,-0.0,0.0,-17.25}) {
            var raw=Map.<String,Object>of("kind","NUMBER","javaType","java.lang.Double","encoding","IEEE754_BINARY64","value",Double.toString(value),"rawBits",String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value)));
            assertEquals(Double.doubleToRawLongBits(value),Double.doubleToRawLongBits(((RealValue)RuntimeValueConverters.decode(real,raw)).value()));
        }
        var bool=converter("Boolean","Boolean","identity");RuntimeValueConverters.validate(bool);assertEquals(BooleanValue.FALSE,RuntimeValueConverters.decode(bool,false));
        assertThrows(RuntimeValueConverters.Unavailable.class,()->RuntimeValueConverters.decode(bool,"false"));assertThrows(RuntimeValueConverters.Unavailable.class,()->RuntimeValueConverters.decode(bool,null));
        var text=converter("String","String","preserve-exact-content");assertEquals(new StringValue("literal 'quoted' \\ path"),RuntimeValueConverters.decode(text,"literal 'quoted' \\ path"));
        assertThrows(IllegalArgumentException.class,()->RuntimeValueConverters.validate(converter("String","String","guess")));
        var integer=converter("Integer","Integer","preserve-exact-integer");RuntimeValueConverters.validate(integer);
        assertEquals(IntegerValue.valueOf(-1),RuntimeValueConverters.decode(integer,-1));
        assertThrows(RuntimeValueConverters.Unavailable.class,()->RuntimeValueConverters.decode(integer,2147483648L));
        assertThrows(RuntimeValueConverters.Unavailable.class,()->RuntimeValueConverters.decode(integer,new java.math.BigDecimal("1.5")));
    }
}
