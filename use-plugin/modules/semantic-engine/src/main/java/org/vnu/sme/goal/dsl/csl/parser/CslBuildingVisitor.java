package org.vnu.sme.goal.dsl.csl.parser;


/**
 * =============================================================================
 * MODULE: CSL parse-tree to CST visitor
 * =============================================================================
 * 1. PURPOSE:
 *    Walks the generated ANTLR parse tree and creates typed CSL CST records. Parser contexts are the input; a CSLModelCS tree is the output.
 *
 * 2. CORE MAPPING / LOGIC RULES:
 *    - Map each grammar alternative to its matching *CS node.
 *    - Preserve declaration order and optional clauses for deterministic diagnostics.
 *    - Do not resolve semantic references while the parse tree is being traversed.
 *    - Main operations exposed by this file: visitModel(), enumValue(), entity(), role(), relation(), group(), compatibility().
 *
 * 3. PIPELINE / WORKFLOW:
 *      1. visitModel / visit...(...)
 *      2. visit child grammar contexts
 *      3. construct *CS records
 *      4. return CSLModelCS to model factory
 * =============================================================================
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.vnu.sme.goal.dsl.csl.ast.*;

public final class CslBuildingVisitor extends CSLBaseVisitor<CslModelCS> {

    @Override public CslModelCS visitModel(CSLParser.ModelContext ctx) {
        List<CslEnumCS> enums = new ArrayList<>(); List<CslDataTypeCS> dataTypes = new ArrayList<>();
        List<CslEntityCS> entities = new ArrayList<>();
        List<CslRoleCS> roles = new ArrayList<>(); List<CslRelationCS> relations = new ArrayList<>();
        List<CslGroupCS> groups = new ArrayList<>(); List<CslCompatibilityCS> compatibilities = new ArrayList<>();
        List<CslInvariantCS> invariants = new ArrayList<>();
        for (var d : ctx.topLevelDecl()) {
            if (d.enumDecl() != null) enums.add(enumValue(d.enumDecl()));
            else if (d.datatypeDecl() != null)
                dataTypes.add(new CslDataTypeCS(d.datatypeDecl().IDENT().getText(), location(d.datatypeDecl())));
            else if (d.entityDecl() != null) entities.add(entity(d.entityDecl()));
            else if (d.roleDecl() != null) roles.add(role(d.roleDecl()));
            else if (d.orgContextDecl() != null) {
                orgContext(d.orgContextDecl(), entities, roles, groups, compatibilities);
            }
            else if (d.entityRelationDecl() != null) relations.add(relation(d.entityRelationDecl()));
            else if (d.compatibilityDecl() != null)
                compatibilities.add(compatibility(d.compatibilityDecl(), "__model__"));
            else if (d.invariantDecl() != null) invariants.add(invariant(d.invariantDecl()));
        }
        return new CslModelCS(ctx.VERSION().getText(), ctx.IDENT().getText(), enums, dataTypes, entities,
                roles, relations, groups, compatibilities, invariants, location(ctx));
    }

    private static CslInvariantCS invariant(CSLParser.InvariantDeclContext c) {
        return new CslInvariantCS(c.IDENT(0).getText(), c.IDENT(1).getText(),
                c.oclExpression().oclToken().stream().map(ParserRuleContext::getText)
                        .collect(java.util.stream.Collectors.joining(" ")), location(c));
    }

    private static CslEnumCS enumValue(CSLParser.EnumDeclContext c) {
        List<String> ids = c.IDENT().stream().map(TerminalNode::getText).toList();
        return new CslEnumCS(ids.get(0), ids.subList(1, ids.size()), location(c));
    }
    private static CslEntityCS entity(CSLParser.EntityDeclContext c) {
        return new CslEntityCS(c.IDENT().getText(), parent(c.specializesClause()), attrs(c.attributeBlock()), location(c));
    }
    private static CslRoleCS role(CSLParser.RoleDeclContext c) {
        return new CslRoleCS(c.IDENT().getText(), parent(c.specializesClause()).stream().toList(),
                attrs(c.attributeBlock()), location(c));
    }
    private static CslRelationCS relation(CSLParser.EntityRelationDeclContext c) {
        List<CslEndpointCS> ends = c.endpointDecl().stream().map(e -> new CslEndpointCS(
                e.IDENT(0).getText(), cardinality(e.cardinality()),
                e.IDENT().size() > 1 ? Optional.of(e.IDENT(1).getText()) : Optional.empty(), location(e))).toList();
        return new CslRelationCS(c.relationKind() == null ? "association" : c.relationKind().getText(),
                c.IDENT().getText(), ends, location(c));
    }
    private static CslCompatibilityCS compatibility(CSLParser.CompatibilityDeclContext c, String groupName) {
        return new CslCompatibilityCS(c.IDENT(0).getText(), c.IDENT(1).getText(), true,
                groupName, List.of(), location(c));
    }

    private static void orgContext(CSLParser.OrgContextDeclContext context,
                                   List<CslEntityCS> entities,
                                   List<CslRoleCS> roles,
                                   List<CslGroupCS> groups,
                                   List<CslCompatibilityCS> compatibilities) {
        String name = context.IDENT().getText();
        List<CslAttributeCS> attributes = new ArrayList<>();
        List<CslGroupMemberCS> members = new ArrayList<>();
        List<CSLParser.OrgContextDeclContext> nested = new ArrayList<>();
        for (var item : context.orgContextItem()) {
            if (item.attributeDecl() != null) {
                attributes.add(attribute(item.attributeDecl()));
            } else if (item.entityDecl() != null) {
                entities.add(entity(item.entityDecl()));
                members.add(requiredMember(item.entityDecl().IDENT().getText(), item.entityDecl()));
            } else if (item.roleDecl() != null) {
                roles.add(role(item.roleDecl()));
                members.add(requiredMember(item.roleDecl().IDENT().getText(), item.roleDecl()));
            } else if (item.orgContextDecl() != null) {
                members.add(requiredMember(item.orgContextDecl().IDENT().getText(), item.orgContextDecl()));
                nested.add(item.orgContextDecl());
            } else if (item.compatibilityDecl() != null) {
                compatibilities.add(compatibility(item.compatibilityDecl(), name));
            }
        }
        // Keep declaration order, with each parent before its nested contexts.
        // Selecting a runtime root and population is outside the M1 grammar.
        groups.add(new CslGroupCS(name, Optional.empty(), attributes, members,
                List.of(), true, location(context)));
        nested.forEach(child -> orgContext(child, entities, roles, groups, compatibilities));
    }

    private static CslGroupMemberCS requiredMember(String type, ParserRuleContext context) {
        // Legacy AST carrier only: the v4 resolver ignores these population
        // bounds. Containment of a declaration does not require an M0 instance.
        CslSourceLocationCS loc = location(context);
        return new CslGroupMemberCS(type,
                new CslCardinalityCS("1", Optional.of("1"), loc), loc);
    }
    private static List<CslAttributeCS> attrs(CSLParser.AttributeBlockContext b) { return b==null?List.of():b.attributeDecl().stream().map(CslBuildingVisitor::attribute).toList(); }
    private static CslAttributeCS attribute(CSLParser.AttributeDeclContext c) {
        String modifiers = c.attributeModifier() == null ? "" : c.attributeModifier().getText();
        boolean optional = modifiers.contains("optional");
        boolean required = modifiers.contains("required");
        boolean mut = modifiers.contains("mutable");
        Optional<String> def=c.defaultClause()==null?Optional.empty():Optional.of(c.defaultClause().defaultValue().getText());
        return new CslAttributeCS(c.IDENT(0).getText(),c.IDENT(1).getText(),optional,required,mut,def,location(c));
    }
    private static Optional<String> parent(CSLParser.SpecializesClauseContext c) { return c==null?Optional.empty():Optional.of(c.IDENT().getText()); }
    private static CslCardinalityCS cardinality(CSLParser.CardinalityContext c) {
        if(c.getText().equals("[*]")) return new CslCardinalityCS("0",Optional.empty(),location(c));
        List<TerminalNode> ints=c.INT(); String min=ints.get(0).getText();
        if(!c.getText().contains("..")) return new CslCardinalityCS(min,Optional.of(min),location(c));
        return new CslCardinalityCS(min,c.getText().contains("*")?Optional.empty():Optional.of(ints.get(1).getText()),location(c));
    }
    private static CslSourceLocationCS location(ParserRuleContext c) { return new CslSourceLocationCS(c.getStart().getLine(),c.getStart().getCharPositionInLine()); }
}
