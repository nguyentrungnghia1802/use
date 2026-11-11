package org.vnu.sme.goal.dsl.csl.ast;

public record CslSourceLocationCS(int line, int column) {
    public CslSourceLocationCS {
        if (line < 1) throw new IllegalArgumentException("line must be positive");
        if (column < 0) throw new IllegalArgumentException("column must be non-negative");
    }
}
