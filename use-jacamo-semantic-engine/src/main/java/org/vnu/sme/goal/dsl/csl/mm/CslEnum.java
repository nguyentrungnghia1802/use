package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;
import java.util.Objects;

public record CslEnum(String name, List<String> literals) implements CslDataType {
    public CslEnum {
        Objects.requireNonNull(name, "name");
        literals = List.copyOf(literals);
    }

    @Override
    public String sourceName() {
        return name;
    }
}
