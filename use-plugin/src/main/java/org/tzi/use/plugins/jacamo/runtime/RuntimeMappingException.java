package org.tzi.use.plugins.jacamo.runtime;

public final class RuntimeMappingException extends IllegalArgumentException {
    private final String code, ruleId, field;
    public RuntimeMappingException(String code, String ruleId, String field) {
        super(code + " [" + ruleId + "] " + field);
        this.code = code; this.ruleId = ruleId; this.field = field;
    }
    public String code() { return code; }
    public String ruleId() { return ruleId; }
    public String field() { return field; }
}
