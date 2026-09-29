package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.List;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.rule.ImplementationStatus;

/** Plans only the native first-slice invariants whose required rules are implemented. */
public final class CodeGroundedConstraintPlanner {
    public List<NativeConstraintSpec> plan(CodeGroundedRuleCatalog catalog) {
        requireImplemented(catalog, "A17", "A19", "A20");
        return List.of(
                new NativeConstraintSpec("A17OrderConsistent", "PlanLibrary",
                        "self.a17Entries->isUnique(rank) and self.a17Entries->isUnique(member) and "
                                + "self.a17Entries->forAll(e | e.rank >= 0 and e.rank < self.a17Entries->size()) and "
                                + "self.a17Entries->collect(member)->asSet() = self.plans->asSet()",
                        List.of("A17"), List.of("jason.ast"), Fidelity.EXACT, "CODE_GROUNDED",
                        ConstraintMigrationStatus.NATIVE),
                new NativeConstraintSpec("A19OrderConsistent", "Plan",
                        "self.a19Entries->isUnique(rank) and self.a19Entries->isUnique(member) and "
                                + "self.a19Entries->forAll(e | e.rank >= 0 and e.rank < self.a19Entries->size()) and "
                                + "self.a19Entries->collect(member)->asSet() = self.bodyElements->asSet()",
                        List.of("A19"), List.of("jason.ast"), Fidelity.EXACT, "CODE_GROUNDED",
                        ConstraintMigrationStatus.NATIVE),
                new NativeConstraintSpec("A20NextAgreesWithA19", "Plan",
                        "self.a19Entries->forAll(e | if e.rank < self.a19Entries->size() - 1 then "
                                + "e.member.next = self.a19Entries->any(n | n.rank = e.rank + 1).member "
                                + "else e.member.next.oclIsUndefined() endif)",
                        List.of("A19", "A20"), List.of("jason.ast"), Fidelity.EXACT, "CODE_GROUNDED",
                        ConstraintMigrationStatus.NATIVE),
                new NativeConstraintSpec("C08LiveObservablePropertyAvailable", "Artifact", "true",
                        List.of("C08"), List.of("cartago.live-observable-property"), Fidelity.EXACT,
                        "CODE_GROUNDED", ConstraintMigrationStatus.SKIPPED_CAPABILITY));
    }

    private static void requireImplemented(CodeGroundedRuleCatalog catalog, String... ruleIds) {
        for (String ruleId : ruleIds)
            if (catalog.require(ruleId).implementationStatus() != ImplementationStatus.IMPLEMENTED)
                throw new IllegalStateException("NATIVE_CONSTRAINT_RULE_NOT_IMPLEMENTED: " + ruleId);
    }
}
