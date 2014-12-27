// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.backend.java.util;

import org.kframework.backend.java.symbolic.BackendJavaKILtoKILTransformer;
import org.kframework.kil.loader.Context;
import org.kframework.kil.Term;
import org.kframework.krun.api.KilContainer;

/**
 * The java backend specific class for the lazy evaluating container.
 * Implements the converter that lazily converts the backend kilTerm to the generic term.
 */
public class JavaKilContainer extends KilContainer {

    private org.kframework.backend.java.kil.Term javaTerm;

    @Override
    public Term getKilTerm(Context context) {
        if(!(kilTerm == null)){
            return kilTerm;
        }
        kilTerm = (Term)javaTerm.accept(new BackendJavaKILtoKILTransformer(context));
        return kilTerm;
    }
}
