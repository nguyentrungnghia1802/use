package org.tzi.use.plugins.jacamo.mapping;

/** A blocking mapping-contract diagnostic. */
public final class MappingException extends RuntimeException {
    private final String code;

    public MappingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MappingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
