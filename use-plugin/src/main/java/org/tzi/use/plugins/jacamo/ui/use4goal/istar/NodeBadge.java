package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.awt.Color;

/**
 * Generic per-node status overlay (a small colored glyph drawn at the node's top-right
 * corner), independent of what put it there. Lets an instance/evaluation layer (e.g.
 * IStarScenario's satisfaction marking) annotate an existing diagram without this
 * package knowing anything about that layer — the dependency points the other way.
 */
public record NodeBadge(Color color, String glyph, String tooltip) {}
