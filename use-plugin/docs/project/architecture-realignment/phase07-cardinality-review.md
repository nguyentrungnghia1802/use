# Phase 7 — relation-cardinality review

Official canonical evidence contains four Hello group-role tuples, two Auction
tuples and nine House tuples. Bounds are retained on `BridgeRelationId`
occurrences `(context, member, min, max)` rather than collapsed onto Role or
Group. House includes `bricklayer 1..2`; Auction includes
`participant 0..300`; all other listed concrete roles are `1..1`.

No current canonical endpoint is reused in multiple group contexts with
different bounds, so the frozen V2 projection does not lose a distinction that
these three accepted cases require. The contract multi-context test nevertheless
proves that two contextual tuples with the same member identity remain distinct.
Bridge/provenance is therefore sufficient for current claims. A V2.x/V3
relation-level class/association is a proposal only if a future real case shows
the same endpoint in multiple contexts and authored verification needs both
bounds. Frozen resources remain unchanged.
