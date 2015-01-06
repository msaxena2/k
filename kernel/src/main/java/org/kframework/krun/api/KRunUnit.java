// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

import org.kframework.kil.Term;

/**
 * A Unit of KRun's computation. It can represent a state, transition e.t.c.
 * Then Unit contains the container, which lazily converts a backend term to a generic term.
 * All intermediate results of KRun are units, and will extend this class.
 */

public abstract class KRunUnit {

    private KilTermContainer termContainer;

    public KRunUnit(KilTermContainer termContainer) {
        this.termContainer = termContainer;
    }

    public KilTermContainer getTermContainer() {
        return termContainer;
    }

    public void setTermContainer(KilTermContainer termContainer) {
        this.termContainer = termContainer;
    }

    public Term getKilTerm() {
        return termContainer.getKilTerm();
    }
}
