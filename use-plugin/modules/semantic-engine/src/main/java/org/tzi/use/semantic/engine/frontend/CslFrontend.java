package org.tzi.use.semantic.engine.frontend;

import java.util.*;
import java.util.regex.Pattern;
import org.tzi.use.semantic.engine.api.EngineDiagnostic;
import org.vnu.sme.goal.dsl.csl.ast.CslModelCS;
import org.vnu.sme.goal.dsl.csl.mm.*;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Stateless admission around the original compiler/factory/validator. No state backend. */
public final class CslFrontend {
    private static final Pattern LOCATION = Pattern.compile("^(.*?):(\\d+):(\\d+): (syntax|semantic|OCL): (.*)$");
    public record Result(CslModelCS ast, CslModel model, List<EngineDiagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        public boolean ok() { return model != null && diagnostics.isEmpty(); }
        public CslModel requireModel() {
            if (!ok()) throw new org.tzi.use.semantic.engine.api.EngineAdmissionException(diagnostics);
            return model;
        }
    }

    public Result compile(String source, String sourceName) {
        Objects.requireNonNull(source); Objects.requireNonNull(sourceName);
        CslCompiler.Result original = CslCompiler.compile(source, sourceName);
        var diagnostics = new ArrayList<EngineDiagnostic>();
        for (String error : original.errors()) {
            var location = LOCATION.matcher(error);
            if (location.matches()) {
                Stage stage = location.group(4).equals("semantic") ? Stage.MODEL_VALIDATION : Stage.PARSE;
                diagnostics.add(new EngineDiagnostic(stage, Kind.ERROR, "ORIGINAL_CSL_" + location.group(4).toUpperCase(Locale.ROOT),
                        location.group(1), Integer.parseInt(location.group(2)), Integer.parseInt(location.group(3)), error));
            } else diagnostics.add(EngineDiagnostic.error(Stage.MODEL_VALIDATION, "ORIGINAL_CSL", sourceName, error));
        }
        if (original.ok()) diagnostics.addAll(validate(original.model(), sourceName).diagnostics());
        return new Result(original.ast(), original.model(), diagnostics);
    }

    /** Programmatically built M1 models obey the same original well-formedness rules. */
    public Result validate(CslModel model, String sourceName) {
        Objects.requireNonNull(model); Objects.requireNonNull(sourceName);
        var diagnostics = new ArrayList<EngineDiagnostic>();
        if (!model.isRevisedCore()) diagnostics.add(unsupported(Stage.CAPABILITY, "CSL_VERSION_NOT_ADMITTED", sourceName,
                "Native backend profile requires CSL v4; original parser acceptance is not runtime admission: " + model.version()));
        CslSemanticValidator.validate(model).forEach(error -> diagnostics.add(
                EngineDiagnostic.error(Stage.MODEL_VALIDATION, "ORIGINAL_CSL_VALIDATOR", sourceName, error)));
        return new Result(null, model, diagnostics);
    }
}
