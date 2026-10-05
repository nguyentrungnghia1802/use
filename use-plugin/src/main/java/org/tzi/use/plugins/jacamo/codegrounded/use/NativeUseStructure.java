package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.tzi.use.uml.mm.MModel;

/** Canonical structural signature used by native build and export/recompile validation. */
public final class NativeUseStructure {
    private NativeUseStructure() { }

    public static List<String> signature(MModel model) {
        List<String> rows = new ArrayList<>();
        model.enumTypes().forEach(type -> rows.add("enum|" + type.name() + "|" + String.join(",", type.getLiterals())));
        model.classes().forEach(cls -> {
            rows.add("class|" + cls.name() + "|" + cls.isAbstract()+"|associationClass="+(cls instanceof org.tzi.use.uml.mm.MAssociationClass));
            cls.parents().forEach(parent -> rows.add("generalization|" + cls.name() + "|" + parent.name()));
            annotations(rows, "class|" + cls.name(), cls);
            cls.attributes().forEach(attribute -> {
                rows.add("attribute|" + cls.name() + "|" + attribute.name()+ "|" + attribute.type() + "|derived=" + attribute.getDeriveExpression());
                annotations(rows,"attribute|"+cls.name()+"|"+attribute.name(),attribute);
            });
            cls.operations().forEach(operation -> {
                rows.add("operation|" + cls.name() + "|" + operation.name()+ "|" + operation.signature());
                annotations(rows,"operation|"+cls.name()+"|"+operation.name(),operation);
            });
        });
        model.associations().forEach(association -> {
            rows.add("association|" + association.name()+"|associationClass="+(association instanceof org.tzi.use.uml.mm.MAssociationClass));
            annotations(rows, "association|" + association.name(), association);
            for (int index = 0; index < association.associationEnds().size(); index++) {
                var end = association.associationEnds().get(index);
                rows.add("associationEnd|" + association.name() + "|" + index + "|" + end.cls().name() + "|"
                        + end.name() + "|" + end.multiplicity() + "|" + end.aggregationKind() + "|" + end.isOrdered());
            }
        });
        model.classInvariants().forEach(invariant -> {
            rows.add("invariant|" + invariant.cls().name() + "|" + invariant.name() + "|" + invariant.bodyExpression());
            annotations(rows,"invariant|"+invariant.qualifiedName(),invariant);
        });
        rows.sort(String::compareTo);
        return List.copyOf(rows);
    }

    private static void annotations(List<String> rows, String identity, org.tzi.use.uml.mm.Annotatable element) {
        element.getAllAnnotations().forEach((name, value) -> value.getValues().forEach((key, text) ->
                rows.add("annotation|" + identity + "|" + name + "|" + key + "|" + text)));
    }

    public static String sha256(MModel model) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", signature(model)).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
