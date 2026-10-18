package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

/** An opaque domain data type declared by {@code datatype Name;}. */
public record CslNamedDataType(String sourceName) implements CslDataType {
    public CslNamedDataType {
        Objects.requireNonNull(sourceName, "sourceName");
    }
}
