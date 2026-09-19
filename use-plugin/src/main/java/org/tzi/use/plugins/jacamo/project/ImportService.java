package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;

/** Contract for future .jcm import; no implementation exists in Phase 1. */
public interface ImportService {
    void importProject(Path jcmFile);
}
