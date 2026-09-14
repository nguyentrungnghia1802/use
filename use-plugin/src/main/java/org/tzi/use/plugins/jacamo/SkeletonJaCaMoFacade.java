package org.tzi.use.plugins.jacamo;

/** Phase 1 facade; later phases add operations through this boundary. */
final class SkeletonJaCaMoFacade implements JaCaMoFacade {
    static final JaCaMoFacade INSTANCE = new SkeletonJaCaMoFacade();

    private SkeletonJaCaMoFacade() { }

    @Override
    public String status() {
        return "JaCaMo plugin ready (Phase 1 skeleton)";
    }
}
