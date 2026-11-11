package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import java.util.regex.*;

/**
 * Parsed, strongly-typed source reference in compact mapping v3.
 */
public sealed interface SourceReference {

    enum Kind {
        ARTIFACT,
        ARTIFACT_PROPERTY,
        ARTIFACT_FIELD,
        ARTIFACT_OPERATION,
        MOISE_GROUP,
        MOISE_ROLE,
        MOISE_SCHEME,
        MOISE_GOAL,
        AGENT,
        ASL_GOAL,
        ASL_PLAN_STEP
    }

    Kind kind();
    String raw();
    default String rawReference() { return raw(); }

    record ArtifactRef(String className, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ARTIFACT; }
    }

    record ArtifactPropertyRef(String className, String propertyName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ARTIFACT_PROPERTY; }
    }

    record ArtifactFieldRef(String className, String fieldName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ARTIFACT_FIELD; }
    }

    record ArtifactOpRef(String className, String operationName, List<String> paramTypes, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ARTIFACT_OPERATION; }
    }

    record MoiseGroupRef(String organization, String groupName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.MOISE_GROUP; }
    }

    record MoiseRoleRef(String organization, String roleName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.MOISE_ROLE; }
    }

    record MoiseSchemeRef(String organization, String schemeName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.MOISE_SCHEME; }
    }

    record MoiseGoalRef(String organization, String goalName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.MOISE_GOAL; }
    }

    record AgentRef(String agentOrProgram, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.AGENT; }
    }

    record AslGoalRef(String aslPath, String goalTrigger, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ASL_GOAL; }
    }

    record AslPlanStepRef(String aslPath, String planTrigger, String stepName, int firstIndex, int afterIndex, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ASL_PLAN_STEP; }
    }

    Pattern STEP_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)\\.\\.(\\d+)\\]$");
    Pattern OP_PATTERN = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)(?:\\((.*)\\))?$");

    static SourceReference parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException("SOURCE_REFERENCE_EMPTY");

        if (trimmed.startsWith("artifact:")) {
            String rest = trimmed.substring("artifact:".length());
            int slash = rest.indexOf('/');
            if (slash < 0) {
                return new ArtifactRef(rest, trimmed);
            }
            String className = rest.substring(0, slash);
            String member = rest.substring(slash + 1);
            if (member.startsWith("property:")) {
                return new ArtifactPropertyRef(className, member.substring("property:".length()), trimmed);
            } else if (member.startsWith("field:")) {
                return new ArtifactFieldRef(className, member.substring("field:".length()), trimmed);
            } else if (member.startsWith("op:")) {
                String opSig = member.substring("op:".length());
                Matcher m = OP_PATTERN.matcher(opSig);
                if (!m.matches()) throw new IllegalArgumentException("INVALID_OPERATION_SIGNATURE:" + opSig);
                String opName = m.group(1);
                String paramsStr = m.group(2);
                List<String> params = (paramsStr == null || paramsStr.isBlank())
                        ? List.of()
                        : Arrays.stream(paramsStr.split(",")).map(String::trim).toList();
                return new ArtifactOpRef(className, opName, params, trimmed);
            } else {
                throw new IllegalArgumentException("UNKNOWN_ARTIFACT_MEMBER:" + member);
            }
        }

        if (trimmed.startsWith("moise:")) {
            String rest = trimmed.substring("moise:".length());
            int slash = rest.indexOf('/');
            if (slash < 0) throw new IllegalArgumentException("INVALID_MOISE_REFERENCE:" + trimmed);
            String org = rest.substring(0, slash);
            String item = rest.substring(slash + 1);
            if (item.startsWith("group:")) {
                return new MoiseGroupRef(org, item.substring("group:".length()), trimmed);
            } else if (item.startsWith("role:")) {
                return new MoiseRoleRef(org, item.substring("role:".length()), trimmed);
            } else if (item.startsWith("scheme:")) {
                return new MoiseSchemeRef(org, item.substring("scheme:".length()), trimmed);
            } else if (item.startsWith("goal:")) {
                return new MoiseGoalRef(org, item.substring("goal:".length()), trimmed);
            } else {
                throw new IllegalArgumentException("UNKNOWN_MOISE_ITEM:" + item);
            }
        }

        if (trimmed.startsWith("agent:")) {
            String name = trimmed.substring("agent:".length());
            if (name.isBlank()) throw new IllegalArgumentException("INVALID_AGENT_REFERENCE:" + trimmed);
            return new AgentRef(name, trimmed);
        }

        if (trimmed.startsWith("asl:")) {
            String rest = trimmed.substring("asl:".length());
            // Format can be:
            // asl:<path>/goal:<trigger>
            // asl:<path>/plan:<trigger>/step:<name>[first..after]
            int goalIdx = rest.indexOf("/goal:");
            if (goalIdx >= 0) {
                String path = rest.substring(0, goalIdx);
                String trigger = rest.substring(goalIdx + "/goal:".length());
                return new AslGoalRef(path, trigger, trimmed);
            }
            int planIdx = rest.indexOf("/plan:");
            if (planIdx >= 0) {
                String path = rest.substring(0, planIdx);
                String afterPlan = rest.substring(planIdx + "/plan:".length());
                int stepIdx = afterPlan.indexOf("/step:");
                if (stepIdx < 0) throw new IllegalArgumentException("PLAN_REQUIRES_STEP_RANGE:" + trimmed);
                String planTrigger = afterPlan.substring(0, stepIdx);
                String stepSpec = afterPlan.substring(stepIdx + "/step:".length());
                Matcher m = STEP_PATTERN.matcher(stepSpec);
                if (!m.matches()) throw new IllegalArgumentException("INVALID_STEP_RANGE_SYNTAX:" + stepSpec);
                String stepName = m.group(1);
                int first = Integer.parseInt(m.group(2));
                int after = Integer.parseInt(m.group(3));
                if (after <= first) throw new IllegalArgumentException("STEP_RANGE_INVALID_BOUNDS:" + stepSpec);
                return new AslPlanStepRef(path, planTrigger, stepName, first, after, trimmed);
            }
            throw new IllegalArgumentException("UNKNOWN_ASL_REFERENCE:" + trimmed);
        }

        throw new IllegalArgumentException("UNKNOWN_SOURCE_SCHEME:" + trimmed);
    }
}

