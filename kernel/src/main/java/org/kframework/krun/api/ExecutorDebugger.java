// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.krun.api;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Cell;
import org.kframework.kil.KApp;
import org.kframework.kil.KLabelConstant;
import org.kframework.kil.Rule;
import org.kframework.kil.Sentence;
import org.kframework.kil.Sort;
import org.kframework.kil.StringBuiltin;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.Transition.TransitionType;
import org.kframework.krun.tools.Debugger;
import org.kframework.krun.tools.Executor;
import org.kframework.parser.TermLoader;
import org.kframework.utils.errorsystem.KExceptionManager;

import com.google.inject.Inject;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.util.Pair;

import java.util.Map.Entry;

public class ExecutorDebugger implements Debugger {
    private Integer currentState;
    private KRunGraph graph;
    private BidiMap<Integer, KRunState> states;

    private static Rule defaultPattern;
    private static RuleCompilerSteps defaultPatternInfo;

    private final Context context;
    private final Executor executor;
    private final TermLoader loader;
    private final KExceptionManager kem;
    private final KRunState.Counter counter;

    @Inject
    public ExecutorDebugger(
            Executor executor,
            Context context,
            TermLoader loader,
            KExceptionManager kem,
            KRunState.Counter counter) throws KRunExecutionException {
        this.context = context;
        this.executor = executor;
        this.loader = loader;
        this.kem = kem;
        this.counter = counter;
    }

    @Override
    public void start(Term initialConfiguration) throws KRunExecutionException {
        try {
            ASTNode pattern = loader.parsePattern(
                    KRunOptions.DEFAULT_PATTERN,
                    null,
                    Sort.BAG,
                    context);
            defaultPatternInfo = new RuleCompilerSteps(context, kem);
            pattern = defaultPatternInfo.compile(new Rule((Sentence) pattern), null);

            defaultPattern = (Rule) pattern;
        } catch (CompilerStepDone e) {
            e.printStackTrace();
        }

        KRunState initialState = new KRunState(initialConfiguration, counter);
        graph = new KRunGraph();
        graph.addVertex(initialState);
        states = new DualHashBidiMap<Integer, KRunState>();
        putState(initialState);
        KRunState reduced = executor.step(initialConfiguration, 0).getResult();
        //reduce may return same node as initial node
        //so we add it just if it is different from the initial node
        if(putState(reduced)){
            graph.addVertex(reduced);
            graph.addEdge(Transition.reduce(), initialState, reduced);
            currentState = reduced.getStateId();
        }else {
            currentState = initialState.getStateId();
        }
    }

    /**
     * Adds the new state to the states map
     * @param state new state to add
     * @return if the stated wasn't previously in the states map return true else return false
     */
    private boolean putState(KRunState state) {
        if(!states.containsValue(state)){
            states.put(state.getStateId(), state);
            return true;
        }
        return false;
    }

    public KRunGraph getGraph() {
        return graph;
    }

    public Integer getCurrentState() {
        return currentState;
    }

    public void setCurrentState(Integer stateNum) throws IllegalArgumentException {
        if (stateNum == null || states.containsKey(stateNum)) {
            currentState = stateNum;
        } else {
            throw new IllegalArgumentException("Cannot set current state to state " + stateNum
                    + ": it does not exist in the graph.");
        }
    }

    public KRunState getState(int stateNum) {
        KRunState state = states.get(stateNum);
        if (state == null) throw new IllegalArgumentException("State " + stateNum + " does not exist in the graph.");
        return state;
    }

    private void steppingLoop(Integer steps) throws KRunExecutionException {
        if (currentState == null) {
            throw new IllegalStateException("Cannot step without a current state to step from. "
                    + "If you previously used the search command you must"
                    + "first select a solution with the select command before executing steps of rewrites!");
        }
        for (int i = 0; steps == null || i < steps; i++) {
            KRunState nextStep = executor.step(getState(currentState).getRawResult(), 1).getResult();
            Entry<Integer, KRunState> prevValue = containsValue(nextStep);
            if (prevValue!=null) {
                nextStep = prevValue.getValue();

                int stateId = prevValue.getKey();
                if (stateId == currentState) {
                    //we've stopped moving, so that means we must have reached a final state
                    return;
                }
                // we've reached this state before, so update the current state and proceed to the next step
                currentState = stateId;
                continue;
            }
            else {
                putState(nextStep);
            }
            graph.addVertex(nextStep);
            graph.addEdge(Transition.unlabelled(), getState(currentState), nextStep);
            currentState = nextStep.getStateId();
        }
    }

    public void step(int steps) throws KRunExecutionException {
        steppingLoop(steps);
    }

    public void resume() throws KRunExecutionException {
        steppingLoop(null);
    }

    public SearchResults stepAll(int steps) throws KRunExecutionException {
        if (currentState == null) {
            throw new IllegalStateException("Cannot step without a current state to step from. "
                    + "If you previously used the search command you must"
                    + "first select a solution with the select command before executing steps of rewrites!");
        }
        SearchResults results = executor.search(null, steps, SearchType.PLUS, defaultPattern, getState(currentState).getRawResult(), defaultPatternInfo).getResult();
        mergeSearchGraph(results.getGraph());
        currentState = null;
        return results;
    }

