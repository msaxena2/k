package org.kframework.krun.api;

import edu.uci.ics.jung.graph.DirectedOrderedSparseMultigraph;
import org.kframework.kil.Term;
import org.kframework.kil.Variable;

/**
 * Represents the substitution within a transition in the KRun.
 * Useful if the compute graph option specified in the step operation.
 */
public class SubstitutionMap extends DirectedOrderedSparseMultigraph<Variable, Term> {
}
