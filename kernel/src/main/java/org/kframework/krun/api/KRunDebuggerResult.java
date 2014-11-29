// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;

/**
 * Contains information about a debugger step operation.
 */
public class KRunDebuggerResult {

    private KRunState steppedState;
    private Transition rule;

    public KRunDebuggerResult() {
        steppedState = null;
        rule = null;
    }

    public KRunDebuggerResult(KRunState steppedState, Transition rule) {
        this.steppedState = steppedState;
        this.rule = rule;
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
