package org.tzi.use.plugins.jacamo.runtime;

import java.util.*;
import moise.oe.OE;
import moise.oe.Permission;

/** Direct OE 1.1 derived sets, not NPL lifecycle, OCL results, or structural Norm objects. */
public record MoiseNormativeSnapshot(List<Fact> facts, List<String> unsupported) {
    public record Fact(String modality,String organisation,String agent,String group,String role,String scheme,String mission) { }
    public MoiseNormativeSnapshot { facts=List.copyOf(facts);unsupported=List.copyOf(unsupported); }
    public static MoiseNormativeSnapshot capture(OE oe,String organisation) {
        List<Fact> facts=new ArrayList<>();
        synchronized(oe) {
            oe.getAgents().stream().sorted().forEach(agent->{
                agent.getObligations().forEach(p->facts.add(fact("DERIVED_OBLIGATION",organisation,p)));
                agent.getPermissions().forEach(p->facts.add(fact("DERIVED_PERMISSION",organisation,p)));
            });
        }
        facts.sort(Comparator.comparing(Fact::organisation).thenComparing(Fact::agent).thenComparing(Fact::group)
            .thenComparing(Fact::role).thenComparing(Fact::scheme).thenComparing(Fact::mission).thenComparing(Fact::modality));
        return new MoiseNormativeSnapshot(facts,List.of("UNSUPPORTED_PROHIBITION","UNSUPPORTED_ACTIVATION",
            "UNSUPPORTED_FULFILMENT","UNSUPPORTED_VIOLATION","UNSUPPORTED_EXPIRATION_DEADLINE",
            "UNSUPPORTED_NORM_TO_OCL","UNSUPPORTED_LAUNCHER_BOARD_NORMATIVE_EQUIVALENCE"));
    }
    private static Fact fact(String modality,String organisation,Permission permission) {
        var role=permission.getRolePlayer();
        return new Fact(modality,organisation,role.getPlayer().getId(),role.getGroup().getId(),role.getRole().getId(),
            permission.getScheme().getId(),permission.getMission().getId());
    }
}
