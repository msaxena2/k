// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.krun.api;


import java.util.Optional;

/**
 * The result of a step operation in the debugger.
 * Contains the step/search graph if the compute graph option is specified.
 */
public class RewriteRelation {
    public KRunStateUnit finalState;
    Optional<KRunExecutionGraph> executionGraph;

    public KRunStateUnit getFinalState() {
        return finalState;
    }

    public void setFinalState(KRunStateUnit finalState) {
        this.finalState = finalState;
    }

    public Optional<KRunExecutionGraph> getExecutionGraph() {
        return executionGraph;
    }

    public void setExecutionGraph(KRunExecutionGraph executionGraph) {
        this.executionGraph = Optional.of(executionGraph);
    }
}
