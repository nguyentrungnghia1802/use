package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.tzi.use.uml.mm.MModel;

/** Read-only compatibility check. Phase 1B does not install external/V2 profiles into the native model. */
public final class NativeProfileCompatibilityPreflight {
    private static final Pattern CONTEXT = Pattern.compile("(?m)^\\s*context\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");

    public Result inspect(Path profile, MModel model) {
        try {
            String source = Files.readString(profile.toAbsolutePath().normalize());
            List<String> diagnostics = new ArrayList<>();
            var matcher = CONTEXT.matcher(source);
            int contexts = 0;
            while (matcher.find()) {
                contexts++;
                String context = matcher.group(1);
                if (model.getClass(context) == null)
                    diagnostics.add("NATIVE_PROFILE_CONTEXT_MISSING:" + context);
            }
            if (contexts == 0) diagnostics.add("NATIVE_PROFILE_CONTEXT_REQUIRED");
            return new Result(diagnostics.isEmpty(), contexts, diagnostics);
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("NATIVE_PROFILE_PREFLIGHT_IO: " + profile, error);
        }
    }

    public record Result(boolean compatible, int contextCount, List<String> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
    }
}
