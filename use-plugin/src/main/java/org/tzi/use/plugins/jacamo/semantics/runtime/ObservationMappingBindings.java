package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

/** Compiled source-to-native lookup only, never another CSL M1 or runtime state. */
public interface ObservationMappingBindings {
    enum Kind { ENTITY, ROLE, ORG_CONTEXT }
    record Type(String id,String sourceId,String symbol,Kind kind,String javaClass) { }
    List<Type> types();
    boolean functionalObservers();
    boolean nestingRegistered(String childSource,String parentSource);
    boolean hasNesting();
    List<MAttribute> properties(MObject object,String sourceName);
    Value decode(MObject object,MAttribute attribute,List<?> values,List<?> javaTypes);
    default boolean canonical(){return false;}
    default void propertyWritten(SemanticObservationMapper.Port port,MObject object,MAttribute attribute,Value before,Value after) { }
}
