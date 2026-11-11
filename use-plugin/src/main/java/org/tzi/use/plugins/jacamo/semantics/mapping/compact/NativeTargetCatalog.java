package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.vnu.sme.goal.dsl.csl.mm.CslAttribute;
import org.vnu.sme.goal.dsl.csl.mm.CslEntity;
import org.vnu.sme.goal.dsl.csl.mm.CslGroup;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.csl.mm.CslRole;
import org.vnu.sme.goal.dsl.istar.mm.*;

import java.util.*;

/**
 * Catalogue indexing target concepts, attributes, actors, and intentional elements
 * directly from compiled CSL and iStar models.
 */
public final class NativeTargetCatalog {

    private final Map<String, TargetSymbol> symbolsByQualified = new LinkedHashMap<>();
    private final Map<String, TargetSymbol> symbolsByModelQualified = new LinkedHashMap<>();
    private final Map<String, List<TargetSymbol>> symbolsByName = new HashMap<>();
    private final Map<String, Set<String>> ownerMembers = new HashMap<>();
    private CslModel cslModel;
    private GoalModel goalModel;

    public NativeTargetCatalog() {}

    public static NativeTargetCatalog fromModels(CslModel cslModel, GoalModel goalModel) {
        NativeTargetCatalog catalog = new NativeTargetCatalog();
        if (cslModel != null) {
            catalog.indexCsl(cslModel);
        }
        if (goalModel != null) {
            catalog.indexIStar(goalModel);
        }
        return catalog;
    }

    public Optional<CslModel> cslModel() {
        return Optional.ofNullable(cslModel);
    }

    public Optional<GoalModel> goalModel() {
        return Optional.ofNullable(goalModel);
    }

    public void indexCsl(CslModel model) {
        Objects.requireNonNull(model, "cslModel");
        this.cslModel = model;
        for (CslEntity entity : model.entities()) {
            register(new TargetSymbol(TargetSymbol.TargetKind.CSL_ENTITY, entity.name(), null, entity.name(), false, "CSL"));
            for (CslAttribute attr : entity.attributes()) {
                String typeName = attr.type() != null ? attr.type().sourceName() : "Object";
                register(new TargetSymbol(TargetSymbol.TargetKind.CSL_ATTRIBUTE, attr.name(), entity.name(), typeName, attr.optional(), "CSL"));
            }
        }
        for (CslRole role : model.roles()) {
            register(new TargetSymbol(TargetSymbol.TargetKind.CSL_ROLE, role.name(), null, role.name(), false, "CSL"));
            for (CslAttribute attr : role.attributes()) {
                String typeName = attr.type() != null ? attr.type().sourceName() : "Object";
                register(new TargetSymbol(TargetSymbol.TargetKind.CSL_ATTRIBUTE, attr.name(), role.name(), typeName, attr.optional(), "CSL"));
            }
        }
        for (CslGroup group : model.groups()) {
            indexGroupRecursively(group);
        }
        for (CslGroup group : model.orgContexts()) {
            indexGroupRecursively(group);
        }
    }

    private void indexGroupRecursively(CslGroup group) {
        register(new TargetSymbol(TargetSymbol.TargetKind.CSL_GROUP, group.name(), null, group.name(), false, "CSL"));
        for (CslAttribute attr : group.attributes()) {
            String typeName = attr.type() != null ? attr.type().sourceName() : "Object";
            register(new TargetSymbol(TargetSymbol.TargetKind.CSL_ATTRIBUTE, attr.name(), group.name(), typeName, attr.optional(), "CSL"));
        }
    }

    public void indexIStar(GoalModel model) {
        Objects.requireNonNull(model, "goalModel");
        this.goalModel = model;
        for (Actor actor : model.getActors()) {
            register(new TargetSymbol(TargetSymbol.TargetKind.ISTAR_ACTOR, actor.name(), null, actor.name(), false, "ISTAR"));
            for (IntentionalElement elem : actor.elements()) {
                TargetSymbol.TargetKind kind = switch (elem) {
                    case Goal g -> TargetSymbol.TargetKind.ISTAR_GOAL;
                    case Task t -> TargetSymbol.TargetKind.ISTAR_TASK;
                    case Resource r -> TargetSymbol.TargetKind.ISTAR_RESOURCE;
                    case Quality q -> TargetSymbol.TargetKind.ISTAR_QUALITY;
                    default -> TargetSymbol.TargetKind.ISTAR_GOAL;
                };
                register(new TargetSymbol(kind, elem.id(), actor.name(), kind.name(), false, "ISTAR"));
            }
        }
    }

