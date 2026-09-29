package org.jacamo.bridge.contract.semantic;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractException;

/** Dependency-free canonical codec for the typed semantic contract. */
public final class SemanticContractCodec {
    private SemanticContractCodec() { }

    public static byte[] encode(JacamoSemanticSnapshot snapshot) { return CanonicalJson.encode(toTree(snapshot)); }
    public static JacamoSemanticSnapshot decode(byte[] bytes) { return fromTree(CanonicalJson.decode(bytes)); }
    public static Object toTree(JacamoSemanticSnapshot snapshot) { return encodeValue(snapshot); }
    public static JacamoSemanticSnapshot fromTree(Object tree) {
        JacamoSemanticSnapshot result = (JacamoSemanticSnapshot) convert(tree, JacamoSemanticSnapshot.class);
        if (!JacamoSemanticSnapshot.CURRENT_VERSION.equals(result.contractVersion()))
            throw new ContractException("unsupported semantic contract version: " + result.contractVersion());
        return result;
    }

    private static Object encodeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof List<?> list) return list.stream().map(SemanticContractCodec::encodeValue).toList();
        if (value instanceof Map<?,?> map) {
            var result = new TreeMap<String,Object>();
            map.forEach((key,item) -> result.put(String.valueOf(key), encodeValue(item)));
            return result;
        }
        if (value.getClass().isRecord()) {
            var result = new LinkedHashMap<String,Object>();
            try {
                for (RecordComponent component : value.getClass().getRecordComponents())
                    result.put(component.getName(), encodeValue(component.getAccessor().invoke(value)));
                return result;
            } catch (ReflectiveOperationException error) { throw new ContractException("semantic encode failed", error); }
        }
        throw new ContractException("unsupported semantic contract value: " + value.getClass().getName());
    }

    private static Object convert(Object value, Type target) {
        if (target instanceof ParameterizedType generic) {
            Class<?> raw = (Class<?>) generic.getRawType();
            if (raw == List.class) {
                if (!(value instanceof List<?> values)) throw new ContractException("semantic list required");
                var result = new ArrayList<>(); for (Object item : values) result.add(convert(item, generic.getActualTypeArguments()[0]));
                return List.copyOf(result);
            }
            if (raw == Map.class) {
                if (!(value instanceof Map<?,?> values)) throw new ContractException("semantic map required");
                var result = new TreeMap<String,Object>();
                values.forEach((key,item) -> result.put(String.valueOf(key), convert(item, generic.getActualTypeArguments()[1])));
                return java.util.Collections.unmodifiableMap(result);
            }
        }
        if (!(target instanceof Class<?> type)) throw new ContractException("unsupported semantic target type");
        if (value == null) return null;
        if (type == String.class) { if(value instanceof String text)return text; throw new ContractException("semantic string required"); }
        if (type == int.class || type == Integer.class) { if(value instanceof Number n)return Math.toIntExact(n.longValue()); throw new ContractException("semantic integer required"); }
        if (type == long.class || type == Long.class) { if(value instanceof Number n)return n.longValue(); throw new ContractException("semantic long required"); }
        if (type == boolean.class || type == Boolean.class) { if(value instanceof Boolean b)return b; throw new ContractException("semantic boolean required"); }
        if (type.isEnum()) {
            if (!(value instanceof String text)) throw new ContractException("semantic enum required");
            try { @SuppressWarnings({"unchecked","rawtypes"}) Object result=Enum.valueOf((Class)type,text); return result; }
            catch (IllegalArgumentException error) { throw new ContractException("unknown semantic enum " + type.getSimpleName() + ": " + text,error); }
        }
        if (type.isRecord()) return convertRecord(value,type);
        throw new ContractException("unsupported semantic target: " + type.getName());
    }

    private static Object convertRecord(Object value, Class<?> type) {
        if (!(value instanceof Map<?,?> input)) throw new ContractException(type.getSimpleName()+" object required");
        RecordComponent[] components=type.getRecordComponents();
        var names=java.util.Arrays.stream(components).map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        if (!names.equals(input.keySet())) throw new ContractException(type.getSimpleName()+" keys mismatch");
        Object[] args=new Object[components.length]; Class<?>[] signature=new Class<?>[components.length];
        for(int i=0;i<components.length;i++){args[i]=convert(input.get(components[i].getName()),components[i].getGenericType());signature[i]=components[i].getType();}
        try{return type.getDeclaredConstructor(signature).newInstance(args);}
        catch(ReflectiveOperationException error){throw new ContractException("semantic decode failed: "+type.getSimpleName(),error);}
    }
}
