package org.tzi.use.semantic.engine.use75;

import java.util.Objects;
import org.tzi.use.uml.sys.MSystemState;

/** Borrowed pinned cuts of one authoritative system. Pin under the writer boundary;
 * the owner must not mutate either cut while evaluation runs. No MSystem is created. */
public record UseStateReadView(MSystemState before,MSystemState after) {
    public UseStateReadView {
        Objects.requireNonNull(after);
        if(before!=null&&before.system()!=after.system())throw new IllegalArgumentException("OCL_STATE_SYSTEM_MISMATCH");
    }
    public static UseStateReadView pin(MSystemState before,MSystemState after) {
        return new UseStateReadView(before==null?null:new MSystemState("semantic-before",before),new MSystemState("semantic-after",after));
    }
}
