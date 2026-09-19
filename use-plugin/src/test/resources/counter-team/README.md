# Counter Team case

Test-owned local extension of the existing `jcm/minimal` counter fixture concept.
The original minimal fixture is unchanged. This fixture adds an executable CArtAgO
counter, a source agent, explicit team/role/mission/goal and authored case contracts.
It is not an upstream JaCaMo example or a validated standalone launcher project.
The integration test executes checked-in Java and loads checked-in AgentSpeak in
real pinned components; environment operations are issued by the harness. The OE
is created by OSBuilder from the stated simple team facts, not from this static
import XML. No autonomous agent-to-organisation chain or deadline is claimed.

Counter permits -1 deliberately; case OCL requires nonnegative count, giving a
repeatable invariant violation. Inputs below -1 fail the operation, giving PRE and
POST-SKIPPED evidence. This policy is local to the fixture, never core logic.