    private void mergeSearchGraph(DirectedGraph<KRunState, Transition> graphFragment) {
        for (KRunState state : graphFragment.getVertices()) {
            //check if graph already contains state
            Entry<Integer, KRunState> prevValue = containsValue(state);
            if (prevValue==null) {
                putState(state);
                graph.addVertex(state);
            }
        }
        for (Transition edge : graphFragment.getEdges()) {
            Pair<KRunState> vertices = graphFragment.getEndpoints(edge);
            Transition existingEdge = graph.findEdge(vertices.getFirst(), vertices.getSecond());
            KRunState first =vertices.getFirst();
            KRunState second = vertices.getSecond();
            //if graph already contained state used old state
            Entry<Integer, KRunState> prevValue = containsValue(first);
            if (prevValue!=null){
                first = prevValue.getValue();
            }
            prevValue = containsValue(second);
            if(prevValue!=null){
                second = prevValue.getValue();
            }
            if (existingEdge != null && existingEdge.getType() == TransitionType.UNLABELLED) {
                graph.removeEdge(existingEdge);
                graph.addEdge(edge, first, second);
            } else if (existingEdge == null) {
                graph.addEdge(edge, first, second);
            }
        }
    }

    /* checks if state already exists(using Semantic equal)
     * if it exists return old value
     * this intends to replace states.containsValue which uses hash and equals defined in KRunState
     */
    private Entry<Integer, KRunState> containsValue(KRunState state){
        for (Entry<Integer,KRunState> e : states.entrySet() ){
            if(SemanticEqual.checkEquality(state.getRawResult(), e.getValue().getRawResult()))
                return e ;
        }
        return null;
    }
    private KRunState canonicalizeState(KRunState state) {
        int stateNum = states.getKey(state);
        return states.get(stateNum);
    }

    public Transition getEdge(int state1, int state2) {
        KRunState first = getState(state1);
        KRunState second = getState(state2);
        Transition edge = graph.findEdge(first, second);
        if (edge == null)
            throw new IllegalArgumentException("Edge between states "
                    + state1 + " and " + state2 + " does not exist in the current graph");
        return edge;
    }

    public void readFromStdin(String s) {
        if (currentState == null) {
            throw new IllegalStateException("Cannot read from stdin without a current state to step from. "
                    + "If you previously used the search command you must"
                    + "first select a solution with the select command before reading from stdin!");
        }
        Term configuration = getState(currentState).getRawResult();
        AppendToStdin transformer = new AppendToStdin(s, context);
        Term result;
        result = (Term) transformer.visitNode(configuration);
        if (!transformer.getSucceeded()) {
            throw new IllegalStateException("Cannot perform command: Configuration does not " +
                    "have an stdin buffer");
        }
        KRunState newState = new KRunState(result, counter);
        Entry<Integer, KRunState> prevValue = containsValue(newState);
        if (prevValue!=null) {
            KRunState canonicalNewState = canonicalizeState(newState);
            Transition edge = graph.findEdge(getState(currentState), canonicalNewState);
            if (edge == null) {
                graph.addEdge(Transition.stdin(s),
                        getState(currentState), canonicalNewState);
            }
            currentState = canonicalNewState.getStateId();
            return;
        }
        putState(newState);
        graph.addVertex(newState);
        graph.addEdge(Transition.stdin(s),
                getState(currentState), newState);
        currentState = newState.getStateId();
    }

    private static class AppendToStdin extends CopyOnWriteTransformer {
        private String str;
        private boolean succeeded;
        private boolean inStdin, inBuffer;
        public AppendToStdin(String str, Context context) {
            super("Append a string to the stdin buffer", context);
            this.str = str;
            succeeded = false;
            inStdin = false;
            inBuffer = false;
        }

        public boolean getSucceeded() {
            return succeeded;
        }

        @Override
        public ASTNode visit(Cell cell, Void _void)  {
            if ("stdin".equals(context.cells.get(cell.getLabel())
                    .getCellAttributes().get("stream"))) {
                inStdin = true;
                ASTNode result = super.visit(cell, _void);
                inStdin = false;
                return result;
            }
            return super.visit(cell, _void);
        }

        @Override
        public ASTNode visit(KApp kapp, Void _void)  {
            if (kapp.getLabel().equals(KLabelConstant.of("'#buffer"))) {
                inBuffer = true;
                ASTNode result = super.visit(kapp, _void);
                inBuffer = false;
                return result;
            }
            return super.visit(kapp, _void);
        }

        @Override
        public ASTNode visit(StringBuiltin s, Void _void)  {
            if (inStdin && inBuffer) {
                succeeded = true;
                return StringBuiltin.of(s.stringValue() + str);
            }
            return super.visit(s, _void);
        }
    }

    @Override
    public void setGraph(KRunGraph graph) {
        this.graph = graph;
        states.clear();
        for (KRunState state : graph.getVertices()) {
            putState(state);
        }
    }
}
