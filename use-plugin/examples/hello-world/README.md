# Hello World

Select `helloworld.jcm` as the JaCaMo project and `hello.mrv` as the system model.
The specification selects `hello.csl`, `hello.istar`,
`hello-compact-mapping.xml` and `hello.ocl`.

Use **Prepare + Import**, then **Start**. JaCaMo owns execution and the checkpoint
barrier; USE maps the admitted snapshot and runs the native CSL/iStar/OCL checks.
A held checkpoint requires review through **Continue**. Manual review preserves
the recorded result and evidence; it does not turn a violation into success.

The compact XML binds source identities to authored model concepts. It does not
replace the CSL/iStar models or load a second legacy XML as an overlay.
An unsupported source or missing evidence must remain explicit.
