package org.tzi.use.plugins.jacamo.materialization;

public final class MaterializationException extends RuntimeException {
    private final String code;
    public MaterializationException(String code, String message) { super(message); this.code = code; }
    public MaterializationException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
