@e02_start +!e02_start(Id, Service) <- lookupArtifact(Id,A); start(Service)[artifact_id(A)].
@e02_bid +!e02_bid(Id, Amount) <- lookupArtifact(Id,A); bid(Amount)[artifact_id(A)].
@e02_stop +!e02_stop(Id) <- lookupArtifact(Id,A); stop[artifact_id(A)].
@e02_expected_failure -!e02_bid(Id, Amount) <- .print("E02 fixture observed failed bid on ",Id).
@e02_late_role +!e02_join_role <- joinWorkspace("/main/aorg",W); lookupArtifact("agrp",G)[wid(W)]; focus(G); adoptRole(participant)[artifact_id(G)]; .print("E02 actual late role adopted"); lookupArtifact("sch_a1",S1)[wid(W)]; focus(S1); commitMission(mParticipant)[artifact_id(S1)]; lookupArtifact("sch_a2",S2)[wid(W)]; focus(S2); commitMission(mParticipant)[artifact_id(S2)].
