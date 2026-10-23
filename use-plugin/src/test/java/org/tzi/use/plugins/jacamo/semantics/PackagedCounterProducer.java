package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.Path;

/** Preserves the shipped Counter workload on the shared cold producer harness. */
final class PackagedCounterProducer extends PackagedProjectProducer {
    PackagedCounterProducer(Path install,Path work,int step)throws Exception {
        super(install,work,step,false);
    }
}