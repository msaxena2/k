// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;

/**
 * Abstract class, to be extended by every container specific to the backend.
 * Allows for lazy evaluation of a backend specific term to a generic kernel term.
 */
public abstract class KilContainer {

    protected Term kilTerm;

    protected Context context;

    public KilContainer(Context context) {
        this.context = context;
    }

    /**
     * To be implemented in the extending class.
     * When called, the function should return the kilTerm, if already calculated,
     * or calculate it using the context and the backend term, and then return it.
     */

    public abstract Term getKil();
}
