
+!do_auction(Id,P)
   <- // creates a scheme to coordinate the auction
      .concat("sch_",Id,SchName);
      ?joined(aorg,OrgWks); // get the workspace id of the org
      createScheme(SchName, doAuction, SchArtId)[wid(OrgWks)];
      setArgumentValue(auction,"Id",Id)[artifact_id(SchArtId)];
      setArgumentValue(auction,"Service",P)[artifact_id(SchArtId)];
      //debug(inspector_gui(on))[artifact_id(SchArtId)];
      .my_name(Me); setOwner(Me)[artifact_id(SchArtId)];  // I am the owner of this scheme!
      focus(SchArtId);
      addScheme(SchName)[wid(OrgWks)];  // set the group as responsible for the scheme
      commitMission(mAuctioneer)[artifact_id(SchArtId)].

+?joined(Name,Id) <- .print("waiting for workspace ",Name); .wait(100); ?joined(Name,Id).

/* plans for organisational goals */

+!start[scheme(Sch)]
   <- !verification_start(Sch); !verification_after_start.
+!verification_start(Sch) <- verification_oracle.mark("fault_missing_artifact").
+!verification_after_start <- verification_oracle.mark("after_start").


+!decide[scheme(Sch)]
   <- ?goalArgument(Sch,auction,"Id",Id);
      ?focusing(ArtId,Id,_,_,_,_);
      stop[artifact_id(ArtId)].

+!bid[scheme(Sch)]
   <- ?goalArgument(Sch,auction,"Id",Id);
      lookupArtifact(Id,AId); focus(AId);
      !verification_one_bid(AId,10); !verification_after_bid_1;
      !verification_one_bid(AId,20); !verification_after_bid_2.
+!verification_one_bid(AId,V) <- bid(V)[artifact_id(AId)].
+!verification_after_bid_1 <- verification_oracle.mark("after_bid_1").
+!verification_after_bid_2 <- verification_oracle.mark("after_bid_2").


+winner(W) : .my_name(W) <- .print("I Won!").

+winner(W)[artifact_id(AId)]
   : W \== no_winner &
    .my_name(Me) & play(Me,auctioneer,_)  // announce if I am the auctioneer
   <- ?task(S)[artifact_id(AId)];
      ?best_bid(V)[artifact_id(AId)];
      .print("Winner for ", S, " is ",W," with ", V).

+oblUnfulfilled( obligation(Ag,_,done(Sch,bid,Ag),_ ) )[artifact_id(AId)]  // it is the case that a bid was not achieved
   : .my_name(Me) & play(Me,auctioneer,_)  // handle unfulfilled obl if I am the auctioneer
   <- .print("Participant ",Ag," didn't bid on time! S/he will be placed in a block list");
       // TODO: implement a block list artifact
       admCommand("goalSatisfied(bid)")[artifact_id(AId)]. // go on in the scheme....

{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
