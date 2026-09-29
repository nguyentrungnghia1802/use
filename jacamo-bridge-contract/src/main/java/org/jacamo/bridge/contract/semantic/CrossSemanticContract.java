package org.jacamo.bridge.contract.semantic;

import java.util.List;
import java.util.Map;

/** Exact cross-dimension evidence; same-name candidates are never bindings. */
public final class CrossSemanticContract {
    private CrossSemanticContract() { }
    public record ExactBindingSemantic(SemanticMetadata metadata, String ruleId,
            List<String> sourceIds, List<String> targetIds, Map<String,String> context) {
        public ExactBindingSemantic { sourceIds=List.copyOf(sourceIds); targetIds=List.copyOf(targetIds); context=SemanticSupport.stringMap(context); }
    }
}
