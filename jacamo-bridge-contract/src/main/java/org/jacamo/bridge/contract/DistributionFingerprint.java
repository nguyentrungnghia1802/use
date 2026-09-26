package org.jacamo.bridge.contract;

import java.util.Map;

public record DistributionFingerprint(String jacamoVersion, String distributionDigest, Map<String, String> components) {
    public DistributionFingerprint {
        jacamoVersion = ContractSupport.required(jacamoVersion, "jacamoVersion");
        distributionDigest = ContractSupport.required(distributionDigest, "distributionDigest");
        components = ContractSupport.map(components);
    }
}
