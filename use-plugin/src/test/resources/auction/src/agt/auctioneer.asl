auction_open.
budget(1000).
affordable(A) :- budget(B) & B >= A.
!start.
+!start : auction_open <- placeBid(item1, 10); .send(observer,tell,bid_placed(item1)).
