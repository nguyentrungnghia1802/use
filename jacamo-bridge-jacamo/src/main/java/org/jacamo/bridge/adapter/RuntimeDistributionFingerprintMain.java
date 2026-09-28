package org.jacamo.bridge.adapter;

/** Prints the fingerprint of the exact Bridge/JaCaMo runtime loaded by this JVM. */
public final class RuntimeDistributionFingerprintMain {
    private RuntimeDistributionFingerprintMain() { }

    public static void main(String[] args) {
        var fingerprint = RuntimeDistributionFingerprint.compute();
        System.out.println("BRIDGE_RUNTIME_DISTRIBUTION_SHA256=" + fingerprint.distributionDigest());
        fingerprint.components().forEach((name, digest) ->
                System.out.println("BRIDGE_RUNTIME_COMPONENT=" + name + ":" + digest));
    }
}
