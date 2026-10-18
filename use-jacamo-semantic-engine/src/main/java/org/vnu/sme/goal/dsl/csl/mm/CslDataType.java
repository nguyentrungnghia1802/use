package org.vnu.sme.goal.dsl.csl.mm;

public sealed interface CslDataType permits CslPrimitiveType, CslEnum, CslNamedDataType {
    String sourceName();
}
