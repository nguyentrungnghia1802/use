package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;

/**
 * Validates correspondences between declared compact mapping and admitted source/target catalogues.
 */
public final class CorrespondenceValidator {

    public record Diagnostic(String code, String message, CompactMappingSpecification.Location location) {
        @Override
        public String toString() {
            return location + ": [" + code + "] " + message;
        }
    }

    public static List<Diagnostic> validate(CompactMappingSpecification spec,
                                           SourceSymbolCatalog sourceCatalog,
                                           NativeTargetCatalog targetCatalog) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        Objects.requireNonNull(targetCatalog, "targetCatalog");

        List<Diagnostic> diagnostics = new ArrayList<>();
        var selectedTargets=new HashSet<String>();

        // Validate CSL correspondences
        for (CompactMappingSpecification.Correspondence c : spec.cslMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());

            SourceSymbol sSymbol = null;
            try {
                sSymbol = sourceCatalog.select(c);
            } catch (Exception e) {
                diagnostics.add(new Diagnostic("SOURCE_RESOLUTION_FAILED", e.getMessage(), c.location()));
            }

            TargetSymbol tSymbol = null;
            try {
                tSymbol = targetCatalog.resolve(tRef, "CSL");
            } catch (Exception e) {
                diagnostics.add(new Diagnostic("TARGET_RESOLUTION_FAILED", e.getMessage(), c.location()));
            }

