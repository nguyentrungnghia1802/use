package org.vnu.sme.goal.dsl.istar.parser;

import java.util.*;
import java.util.stream.Collectors;

import org.vnu.sme.goal.dsl.istar.ast.*;

/**
 * Walks the ANTLR parse tree and builds the iStar 2.0 AST (CS layer).
 * Use {@link IStarModelFactory} to convert the resulting AST to the MM layer.
 */
public final class IStarBuildingVisitor extends IStarBaseVisitor<Object> {

    private IStarModelCS model;

    public static IStarModelCS build(IStarParser.ModelContext ctx) {
        IStarBuildingVisitor v = new IStarBuildingVisitor();
        v.visitModel(ctx);
        return v.model;
    }

    // ── Root ─────────────────────────────────────────────────────────────────

    @Override
    public Object visitModel(IStarParser.ModelContext ctx) {
        List<ActorDefCS> actors = ctx.actorDef().stream()
                .map(a -> (ActorDefCS) visitActorDef(a))
                .collect(Collectors.toList());

        List<DependencyCS> deps = ctx.modelStatement().stream()
                .filter(statement -> statement.dependency() != null)
                .map(statement -> (DependencyCS) visitDependency(statement.dependency()))
                .collect(Collectors.toList());

        model = new IStarModelCS(ctx.IDENT().getText(), actors, deps);
        return model;
    }

    // ── Actor ─────────────────────────────────────────────────────────────────

    @Override
    public Object visitActorDef(IStarParser.ActorDefContext ctx) {
        String id   = ctx.IDENT().getText();
        String kind = ctx.actorKind().getText();   // "role" | "agent"

        List<ElementBodyCS> body = new ArrayList<>();
        for (IStarParser.ActorBodyContext b : ctx.actorBody()) {
            ElementBodyCS item = buildBodyItem(b);
            if (item != null) body.add(item);
        }
        return new ActorDefCS(id, kind, body);
    }

    private ElementBodyCS buildBodyItem(IStarParser.ActorBodyContext body) {
        return switch (body) {
            case IStarParser.BodyGoalContext b -> new ElementBodyCS.GoalCS(
                    b.IDENT().getText(),
                    b.goalType() != null ? b.goalType().goalTypeName().getText() : null,
                    buildRels(b.rel()),
                    buildGoalConstraints(b.goalCondition()));
            case IStarParser.BodyTaskContext b ->
                    new ElementBodyCS.TaskCS(b.IDENT().getText(), buildRels(b.rel()),
                            buildOclConstraints(b.oclCondition()));
            case IStarParser.BodyResourceContext b ->
                    new ElementBodyCS.ResourceCS(b.IDENT().getText(), buildRels(b.rel()));
            case IStarParser.BodyQualityContext b ->
                    new ElementBodyCS.QualityCS(b.IDENT().getText(), buildRels(b.rel()));
            case IStarParser.BodyIsAContext b ->
                    new ElementBodyCS.IsACS(b.IDENT(0).getText(), b.IDENT(1).getText());
            case IStarParser.BodyParticipatesContext b ->
                    new ElementBodyCS.ParticipatesCS(b.IDENT(0).getText(), b.IDENT(1).getText());
            default -> null;
        };
    }

    private List<IStarOclConstraintCS> buildOclConstraints(List<IStarParser.OclConditionContext> contexts) {
        List<IStarOclConstraintCS> result = new ArrayList<>();
        for (IStarParser.OclConditionContext ctx : contexts) {
            boolean legacy = ctx.OCL_CLAUSE() != null;
            String raw = legacy ? ctx.OCL_CLAUSE().getText() : ctx.OCL_BLOCK().getText();
            String body = extractOclBody(raw);
            if (body == null) continue;
            IStarOclConstraintCS.Kind kind = !legacy && "pre".equals(ctx.getStart().getText())
                    ? IStarOclConstraintCS.Kind.PRE : IStarOclConstraintCS.Kind.POST;
            result.add(new IStarOclConstraintCS(kind, body));
        }
        return result;
    }

    private List<IStarOclConstraintCS> buildGoalConstraints(List<IStarParser.GoalConditionContext> contexts) {
        List<IStarOclConstraintCS> result = new ArrayList<>();
        for (IStarParser.GoalConditionContext ctx : contexts) {
            if (ctx.OCL_CLAUSE() != null) {
                String body = extractOclBody(ctx.OCL_CLAUSE().getText());
                if (body != null) {
                    result.add(new IStarOclConstraintCS(IStarOclConstraintCS.Kind.CONDITION, body));
                }
                continue;
            }
            String keyword = ctx.getStart().getText();
            String body = extractOclBody(ctx.OCL_BLOCK().getText());
            if (body == null) continue;
            IStarOclConstraintCS.Kind kind = switch (keyword) {
                case "condition", "satisfy", "ensure" -> IStarOclConstraintCS.Kind.CONDITION;
                default -> throw new IllegalStateException("Unknown goal contract clause: " + keyword);
            };
            result.add(new IStarOclConstraintCS(kind, body));
        }
        return result;
    }

    private String extractOclBody(String raw) {
        String body;
        if (raw.startsWith("ocl:")) {
            body = raw.substring("ocl:".length(), raw.length() - 1).trim();
        } else {
            int start = raw.indexOf("{[");
            int end = raw.lastIndexOf("]}");
            body = start >= 0 && end >= start ? raw.substring(start + 2, end).trim() : "";
        }
        return body.isEmpty() ? null : body;
    }

    // ── Inline relations ('>' relation target) ──────────────────────────────

    private List<RelCS> buildRels(List<IStarParser.RelContext> relCtxs) {
        List<RelCS> rels = new ArrayList<>();
        for (IStarParser.RelContext r : relCtxs) rels.add(buildRel(r));
        return rels;
    }

    private RelCS buildRel(IStarParser.RelContext ctx) {
        return switch (ctx) {
            case IStarParser.RelAndContext r -> new RelCS.RefineCS(r.target.getText(), false);
            case IStarParser.RelOrContext r -> new RelCS.RefineCS(r.target.getText(), true);
            case IStarParser.RelContributeContext r ->
                    new RelCS.ContributesCS(r.target.getText(), r.contribType().getText());
            case IStarParser.RelQualifiesContext r -> new RelCS.QualifiesCS(r.target.getText());
            case IStarParser.RelNeededByContext r -> new RelCS.NeededByCS(r.target.getText());
            default -> throw new IllegalStateException("Unknown relation: " + ctx.getText());
        };
    }

    // ── Dependency ────────────────────────────────────────────────────────────

    @Override
    public Object visitDependency(IStarParser.DependencyContext ctx) {
        IStarParser.DepEndContext dependerEnd = ctx.depEnd(0);
        IStarParser.DepEndContext dependeeEnd = ctx.depEnd(1);
        IStarParser.DependumRefContext dependum = ctx.dependumRef();
        return new DependencyCS(
                dependerEnd.IDENT(0).getText(),
                dependerEnd.IDENT().size() > 1 ? dependerEnd.IDENT(1).getText() : null,
                dependum.dependumKind().getText(),
                dependum.IDENT().getText(),
                dependeeEnd.IDENT(0).getText(),
                dependeeEnd.IDENT().size() > 1 ? dependeeEnd.IDENT(1).getText() : null);
    }
}
