package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Resolved source symbol indexed in the source catalogue.
 */
public record SourceSymbol(
        SourceReference.Kind kind,
        String qualifiedName,
        String owner,
        String simpleName,
        String signature,
        String sourceDigest,
        String sourcePosition) {

    public SourceSymbol {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        simpleName = Objects.requireNonNullElse(simpleName, qualifiedName);
    }

    public static SourceSymbol artifact(String className, String digest) {
        return new SourceSymbol(SourceReference.Kind.ARTIFACT, className, null, className, className, digest, null);
    }

    public static SourceSymbol property(String className, String propertyName) {
        return new SourceSymbol(SourceReference.Kind.ARTIFACT_PROPERTY, className + "/" + propertyName,
                className, propertyName, propertyName, null, null);
    }

    public static SourceSymbol field(String className, String fieldName) {
        return new SourceSymbol(SourceReference.Kind.ARTIFACT_FIELD, className + "/" + fieldName,
                className, fieldName, fieldName, null, null);
    }
    public static SourceSymbol field(String className,String fieldName,String javaType,String digest) {
        return new SourceSymbol(SourceReference.Kind.ARTIFACT_FIELD,className+"/"+fieldName,className,fieldName,javaType,digest,null);
    }

    public static SourceSymbol operation(String className, String opName, String signature) {
        return new SourceSymbol(SourceReference.Kind.ARTIFACT_OPERATION, className + "/" + opName,
                className, opName, signature, null, null);
    }

    public static SourceSymbol moiseGroup(String org, String groupName) {
        return new SourceSymbol(SourceReference.Kind.MOISE_GROUP, org + "/" + groupName,
                org, groupName, groupName, null, null);
    }

    public static SourceSymbol moiseRole(String org, String roleName) {
        return new SourceSymbol(SourceReference.Kind.MOISE_ROLE, org + "/" + roleName,
                org, roleName, roleName, null, null);
    }

    public static SourceSymbol moiseScheme(String org, String schemeName) {
        return new SourceSymbol(SourceReference.Kind.MOISE_SCHEME, org + "/" + schemeName,
                org, schemeName, schemeName, null, null);
    }

    public static SourceSymbol moiseGoal(String org, String goalName) {
        return new SourceSymbol(SourceReference.Kind.MOISE_GOAL, org + "/" + goalName,
                org, goalName, goalName, null, null);
    }

    public static SourceSymbol agent(String name) {
        return new SourceSymbol(SourceReference.Kind.AGENT, name, null, name, name, null, null);
    }

    public static SourceSymbol aslGoal(String aslPath, String goalTrigger) {
        return new SourceSymbol(SourceReference.Kind.ASL_GOAL, aslPath + "/" + goalTrigger,
                aslPath, goalTrigger, goalTrigger, null, null);
    }

    public static SourceSymbol aslPlanStep(String aslPath, String planTrigger, String stepName, int firstIndex, int afterIndex) {
        return new SourceSymbol(SourceReference.Kind.ASL_PLAN_STEP,
                aslPath + "/" + planTrigger + "/" + stepName + "[" + firstIndex + ".." + afterIndex + "]",
                aslPath, stepName, planTrigger, null, "[" + firstIndex + ".." + afterIndex + "]");
    }
}

