package org.tzi.use.semantic.engine.use75;

import java.io.PrintWriter;
import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.util.uml.sorting.UseFileOrderComparator;
import org.tzi.use.util.uml.sorting.UseModelElementFileOrderComparator;

/** Host 7.5 visitor adapter: visitModel omits dataTypes(), although visitDataType exists.
 * Reuses the host visitors for every declaration/expression. Used only for canonical CSL v4.
 */
public final class Use75ModelPrinter extends MMPrintVisitor {
    public Use75ModelPrinter(PrintWriter output) { super(output); }
    @Override public void visitAnnotation(MElementAnnotation annotation) {
        // Map.of iteration is salted per JVM. Reuse host syntax with a sorted
        // read-only copy so the exact replay baseline is portable across PIDs.
        super.visitAnnotation(new MElementAnnotation(annotation.getName(),new TreeMap<>(annotation.getValues())));
    }
    @Override public void visitModel(MModel model) {
        model.getAllAnnotations().values().forEach(a -> a.processWithVisitor(this));
        println(keyword("model") + ws() + id(model.name())); println();
        EnumType[] enumerations = model.enumTypes().toArray(EnumType[]::new);
        Arrays.sort(enumerations, new UseFileOrderComparator());
        for (var enumeration : enumerations) visitEnum(enumeration);
        println();
        // Compatibility addition missing from host visitModel. No manual datatype syntax.
        model.dataTypes().stream().sorted(Comparator.comparing(MDataType::name)).forEach(t -> { t.processWithVisitor(this); println(); });
        Set<MModelElement> declarations = new HashSet<>();
        declarations.addAll(model.classes()); declarations.addAll(model.associations());
        var ordered = new ArrayList<>(declarations);
        ordered.sort(new UseModelElementFileOrderComparator());
        for (var declaration : ordered) { declaration.processWithVisitor(this); println(); }
        println(keyword("constraints"));
        MClassInvariant[] invariants = model.classInvariants().toArray(MClassInvariant[]::new);
        Arrays.sort(invariants, new UseFileOrderComparator());
        for (var invariant : invariants) { invariant.processWithVisitor(this); println(); }
        MPrePostCondition[] conditions = model.prePostConditions().toArray(MPrePostCondition[]::new);
        Arrays.sort(conditions, new UseFileOrderComparator());
        for (var condition : conditions) { condition.processWithVisitor(this); println(); }
    }
}
