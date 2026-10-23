@e02_start +!e02_start(Id, Service) <- lookupArtifact(Id,A); start(Service)[artifact_id(A)].
@e02_bid +!e02_bid(Id, Amount) <- lookupArtifact(Id,A); bid(Amount)[artifact_id(A)].
@e02_stop +!e02_stop(Id) <- lookupArtifact(Id,A); stop[artifact_id(A)].
@e02_expected_failure -!e02_bid(Id, Amount) <- .print("E02 fixture observed failed bid on ",Id).
