from pathlib import Path
base=Path('use-plugin/src/test/java/org/tzi/use/plugins/jacamo/semantics/mapping/compact')
for filename in ['RuntimeInstanceLinkerTest.java','NativeVerificationCoordinatorTest.java']:
 p=base/filename;s=p.read_text(encoding='utf-8')
 s=s.replace('"auction_wsp",\n                Map.of(', '"auction_wsp",\n                "run-1", "incarnation-1",\n                Map.of(')
 if filename=='RuntimeInstanceLinkerTest.java':
  s=s.replace('"auction_auction_wsp_auction_art"','RuntimeInstanceLinker.artifactObjectName("Auction", artCut1)')
 else:
  s=s.replace('void evaluatesNativeInvariantsFromModelBytesAndDecidesCheckpointActions()', 'void evaluatesNativeInvariantsButCannotApproveWithoutNativeInventory()')
  s=s.replace('assertTrue(assessment1.allPassed());','assertFalse(assessment1.allPassed());\n        assertEquals("PASS", assessment1.invariantResults().getFirst().verdict());')
  s=s.replace('Action.APPROVE, assessment1.recommendedAction()', 'Action.HOLD_UNVERIFIED, assessment1.recommendedAction()')
  s=s.replace('assertEquals("PASS", assessment1.checkpointChecks()', 'assertEquals("UNKNOWN", assessment1.checkpointChecks()')
  s=s.replace('contains("auction_auction_wsp_auction_art")','contains(RuntimeInstanceLinker.artifactObjectName("Auction", artCut))')
 p.write_text(s,encoding='utf-8')
p=Path('use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/compact/RuntimeInstanceLinker.java')
s=p.read_text(encoding='utf-8');a=s.index('        List<RuntimeInstanceLinker') if '        List<RuntimeInstanceLinker' in s else s.index('        List<ObjectMutation> objectMutations')
b=s.index('    public static String artifactObjectName(',a)
s=s[:a]+'''        if(!cut.disposedObjects().isEmpty())throw new IllegalArgumentException("COMPACT_DISPOSAL_REQUIRES_NATIVE_SOURCE_INVENTORY");
        if(plan.cslBindings().stream().anyMatch(b -> b.kind()==CslBinding.BindingKind.GROUP_MAPPING || b.kind()==CslBinding.BindingKind.ROLE_MAPPING))
            throw new IllegalArgumentException("COMPACT_TYPED_ROLE_CONTEXT_PROJECTION_REQUIRED");
        var objects=new ArrayList<ObjectMutation>();
        for(var binding:plan.cslBindings())if(binding.sourceRef() instanceof SourceReference.ArtifactRef) {
            String target=binding.targetRef().qualifier();String sourceClass=binding.sourceSymbol().qualifiedName();
            for(var artifact:cut.artifacts())if(sourceClass.equals(artifact.className())) {
                var values=new LinkedHashMap<String,Value>();
                for(var member:plan.cslBindings())if(member.isAttribute() && member.targetRef().qualifier().equals(target)) {
                    Object raw=switch(member.sourceRef()) {
                        case SourceReference.ArtifactPropertyRef property -> artifact.properties().get(property.propertyName());
                        case SourceReference.ArtifactFieldRef field -> artifact.fields().get(field.fieldName());
                        default -> null;
                    };
                    values.put(member.targetRef().member(),convertValue(raw,member.conversion()));
                }
                objects.add(new ObjectMutation(target,artifactObjectName(target,artifact),Map.copyOf(values),true));
            }
        }
        return new ProjectionTransaction(cut.cutId(),List.copyOf(objects),List.of(),List.of());
    }

'''+s[b:]
s=s.replace(' * Supports dynamic creation, property/field value projection, role/actor linking, and reversible rollback.',
''' * Component-level artifact/scalar projection only. Physical agents, role intervals,
 * scopes and disposal require the native runtime adapter; no Actor MClass is invented.''')
p.write_text(s,encoding='utf-8')
