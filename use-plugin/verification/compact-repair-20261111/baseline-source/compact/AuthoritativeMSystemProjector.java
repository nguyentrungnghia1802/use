package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemException;

import java.util.*;

/**
 * Directly projects admitted RuntimeInstanceLinker transactions into the authoritative USE MSystem.
 * Maintains undo history for clean transaction rollback on verification hold or rejection.
 */
public final class AuthoritativeMSystemProjector {

    public interface UndoAction {
        void undo(MSystem system);
    }

    private record SavedLink(MAssociation association, List<String> participantNames) {}

    public static final class AppliedTransaction {
        private final String cutId;
        private final List<UndoAction> undoActions;
        private boolean rolledBack = false;

        AppliedTransaction(String cutId, List<UndoAction> undoActions) {
            this.cutId = cutId;
            this.undoActions = List.copyOf(undoActions);
        }

        public String cutId() {
            return cutId;
        }

        public boolean isRolledBack() {
            return rolledBack;
        }

        public void rollback(MSystem system) {
            if (rolledBack) return;
            // Roll back in reverse order
            for (int i = undoActions.size() - 1; i >= 0; i--) {
                undoActions.get(i).undo(system);
            }
            rolledBack = true;
        }
    }

    public static AppliedTransaction project(MSystem system, RuntimeInstanceLinker.ProjectionTransaction tx) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(tx, "tx");

        List<UndoAction> undoActions = new ArrayList<>();

        try {
            // 1. Process object mutations
            for (RuntimeInstanceLinker.ObjectMutation mut : tx.objectMutations()) {
                MClass cls = system.model().getClass(mut.className());
                if (cls == null) {
                    throw new IllegalArgumentException("MSystem model missing class: " + mut.className());
                }

                MObject obj = system.state().objectByName(mut.objectName());
                boolean newlyCreated = false;

                if (obj == null) {
                    obj = system.state().createObject(cls, mut.objectName());
                    newlyCreated = true;
                    final MObject toDelete = obj;
                    undoActions.add(s -> s.state().deleteObject(toDelete));
                }

                // Apply attributes
                for (var attrEntry : mut.attributes().entrySet()) {
                    String attrName = attrEntry.getKey();
                    Value newVal = attrEntry.getValue();

                    MAttribute attr = cls.attribute(attrName, true);
                    if (attr == null) {
                        throw new IllegalArgumentException("MSystem class '" + cls.name() + "' missing attribute: " + attrName);
                    }
                    Value prevVal = obj.state(system.state()).attributeValue(attr);
                    obj.state(system.state()).setAttributeValue(attr, newVal);

                    if (!newlyCreated) {
                        final MObject targetObj = obj;
                        final Value restoreVal = prevVal;
                        undoActions.add(s -> targetObj.state(s.state()).setAttributeValue(attr, restoreVal));
                    }
                }
            }

            // 2. Process link mutations
            if (tx.linkMutations() != null) {
                for (RuntimeInstanceLinker.LinkMutation lm : tx.linkMutations()) {
                    MAssociation assoc = system.model().getAssociation(lm.associationName());
                    if (assoc == null) {
                        throw new IllegalArgumentException("MSystem model missing association: " + lm.associationName());
                    }
                    MObject srcObj = system.state().objectByName(lm.sourceObject());
                    MObject tgtObj = system.state().objectByName(lm.targetObject());
                    if (srcObj == null || tgtObj == null) {
                        throw new IllegalArgumentException("MSystem link endpoints missing: " + lm.sourceObject() + " -> " + lm.targetObject());
                    }
                    List<MObject> participants = List.of(srcObj, tgtObj);
                    if (!system.state().hasLink(assoc, participants, List.of())) {
                        system.state().createLink(assoc, participants, List.of());
                        undoActions.add(s -> {
                            try {
                                List<MObject> current = List.of(
                                        s.state().objectByName(lm.sourceObject()),
                                        s.state().objectByName(lm.targetObject()));
                                if (s.state().hasLink(assoc, current, List.of())) {
                                    s.state().deleteLink(assoc, current, List.of());
                                }
                            } catch (MSystemException e) {
                                throw new IllegalStateException("LINK_ROLLBACK_FAILED: " + e.getMessage(), e);
                            }
                        });
                    }
                }
            }

            // 3. Process disposed objects with full state + link capture for rollback
            if (tx.disposedObjects() != null) {
                for (String disposedName : tx.disposedObjects()) {
                    MObject obj = system.state().objectByName(disposedName);
                    if (obj != null) {
                        final MClass savedClass = obj.cls();
                        final String savedName = obj.name();
                        final Map<MAttribute, Value> savedAttributes = new LinkedHashMap<>();
                        for (MAttribute attr : savedClass.allAttributes()) {
                            savedAttributes.put(attr, obj.state(system.state()).attributeValue(attr));
                        }
                        final List<SavedLink> savedLinks = new ArrayList<>();
                        for (MLink link : new ArrayList<>(system.state().allLinks())) {
                            if (link.linkedObjects().contains(obj)) {
                                List<String> pNames = link.linkedObjects().stream().map(MObject::name).toList();
                                savedLinks.add(new SavedLink(link.association(), pNames));
                                system.state().deleteLink(link.association(), link.linkedObjects(), List.of());
                            }
                        }

                        system.state().deleteObject(obj);

                        undoActions.add(s -> {
                            try {
                                MObject restored = s.state().objectByName(savedName);
                                if (restored == null) {
                                    restored = s.state().createObject(savedClass, savedName);
                                }
                                for (var entry : savedAttributes.entrySet()) {
                                    restored.state(s.state()).setAttributeValue(entry.getKey(), entry.getValue());
                                }
                                for (SavedLink sl : savedLinks) {
                                    List<MObject> participants = new ArrayList<>();
                                    for (String pName : sl.participantNames()) {
                                        MObject pObj = s.state().objectByName(pName);
                                        if (pObj != null) participants.add(pObj);
                                    }
                                    if (participants.size() == sl.participantNames().size()
                                            && !s.state().hasLink(sl.association(), participants, List.of())) {
                                        s.state().createLink(sl.association(), participants, List.of());
                                    }
                                }
                            } catch (MSystemException e) {
                                throw new IllegalStateException("OBJECT_ROLLBACK_FAILED: " + e.getMessage(), e);
                            }
                        });
                    }
                }
            }

            return new AppliedTransaction(tx.cutId(), undoActions);
        } catch (Exception e) {
            // Auto rollback on failure
            for (int i = undoActions.size() - 1; i >= 0; i--) {
                try {
                    undoActions.get(i).undo(system);
                } catch (Exception ignored) {}
            }
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalStateException("PROJECT_TRANSACTION_FAILED: " + e.getMessage(), e);
        }
    }

    private AuthoritativeMSystemProjector() {}
}

