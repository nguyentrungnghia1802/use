# ADR — Phase 8 production transport

## Decision

Select authenticated loopback TCP with bounded length-prefixed canonical JSON
behind the existing `BridgeTransport` SPI. Remote mode is deliberately not
supported in this release; constructors reject non-loopback addresses. TLS is
therefore not applicable. Enabling remote deployment later requires a separate
ADR, mutual TLS and authorization design.

Each request has a nonce and HMAC-SHA-256 over operation, resume token and nonce.
The server compares MACs in constant time, bounds retained nonces, exposes only
read-only handshake/model/snapshot/subscribe/ack operations, and never returns
authentication detail. It performs no Java deserialization, class loading,
reflection or arbitrary invocation. Canonical JSON parsing and contract decoding
enforce byte, nesting and string limits.

Subscriptions have count and byte bounds. Retained resume tokens support replay;
unknown/evicted tokens and queue overflow produce an explicit GAP frame. The
client acknowledges source sequence tokens. At-least-once delivery plus event-ID
idempotency is claimed; exactly-once delivery is not.

## Evidence

For a 65,536-byte frame on this host, the latest final-regression measured median was
9,900 ns for the in-memory recorded candidate and 637,500 ns for a fresh loopback TCP request
(100 and 30 iterations respectively). These are measurements, not budgets.
TCP was selected because it preserves process/classloader isolation, supports
reconnect and requires no new runtime library.

`LocalTcpBridgeTransportTest` covers all three canonical models, wrong HMAC,
unknown resume/GAP, schema-major rejection and non-loopback rejection.
`BridgeMirrorStateMachineTest` covers idempotent replay, conflicting duplicate,
gap, stale session and unknown incarnation. `SeparateJvmBridgeTest` launches the
selected transport for Hello, Auction and House with the USE JVM stripped of all
platform jars. Contract fuzz/size/depth tests and server shutdown cover malformed
payload and resource cleanup boundaries.
