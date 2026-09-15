package org.tzi.use.plugins.jacamo.verification.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class VerificationProfileLoader {
    private static final String V1 = "/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v1.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    public VerificationProfile loadV1() {
        try (InputStream input = VerificationProfileLoader.class.getResourceAsStream(V1)) {
            if (input == null) throw new VerificationProfileException("VERIFICATION_PROFILE_MISSING", V1);
            JsonNode root = JSON.readTree(input);
            List<VerificationProfile.Decision> decisions = new ArrayList<>();
            HashSet<String> ids = new HashSet<>();
            for (JsonNode node : root.withArray("decisions")) {
                String id = required(node, "id");
                if (!ids.add(id)) throw new VerificationProfileException("VERIFICATION_PROFILE_INVALID", "Duplicate decision " + id);
                String classification = required(node, "classification").replace('-', '_');
                decisions.add(new VerificationProfile.Decision(id,
                        VerificationProfile.Classification.valueOf(classification),
                        VerificationProfile.Kind.valueOf(required(node, "kind")), optional(node, "subclass"),
                        optional(node, "superclass"), optional(node, "mappingRuleId"), optional(node, "from"),
                        optional(node, "to"), required(node, "rationale")));
            }
            VerificationProfile profile = new VerificationProfile(required(root, "profileId"), required(root, "version"),
                    required(root, "status"), required(root, "baselineMappingId"), decisions);
            if (!profile.profileId().equals("JACAMO_VERIFICATION_PROFILE_V1") || !profile.status().equals("ACTIVE")
                    || decisions.size() != 5) throw new VerificationProfileException("VERIFICATION_PROFILE_INVALID", profile.toString());
            return profile;
        } catch (VerificationProfileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VerificationProfileException("VERIFICATION_PROFILE_INVALID", exception.getMessage(), exception);
        }
    }

    private String required(JsonNode node, String field) {
        String value = optional(node, field);
        if (value == null || value.isBlank()) throw new VerificationProfileException("VERIFICATION_PROFILE_INVALID", field + " is required");
        return value;
    }

    private String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
