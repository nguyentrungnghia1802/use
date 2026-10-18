package org.vnu.sme.goal.dsl.istar.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModelValidator;

/**
 * Compiles i* concrete syntax into the MM. The compiler owns parsing and AST -> MM
 * orchestration; semantic invariants of the model are enforced by {@link GoalModelValidator}.
 */
public final class IStarCompiler {

    public record Result(GoalModel model, List<String> errors) {
        public boolean ok() { return errors.isEmpty(); }
    }

    public static Result compile(Path file) throws IOException {
        return compileStream(CharStreams.fromPath(file));
    }

    public static Result compile(String source) {
        return compileStream(CharStreams.fromString(source));
    }

    private static Result compileStream(CharStream chars) {
        List<String> errors = new ArrayList<>();

        IStarLexer  lexer  = new IStarLexer(chars);
        IStarParser parser = new IStarParser(new CommonTokenStream(lexer));

        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        ANTLRErrorListener errListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> rec, Object sym, int line, int col,
                                    String msg, RecognitionException e) {
                errors.add("line " + line + ":" + col + " " + msg);
            }
        };
        lexer.addErrorListener(errListener);
        parser.addErrorListener(errListener);

        IStarParser.ModelContext tree = parser.model();
        if (!errors.isEmpty()) return new Result(null, errors);

        org.vnu.sme.goal.dsl.istar.ast.IStarModelCS ast = IStarBuildingVisitor.build(tree);
        GoalModel model;
        try {
            model = IStarModelFactory.build(ast);
        } catch (IllegalArgumentException ex) {
            return new Result(null, List.of(ex.getMessage()));
        }

        List<String> semErrors = GoalModelValidator.validate(model);
        if (!semErrors.isEmpty()) return new Result(null, semErrors);

        return new Result(model, Collections.emptyList());
    }
}
