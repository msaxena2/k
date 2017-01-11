// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.DebugMode;

import org.jgrapht.DirectedGraph;
import org.kframework.debugger.DebuggerMatchResult;
import org.kframework.debugger.DebuggerState;
import org.kframework.debugger.KDebug;
import org.kframework.debugger.ProofState;
import org.kframework.definition.Rule;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kore.K;
import org.kframework.krun.KRun;
import org.kframework.unparser.OutputModes;
import org.kframework.utils.Goal;
import org.kframework.utils.PatternNode;
import org.kframework.utils.ProofTransition;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.kframework.krun.KRun.*;

/**
 * Created by Manasvi on 7/22/15.
 * <p>
 * Classes concerned with TUI commands go under this class.
 * Commands must implement {@link org.kframework.DebugMode.Command}.
 */
public class Commands {

    public static class StepCommand implements Command {

        private int stepCount;

        public StepCommand(int stepCount) {
            this.stepCount = stepCount;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            int activeStateId = session.getActiveStateId();
            DebuggerState prevState = session.getStates().get(activeStateId);
            DebuggerState steppedState = session.step(activeStateId, stepCount);
            int effectiveStepCount = steppedState.getStepNum() - prevState.getStepNum();
            if (effectiveStepCount < stepCount) {
                utils.print("Attempted " + stepCount + " step(s). " + "Took " + effectiveStepCount + " steps(s).");
                utils.print("Final State Reached");
            } else
                utils.print(stepCount + " Step(s) Taken.");
            utils.displayWatches(steppedState.getWatchList(), compiledDefinition);
        }
    }

    public static class WatchCommand implements Command {
        private Optional<String> pattern;

        public WatchCommand(Optional<String> pattern) {
            this.pattern = pattern;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            pattern.ifPresent(pattern -> {
                if (isSource) {
                    session.addWatch(pattern, "<Source File>");
                } else {
                    session.addWatch(pattern, "<Command Line>");
                }
            });
            utils.displayWatches(
                    session.getActiveState().getWatchList(),
                    compiledDefinition
            );
        }
    }

