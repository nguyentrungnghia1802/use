package org.tzi.use.plugins.jacamo.binding;

import java.util.List;

public record BindingFile(String schemaVersion, List<BindingEntry> entries) {
    public BindingFile { entries = List.copyOf(entries); }
    public static BindingFile empty() { return new BindingFile("1.0.0", List.of()); }
}