            if (sSymbol != null && tSymbol != null) {
                if(!selectedTargets.add("CSL:"+tSymbol.qualifiedName()))diagnostics.add(new Diagnostic("AMBIGUOUS_TARGET_BINDING",c.target(),c.location()));
                boolean compatible=switch(sSymbol.kind()) {
                    case ARTIFACT -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_ENTITY;
                    case ARTIFACT_PROPERTY,ARTIFACT_PROPERTY_SELECTION,ARTIFACT_FIELD,ARTIFACT_IDENTITY,WORKSPACE_IDENTITY,AGENT_IDENTITY -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_ATTRIBUTE;
                    case MOISE_GROUP,PROJECT,WORKSPACE,ORGANIZATION -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_GROUP;
                    case MOISE_ROLE -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_ROLE;
                    case MOISE_SCHEME -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_GROUP&&"SCHEME_EXECUTION_VIEW/1.0".equals(c.attributes().get("contextAbstraction"));
                    case AGENT -> tSymbol.kind()==TargetSymbol.TargetKind.CSL_GROUP&&"SINGLE_AGENT_CONTEXT/1.0".equals(c.attributes().get("contextAbstraction"));
                    default -> false;
                };
                if(!compatible)diagnostics.add(new Diagnostic("UNSUPPORTED_SOURCE_TARGET_KIND",c.source()+" -> "+c.target(),c.location()));
                String converter=c.attributes().getOrDefault("converter","");
                if(!converter.isBlank()&&(!(sRef instanceof SourceReference.ArtifactPropertyRef)
                        ||!converter.equals("JASON_ATOM_TO_STRING/1.0")&&!converter.equals(CompactValueConverters.NUMBER_TO_REAL)
                        ||converter.equals("JASON_ATOM_TO_STRING/1.0")&&!"String".equals(tSymbol.declaredType())
                        ||converter.equals(CompactValueConverters.NUMBER_TO_REAL)&&!"Real".equals(tSymbol.declaredType())))
                    diagnostics.add(new Diagnostic("PROPERTY_CONVERTER_SOURCE_OR_TARGET_UNSUPPORTED",converter,c.location()));
                if((sSymbol.kind()==SourceReference.Kind.ARTIFACT_IDENTITY||sSymbol.kind()==SourceReference.Kind.WORKSPACE_IDENTITY||sSymbol.kind()==SourceReference.Kind.AGENT_IDENTITY)
                        &&!"String".equals(tSymbol.declaredType()))diagnostics.add(new Diagnostic("IDENTITY_ATTRIBUTE_MUST_BE_STRING",c.target(),c.location()));
                if(sRef instanceof SourceReference.WorkspaceIdentityRef identity) {
                    var owners=spec.cslMappings().stream().filter(m->m.target().equals(tRef.qualifier())).toList();
                    if(owners.size()!=1||!(SourceReference.parse(owners.getFirst().source()) instanceof SourceReference.WorkspaceRef owner)||!owner.name().equals(identity.name()))
                        diagnostics.add(new Diagnostic("WORKSPACE_IDENTITY_OWNER_MISMATCH",c.source()+" -> "+c.target(),c.location()));
                }
                if(sRef instanceof SourceReference.AgentIdentityRef identity) {
                    var owners=spec.cslMappings().stream().filter(m->m.target().equals(tRef.qualifier())).toList();
                    if(owners.size()!=1||!(SourceReference.parse(owners.getFirst().source()) instanceof SourceReference.AgentRef owner)||!owner.agentOrProgram().equals(identity.agentOrProgram()))
                        diagnostics.add(new Diagnostic("AGENT_IDENTITY_OWNER_MISMATCH",c.source()+" -> "+c.target(),c.location()));
                }
                if(sSymbol.kind()==SourceReference.Kind.ARTIFACT_PROPERTY||sSymbol.kind()==SourceReference.Kind.ARTIFACT_PROPERTY_SELECTION||sSymbol.kind()==SourceReference.Kind.ARTIFACT_FIELD||sSymbol.kind()==SourceReference.Kind.ARTIFACT_IDENTITY) {
                    var ownerBindings=spec.cslMappings().stream().filter(m->m.target().equals(tRef.qualifier())).toList();
                    if(ownerBindings.size()!=1)diagnostics.add(new Diagnostic("ATTRIBUTE_OWNER_MAPPING_REQUIRED",c.target(),c.location()));
                    else try {
                        var owner=sourceCatalog.resolve(SourceReference.parse(ownerBindings.getFirst().source()));
                        if(owner.kind()!=SourceReference.Kind.ARTIFACT||!Objects.equals(owner.qualifiedName(),sSymbol.owner()))
                            diagnostics.add(new Diagnostic("ATTRIBUTE_SOURCE_OWNER_MISMATCH",c.source()+" -> "+c.target(),c.location()));
                    }catch(IllegalArgumentException invalid){diagnostics.add(new Diagnostic("ATTRIBUTE_OWNER_RESOLUTION_FAILED",invalid.getMessage(),c.location()));}
                }
                if (!tSymbol.isCsl()) {
                    diagnostics.add(new Diagnostic("TARGET_MODEL_MISMATCH",
                            "Target '" + c.target() + "' belongs to " + tSymbol.modelSource() + ", expected CSL",
                            c.location()));
                }

                // Check correspondence compatibility
                if (sSymbol.kind() == SourceReference.Kind.ARTIFACT_PROPERTY || sSymbol.kind() == SourceReference.Kind.ARTIFACT_PROPERTY_SELECTION || sSymbol.kind() == SourceReference.Kind.ARTIFACT_FIELD) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.CSL_ATTRIBUTE) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " '" + c.source() + "' cannot map to target " + tSymbol.kind(),
                                c.location()));
                    }
                } else if (sSymbol.kind() == SourceReference.Kind.ARTIFACT || sSymbol.kind() == SourceReference.Kind.MOISE_GROUP) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.CSL_ENTITY && tSymbol.kind() != TargetSymbol.TargetKind.CSL_ROLE && tSymbol.kind() != TargetSymbol.TargetKind.CSL_GROUP) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " '" + c.source() + "' cannot map to target " + tSymbol.kind(),
                                c.location()));
                    }
                }
            }
        }

        // Validate iStar correspondences
        for (CompactMappingSpecification.Correspondence c : spec.istarMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());

            SourceSymbol sSymbol = null;
            try {
                sSymbol = sourceCatalog.select(c);
            } catch (Exception e) {
                diagnostics.add(new Diagnostic("SOURCE_RESOLUTION_FAILED", e.getMessage(), c.location()));
            }

            TargetSymbol tSymbol = null;
            try {
                tSymbol = targetCatalog.resolve(tRef, "ISTAR");
            } catch (Exception e) {
                diagnostics.add(new Diagnostic("TARGET_RESOLUTION_FAILED", e.getMessage(), c.location()));
            }

            if (sSymbol != null && tSymbol != null) {
                if(!selectedTargets.add("ISTAR:"+tSymbol.qualifiedName()))diagnostics.add(new Diagnostic("AMBIGUOUS_TARGET_BINDING",c.target(),c.location()));
                boolean compatible=switch(sSymbol.kind()) {
                    case AGENT,MOISE_ROLE,PROJECT,WORKSPACE -> tSymbol.kind()==TargetSymbol.TargetKind.ISTAR_ACTOR;
                    case ASL_GOAL,MOISE_GOAL -> tSymbol.kind()==TargetSymbol.TargetKind.ISTAR_GOAL;
                    case ASL_PLAN_STEP -> tSymbol.kind()==TargetSymbol.TargetKind.ISTAR_TASK;
                    case MOISE_GROUP,MOISE_SCHEME,ARTIFACT -> tSymbol.kind()==TargetSymbol.TargetKind.ISTAR_ACTOR||tSymbol.kind()==TargetSymbol.TargetKind.ISTAR_RESOURCE;
                    default -> false;
                };
                if(!compatible)diagnostics.add(new Diagnostic("UNSUPPORTED_SOURCE_TARGET_KIND",c.source()+" -> "+c.target(),c.location()));
                if (!tSymbol.isIStar()) {
                    diagnostics.add(new Diagnostic("TARGET_MODEL_MISMATCH",
                            "Target '" + c.target() + "' belongs to " + tSymbol.modelSource() + ", expected ISTAR",
                            c.location()));
                }

                // Type checks
                if (sSymbol.kind() == SourceReference.Kind.ASL_GOAL || sSymbol.kind() == SourceReference.Kind.MOISE_GOAL) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.ISTAR_GOAL) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " must map to ISTAR_GOAL, got " + tSymbol.kind(),
                                c.location()));
                    }
                } else if (sSymbol.kind() == SourceReference.Kind.ASL_PLAN_STEP) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.ISTAR_TASK) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " must map to ISTAR_TASK, got " + tSymbol.kind(),
                                c.location()));
                    }
                } else if (sSymbol.kind() == SourceReference.Kind.AGENT || sSymbol.kind() == SourceReference.Kind.MOISE_ROLE) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.ISTAR_ACTOR) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " must map to ISTAR_ACTOR, got " + tSymbol.kind(),
                                c.location()));
                    }
                } else if (sSymbol.kind() == SourceReference.Kind.MOISE_GROUP
                        || sSymbol.kind() == SourceReference.Kind.MOISE_SCHEME
                        || sSymbol.kind() == SourceReference.Kind.ARTIFACT) {
                    if (tSymbol.kind() != TargetSymbol.TargetKind.ISTAR_ACTOR
                            && tSymbol.kind() != TargetSymbol.TargetKind.ISTAR_RESOURCE) {
                        diagnostics.add(new Diagnostic("INCOMPATIBLE_TYPE_MAPPING",
                                "Source " + sSymbol.kind() + " must map to ISTAR_ACTOR or ISTAR_RESOURCE, got " + tSymbol.kind(),
                                c.location()));
                    }
                }
            }
        }

        // Validate actor-runtime
        for (CompactMappingSpecification.ActorRuntime ar : spec.actorRuntimes()) {
            TargetReference tRef = TargetReference.parse(ar.actor());
            try {
                TargetSymbol ts = targetCatalog.resolve(tRef, "ISTAR");
                if (ts.kind() != TargetSymbol.TargetKind.ISTAR_ACTOR) {
                    diagnostics.add(new Diagnostic("ACTOR_RUNTIME_NOT_ACTOR",
                            "Actor '" + ar.actor() + "' is not an ISTAR_ACTOR: " + ts.kind(),
                            ar.location()));
                }
            } catch (Exception e) {
                diagnostics.add(new Diagnostic("ACTOR_RESOLUTION_FAILED", e.getMessage(), ar.location()));
            }
        }

        for(var verification:spec.verificationRequirements())try {
            var target=targetCatalog.resolve(TargetReference.parse(verification.target()),"ISTAR");
            if(!Set.of(TargetSymbol.TargetKind.ISTAR_GOAL,TargetSymbol.TargetKind.ISTAR_TASK).contains(target.kind()))throw new IllegalArgumentException("VERIFICATION_INTENTIONAL_TARGET_REQUIRED");
            if(!verification.scope().isBlank()) {
                sourceCatalog.resolve(SourceReference.parse(verification.scope()));
                boolean selected=spec.istarMappings().stream().filter(c->TargetReference.parse(c.target()).isSimple())
                        .anyMatch(c->c.source().equals(verification.scope())||verification.scope().equals(c.attributes().get("scope")));
                if(!selected)throw new IllegalArgumentException("VERIFICATION_SCOPE_NOT_DECLARED_BY_ACTOR_MAPPING");
            }
        }catch(IllegalArgumentException invalid){diagnostics.add(new Diagnostic("VERIFICATION_SELECTION_INVALID",invalid.getMessage(),verification.location()));}
        return List.copyOf(diagnostics);
    }

    public static void requireValid(CompactMappingSpecification spec,
                                    SourceSymbolCatalog sourceCatalog,
                                    NativeTargetCatalog targetCatalog) {
        List<Diagnostic> diagnostics = validate(spec, sourceCatalog, targetCatalog);
        if (!diagnostics.isEmpty()) {
            throw new IllegalArgumentException("COMPACT_MAPPING_VALIDATION_FAILED:\n" +
                    String.join("\n", diagnostics.stream().map(Diagnostic::toString).toList()));
        }
    }

    private CorrespondenceValidator() {}
}

