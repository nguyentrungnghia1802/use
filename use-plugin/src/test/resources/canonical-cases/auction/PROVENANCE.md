# Original auction regression fixture

Copied without source modification from JaCaMo commit
`04fc08df259d94045f6cfc95637d56faa1cb33b2`, `examples/auction`.
Original JaCaMo licensing applies. Git blob uses LF line endings; the existing
Windows checkout previously supplied CRLF hashes.

This fixture pins the original single-organization scenario used by historical
OCL/cardinality tests. The user's working JaCaMo auction contains an additional
organization; it remains unchanged. Live tests can continue to use that project,
while original one-group invariants use this reproducible fixture.
