package org.vnu.sme.goal.dsl.istar.mm;

/**
 * quality ···· element  (dotted line — quality constrains element).
 * {@code element} is any {@link ConcreteIntentionalElement} (resource,
 * goal, task, or resource) — not restricted to goal/task.
 */
public final class Qualification {

    private String quality;
    private String element;

    public Qualification(String quality, String element) {
        this.quality = quality;
        this.element = element;
    }

    public String quality() { return quality; }
    public String element() { return element; }

    public void setQuality(String quality) { this.quality = quality; }
    public void setElement(String element) { this.element = element; }
}
