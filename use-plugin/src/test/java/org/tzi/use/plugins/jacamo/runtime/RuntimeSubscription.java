package org.tzi.use.plugins.jacamo.runtime;

@FunctionalInterface
public interface RuntimeSubscription extends AutoCloseable {
    @Override void close();
}
