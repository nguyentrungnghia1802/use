package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolEntityInstance;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolGroupInstance;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolLink;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolModel;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolPlay;

public final class AolSpecText {
    private static final String INDENT = "    ";

    private AolSpecText() {}

    public static String render(AolModel model) {
        StringBuilder out = new StringBuilder();
        out.append("aol ").append(model.version()).append(' ').append(model.name())
                .append(" for \"").append(model.cslFile()).append("\" {\n\n");
        for (String agent : model.agents()) {
            out.append(INDENT).append("agent ");
            String profile = model.agentProfileRoles().get(agent);
            if (profile != null) out.append(profile).append(" as ");
            out.append(agent);
            renderValues(out, model.agentAttributeValues().getOrDefault(agent, java.util.Map.of()), 2);
        }
        model.groupInstances().forEach(g -> renderGroup(out, g, 1));
        for (AolEntityInstance entity : model.topLevelEntities()) {
            out.append('\n').append(INDENT).append("entity ").append(entity.entityType())
                    .append(" as ").append(entity.instanceId());
            renderValues(out, entity.attributeValues(), 2);
        }
        if (!model.links().isEmpty()) out.append('\n');
        for (AolLink link : model.links()) {
            out.append(INDENT).append("link ").append(link.relationName()).append(": ")
                    .append(link.sourceInstanceId()).append(" -> ")
                    .append(String.join(", ", link.targetInstanceIds())).append(";\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static void renderGroup(StringBuilder out, AolGroupInstance group, int depth) {
        out.append('\n').append(INDENT.repeat(depth)).append("group ").append(group.typeName())
                .append(" as ").append(group.instanceId()).append(" {\n");
        group.attributeValues().forEach((k, v) -> out.append(INDENT.repeat(depth + 1))
                .append(k).append(" = ").append(v).append(";\n"));
        for (AolPlay play : group.plays()) {
            out.append(INDENT.repeat(depth + 1)).append("play ").append(play.roleType())
                    .append(" as ").append(play.instanceId());
            if (play.agentId() != null) out.append(" by ").append(play.agentId());
            renderValues(out, play.attributeValues(), depth + 2);
        }
        for (AolEntityInstance entity : group.entities()) {
            out.append(INDENT.repeat(depth + 1)).append("entity ").append(entity.entityType())
                    .append(" as ").append(entity.instanceId());
            renderValues(out, entity.attributeValues(), depth + 2);
        }
        group.subgroups().forEach(sub -> renderGroup(out, sub, depth + 1));
        out.append(INDENT.repeat(depth)).append("}\n");
    }

    private static void renderValues(StringBuilder out, java.util.Map<String, String> values, int depth) {
        if (values.isEmpty()) { out.append(";\n"); return; }
        out.append(" {\n");
        values.forEach((k, v) -> out.append(INDENT.repeat(depth)).append(k).append(" = ").append(v).append(";\n"));
        out.append(INDENT.repeat(depth - 1)).append("}\n");
    }
}
