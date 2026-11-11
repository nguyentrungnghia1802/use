package org.vnu.sme.goal.dsl.csl.mm;

import java.util.List;

/** YAML-facing entity definition; CSL keeps the CslEntity adapter name too. */
public interface EntityDefinition extends CardinalityTarget {
    String name();
    List<CslAttribute> attributes();
}
