package org.kframework.kore.strategies;

import org.kframework.Rewriter;
import org.kframework.Strategy;
import org.kframework.kore.K;

/**
 * Created by manasvi on 6/9/15.
 * Entry point for the debugger.
 */
public class Debug implements Strategy<Void>{

    @Override
    public Void execute(K k, Rewriter rewriter) {
        return null;
    }
}
