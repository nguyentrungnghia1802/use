# Draw.io realization decision - resolved

The earlier proposal to add three capability Dependencies was reviewed but not
adopted. The user selected the supplied Draw.io. The executable canonical model
therefore keeps exactly D_start, D_bid and D_decide and the original graph edges.

The actual auction_capabilities node declares elementType=role. It is represented
as an iStar Role, with an explicit physical-player occurrence view; it is neither
a sixth running agent nor an invented MOISE role enactment.

The Role Goal and capability Goal copies carry the same structured owner and
sourceGoal metadata in the drawing. The authored XML declares these three view
identities explicitly under SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0:

| Role Goal | Refinement view | Admitted MOISE source Goal |
| --- | --- | --- |
| Auctioneer.A_start | auction_capabilities.C_start | doAuction.start |
| Auctioneer.A_decide | auction_capabilities.C_decide | doAuction.decide |
| Participant.P_bid | auction_capabilities.C_bid | doAuction.bid |

This is an adapter extension, not an automatic inference from participates-in.
It requires a pinned source declaration, explicit policy/reason, one counterpart
with the same physical player and execution scope, a refined source Goal and an
unconditioned target view. Invalid, ambiguous or unrelated declarations reject
preparation. Role interval identity fences PRE/POST and retained Task history.

For native evaluation the view's refinement DAG is instantiated under that Role
occurrence, using exactly the provider's captured source Task inputs. The native
Goal/Task/AND/OR engines are unchanged. No new social Dependencies are inserted,
no literal true condition or administrative status grants Goal achievement, and
missing evidence remains unknown. Canonical model/export topology stays intact.

GoalViewRealizationsTest exercises success, real failed Task predicates, missing
evidence, a different player, ambiguous counterparts and role reenactment.
CurrentAuctionBundleTest prepares both current model bundles with native USE.
This is a deliberate executable interpretation of the drawing's structured
view identities; the flat DSL alone cannot express those repeated views.
