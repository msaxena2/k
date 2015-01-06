// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.ics.jung.graph.util.Pair;
import org.kframework.backend.java.kil.ConstrainedExecutionGraph;
import org.kframework.backend.java.kil.ConstrainedRewriteRelation;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.JavaTransition;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.JavaKilContainer;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.Rewrite;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.SubstitutionFilter;
import org.kframework.krun.api.KRunExecutionGraph;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.KRunStateUnit;

import org.kframework.krun.api.KRunTransitionUnit;
import org.kframework.krun.api.KilContainer;
import org.kframework.krun.api.RewriteRelation;
import org.kframework.krun.api.SearchResult;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.tools.Executor;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class JavaSymbolicExecutor implements Executor {

    private final JavaExecutionOptions javaOptions;
    private final KILtoBackendJavaKILTransformer kilTransformer;
    private final GlobalContext globalContext;
    private final Provider<SymbolicRewriter> symbolicRewriter;
    private final Provider<PatternMatchRewriter> patternMatchRewriter;
    private final KILtoBackendJavaKILTransformer transformer;
    private final Context context;
    private final KRunState.Counter counter;

    @Inject
    JavaSymbolicExecutor(
            Context context,
            JavaExecutionOptions javaOptions,
            KILtoBackendJavaKILTransformer kilTransformer,
            GlobalContext globalContext,
            Provider<SymbolicRewriter> symbolicRewriter,
            Provider<PatternMatchRewriter> patternMatchRewriter,
            KILtoBackendJavaKILTransformer transformer,
            Definition definition,
            KRunState.Counter counter) {
        this.context = context;
        this.javaOptions = javaOptions;
        this.kilTransformer = kilTransformer;
        this.globalContext = globalContext;
        this.symbolicRewriter = symbolicRewriter;
        this.patternMatchRewriter = patternMatchRewriter;
        this.transformer = transformer;
        globalContext.setDefinition(definition);
        this.counter = counter;
    }

    @Override
    public KRunState run(org.kframework.kil.Term cfg) throws KRunExecutionException {
        return internalRun(cfg, -1);
    }

    private KRunState internalRun(org.kframework.kil.Term cfg, int bound) throws KRunExecutionException {
        ConstrainedTerm result = javaKILRun(cfg, bound);
        org.kframework.kil.Term kilTerm = (org.kframework.kil.Term) result.term().accept(
                new BackendJavaKILtoKILTransformer(context));
        KRunState returnResult = new KRunState(kilTerm, counter);
        return returnResult;
    }

    private ConstrainedTerm javaKILRun(org.kframework.kil.Term cfg, int bound) {
        Term term = kilTransformer.transformAndEval(cfg);
        TermContext termContext = TermContext.of(globalContext);
        termContext.setTopTerm(term);

        if (javaOptions.patternMatching) {
            ConstrainedTerm rewriteResult = new ConstrainedTerm(getPatternMatchRewriter().rewrite(term, bound, termContext), termContext);
            return rewriteResult;
        } else {
            SymbolicConstraint constraint = new SymbolicConstraint(termContext);
            ConstrainedTerm constrainedTerm = new ConstrainedTerm(term, constraint);
            return getSymbolicRewriter().rewrite(constrainedTerm, bound);
        }
    }

    @Override
    public SearchResults search(
            Integer bound,
            Integer depth,
            SearchType searchType,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) throws KRunExecutionException {

        List<Rule> claims = Collections.emptyList();
        if (bound == null) {
            bound = -1;
        }
        if (depth == null) {
            depth = -1;
        }

        // The pattern needs to be a rewrite in order for the transformer to be
        // able to handle it, so we need to give it a right-hand-side.
        org.kframework.kil.Cell c = new org.kframework.kil.Cell();
        c.setLabel("generatedTop");
        c.setContents(new org.kframework.kil.Bag());
        pattern.setBody(new org.kframework.kil.Rewrite(pattern.getBody(), c, context));
        Rule patternRule = transformer.transformAndEval(pattern);

        List<SearchResult> searchResults = new ArrayList<SearchResult>();
        List<Map<Variable, Term>> hits;
        Term initialTerm = kilTransformer.transformAndEval(cfg);
        Term targetTerm = null;
        TermContext termContext = TermContext.of(globalContext);
        if (javaOptions.patternMatching) {
            hits = getPatternMatchRewriter().search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType, termContext);
        } else {
            hits = getSymbolicRewriter().search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType, termContext);
        }

        for (Map<Variable, Term> map : hits) {
            // Construct substitution map from the search results
            Map<String, org.kframework.kil.Term> substitutionMap =
                    new HashMap<String, org.kframework.kil.Term>();
            for (Variable var : map.keySet()) {
                org.kframework.kil.Term kilTerm =
                        (org.kframework.kil.Term) map.get(var).accept(
                                new BackendJavaKILtoKILTransformer(context));
                substitutionMap.put(var.name(), kilTerm);
            }

            // Apply the substitution to the pattern
            org.kframework.kil.Term rawResult =
                    (org.kframework.kil.Term) new SubstitutionFilter(substitutionMap, context)
                            .visitNode(pattern.getBody());

            searchResults.add(new SearchResult(
                    new KRunState(rawResult, counter),
                    substitutionMap,
                    compilationInfo));
        }

        SearchResults retval = new SearchResults(
                searchResults,
                null);

        return retval;
    }

    @Override
    public KRunState step(org.kframework.kil.Term cfg, int steps)
            throws KRunExecutionException {
        return internalRun(cfg, steps);
    }

    public SymbolicRewriter getSymbolicRewriter() {
        return symbolicRewriter.get();
    }

    private PatternMatchRewriter getPatternMatchRewriter() {
        return patternMatchRewriter.get();
    }

    private ConstrainedRewriteRelation javaTraceRun(org.kframework.kil.Term cfg, int steps, boolean computeGraph)
            throws KRunExecutionException {
        Term term = kilTransformer.transformAndEval(cfg);
        TermContext termContext = TermContext.of(globalContext);
        ConstrainedRewriteRelation resultRelation;
        if (javaOptions.patternMatching) {
            if (computeGraph) {
                throw new KRunExecutionException("Sorry! Compute Graph not yet implemented with pattern matching.");
            } else {
                //implement this
                return null;

            }
        } else {
            SymbolicConstraint constraint = new SymbolicConstraint(termContext);
            ConstrainedTerm constrainedTerm = new ConstrainedTerm(term, constraint);
            resultRelation = getSymbolicRewriter().traceRewrite(constrainedTerm, steps, computeGraph);
        }

        return resultRelation;
    }
    private KRunTransitionUnit transitionTransformer(JavaTransition javaTransition) {
        KRunTransitionUnit kilTransition = new KRunTransitionUnit(new JavaKilContainer(context,() (Term)javaTransition.getRule()));
    }

    private RewriteRelation toGenericTransformer(ConstrainedRewriteRelation rewriteRelation) {
        RewriteRelation returnRelation = new RewriteRelation();
        /* set the final state */
        returnRelation.setFinalState(new KRunStateUnit(new JavaKilContainer(context, rewriteRelation.getFinalTerm().term()), counter));
        KRunExecutionGraph executionGraph = null;
        /* processing the graph */
        if(rewriteRelation.getConstrainedExecutionGraph().isPresent()) {
            ConstrainedExecutionGraph constrainedGraph = rewriteRelation.getConstrainedExecutionGraph().get();
            executionGraph = new KRunExecutionGraph();
            for(JavaTransition transition : constrainedGraph.getEdges()) {
                Pair<ConstrainedTerm> nodes = constrainedGraph.getEndpoints(transition);
                KRunStateUnit node1 = new KRunStateUnit(
                        new JavaKilContainer(context, nodes.getFirst().term()), counter);
                KRunStateUnit node2 = new KRunStateUnit(
                        new JavaKilContainer(context, nodes.getSecond().term()), counter);
                KRunTransitionUnit kilTransition = transitionTransformer(transition);
            }
        }
        return null;
    }

    private RewriteRelation graphStep(org.kframework.kil.Term cfg, int steps, boolean computeGraph)
            throws KRunExecutionException {
        ConstrainedRewriteRelation resultRelation = javaTraceRun(cfg, steps, computeGraph);
        /* Process Result Relation i.e convert to generic relation by adding wrapper classes */
        RewriteRelation returnResult = new RewriteRelation();
        return null;
    }
}
