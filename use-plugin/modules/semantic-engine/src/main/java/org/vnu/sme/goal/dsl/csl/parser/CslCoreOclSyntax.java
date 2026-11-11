package org.vnu.sme.goal.dsl.csl.parser;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.tzi.use.parser.ParseErrorHandler;
import org.tzi.use.parser.ocl.OCLLexer;
import org.tzi.use.parser.ocl.OCLParser;

/**
 * Syntax validation of M1 OCL bodies, using the host's complete OCL grammar.
 * This is deliberately not a claim of runtime support or expression type checking:
 * those require a backend implementing the revised CSL object/enactment semantics.
 * The old NativeOclEvaluator's restricted subset must not define the v4 language.
 */
final class CslCoreOclSyntax {
    private CslCoreOclSyntax() {}

    static void validate(String expression) {
        StringWriter diagnostics = new StringWriter();
        PrintWriter writer = new PrintWriter(diagnostics);
        ParseErrorHandler errors = new ParseErrorHandler("<invariant>", writer);
        OCLLexer lexer = new OCLLexer(new ANTLRStringStream(expression));
        OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
        lexer.init(errors);
        parser.init(errors);
        try {
            parser.expressionOnly();
        } catch (RecognitionException exception) {
            throw new IllegalArgumentException("Invalid OCL at " + exception.line + ":"
                    + exception.charPositionInLine + ": " + exception, exception);
        }
        writer.flush();
        if (errors.errorCount() != 0) {
            throw new IllegalArgumentException(diagnostics.toString().trim());
        }
    }
}
