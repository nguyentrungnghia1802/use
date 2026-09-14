package org.tzi.use.plugins.jacamo.semantic;

import java.util.Objects;

/** Scalar Ecore value; absent attributes are omitted, never invented. */
public sealed interface AttributeValue permits AttributeValue.Text, AttributeValue.IntegerNumber, AttributeValue.Bool {
    record Text(String value) implements AttributeValue {
        public Text { Objects.requireNonNull(value, "value"); }
    }
    record IntegerNumber(long value) implements AttributeValue { }
    record Bool(boolean value) implements AttributeValue { }
}
