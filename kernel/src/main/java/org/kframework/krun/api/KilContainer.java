// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

import org.kframework.kil.loader.Context;
import org.kframework.kil.Term;

/**
 * Abstract class, to be extended by every container specific to the backend.
 * Allows for lazy evaluation of a backend specific term to a generic kernel term.
 */
public abstract class KilContainer {

    protected Term kilTerm;

    private TermType type;

    /**
     * To be implemented in the extending class.
     * When called, the function should return the kilTerm, if already calculated,
     * or calculate it using the context and the backend term, and then return it.
     */

    public abstract Term getKilTerm(Context context);

    public TermType getType() {
        return type;
    }

    public void setType(TermType type) {
        this.type = type;
    }
}
