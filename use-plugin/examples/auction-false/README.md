# Auction False

Select `auction.jcm` as the JaCaMo project and `auction.mrv` as the system model.
The specification selects `auction.csl`, `auction.istar`,
`auction-compact-mapping.xml` and `auction.ocl`.

Use **Prepare + Import**, then **Start**. JaCaMo owns execution and the checkpoint
barrier; USE maps the admitted snapshot and runs the native CSL/iStar/OCL checks.
A held checkpoint requires review through **Continue**. Manual review preserves
the recorded result and evidence; it does not turn a violation into success.

The compact XML binds source identities to authored model concepts. It does not
replace the CSL/iStar models or load a second legacy XML as an overlay.
An unsupported source or missing evidence must remain explicit.

This negative example intentionally omits the stop-state update in its artifact.
The verification constraints must retain this failure; do not repair or force it.
