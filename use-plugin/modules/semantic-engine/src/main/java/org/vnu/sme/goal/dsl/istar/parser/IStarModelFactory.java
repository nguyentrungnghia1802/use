package org.vnu.sme.goal.dsl.istar.parser;

import java.util.*;

import org.vnu.sme.goal.dsl.istar.ast.*;
import org.vnu.sme.goal.dsl.istar.mm.*;

/**
 * Converts the iStar 2.0 AST (CS layer) into the runtime MetaModel (MM layer).
 * Dependency direction: factory → ast, factory → mm.  Neither ast nor mm knows the other.
 */
public final class IStarModelFactory {

    private IStarModelFactory() {}

    public static GoalModel build(IStarModelCS cs) {
        GoalModel model = new GoalModel(cs.name());

        for (ActorDefCS aCS : cs.actors()) {
            model.addActor(buildActor(aCS));
        }
        for (DependencyCS dCS : cs.dependencies()) {
            model.addDependency(new Dependency(
                    dCS.depender(), dCS.dependerElmt(),
                    dependum(dCS.dependumKind(), dCS.dependum()),
                    dCS.dependee(), dCS.dependeeElmt()));
        }
        return model;
    }

    /** Accumulates the relations gathered while walking an actor's elements. */
    private static final class Relations {
        final Map<String, List<String>> andChildren = new LinkedHashMap<>(); // parent -> children
        final List<OrRefinement>   orRefinements = new ArrayList<>();
        final List<Contribution>   contribs      = new ArrayList<>();
        final List<Qualification>  qualifs       = new ArrayList<>();
        final List<NeededBy>       neededBys     = new ArrayList<>();

        /** {@code sourceId} is the element that declared these inline '>' relations. */
        void apply(String sourceId, List<RelCS> rels) {
            for (RelCS rel : rels) {
                switch (rel) {
                    case RelCS.RefineCS r -> {
                        if (r.or()) orRefinements.add(new OrRefinement(r.target(), sourceId));
                        else andChildren.computeIfAbsent(r.target(), k -> new ArrayList<>()).add(sourceId);
                    }
                    case RelCS.ContributesCS r ->
                            contribs.add(new Contribution(sourceId, ContributionType.from(r.type()), r.target()));
                    case RelCS.QualifiesCS r -> qualifs.add(new Qualification(sourceId, r.target()));
                    case RelCS.NeededByCS r -> neededBys.add(new NeededBy(sourceId, r.target()));
                }
            }
        }

        List<Refinement> refinements() {
            List<Refinement> result = new ArrayList<>();
            andChildren.forEach((parent, children) -> result.add(new AndRefinement(parent, children)));
            result.addAll(orRefinements);
            return result;
        }
    }

    private static Actor buildActor(ActorDefCS cs) {
        List<IntentionalElement> elements = new ArrayList<>();
        List<Association>        assocs   = new ArrayList<>();
        Relations rels = new Relations();

        for (ElementBodyCS item : cs.body()) {
            switch (item) {
                case ElementBodyCS.GoalCS e -> {
                    GoalType gt = e.goalType() != null ? GoalType.from(e.goalType()) : null;
                    elements.add(new Goal(e.id(), gt,
                            constraint(e.constraints(), IStarOclConstraintCS.Kind.CONDITION, e.id())));
                    rels.apply(e.id(), e.rels());
                }
                case ElementBodyCS.TaskCS e -> {
                    elements.add(new Task(e.id(),
                            constraint(e.constraints(), IStarOclConstraintCS.Kind.PRE, e.id()),
                            constraint(e.constraints(), IStarOclConstraintCS.Kind.POST, e.id())));
                    rels.apply(e.id(), e.rels());
                }
                case ElementBodyCS.ResourceCS e -> {
                    elements.add(new Resource(e.id()));
                    rels.apply(e.id(), e.rels());
                }
                case ElementBodyCS.QualityCS e -> {
                    elements.add(new Quality(e.id()));
                    rels.apply(e.id(), e.rels());
                }
                case ElementBodyCS.IsACS e ->
                        assocs.add(new Association(e.actor(), AssocKind.IS_A, e.target()));
                case ElementBodyCS.ParticipatesCS e ->
                        assocs.add(new Association(e.actor(), AssocKind.PARTICIPATES_IN, e.target()));
            }
        }

        List<Refinement>    refinements    = rels.refinements();
        List<Contribution>  contributions  = rels.contribs;
        List<Qualification> qualifications = rels.qualifs;
        List<NeededBy>      neededBys      = rels.neededBys;

        return switch (cs.kind().toLowerCase()) {
            case "role" -> new Role(cs.id(), elements, refinements, contributions, qualifications,
                    neededBys, assocs);
            case "agent" -> new Agent(cs.id(), elements, refinements, contributions, qualifications,
                    neededBys, assocs);
            default -> throw new IllegalArgumentException("Unknown actor kind: " + cs.kind());
        };
    }

    private static IStarOclConstraint constraint(List<IStarOclConstraintCS> values,
                                                 IStarOclConstraintCS.Kind kind,
                                                 String elementId) {
        List<IStarOclConstraintCS> matching = values.stream()
                .filter(value -> value.kind() == kind).toList();
        if (matching.size() > 1) {
            throw new IllegalArgumentException("semantic: element '" + elementId
                    + "' declares more than one '" + kind.name().toLowerCase()
                    + "' OCL contract; each typed contract slot has multiplicity 0..1");
        }
        return matching.isEmpty() ? null : new IStarOclConstraint(matching.get(0).oclBody());
    }

    private static IntentionalElement dependum(String kind, String id) {
        return switch (kind) {
            case "goal" -> new Goal(id, null);
            case "task" -> new Task(id);
            case "resource" -> new Resource(id);
            case "quality" -> new Quality(id);
            default -> throw new IllegalArgumentException("Unknown dependum kind: " + kind);
        };
    }
}
