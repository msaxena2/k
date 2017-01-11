// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.utils;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.kframework.definition.Rule;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.Unapply;

public class Goal {
    private K originalTerm;
    private K targetTerm;
    private boolean proved;
    private DirectedGraph<PatternNode, ProofTransition> proofTree;
    int nodeIds;


    public Goal(K originalTerm, K targetTerm, boolean proved) {
        this.originalTerm = originalTerm;
        this.targetTerm = targetTerm;
        this.proved = proved;
        this.proofTree = new DefaultDirectedGraph<PatternNode, ProofTransition>(ProofTransition.class);
        int nodeIds = 1;
        proofTree.addVertex(new PatternNode(originalTerm, nodeIds));
        nodeIds++;
    }

    public K getOriginalTerm() {
        return originalTerm;
    }

    public K getTargetTerm() {
        return targetTerm;
    }

    public boolean isProved() {
        return proved;
    }

    public DirectedGraph<PatternNode, ProofTransition> getProofTree() {
        return proofTree;
    }

    public int getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(int nodeIds) {
        this.nodeIds = nodeIds;
    }
}