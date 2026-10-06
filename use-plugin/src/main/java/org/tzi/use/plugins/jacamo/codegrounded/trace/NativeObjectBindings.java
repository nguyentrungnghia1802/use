package org.tzi.use.plugins.jacamo.codegrounded.trace;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/** Exact aliases and provenance for native objects. Never reads or writes domain MAttributes. */
public final class NativeObjectBindings extends AbstractMap<String,MObject> {
    private final Map<String,MObject> aliases = new LinkedHashMap<>();
    private final Map<MObject,Map<String,String>> metadata = new IdentityHashMap<>();

    public NativeObjectBindings() { }
    public NativeObjectBindings(NativeObjectBindings source) {
        aliases.putAll(source.aliases);
        source.metadata.forEach((object, values) -> metadata.put(object, new LinkedHashMap<>(values)));
    }
    @Override public Set<Entry<String,MObject>> entrySet() { return aliases.entrySet(); }
    @Override public MObject get(Object identity) { return aliases.get(identity); }
    @Override public boolean containsKey(Object identity) { return aliases.containsKey(identity); }
    @Override public MObject remove(Object identity) { return aliases.remove(identity); }
    @Override public int size() { return aliases.size(); }
    @Override public MObject put(String identity, MObject object) {
        if (identity == null || identity.isBlank() || object == null) throw new IllegalArgumentException("NATIVE_BINDING_IDENTITY_REQUIRED");
        var previous = aliases.get(identity);
        if (previous != null && previous != object) throw new IllegalArgumentException("NATIVE_BINDING_REDIRECT:" + identity);
        metadata.computeIfAbsent(object, key -> new LinkedHashMap<>()).putIfAbsent("semanticId", identity);
        return aliases.put(identity, object);
    }
    public String metadata(MObject object, String key) { return metadata.getOrDefault(object, Map.of()).getOrDefault(key, ""); }
    public void metadata(MObject object, String key, String value) {
        if (!metadata.containsKey(object)) throw new IllegalArgumentException("NATIVE_OBJECT_BINDING_REQUIRED:" + object.name());
        metadata.get(object).put(key, Objects.requireNonNullElse(value, ""));
    }
    public Map<String,String> metadata(MObject object) { return Map.copyOf(metadata.getOrDefault(object, Map.of())); }
    public void source(MObject object, org.jacamo.bridge.contract.semantic.SemanticMetadata source, String rule) {
        metadata(object,"sourceSemanticId",source.semanticId());
        metadata(object,"sourceKind",source.sourceKind());
        metadata(object,"projectionRule",rule);
        metadata(object,"sourceEvidence",org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder.canonicalJson(source.evidence()));
    }
    public String identity(MObject object) {
        String id = metadata(object, "semanticId");
        if (id.isBlank()) throw new IllegalArgumentException("NATIVE_OBJECT_IDENTITY_REQUIRED:" + object.name());
        return id;
    }
    public void forget(MObject object) {
        aliases.values().removeIf(value -> value == object);
        metadata.remove(object);
    }
    public void restore(NativeObjectBindings image) {
        aliases.clear(); aliases.putAll(image.aliases); metadata.clear();
        image.metadata.forEach((object, values) -> metadata.put(object, new LinkedHashMap<>(values)));
    }
    /** Portable, integrity-checked alongside the native model/SOIL; names address objects in that exact file only. */
    public Map<String,Object> toMap() {
        var names = new TreeMap<String,String>(); aliases.forEach((id, object) -> names.put(id, object.name()));
        var objects = new TreeMap<String,Object>();
        aliases.values().forEach(object -> objects.put(object.name(), Map.of("className", object.cls().name(), "metadata", metadata(object))));
        return Map.of("version", "1.0.0", "aliases", names, "objects", objects);
    }
    public static NativeObjectBindings fromMap(MSystem system, Map<String,Object> image) {
        if (!"1.0.0".equals(image.get("version"))) throw new IllegalArgumentException("NATIVE_BINDING_VERSION_UNSUPPORTED");
        var result = new NativeObjectBindings();
        var objects = CanonicalJson.object(image.get("objects"));
        if (!objects.keySet().equals(system.state().allObjects().stream().map(MObject::name).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("NATIVE_BINDING_OBJECT_SET_MISMATCH");
        CanonicalJson.object(image.get("aliases")).forEach((id, name) -> {
            var object = system.state().objectByName((String) name);
            if (object == null) throw new IllegalArgumentException("NATIVE_BINDING_OBJECT_MISSING:" + name);
            result.put(id, object);
        });
        objects.forEach((name, raw) -> {
            var object = system.state().objectByName(name); var row = CanonicalJson.object(raw);
            if (!object.cls().name().equals(row.get("className"))) throw new IllegalArgumentException("NATIVE_BINDING_CLASS_MISMATCH:" + name);
            var fields = CanonicalJson.object(row.get("metadata"));
            if (!(fields.get("semanticId") instanceof String id) || result.get(id) != object)
                throw new IllegalArgumentException("NATIVE_BINDING_PRIMARY_ID_MISMATCH:" + name);
            fields.forEach((key, value) -> result.metadata(object, key, (String) value));
        });
        return result;
    }
}
