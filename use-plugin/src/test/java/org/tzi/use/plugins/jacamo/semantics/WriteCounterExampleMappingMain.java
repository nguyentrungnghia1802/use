package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.List;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleSet;

/** Explicit developer authoring tool, not shipped or called by production mapping. */
public final class WriteCounterExampleMappingMain {
    private WriteCounterExampleMappingMain() { }
    public static void main(String[] args) throws Exception {
        Path jcm=Path.of(args[0]).toAbsolutePath();
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var base=CanonicalGoalMappingTest.rules(source);String type=source.semanticContract().artifactDeclarations().getFirst().javaClass();
        var contracts=base.contracts().stream().map(c->new MappingRuleSet.Contract(c.id(),c.kind(),c.contextDeclaration(),c.targetElementId(),c.body(),
                List.of(type,"increment",List.of(),"void"),c.variableTypes(),c.origin())).toList();
        var authored=new MappingRuleSet(base.version(),"counter-observed-example",base.profileId(),base.profileVersion(),"",base.cslSource(),base.iStarSource(),base.bindings(),contracts,base.decisions(),base.options());
        Files.write(Path.of(args[1]),authored.bytes());System.out.println("AUTHORED_COUNTER_MAPPING_WRITTEN:"+authored.hash());
    }
}
