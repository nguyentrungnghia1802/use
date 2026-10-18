package org.vnu.sme.goal.dsl.istar.ast;

/** Raw OCL clause attached to an iStar Goal or Task. */
public record IStarOclConstraintCS(Kind kind, String oclBody) {
    public enum Kind { PRE, POST, CONDITION }
}
