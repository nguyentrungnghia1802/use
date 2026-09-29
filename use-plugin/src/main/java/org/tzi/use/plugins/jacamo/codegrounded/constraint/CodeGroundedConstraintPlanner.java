package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.List;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.rule.ImplementationStatus;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionProfile;

/** Plans only the native first-slice invariants whose required rules are implemented. */
public final class CodeGroundedConstraintPlanner {
    public List<NativeConstraintSpec> plan(CodeGroundedRuleCatalog catalog) {
        return plan(catalog, NativeProjectionProfile.forMode(
                org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.FULL));
    }

    public List<NativeConstraintSpec> plan(CodeGroundedRuleCatalog catalog, NativeProjectionProfile profile) {
        requireImplemented(catalog, "A17", "A19", "A20");
        String a17 = profile.materializesOrderEntries()
                ? "self.a17Entries->isUnique(rank) and self.a17Entries->isUnique(member) and "
                + "self.a17Entries->forAll(e | e.rank >= 0 and e.rank < self.a17Entries->size()) and "
                + "self.a17Entries->collect(member)->asSet() = self.plans->asSet()"
                : "self.plans->isUnique(ordinal) and self.plans->forAll(p | p.ordinal >= 0 and "
                + "p.ordinal < self.plans->size())";
        String a19 = profile.materializesOrderEntries()
                ? "self.a19Entries->isUnique(rank) and self.a19Entries->isUnique(member) and "
                + "self.a19Entries->forAll(e | e.rank >= 0 and e.rank < self.a19Entries->size()) and "
                + "self.a19Entries->collect(member)->asSet() = self.bodyElements->asSet()"
                : "self.bodyElements->isUnique(ordinal) and self.bodyElements->forAll(e | e.ordinal >= 0 and "
                + "e.ordinal < self.bodyElements->size())";
        String a20 = profile.materializesOrderEntries()
                ? "self.a19Entries->forAll(e | if e.rank < self.a19Entries->size() - 1 then "
                + "e.member.next = self.a19Entries->any(n | n.rank = e.rank + 1).member "
                + "else e.member.next.oclIsUndefined() endif)"
                : "self.bodyElements->forAll(e | if e.ordinal < self.bodyElements->size() - 1 then "
                + "e.next = self.bodyElements->any(n | n.ordinal = e.ordinal + 1) "
                + "else e.next.oclIsUndefined() endif)";
        return List.of(
                new NativeConstraintSpec("A17OrderConsistent", "PlanLibrary",
                        a17,
                        List.of("A17"), List.of("jason.ast"), Fidelity.EXACT, "CODE_GROUNDED",
                        ConstraintMigrationStatus.NATIVE),
                new NativeConstraintSpec("A19OrderConsistent", "Plan",
                        a19,
                        List.of("A19"), List.of("jason.ast"), Fidelity.EXACT, "CODE_GROUNDED",
                        ConstraintMigrationStatus.NATIVE),
                new NativeConstraintSpec("A20NextAgreesWithA19", "Plan",
                        a20,
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
