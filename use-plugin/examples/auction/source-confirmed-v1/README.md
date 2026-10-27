# Source-confirmed Auction requirements, version 1

Select the Auction `.jcm`, this directory's `auction-mapping.xml` and
`auction_confirmed.ocl`, and the unchanged `../authored-v1/auction_csl.csl`
and `../authored-v1/auction_istar_final.istar` in USE's five-input GUI.
All five selections remain explicit. Prepare/import and Start owned runtime
use the existing external JaCaMo/TCP pipeline.

This profile intentionally differs from the strict Desktop requirements:
`*ConfirmedAt` is the first qualified **public source confirmation** of the
selected MOISE state in the complete post-creation observed prefix. It is
the CArtAgO `newPercept` logger callback's source epoch milliseconds,
converted with the pinned producer clock anchor. It is not the instant
an internal MOISE proposition first became true, a poll time, an operation
return time, or a USE receipt time. Equal millisecond timestamps are allowed;
the OCL requirements use non-strict ordering. No strict field is populated.

`MOISE_PUBLIC_CONFIRMATION/1.0` requires the existing pinned scheme decoder,
exact physical/source identities, post-init creation baseline, continuous
public-state prefix, actual publication timestamps and no timestamp regression.
A state already present in the creation baseline has no observed confirmation
timestamp and stays undefined. Known absence is `-1` only under the complete
qualified prefix. Missing publication, late attachment, gap, unsupported binary,
clock mismatch/regression and out-of-range time stay unavailable. NPL deadlines
retain their original source TimeTerm authority and are independent of this
choice. The strict `authored-v1` XML/OCL remain selectable and retain UNKNOWN
for missing internal timestamps.

`ConfirmedSchemeOrder` and `ConfirmedBidEvidence` are newly authored properties;
they do not claim equivalence to the strict internal-time contracts. Mission
membership is frozen at first qualified satisfaction as in the strict profile;
successful real bids and administrative recovery stay distinct. CSL/iStar,
original engine recurrence, native OCL, authoritative MSystem, TCP and replay
ownership are unchanged. No case-specific Java mapper is used.
