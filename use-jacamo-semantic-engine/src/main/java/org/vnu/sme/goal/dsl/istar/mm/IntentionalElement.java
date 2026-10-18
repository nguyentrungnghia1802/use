package org.vnu.sme.goal.dsl.istar.mm;

/**
 * iStar 2.0 intentional elements: {@link Quality} (cloud) directly, and
 * everything under {@link ConcreteIntentionalElement} (Resource,
 * GoalTaskElement).
 */
public sealed interface IntentionalElement
        permits Quality, ConcreteIntentionalElement {

    String id();
}
