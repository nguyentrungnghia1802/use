package org.tzi.use.plugins.jacamo.materialization;

import org.tzi.use.plugins.jacamo.mapping.StructuralUseGenerator;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;

/** Generates deterministic, inspectable USE specification and initial SOIL command script. */
public final class TextBackend {
    public GeneratedArtifacts generate(String projectName, TransformationPlan structure, InstancePlan instances) {
        String use = new StructuralUseGenerator().generate(projectName, structure);
        StringBuilder cmd = new StringBuilder();
        for (ObjectPlan object : instances.objects())
            cmd.append("!create ").append(object.name()).append(" : ").append(object.className()).append('\n');
        for (ObjectPlan object : instances.objects()) for (var entry : object.values().entrySet())
            cmd.append("!set ").append(object.name()).append('.').append(entry.getKey()).append(" := ")
                    .append(format(entry.getValue())).append('\n');
        for (LinkPlan link : instances.links()) cmd.append("!insert (").append(link.sourceObject()).append(',')
                .append(link.targetObject()).append(") into ").append(link.association()).append('\n');
        return new GeneratedArtifacts(use, cmd.toString());
    }

    private String format(AttributeValue value) {
        if (value instanceof AttributeValue.Text text) return "'" + text.value().replace("\\", "\\\\").replace("'", "\\'") + "'";
        if (value instanceof AttributeValue.IntegerNumber integer) return Long.toString(integer.value());
        return Boolean.toString(((AttributeValue.Bool) value).value());
    }

    public record GeneratedArtifacts(String useModel, String initialCommands) { }
}
