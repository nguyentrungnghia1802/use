/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2010 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.tzi.use.uml.sys.events;

import java.util.Collection;

import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.sys.events.tags.EventContext;
import org.tzi.use.uml.sys.events.tags.ModelChangedEvent;

/**
 * Event for loaded class invariant.
 *
 * @author Frank Hilken
 */
public class ClassInvariantsLoadedEvent extends Event implements ModelChangedEvent {

	private final Collection<MClassInvariant> invariant;
	private final boolean evaluationDeferred;

	public ClassInvariantsLoadedEvent(EventContext ctx, Collection<MClassInvariant> newInv) {
		this(ctx, newInv, false);
	}

	/** A native atomic coordinator will publish the verified result after its checkpoint. */
	public ClassInvariantsLoadedEvent(EventContext ctx, Collection<MClassInvariant> newInv, boolean evaluationDeferred) {
		super(ctx);
		invariant = java.util.List.copyOf(newInv);
		this.evaluationDeferred = evaluationDeferred;
	}
	public boolean isEvaluationDeferred() { return evaluationDeferred; }

	public Collection<MClassInvariant> getInvariants() {
		return invariant;
	}

}
