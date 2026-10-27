package org.tzi.use.plugins.jacamo.ui;

import java.awt.Color;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.NodeBadge;

/** Read-only presentation of original occurrence results. No cross-player
 * satisfaction aggregation or Boolean recurrence is performed by the diagram. */
public final class SemanticDiagramBadges {
    private SemanticDiagramBadges() { }
    public static Map<String,NodeBadge> from(RuntimeVerificationResult result) {
        if(result==null||result.semantic()==null||!(result.semantic().observation().get("originalEngine") instanceof Map<?,?> raw))return Map.of();
        var core=CanonicalJson.object(raw);var groups=new TreeMap<String,List<Map<String,Object>>>();
        Object contexts=core.get("contexts");if(!(contexts instanceof List<?> list))return Map.of();
        int count=0;
        for(var item:list)for(var entry:CanonicalJson.object(CanonicalJson.object(item).getOrDefault("occurrences",Map.of())).entrySet()) {
            if(++count>4096)break;var row=CanonicalJson.object(entry.getValue());
            if(row.get("sourceElement") instanceof String source)groups.computeIfAbsent(source,k->new ArrayList<>()).add(row);
        }
        var badges=new TreeMap<String,NodeBadge>();
        groups.forEach((element,rows)->{
            var first=rows.getFirst();String status=Objects.toString(first.get("aggregateStatus"),"UNAVAILABLE");
            String quality=Objects.toString(core.getOrDefault("qualityOverlay",first.getOrDefault("quality","UNAVAILABLE")));
            boolean known=rows.size()==1&&quality.equals("KNOWN")&&"OBSERVED_PREFIX".equals(core.get("historyQuality"))&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"));
            String glyph=rows.size()>1?Integer.toString(rows.size()):known?switch(status){case "FULFILLED"->"+";case "VIOLATED"->"!";case "PENDING"->"...";default->"?";}:"?";
            Color color=rows.size()>1?new Color(60,100,160):known&&status.equals("FULFILLED")?new Color(30,130,60):known&&status.equals("VIOLATED")?new Color(170,45,40):Color.GRAY;
            String tooltip="Original occurrences; history="+core.getOrDefault("historyQuality","UNAVAILABLE")+". Multiple occurrences are not a combined verdict.\n"+rows.stream().limit(12).map(r->"player="+r.getOrDefault("player","UNAVAILABLE")+", role="+r.getOrDefault("role","UNAVAILABLE")+", raw="+r.getOrDefault("aggregateStatus","UNAVAILABLE")+", quality="+core.getOrDefault("qualityOverlay",r.getOrDefault("quality","UNAVAILABLE"))).collect(java.util.stream.Collectors.joining("\n"));
            badges.put(element,new NodeBadge(color,glyph,tooltip));
        });
        return Collections.unmodifiableMap(badges);
    }
}