    public static class PeekCommand implements Command {

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            DebuggerState requestedState = session.getActiveState();
            if (requestedState != null) {
                prettyPrint(compiledDefinition, OutputModes.PRETTY, s -> utils.print(s), requestedState.getCurrentK());
            } else {
                throw KEMException.debuggerError("\"Requested State/Configuration Unreachable\",");
            }
        }
    }

    public static class BackStepCommand implements Command {

        private int backStepCount;

        public BackStepCommand(int backStepCount) {
            this.backStepCount = backStepCount;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            int activeConfigurationId = session.getActiveState().getStepNum();
            CommandUtils utils = new CommandUtils(isSource);
            if (backStepCount > 1 && (backStepCount) > activeConfigurationId) {
                throw KEMException.debuggerError("Number of Configuration(s) is " + (activeConfigurationId + 1) + ". Step Count to go back must be in range [0, " + (activeConfigurationId + 1) + ")");

            }
            DebuggerState backSteppedState = session.backStep(session.getActiveStateId(), backStepCount);
            if (backSteppedState == null) {
                throw KEMException.debuggerError("\"Already at Start State, Cannot take steps.\",");
            }
            utils.print("Took -" + backStepCount + " step(s)");
            utils.displayWatches(backSteppedState.getWatchList(), compiledDefinition);
        }
    }

    public static class SelectCommand implements Command {
        private int stateNum;

        public SelectCommand(int stepNum) {
            this.stateNum = stepNum;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            DebuggerState selectedState = session.setState(stateNum);
            if (selectedState == null) {
                throw KEMException.debuggerError("Requested State not Present in List of states");
            }
            CommandUtils utils = new CommandUtils(isSource);
            utils.print("Selected State " + stateNum);
        }
    }

    public static class JumpToCommand implements Command {

        private Optional<Integer> stateNum;

        private Optional<Integer> configurationNum;

        public JumpToCommand(Optional<Integer> stateNum, Optional<Integer> configurationNum) {
            this.stateNum = stateNum;
            this.configurationNum = configurationNum;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            int requestedState = stateNum.orElse(session.getActiveStateId());
            CommandUtils utils = new CommandUtils(isSource);
            DebuggerState nextState = session.setState(requestedState);
            if (nextState == null) {
                throw KEMException.debuggerError("State Number specified does not exist");
            } else if (!configurationNum.isPresent()) {
                utils.print("Selected State " + requestedState);
            } else {
                int requestedConfig = configurationNum.get();
                DebuggerState finalState = session.jumpTo(requestedState, requestedConfig);
                if (finalState == null) {
                    throw KEMException.debuggerError("Requested Step Number couldn't be selected.");
                } else if (!stateNum.isPresent()) {
                    utils.print("Jumped to Step Number " + requestedConfig);
                } else {
                    utils.print("Jumped to State Number " + requestedState + " and Step Number " + requestedConfig);
                }
                utils.displayWatches(finalState.getWatchList(), compiledDefinition);
                return;
            }
            utils.displayWatches(nextState.getWatchList(), compiledDefinition);
        }

    }

    public static class QuitCommand implements Command {

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            return;
        }
    }

    public static class ResumeCommand implements Command {
        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            DebuggerState currentState = session.getStates().get(session.getActiveStateId());
            DebuggerState finalState = session.resume();
            CommandUtils utils = new CommandUtils(isSource);
            utils.print("Took " + (finalState.getStepNum() - currentState.getStepNum()) + " step(s)");
            utils.displayWatches(finalState.getWatchList(), compiledDefinition);
        }
    }

    public static class CheckpointCommand implements Command {
        private int checkpointInterval;

        public CheckpointCommand(int checkpointInterval) {
            this.checkpointInterval = checkpointInterval;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            if (checkpointInterval <= 0) {
                KEMException.debuggerError("Checkpoint Value must be >= 1");
            }
            session.setCheckpointInterval(checkpointInterval);
            utils.print("Checkpointing Interval set to " + checkpointInterval);
            return;
        }
    }


    public static class ShowCommand implements Command {
        private String pattern;

        public ShowCommand(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            DebuggerMatchResult result = session.match(pattern, "<Command Line>");
            CommandUtils utils = new CommandUtils(isSource);
            utils.prettyPrintSubstitution(result, compiledDefinition);
        }

    }

    public static class SourceCommand implements Command {
        private String sourceFile;

        public SourceCommand(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            return;
        }
    }

    public static class MatchUntilCommand implements Command {
        private String sourceFile;

        public MatchUntilCommand(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getPatternSourceFile() {
            return sourceFile;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            DebuggerState res = session.matchUntilPattern(this.getPatternSourceFile());
            if (res != null) {
                KRun.printK(res.getCurrentK(), null, OutputModes.PRETTY, null, compiledDefinition, files, kem);
            }
        }
    }

    public static class PatternSourceCommand implements Command {
        private String sourceFile;

        public PatternSourceCommand(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public String getPatternSourceFile() {
            return sourceFile;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(isSource);
            ProofState res = session.addPatternSourceFile(this.getPatternSourceFile());
            List<Goal> goalList = res.getGoalList();
            if (!goalList.isEmpty()) {
                System.out.println(goalList.size() + " goal(s) loaded");
            }

        }
    }

    public static class GoalPeekCommand implements Command {

        Optional<Integer> claimNum;

        Optional<Integer> termNum;

        public GoalPeekCommand(Optional<Integer> claimNum, Optional<Integer> termNum) {
            this.claimNum = claimNum;
            this.termNum = termNum;
        }


        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean disableOutput, FileUtil files, KExceptionManager kem) {
            ProofState proofState = session.getProofState();
            List<Goal> goals = proofState.getGoalList();
            CommandUtils utils = new CommandUtils(disableOutput);
            if (claimNum.isPresent()) {
                Integer currentClaim = claimNum.get();
                if (currentClaim >= goals.size() || currentClaim < 0) {
                    throw KEMException.debuggerError(currentClaim + " is not a valid claim id");
                }
                Goal currentGoal = goals.get(currentClaim);
                if (termNum.isPresent()) {
                    List<PatternNode> termList = currentGoal.getProofTree().vertexSet().stream().filter(x -> x.getId() == termNum.get()).collect(Collectors.toList());
                    if (termList.isEmpty()) {
                        throw KEMException.debuggerError(termNum.get() + " is not a valid term number in " + currentClaim);
                    }
                    utils.print("Claim " + claimNum.get() + "Term " + termNum.get() + "\n");
                    KRun.printK(termList.get(0).getPattern(), null, OutputModes.PRETTY, null, compiledDefinition, files, kem);
                } else {
                    utils.print("Claim " + claimNum.get() + "\n");
                    printReachabilityClaim(compiledDefinition, files, kem, utils, currentGoal.getOriginalTerm(), currentGoal.getTargetTerm());
                    printTerms(currentGoal, disableOutput, compiledDefinition, files, kem);

                }
            } else {
                //Default case for neither claimNum nor termNum provided by the user
                for (int i = 0; i < goals.size(); ++i) {
                    if (i == proofState.getActiveId()) {
                        utils.print("Claim " + i + " * ");
                    } else {
                        utils.print("Claim " + i);
                    }
                    printReachabilityClaim(compiledDefinition, files, kem, utils, goals.get(i).getOriginalTerm(), goals.get(i).getTargetTerm());
                }
            }
        }

        private void printTerms(Goal goal, boolean disableOutput, CompiledDefinition compiledDefinition, FileUtil files, KExceptionManager kem) {
            CommandUtils utils = new CommandUtils(disableOutput);
            DirectedGraph<PatternNode, ProofTransition> graph = goal.getProofTree();
            List<PatternNode> leafNodes = graph.vertexSet().stream().filter(x -> graph.outDegreeOf(x) == 0).collect(Collectors.toList());
            leafNodes.forEach(x -> {
                utils.print("\nTerm " + x.getId());
                KRun.printK(x.getPattern(), null, OutputModes.PRETTY, null, compiledDefinition, files, kem);
            });
        }
        //helper function to print out the reachability claim
        private void printReachabilityClaim(CompiledDefinition compiledDefinition, FileUtil files, KExceptionManager kem, CommandUtils utils, K initialTerm, K targetTerm) {
            KRun.printK(initialTerm, null, OutputModes.PRETTY, null, compiledDefinition, files, kem);
            utils.print("=>");
            KRun.printK(targetTerm, null, OutputModes.PRETTY, null, compiledDefinition, files, kem);
        }
    }

    public static class StepAllCommand implements Command {
        private int stepNum;

        public StepAllCommand(int stepNum) {
            this.stepNum = stepNum;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean disableOutput, FileUtil files, KExceptionManager kem) {
            ProofState proofState = session.stepAll(stepNum);
            CommandUtils utils = new CommandUtils(disableOutput);
            utils.print("Took " + stepNum + " step(s)");
        }
    }

    public static class RemoveWatchCommand implements Command {
        private int watchNum;

        public RemoveWatchCommand(int watchNum) {
            this.watchNum = watchNum;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            if (session.removeWatch(watchNum) < 0) {
                throw KEMException.debuggerError("Watch Doesn't Exists");
            }
            CommandUtils utils = new CommandUtils(isSource);
            utils.print("Watch " + (watchNum) + " removed");
        }
    }

    public static class CopyCommand implements Command {
        private int stateNum;

        public CopyCommand(int stateNum) {
            this.stateNum = stateNum;
        }

        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            if (session.createCopy(stateNum) == null) {
                throw KEMException.debuggerError("StateNumber Speicified doesn't exist");
            }
            CommandUtils utils = new CommandUtils(isSource);
            utils.print("Copied State " + stateNum);
        }
    }

    public static class GetStatesCommand implements Command {
        @Override
        public void runCommand(KDebug session, CompiledDefinition compiledDefinition, boolean isSource, FileUtil files, KExceptionManager kem) {
            List<DebuggerState> stateList = session.getStates();
            int activeStateIndex = session.getActiveStateId();
            int i = 0;
            CommandUtils utils = new CommandUtils(isSource);
            for (DebuggerState state : stateList) {
                if (i == activeStateIndex) {
                    utils.print("State " + (i++) + "*");
                } else {
                    utils.print("State " + (i++));
                }
                utils.print("Step Count " + state.getStepNum());
            }
        }
    }

    private static class CommandUtils {
        private boolean disableOutput;

        private CommandUtils(boolean isSource) {
            this.disableOutput = isSource;
        }

        private void prettyPrintSubstitution(DebuggerMatchResult result, CompiledDefinition compiledDefinition) {
            if (disableOutput) {
                return;
            }
            KRun.prettyPrint(compiledDefinition, OutputModes.PRETTY, s -> System.out.println(s), result.getSubstitutions());
        }

        private void print(byte[] bytes) {
            if (!disableOutput) {
                try {
                    System.out.write(bytes);
                } catch (IOException e) {
                    KEMException.debuggerError("IOError :" + e.getMessage());
                }
            }
        }

        private void print(String printString) {
            if (!disableOutput) {
                System.out.println(printString);
            }
        }

        private void displayWatches(List<DebuggerMatchResult> watches, CompiledDefinition compiledDefinition) {
            if (watches.isEmpty()) {
                return;
            }
            print("Watches:");
            int i = 0;
            for (DebuggerMatchResult watch : watches) {
                print("Watch " + (i));
                print("Pattern : " + watch.getPattern());
                prettyPrintSubstitution(watch, compiledDefinition);
                i++;
            }
        }
    }
}
