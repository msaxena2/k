// Copyright (c) 2013-2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

/**
 * Contains information about a debugger step operation.
 */
public class KRunDebuggerResult {

    private KRunState originalState;
    private KRunState steppedState;
    private Transition rule;

    public KRunDebuggerResult() {
        originalState = null;
        steppedState = null;
        rule = null;
    }

    public KRunDebuggerResult(KRunState originalState, KRunState steppedState, Transition rule) {
        this.originalState = originalState;
        this.steppedState = steppedState;
        this.rule = rule;
    }

    public KRunState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(KRunState originalState) {
        this.originalState = originalState;
    }

    public KRunState getSteppedState() {
        return steppedState;
    }

    public void setSteppedState(KRunState steppedState) {
        this.steppedState = steppedState;
    }

    public Transition getRule() {
        return rule;
    }

    public void setRule(Transition rule) {
        this.rule = rule;
    }
}
