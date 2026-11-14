!run.
// One execution step: local subplans are checked together when run ends.
// Keep the violation at that boundary; intermediate values are computations.
+!run <- .wait(300); !initialise; !increment; !reach_target; !invalid.
+!initialise <- set(0).
+!increment <- set(1).
+!reach_target <- set(2).
+!invalid <- set(-1).
{ include("$jacamo/templates/common-cartago.asl") }
