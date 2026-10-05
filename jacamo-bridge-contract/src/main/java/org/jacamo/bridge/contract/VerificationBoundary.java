package org.jacamo.bridge.contract;

import java.math.BigDecimal;
import java.util.Map;

/** Protocol 1.1 callback checkpoint evidence; never a domain-object mutation. */
public record VerificationBoundary(String sourceId, long sequence, String eventId) {
    public static final String SOURCE_ID = "bridge:platform";

    public static VerificationBoundary require(RuntimeEvent event, Map<String, Long> observedSources) {
        BridgeEntityId id = event.entityId();
        if (event.kind() != RuntimeEventKind.STREAM_BOUNDARY
                || !event.subsystem().equals("bridge") || !event.sourceId().equals(SOURCE_ID)
                || event.factKind() != RuntimeFactKind.RUNTIME_EVENT
                || event.projectionStatus() != ProjectionStatus.EVIDENCE_ONLY
                || event.completeness() != Completeness.COMPLETE || id == null
                || !id.authority().equals("bridge") || !id.dimension().equals("runtime")
                || !id.kind().equals("verification-boundary") || !id.scope().equals(event.sessionId())
                || !id.localId().equals("batch") || !id.incarnation().equals(event.causationId())
                || event.correlationId().isBlank() || event.causationId().isBlank()
                || event.relationId() != null || !event.before().isEmpty()
                || !event.watermark().sourceId().equals(event.sourceId())
                || event.watermark().sequence() != event.sourceSequence())
            throw new ContractException("VERIFICATION_BOUNDARY_INVALID_IDENTITY_OR_ENVELOPE");
        Object source = event.after().get("boundarySource");
        Object cause = event.after().get("boundaryEventId");
        Object number = event.after().get("boundarySourceSequence");
        if (!(source instanceof String sourceId) || sourceId.isBlank() || sourceId.equals(SOURCE_ID)
                || !event.causationId().equals(cause) || !(number instanceof Number))
            throw new ContractException("VERIFICATION_BOUNDARY_CAUSATION_REQUIRED");
        long sequence;
        try { sequence = new BigDecimal(number.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException invalid) {
            throw new ContractException("VERIFICATION_BOUNDARY_SEQUENCE_INVALID", invalid);
        }
        if (sequence < 0 || observedSources.getOrDefault(sourceId, -1L) < sequence)
            throw new ContractException("VERIFICATION_BOUNDARY_CAUSE_NOT_OBSERVED");
        return new VerificationBoundary(sourceId, sequence, event.causationId());
    }
}
