package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;

public sealed interface CslLinkOptionCS permits CslLinkOptionCS.ScopeCS,
        CslLinkOptionCS.ExtendsSubgroupsCS, CslLinkOptionCS.BidirectionalCS, CslLinkOptionCS.TypeCS {
    CslSourceLocationCS location();

    record ScopeCS(String value, CslSourceLocationCS location) implements CslLinkOptionCS {
        public ScopeCS {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(location, "location");
        }
    }

    record ExtendsSubgroupsCS(boolean value, CslSourceLocationCS location) implements CslLinkOptionCS {
        public ExtendsSubgroupsCS { Objects.requireNonNull(location, "location"); }
    }

    record BidirectionalCS(boolean value, CslSourceLocationCS location) implements CslLinkOptionCS {
        public BidirectionalCS { Objects.requireNonNull(location, "location"); }
    }

    /** compatibility ... type compatible|incompatible; */
    record TypeCS(String value, CslSourceLocationCS location) implements CslLinkOptionCS {
        public TypeCS {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(location, "location");
        }
    }
}
