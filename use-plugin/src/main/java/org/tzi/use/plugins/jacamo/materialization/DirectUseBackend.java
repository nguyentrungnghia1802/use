package org.tzi.use.plugins.jacamo.materialization;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;

/** Direct supported USE API backend sharing exactly the same plans as the text backend. */
public final class DirectUseBackend {
    public Result materialize(TextBackend.GeneratedArtifacts artifacts, InstancePlan plan) {
        StringWriter compilerErrors = new StringWriter();
        var model = USECompiler.compileSpecification(new ByteArrayInputStream(
                        artifacts.useModel().getBytes(StandardCharsets.UTF_8)), "generated.use",
                URI.create("memory:/generated.use"), new PrintWriter(compilerErrors), new ModelFactory());
        if (model == null) throw new MaterializationException("USE_MODEL_INVALID", compilerErrors.toString());
        MSystem system = new MSystem(model);
        List<Diagnostic> diagnostics = new ArrayList<>(plan.diagnostics());
        try {
            for (ObjectPlan object : plan.objects()) {
                var cls = model.getClass(object.className());
                if (cls == null) throw new MaterializationException("USE_CLASS_MISSING", object.className());
                MObject created = system.state().createObject(cls, object.name());
                for (var entry : object.values().entrySet()) {
                    var attribute = cls.attribute(entry.getKey(), true);
                    if (attribute == null) throw new MaterializationException("USE_ATTRIBUTE_MISSING",
                            object.className() + "." + entry.getKey());
                    created.state(system.state()).setAttributeValue(attribute, value(entry.getValue()));
                }
            }
            for (LinkPlan link : plan.links()) {
                var association = model.getAssociation(link.association());
                MObject source = system.state().objectByName(link.sourceObject());
                MObject target = system.state().objectByName(link.targetObject());
                if (association == null || source == null || target == null)
                    throw new MaterializationException("USE_LINK_ENDPOINT_MISSING", link.toString());
                system.state().createLink(association, List.of(source, target), null);
            }
        } catch (MaterializationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MaterializationException("USE_STATE_MUTATION_FAILED", exception.getMessage(), exception);
        }
        StringWriter validation = new StringWriter();
        boolean structure = system.state().checkStructure(new PrintWriter(validation));
        boolean allChecks = system.state().check(new PrintWriter(validation), false, true, true, List.of());
        if (!structure || !allChecks) diagnostics.add(new Diagnostic("USE_INITIAL_STATE_INVALID", Severity.ERROR,
                Phase.MATERIALIZATION, null, null, null,
                "Initial USE state failed structure or invariant validation", validation.toString(),
                "Resolve traced multiplicity/containment diagnostics before accepting the initial state"));
        return new Result(system, structure, allChecks, validation.toString(), diagnostics);
    }

    private Value value(AttributeValue value) {
        if (value instanceof AttributeValue.Text text) return new StringValue(text.value());
        if (value instanceof AttributeValue.IntegerNumber integer) {
            try { return new IntegerValue(Math.toIntExact(integer.value())); }
            catch (ArithmeticException exception) { throw new MaterializationException("USE_INTEGER_OUT_OF_RANGE", Long.toString(integer.value())); }
        }
        return BooleanValue.get(((AttributeValue.Bool) value).value());
    }

    public record Result(MSystem system, boolean structureValid, boolean invariantsValid,
                         String validationOutput, List<Diagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
    }
}
