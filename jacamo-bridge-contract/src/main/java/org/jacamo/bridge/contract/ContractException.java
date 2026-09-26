package org.jacamo.bridge.contract;

/** A fail-closed neutral-contract validation or decoding failure. */
public final class ContractException extends RuntimeException {
    public ContractException(String message) { super(message); }
    public ContractException(String message, Throwable cause) { super(message, cause); }
}
