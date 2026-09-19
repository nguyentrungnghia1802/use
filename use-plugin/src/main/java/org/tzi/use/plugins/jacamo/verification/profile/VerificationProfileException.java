package org.tzi.use.plugins.jacamo.verification.profile;

public final class VerificationProfileException extends RuntimeException {
    private final String code;
    public VerificationProfileException(String code, String message) { super(message); this.code = code; }
    public VerificationProfileException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
