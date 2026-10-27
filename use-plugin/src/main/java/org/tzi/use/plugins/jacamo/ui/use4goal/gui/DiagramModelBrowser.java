package org.tzi.use.plugins.jacamo.ui.use4goal.gui;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;

import org.vnu.sme.goal.dsl.csl.mm.*;
import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;

/** Read-only tree browser for the Goal plugin's diagram models. */
@SuppressWarnings("serial")
public final class DiagramModelBrowser extends JPanel {
    private final JTree tree = new JTree();
    private final JEditorPane details = new JEditorPane("text/html", "");

    private DiagramModelBrowser(DefaultMutableTreeNode root) {
        super(new BorderLayout());
        tree.setModel(new DefaultTreeModel(root));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(this::selectionChanged);
        ToolTipManager.sharedInstance().registerComponent(tree);
        details.setEditable(false);
        details.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(details));
        split.setResizeWeight(0.72);
        split.setDividerLocation(360);
        split.setOneTouchExpandable(true);
        add(split, BorderLayout.CENTER);
        expand(root.getChildCount() < 12 ? 3 : 1);
        tree.setSelectionRow(0);
    }

    public static DiagramModelBrowser forCsl(CslModel model) {
        DefaultMutableTreeNode root = node("CSL: " + model.name(), "CSL state specification",
                "Version", model.version(),
                model.rootGroup().isOrganizationalContext() ? "Root organizational context" : "Root group",
                model.rootGroup().name());
        DefaultMutableTreeNode enums = group(root, "Enums", model.enums().size());
        model.enums().forEach(value -> enums.add(node("Enum: " + value.name(), "Enumeration",
                "Literals", String.join(", ", value.literals()))));
        DefaultMutableTreeNode roles = group(root, "Roles", model.roles().size());
        model.roles().forEach(role -> {
            DefaultMutableTreeNode roleNode = node((role.isAbstract() ? "Abstract role: " : "Role: ") + role.name(),
                    "Role definition", "Parents", String.join(", ", role.parentRoles()));
            roles.add(roleNode);
            role.attributes().forEach(a -> roleNode.add(attribute(a)));
        });
        DefaultMutableTreeNode entities = group(root, "Entities", model.entities().size());
        model.entities().forEach(entity -> {
            DefaultMutableTreeNode entityNode = node("Entity: " + entity.name(), "Entity definition");
            entities.add(entityNode);
            entity.attributes().forEach(a -> entityNode.add(attribute(a)));
        });
        root.add(cslGroup(model.rootGroup(), true));
        return new DiagramModelBrowser(root);
    }

    public static DiagramModelBrowser forIStar(GoalModel model) {
        DefaultMutableTreeNode root = node("iStar: " + model.getName(), "iStar goal model",
                "Actors", model.getActors().size(), "Dependencies", model.getDependencies().size());
        DefaultMutableTreeNode actors = group(root, "Actors", model.getActors().size());
        for (Actor actor : model.getActors()) {
            DefaultMutableTreeNode actorNode = node(actor.getClass().getSimpleName() + ": " + actor.name(),
                    "Intentional actor", "Elements", actor.elements().size());
            actors.add(actorNode);
            DefaultMutableTreeNode elements = group(actorNode, "Intentional elements", actor.elements().size());
            actor.elements().forEach(element -> elements.add(iStarElement(element)));
            addRelationGroup(actorNode, "Refinements", actor.refinements());
            addRelationGroup(actorNode, "Contributions", actor.contributions());
            addRelationGroup(actorNode, "Qualifications", actor.qualifications());
            addRelationGroup(actorNode, "Needed-by", actor.neededBys());
            addRelationGroup(actorNode, "Associations", actor.associations());
        }
        DefaultMutableTreeNode dependencies = group(root, "Dependencies", model.getDependencies().size());
        model.getDependencies().forEach(d -> dependencies.add(node(d.depender() + " -> " + d.dependee(),
                "iStar dependency", "Depender element", d.dependerElmt(),
                "Dependum type", d.dependum().getClass().getSimpleName(),
                "Dependum", d.dependum().id(), "Dependee element", d.dependeeElmt())));
        return new DiagramModelBrowser(root);
    }

    private static DefaultMutableTreeNode cslGroup(CslGroup value, boolean rootGroup) {
        String prefix = value.isOrganizationalContext()
                ? (rootGroup ? "Root orgContext: " : "Nested orgContext: ")
                : (rootGroup ? "Root group: " : "Subgroup: ");
        DefaultMutableTreeNode result = node(prefix + value.name(),
                value.isOrganizationalContext()
                        ? "CSL organizational context" : "CSL group specification");
        DefaultMutableTreeNode roles = group(result, "Role memberships", value.roles().size());
        value.roles().forEach(v -> roles.add(node(v.roleName() + " " + cardinality(v.cardinality()), "Role membership")));
        DefaultMutableTreeNode entities = group(result, "Entity memberships", value.entities().size());
        value.entities().forEach(v -> entities.add(node(v.entityName() + " " + cardinality(v.cardinality()), "Entity membership")));
        DefaultMutableTreeNode subgroups = group(result, "Subgroups", value.subgroups().size());
        value.subgroups().forEach(v -> subgroups.add(cslGroup(v.group(), false)));
        DefaultMutableTreeNode compatibility = group(result, "Compatibility", value.compatibilities().size());
        value.compatibilities().forEach(v -> compatibility.add(node(v.fromRole() + " <-> " + v.toRole(),
                "Role compatibility", "Type", v.type().sourceName(), "Scope", v.scope().sourceName())));
        DefaultMutableTreeNode relations = group(result, "Role-entity relations", value.roleEntityRelations().size());
        value.roleEntityRelations().forEach(v -> relations.add(node(v.name(), "Role-entity relation",
                "Type", v.type().sourceName(), "Source role", v.sourceRole().name(),
                "Target entity", v.targetEntity().name(), "Scope", v.scope().sourceName())));
        DefaultMutableTreeNode cardinalities = group(result, "Cardinality constraints", value.cardinalityConstraints().size());
        value.cardinalityConstraints().forEach(v -> cardinalities.add(node(v.targetKind().sourceName() + ": "
                + v.targetName() + " " + cardinality(v.cardinality()), "Cardinality constraint")));
        return result;
    }

    private static DefaultMutableTreeNode attribute(CslAttribute value) {
        return node("Attribute: " + value.name(), "Attribute", "Type", value.type().sourceName(),
                "Optional", value.optional(), "Mutable", value.mutable(), "Default", value.defaultValue().orElse(null));
    }

    private static DefaultMutableTreeNode iStarElement(IntentionalElement element) {
        String type = element.getClass().getSimpleName();
        if (element instanceof Goal goal) type += " (" + goal.goalType() + ")";
        if (element instanceof Goal goal) {
            return node(type + ": " + element.id(), "iStar goal",
                    "Condition OCL", goal.conditions().stream()
                            .map(org.vnu.sme.goal.dsl.istar.mm.IStarOclConstraint::oclBody).findFirst().orElse(null));
        }
        if (element instanceof org.vnu.sme.goal.dsl.istar.mm.Task task) {
            return node(type + ": " + element.id(), "iStar task",
                    "Pre OCL", task.preconditions().stream()
                            .map(org.vnu.sme.goal.dsl.istar.mm.IStarOclConstraint::oclBody).findFirst().orElse(null),
                    "Post OCL", task.postconditions().stream()
                            .map(org.vnu.sme.goal.dsl.istar.mm.IStarOclConstraint::oclBody).findFirst().orElse(null));
        }
        return node(type + ": " + element.id(), "iStar intentional element");
    }

    private static void addRelationGroup(DefaultMutableTreeNode parent, String label, List<?> values) {
        DefaultMutableTreeNode result = group(parent, label, values.size());
        values.forEach(value -> result.add(node(relationLabel(value), value.getClass().getSimpleName())));
    }

    private static String relationLabel(Object value) {
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.AndRefinement v)
            return "AND: " + String.join(", ", v.children()) + " -> " + v.parent();
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.OrRefinement v)
            return "OR: " + v.child() + " -> " + v.parent();
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.Contribution v)
            return v.element() + " -" + v.type().label() + "-> " + v.quality();
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.Qualification v)
            return v.quality() + " -> " + v.element();
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.NeededBy v)
            return v.resource() + " -> " + v.task();
        if (value instanceof org.vnu.sme.goal.dsl.istar.mm.Association v)
            return v.actor() + " -" + v.kind() + "-> " + v.target();
        return String.valueOf(value);
    }

    private static DefaultMutableTreeNode group(DefaultMutableTreeNode parent, String label, int size) {
        DefaultMutableTreeNode result = node(label + " (" + size + ")", label);
        parent.add(result);
        return result;
    }

    private static DefaultMutableTreeNode node(String label, String description, Object... fields) {
        return new DefaultMutableTreeNode(new Entry(label, html(description, fields)));
    }

    private static String html(String description, Object... fields) {
        StringBuilder out = new StringBuilder("<html><body><b>").append(escape(description)).append("</b>");
        for (int i = 0; i + 1 < fields.length; i += 2) {
            Object value = fields[i + 1];
            if (value == null || String.valueOf(value).isBlank()) continue;
            out.append("<br><b>").append(escape(String.valueOf(fields[i]))).append(":</b> ")
                    .append(escape(String.valueOf(value)).replace("\n", "<br>"));
        }
        return out.append("</body></html>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String named(String id, String name) {
        return name == null || name.isBlank() ? id : id + " (" + name + ")";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String preview(String value) {
        if (!hasText(value)) return "";
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 72 ? oneLine : oneLine.substring(0, 69) + "...";
    }

    private static String cardinality(CslCardinality value) {
        return "[" + value.min() + ".." + (value.max().isPresent() ? value.max().getAsInt() : "*") + "]";
    }

    private void selectionChanged(TreeSelectionEvent event) {
        Object selected = tree.getLastSelectedPathComponent();
        if (selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Entry entry) {
            details.setText(entry.details());
            details.setCaretPosition(0);
        }
    }

    private void expand(int depth) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            if (tree.getPathForRow(row).getPathCount() <= depth) tree.expandRow(row);
        }
    }

    private record Entry(String label, String details) {
        @Override public String toString() { return label; }
    }
}
