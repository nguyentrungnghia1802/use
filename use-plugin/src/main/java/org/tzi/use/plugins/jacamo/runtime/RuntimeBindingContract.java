package org.tzi.use.plugins.jacamo.runtime;

/** Metamodel-specific binding validation; source event/action semantics remain generic. */
public interface RuntimeBindingContract {
    void validate(RuntimeMapping.Rule rule);
}
