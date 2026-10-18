!observe.
+!observe <- .wait(1000);
    ?focusing(C,counter,_,_,_,_);
    gate[artifact_id(C)];
    commitMission(m)[artifact_name(workflow),wsp(org)];
    increment[artifact_id(C)]; .wait(300);
    increment[artifact_id(C)]; .wait(300);
    set(-1)[artifact_id(C)];
    awaitStopMas[artifact_id(C)];
    .stopMAS.
{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
