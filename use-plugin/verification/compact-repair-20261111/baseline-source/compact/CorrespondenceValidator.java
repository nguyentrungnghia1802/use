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

        // Validate CSL correspondences
        for (CompactMappingSpecification.Correspondence c : spec.cslMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());

            SourceSymbol sSymbol = null;
            try {
                sSymbol = sourceCatalog.resolve(sRef);
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
                if (!tSymbol.isCsl()) {
                    diagnostics.add(new Diagnostic("TARGET_MODEL_MISMATCH",
                            "Target '" + c.target() + "' belongs to " + tSymbol.modelSource() + ", expected CSL",
                            c.location()));
                }

                // Check correspondence compatibility
                if (sSymbol.kind() == SourceReference.Kind.ARTIFACT_PROPERTY || sSymbol.kind() == SourceReference.Kind.ARTIFACT_FIELD) {
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
                sSymbol = sourceCatalog.resolve(sRef);
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

