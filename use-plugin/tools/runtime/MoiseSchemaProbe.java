import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import moise.os.OS;
import moise.os.OSBuilder;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/** Diagnostic controls only: never rewrites the static import fixture. */
public class MoiseSchemaProbe {
    public static void main(String[] args) throws Exception {
        Path original = Path.of(args[0]), output = Path.of(args[1]);
        String source = Files.readString(original);
        Path namespaced = output.resolve("auction-namespace-only.xml");
        Files.writeString(namespaced, source.replace("<organisational-specification ",
                "<organisational-specification xmlns=\"http://moise.sourceforge.net/os\" os-version=\"1.0\" "));

        // An explicit control using the same pinned builder as the component test.
        // No claim that dropping the source plan/deadline preserves Auction semantics.
        OSBuilder builder = new OSBuilder();
        builder.addRootGroup("auction_group");
        builder.addRole("auction_group", "auctioneer");
        builder.addScheme("auction_scheme", "sell_item");
        builder.addMission("auction_scheme", "run_auction", "sell_item");
        Path control = output.resolve("builder-control.xml");
        builder.save(control.toString());

        var factory = SchemaFactory.newDefaultInstance();
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var schema = factory.newSchema(OS.class.getResource("/xml/os.xsd"));
        List<Map<String,Object>> results = new ArrayList<>();
        for (Path file : List.of(original, namespaced, control)) {
            List<String> errors = new ArrayList<>();
            var validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) { record(e); }
                public void error(SAXParseException e) { record(e); }
                public void fatalError(SAXParseException e) { record(e); }
                private void record(SAXParseException e) {
                    errors.add("line " + e.getLineNumber() + ":" + e.getColumnNumber() + " " + e.getMessage());
                }
            });
            validator.validate(new StreamSource(file.toFile()));
            boolean loadable = errors.isEmpty() && OS.loadOSFromURI(file.toUri().toString()) != null;
            results.add(Map.of("file", file.getFileName().toString(), "schemaValid", errors.isEmpty(),
                    "loadable", loadable, "diagnostics", errors));
        }
        if (!(boolean) results.get(2).get("loadable")) throw new IllegalStateException("PHASE20_OS_CONTROL_FAILED");
        if ((boolean) results.get(0).get("schemaValid") || (boolean) results.get(1).get("schemaValid"))
            throw new IllegalStateException("PHASE20_EXPECTED_FIXTURE_BOUNDARY_CHANGED");
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("results", results);
        report.put("groupBoardStateType", ora4mas.nopl.GroupBoard.class.getMethod("getGrpState").getReturnType().getName());
        report.put("schemeBoardStateType", ora4mas.nopl.SchemeBoard.class.getMethod("getSchState").getReturnType().getName());
        report.put("connectorStateType", "moise.oe.OE");
        report.put("controlEquivalentToAuction", false);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("moise-schema-audit.json").toFile(), report);
    }
}
