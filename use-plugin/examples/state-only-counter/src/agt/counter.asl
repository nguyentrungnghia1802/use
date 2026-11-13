!run.
+!run <- .wait(300); !initialise; !increment; !reach_target; !invalid; !must_not_run.
+!initialise <- set(0).
+!increment <- set(1).
+!reach_target <- set(2).
+!invalid <- set(-1).
+!must_not_run <- set(999).
{ include("$jacamo/templates/common-cartago.asl") }
