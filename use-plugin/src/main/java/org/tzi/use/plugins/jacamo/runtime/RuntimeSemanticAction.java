package org.tzi.use.plugins.jacamo.runtime;

/** Generic action mechanics; no event selectors or concrete metamodel names. */
public enum RuntimeSemanticAction {
    ATTRIBUTE_STATE_SET("SET_ATTRIBUTE", "ATTRIBUTE", "AFTER_MUTATION"),
    ATTRIBUTE_STATE_UNSET("SET_ATTRIBUTE", "ATTRIBUTE", "AFTER_MUTATION"),
    OBJECT_AVAILABLE("CREATE_OBJECT", "OBJECT", "AFTER_MUTATION"),
    OBJECT_UNAVAILABLE("DESTROY_OBJECT", "OBJECT", "AFTER_MUTATION"),
    RELATION_INSERT("INSERT_LINK", "ASSOCIATION", "AFTER_MUTATION"),
    RELATION_DELETE("DELETE_LINK", "ASSOCIATION", "AFTER_MUTATION"),
    OPERATION_ENTER("OP_ENTER", "OPERATION", "OPERATION_PRE"),
    OPERATION_EXIT("OP_EXIT", "OPERATION", "OPERATION_POST"),
    OPERATION_FAIL("OP_FAIL", "OPERATION", "OPERATION_FAIL"),
    TRACE_ONLY("NONE", "TRACE", "NONE"),
    NO_MUTATION("NONE", "TRACE", "NONE"),
    UNSUPPORTED("NONE", "TRACE", "NONE");

    private final String mutation, target, checkpoint;
    RuntimeSemanticAction(String mutation, String target, String checkpoint) {
        this.mutation = mutation; this.target = target; this.checkpoint = checkpoint;
    }
    public String mutation() { return mutation; }
    public String target() { return target; }
    public String checkpoint() { return checkpoint; }
    public boolean mutates() { return !mutation.equals("NONE"); }
    public boolean operation() { return target.equals("OPERATION"); }
}
