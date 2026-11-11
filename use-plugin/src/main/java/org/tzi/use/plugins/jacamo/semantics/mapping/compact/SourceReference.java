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
        ARTIFACT_PROPERTY_SELECTION,
        ARTIFACT_FIELD,
        ARTIFACT_OPERATION,
        ARTIFACT_IDENTITY,
        PROJECT,
        WORKSPACE,
        WORKSPACE_IDENTITY,
        ORGANIZATION,
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
    record ArtifactIdentityRef(String className,String field,String raw) implements SourceReference {
        @Override public Kind kind(){return Kind.ARTIFACT_IDENTITY;}
    }
    record ProjectRef(String name,String raw) implements SourceReference {@Override public Kind kind(){return Kind.PROJECT;}}
    record WorkspaceRef(String name,String raw) implements SourceReference {@Override public Kind kind(){return Kind.WORKSPACE;}}
    record WorkspaceIdentityRef(String name,String field,String raw) implements SourceReference {@Override public Kind kind(){return Kind.WORKSPACE_IDENTITY;}}
    record OrganizationRef(String name,String raw) implements SourceReference {@Override public Kind kind(){return Kind.ORGANIZATION;}}

    record ArtifactFieldRef(String className, String fieldName, String raw) implements SourceReference {
        @Override public Kind kind() { return Kind.ARTIFACT_FIELD; }
    }

    record ArtifactOpRef(String className, String operationName, List<String> paramTypes, String raw) implements SourceReference {
        public ArtifactOpRef {paramTypes=List.copyOf(paramTypes);}
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
        if(trimmed.length()>4096||trimmed.indexOf('\n')>=0||trimmed.indexOf('\r')>=0||trimmed.indexOf('\0')>=0)
            throw new IllegalArgumentException("SOURCE_REFERENCE_CAPACITY_OR_CONTROL_CHARACTER");

        if (trimmed.startsWith("artifact:")) {
            String rest = trimmed.substring("artifact:".length());
            int slash = rest.indexOf('/');
            if (slash < 0) {
                javaName(rest);
                return new ArtifactRef(rest, trimmed);
            }
            String className = rest.substring(0, slash);
            javaName(className);
            String member = rest.substring(slash + 1);
            if (member.startsWith("property:")) {
                identifier(member.substring("property:".length()));
                return new ArtifactPropertyRef(className, member.substring("property:".length()), trimmed);
            } else if(member.startsWith("identity:")) {
                String field=member.substring("identity:".length());
                if(!Set.of("name","workspace","workspaceName","uuid","semanticId").contains(field))throw new IllegalArgumentException("ARTIFACT_IDENTITY_MEMBER_UNSUPPORTED:"+field);
                return new ArtifactIdentityRef(className,field,trimmed);
            } else if (member.startsWith("field:")) {
                identifier(member.substring("field:".length()));
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
                for(String parameter:params)javaName(parameter);
                return new ArtifactOpRef(className, opName, params, trimmed);
            } else {
                throw new IllegalArgumentException("UNKNOWN_ARTIFACT_MEMBER:" + member);
            }
        }

        if(trimmed.startsWith("project:"))return new ProjectRef(named(trimmed,"project:"),trimmed);
        if(trimmed.startsWith("workspace:")) {
            int separator=trimmed.indexOf("/identity:");
            if(separator<0)return new WorkspaceRef(named(trimmed,"workspace:"),trimmed);
            String name=named(trimmed.substring(0,separator),"workspace:"),field=trimmed.substring(separator+"/identity:".length());
            if(!Set.of("name","path","uuid","semanticId").contains(field))throw new IllegalArgumentException("WORKSPACE_IDENTITY_MEMBER_UNSUPPORTED:"+field);
            return new WorkspaceIdentityRef(name,field,trimmed);
        }
        if(trimmed.startsWith("organization:"))return new OrganizationRef(named(trimmed,"organization:"),trimmed);

        if (trimmed.startsWith("moise:")) {
            String rest = trimmed.substring("moise:".length());
            int slash = rest.indexOf('/');
            if (slash < 0) throw new IllegalArgumentException("INVALID_MOISE_REFERENCE:" + trimmed);
            String org = rest.substring(0, slash);
            if(!org.matches("[A-Za-z_][A-Za-z0-9_.-]*"))throw new IllegalArgumentException("INVALID_ORGANIZATION_REFERENCE:"+org);
            String item = rest.substring(slash + 1);
            if(item.indexOf(':')<0||!item.substring(item.indexOf(':')+1).matches("[A-Za-z_][A-Za-z0-9_.-]*"))
                throw new IllegalArgumentException("INVALID_MOISE_MEMBER:"+item);
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
                relativeAslPath(path);
                if(trigger.isBlank())throw new IllegalArgumentException("ASL_GOAL_TRIGGER_REQUIRED");
                return new AslGoalRef(path, trigger, trimmed);
            }
            int planIdx = rest.indexOf("/plan:");
            if (planIdx >= 0) {
                String path = rest.substring(0, planIdx);
                relativeAslPath(path);
                String afterPlan = rest.substring(planIdx + "/plan:".length());
                int stepIdx = afterPlan.indexOf("/step:");
                if (stepIdx < 0) throw new IllegalArgumentException("PLAN_REQUIRES_STEP_RANGE:" + trimmed);
                String planTrigger = afterPlan.substring(0, stepIdx);
                if(planTrigger.isBlank())throw new IllegalArgumentException("ASL_PLAN_TRIGGER_REQUIRED");
                String stepSpec = afterPlan.substring(stepIdx + "/step:".length());
                Matcher m = STEP_PATTERN.matcher(stepSpec);
                if (!m.matches()) throw new IllegalArgumentException("INVALID_STEP_RANGE_SYNTAX:" + stepSpec);
                String stepName = m.group(1);
                identifier(stepName);
                int first = Integer.parseInt(m.group(2));
                int after = Integer.parseInt(m.group(3));
                if (after <= first) throw new IllegalArgumentException("STEP_RANGE_INVALID_BOUNDS:" + stepSpec);
                return new AslPlanStepRef(path, planTrigger, stepName, first, after, trimmed);
            }
            throw new IllegalArgumentException("UNKNOWN_ASL_REFERENCE:" + trimmed);
        }

        throw new IllegalArgumentException("UNKNOWN_SOURCE_SCHEME:" + trimmed);
    }

    private static void identifier(String value) {
        if(!value.matches("[A-Za-z_$][A-Za-z0-9_$]*"))throw new IllegalArgumentException("INVALID_SOURCE_IDENTIFIER:"+value);
    }
    private static String named(String value,String prefix) {
        String name=value.substring(prefix.length());
        if(!name.equals("*")&&!name.matches("[A-Za-z_][A-Za-z0-9_.-]*"))throw new IllegalArgumentException("INVALID_NAMED_SOURCE_REFERENCE:"+value);
        return name;
    }
    private static void javaName(String value) {
        if(!value.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))throw new IllegalArgumentException("INVALID_JAVA_SOURCE_NAME:"+value);
    }
    private static void relativeAslPath(String path) {
        if(path.isBlank()||path.startsWith("/")||path.startsWith("\\")||path.contains(":")||path.contains("\\")||!path.endsWith(".asl")
                ||Arrays.stream(path.split("/",-1)).anyMatch(p->p.isBlank()||p.equals(".")||p.equals("..")))
            throw new IllegalArgumentException("ASL_SOURCE_PATH_MUST_BE_PROJECT_RELATIVE:"+path);
    }
}

