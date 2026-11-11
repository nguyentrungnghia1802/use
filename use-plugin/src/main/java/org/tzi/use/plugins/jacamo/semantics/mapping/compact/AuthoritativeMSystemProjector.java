package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.*;

/** Component transaction adapter, not a runtime owner. The live compact path uses
 * NativeRuntimeMutationEngine and its existing writer, metadata and savepoint. */
public final class AuthoritativeMSystemProjector {
    private record Image(Map<MObject,MObjectState> objects,List<MLink> links,MSystemState snapshot) {
        static Image capture(MSystem system) {
            var states=new LinkedHashMap<MObject,MObjectState>();
            var pinned=new MSystemState("compact-transaction-image",system.state());
            for(var object:system.state().allObjects())states.put(object,object.state(pinned));
            return new Image(states,List.copyOf(system.state().allLinks()),pinned);
        }
        void restore(MSystem system) throws MSystemException {
            for(var link:List.copyOf(system.state().allLinks()))system.state().deleteLink(link);
            for(var object:List.copyOf(system.state().allObjects()))if(!objects.containsKey(object))system.state().deleteObject(object);
            var copied=new MSystemState("compact-transaction-restore",snapshot);
            for(var entry:objects.entrySet()) {
                var original=entry.getKey();var current=system.state().objectByName(original.name());
                if(current!=null&&current!=original)system.state().deleteObject(current);
                if(current!=original)system.state().restoreObject(original.state(copied));
                else for(var attribute:original.cls().allAttributes())original.state(system.state()).setAttributeValue(attribute,entry.getValue().attributeValue(attribute));
            }
            for(var link:links)system.state().insertLink(link);
        }
        boolean matches(MSystem system) {
            if(!objects.keySet().equals(system.state().allObjects())||!new HashSet<>(links).equals(system.state().allLinks()))return false;
            for(var entry:objects.entrySet())for(var attribute:entry.getKey().cls().allAttributes())
                if(!Objects.equals(entry.getValue().attributeValue(attribute),entry.getKey().state(system.state()).attributeValue(attribute)))return false;
            return true;
        }
    }
    public static final class AppliedTransaction {
        private final String cutId;
        private final MSystem owner;
        private final Image before,after;
        private boolean rolledBack;
        private AppliedTransaction(String cutId,MSystem owner,Image before,Image after) {this.cutId=cutId;this.owner=owner;this.before=before;this.after=after;}
        public String cutId(){return cutId;}
        public boolean isRolledBack(){return rolledBack;}
        public void rollback(MSystem system) {
            if(system!=owner)throw new IllegalArgumentException("COMPACT_ROLLBACK_OWNER_MISMATCH");
            if(rolledBack)return;
            if(!after.matches(system))throw new IllegalStateException("COMPACT_ROLLBACK_STALE_TRANSACTION");
            try {before.restore(system);rolledBack=true;}
            catch(MSystemException error){throw new IllegalStateException("COMPACT_ROLLBACK_FAILED",error);}
        }
    }
    public static AppliedTransaction project(MSystem system,RuntimeInstanceLinker.ProjectionTransaction transaction) {
        Objects.requireNonNull(system);Objects.requireNonNull(transaction);
        var before=Image.capture(system);
        try {
            for(var mutation:transaction.objectMutations()) {
                var type=system.model().getClass(mutation.className());
                if(type==null)throw new IllegalArgumentException("COMPACT_NATIVE_CLASS_MISSING:"+mutation.className());
                var object=system.state().objectByName(mutation.objectName());
                if(object==null)object=system.state().createObject(type,mutation.objectName());
                else if(object.cls()!=type)throw new IllegalArgumentException("COMPACT_NATIVE_OBJECT_TYPE_MISMATCH:"+mutation.objectName());
                for(var entry:mutation.attributes().entrySet()) {
                    var attribute=type.attribute(entry.getKey(),true);Value value=entry.getValue();
                    if(attribute==null||value==null||!value.isUndefined()&&!value.type().conformsTo(attribute.type()))
                        throw new IllegalArgumentException("COMPACT_NATIVE_ATTRIBUTE_TYPE_MISMATCH:"+mutation.className()+"."+entry.getKey());
                    object.state(system.state()).setAttributeValue(attribute,value);
                }
            }
            for(var mutation:transaction.linkMutations()) {
                var association=system.model().getAssociation(mutation.associationName());
                var first=system.state().objectByName(mutation.sourceObject());var second=system.state().objectByName(mutation.targetObject());
                if(association==null||first==null||second==null)throw new IllegalArgumentException("COMPACT_NATIVE_LINK_UNRESOLVED:"+mutation.associationName());
                if(!system.state().hasLink(association,List.of(first,second),List.of()))system.state().createLink(association,List.of(first,second),List.of());
            }
            for(String name:transaction.disposedObjects()) {
                var object=system.state().objectByName(name);if(object!=null)system.state().deleteObject(object);
            }
            return new AppliedTransaction(transaction.cutId(),system,before,Image.capture(system));
        }catch(Exception error) {
            try {before.restore(system);}catch(Exception rollback){error.addSuppressed(rollback);throw new IllegalStateException("COMPACT_TRANSACTION_AND_ROLLBACK_FAILED",error);}
            throw new IllegalArgumentException("COMPACT_TRANSACTION_REJECTED:"+error.getMessage(),error);
        }
    }
    private AuthoritativeMSystemProjector() { }
}
