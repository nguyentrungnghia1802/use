package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.List;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionProfile;

/** Execution/AST invariants have no target in the domain projection. Structural OCL is planned from OS relations. */
public final class CodeGroundedConstraintPlanner {
    public List<NativeConstraintSpec> plan(CodeGroundedRuleCatalog catalog) { return List.of(); }
    public List<NativeConstraintSpec> plan(CodeGroundedRuleCatalog catalog,NativeProjectionProfile profile) { return List.of(); }
}