    public void register(TargetSymbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        String qKey = symbol.qualifiedName();
        symbolsByQualified.putIfAbsent(qKey, symbol);
        symbolsByModelQualified.put(symbol.modelSource().toUpperCase(Locale.ROOT) + ":" + qKey, symbol);
        symbolsByName.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(symbol);

        if (symbol.owner() != null) {
            ownerMembers.computeIfAbsent(symbol.owner(), k -> new HashSet<>()).add(symbol.name());
        }
    }

    public TargetSymbol resolve(TargetReference ref) {
        return resolve(ref, null);
    }

    public TargetSymbol resolve(TargetReference ref, String expectedModel) {
        Objects.requireNonNull(ref, "ref");
        String modelKey = expectedModel != null ? expectedModel.toUpperCase(Locale.ROOT) : null;
        if (ref.isSimple()) {
            String name = ref.qualifier();
            if (modelKey != null) {
                TargetSymbol s = symbolsByModelQualified.get(modelKey + ":" + name);
                if (s != null) return s;
            }
            TargetSymbol s = symbolsByQualified.get(name);
            if (s != null && (modelKey == null || s.modelSource().equalsIgnoreCase(modelKey))) return s;

            List<TargetSymbol> matches = symbolsByName.get(name);
            if (matches == null || matches.isEmpty()) {
                throw new IllegalArgumentException("TARGET_SYMBOL_NOT_FOUND: '" + name + "'");
            }
            if (modelKey != null) {
                var filtered = matches.stream().filter(m -> m.modelSource().equalsIgnoreCase(modelKey)).toList();
                if (filtered.size() == 1) return filtered.get(0);
                if (!filtered.isEmpty()) matches = filtered;
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            List<TargetSymbol> topLevel = matches.stream().filter(m -> m.owner() == null).toList();
            if (topLevel.size() == 1) {
                return topLevel.get(0);
            }
            throw new IllegalArgumentException("AMBIGUOUS_TARGET_SYMBOL: '" + name + "' matches multiple targets: "
                    + matches.stream().map(TargetSymbol::qualifiedName).toList());
        }

        // Qualified reference: owner.member
        String owner = ref.qualifier();
        String member = ref.member();
        String qKey = owner + "." + member;

        if (modelKey != null) {
            TargetSymbol s = symbolsByModelQualified.get(modelKey + ":" + qKey);
            if (s != null) return s;
        }
        TargetSymbol s = symbolsByQualified.get(qKey);
        if (s != null && (modelKey == null || s.modelSource().equalsIgnoreCase(modelKey))) return s;

        // Check if owner exists
        TargetSymbol ownerSymbol = modelKey != null ? symbolsByModelQualified.get(modelKey + ":" + owner) : symbolsByQualified.get(owner);
        if (ownerSymbol == null) {
            List<TargetSymbol> owners = symbolsByName.get(owner);
            if (owners == null || owners.isEmpty()) {
                throw new IllegalArgumentException("TARGET_OWNER_NOT_FOUND: owner '" + owner + "' does not exist");
            }
        }

        // Owner exists, but member does not
        Set<String> members = ownerMembers.get(owner);
        if (members == null || !members.contains(member)) {
            throw new IllegalArgumentException("TARGET_MEMBER_NOT_FOUND: member '" + member + "' not found on owner '" + owner + "'");
        }

        throw new IllegalArgumentException("TARGET_SYMBOL_NOT_FOUND: '" + qKey + "'");
    }

    public Optional<TargetSymbol> find(String qualifiedName) {
        return Optional.ofNullable(symbolsByQualified.get(qualifiedName));
    }

    public List<TargetSymbol> allSymbols() {
        return List.copyOf(symbolsByQualified.values());
    }
}
