// Copyright (c) 2013-2014 K Team. All Rights Reserved.

package org.kframework.backend.java.kil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.indexing.IndexingPair;
import org.kframework.backend.java.rewritemachine.Instruction;
import org.kframework.backend.java.rewritemachine.KAbstractRewriteMachine;
import org.kframework.backend.java.symbolic.BottomUpVisitor;
import org.kframework.backend.java.symbolic.SymbolicConstraint;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.UninterpretedConstraint;
import org.kframework.backend.java.symbolic.UninterpretedConstraint.Equality;
import org.kframework.backend.java.symbolic.VariableOccurrencesCounter;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.loader.Constants;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;


/**
 * A K rule in the format of the Java Rewrite Engine.
 *
 * @author AndreiS
 */
public class Rule extends JavaSymbolicObject {

    private final String label;
    private final Term leftHandSide;
    private final Term rightHandSide;
    private final ImmutableList<Term> requires;
    private final ImmutableList<Term> ensures;
    private final ImmutableSet<Variable> freshConstants;
    private final ImmutableSet<Variable> freshVariables;
    private final UninterpretedConstraint lookups;
    private final IndexingPair indexingPair;
    private final boolean containsKCell;

    /**
     * Specifies whether this rule has been compiled to generate instructions
     * for the {@link KAbstractRewriteMachine}.
     */
    private boolean compiledForFastRewriting;
    /**
     * Left-hand sides of the local rewrite operations under read cells; such
     * left-hand sides are used as patterns to match against the subject term.
     */
    private final Map<CellLabel, Term> lhsOfReadCells;
    /**
     * Right-hand sides of the local rewrite operations under write cells.
     */
    private final Map<CellLabel, Term> rhsOfWriteCells;
    /**
     * @see Rule#computeReusableBoundVars()
     */
    private final Multiset<Variable> reusableVariables;
    /**
     * Ground cells inside the right-hand side of this rule. Since cells are
     * mutable, they must be copied when the RHS is instantiated to avoid
     * undesired sharing.
     */
    private final Set<CellLabel> groundCells;
    /**
     * Instructions generated from this rule to be executed by the
     * {@link KAbstractRewriteMachine}.
     */
    private final List<Instruction> instructions;

    private final boolean modifyCellStructure;

    // TODO(YilongL): make it final
    private boolean isSortPredicate;
    private final Sort predSort;
    private final KItem sortPredArg;

    public Rule(
            String label,
            Term leftHandSide,
            Term rightHandSide,
            List<Term> requires,
            List<Term> ensures,
            Set<Variable> freshConstants,
            Set<Variable> freshVariables,
            UninterpretedConstraint lookups,
            boolean compiledForFastRewriting,
            Map<CellLabel, Term> lhsOfReadCells,
            Map<CellLabel, Term> rhsOfWriteCells,
            Set<CellLabel> cellsToCopy,
            List<Instruction> instructions,
            ASTNode oldRule,
            Definition definition) {
        this.label = label;
        this.leftHandSide = leftHandSide;
        this.rightHandSide = rightHandSide;
        this.requires = ImmutableList.copyOf(requires);
        this.ensures = ImmutableList.copyOf(ensures);
        this.freshConstants = ImmutableSet.copyOf(freshConstants);
        this.freshVariables = ImmutableSet.copyOf(freshVariables);
        this.lookups = lookups;

        copyAttributesFrom(oldRule);
        setLocation(oldRule.getLocation());
        setSource(oldRule.getSource());

        if (oldRule.containsAttribute(Constants.STDIN)
                || oldRule.containsAttribute(Constants.STDOUT)
                || oldRule.containsAttribute(Constants.STDERR)) {
            Variable listVar = (Variable) lhsOfReadCells.values().iterator().next();
            BuiltinList.Builder streamListBuilder = BuiltinList.builder();
            for (Equality eq : lookups.equalities()) {
                streamListBuilder.addItem(eq.rightHandSide());
            }
            if (!(listVar instanceof ConcreteCollectionVariable)) {
                streamListBuilder.concatenate(Variable.getAnonVariable(Sort.LIST));
            }

            Term streamList = streamListBuilder.build();
            this.indexingPair = oldRule.containsAttribute(Constants.STDIN) ?
                    IndexingPair.getInstreamIndexingPair(streamList, definition) :
                    IndexingPair.getOutstreamIndexingPair(streamList, definition);
        } else {
            Collection<IndexingPair> indexingPairs = leftHandSide.getKCellIndexingPairs(definition);

            /*
             * Compute indexing information only if the left-hand side of this rule has precisely one
             * k cell; set indexing to top otherwise (this rule could rewrite any term).
             */
            if (indexingPairs.size() == 1) {
                this.indexingPair = indexingPairs.iterator().next();
            } else {
                this.indexingPair = definition.indexingData.TOP_INDEXING_PAIR;
            }
        }

        leftHandSide.accept(new BottomUpVisitor() {
            @Override
            public void visit(Cell cell) {
                if (cell.getLabel().equals(CellLabel.K)) {
                    tempContainsKCell = true;
                } else if (cell.contentKind().isStructural()) {
                    super.visit(cell);
                }
            }
        });
        containsKCell = tempContainsKCell;

        isSortPredicate = isFunction() && definedKLabel().isSortPredicate();
        if (isSortPredicate) {
            predSort = definedKLabel().getPredicateSort();

            if (leftHandSide instanceof KItem
                    && rightHandSide.equals(BoolToken.TRUE)
                    && ((KList) ((KItem) leftHandSide).kList()).concreteSize() == 1) {
                Term arg = ((KList) ((KItem) leftHandSide).kList()).get(0);
                sortPredArg = arg instanceof KItem ? (KItem) arg : null;
            } else {
                sortPredArg = null;
            }

            if (sortPredArg == null) {
                isSortPredicate = false;

                /*
                 * YilongL: the right-hand side of the sort predicate rule
                 * is not necessarily {@code BoolToken#True}? for example:
                 *     rule isNat(I:Int) => '_>=Int_(I:Int,, Int(#"0"))
                 */
                // TODO(YilongL): properly re-implement support for sort predicate rules
//                GlobalSettings.kem.registerCriticalWarning("Unexpected sort predicate rule: " + this, null, this);
            }
        } else {
            predSort = null;
            sortPredArg = null;
        }

        // setting fields related to fast rewriting
        this.compiledForFastRewriting = compiledForFastRewriting;
        this.lhsOfReadCells     = compiledForFastRewriting ? ImmutableMap.copyOf(lhsOfReadCells) : null;
        this.rhsOfWriteCells    = compiledForFastRewriting ? ImmutableMap.copyOf(rhsOfWriteCells) : null;
        this.reusableVariables  = computeReusableBoundVars();
        this.groundCells        = cellsToCopy != null ? ImmutableSet.copyOf(cellsToCopy) : null;
        this.instructions       = compiledForFastRewriting ? ImmutableList.copyOf(instructions) : null;

        boolean modifyCellStructure;
        if (compiledForFastRewriting) {
            modifyCellStructure = false;
            for (CellLabel wrtCellLabel : rhsOfWriteCells.keySet()) {
                if (definition.context().getConfigurationStructureMap().get(wrtCellLabel.name()).hasChildren()) {
                    modifyCellStructure = true;
                }
            }
        } else {
            modifyCellStructure = true;
        }
        this.modifyCellStructure = modifyCellStructure;
    }

    /**
     * Private helper method that computes bound variables that can be reused to
     * instantiate the right-hand sides of the local rewrite operations.
     * <p>
     * Essentially, reusable bound variables are
     * <li>variables that occur in the left-hand sides of the rewrite operations
     * under read-write cells, plus
     * <li>variables in the key and value positions of data structure operations
     * (they are initially in the left-hand sides but moved to side conditions
     * during compilation)
     *
     * @return a multi-set representing reusable bound variables
     */
    private Multiset<Variable> computeReusableBoundVars() {
        Multiset<Variable> lhsVariablesToReuse = HashMultiset.create();
        if (compiledForFastRewriting) {
            Set<Term> lhsOfReadOnlyCell = Sets.newHashSet();
            /* add all variables that occur in the left-hand sides of read-write cells */
            for (Map.Entry<CellLabel, Term> entry : lhsOfReadCells.entrySet()) {
                CellLabel cellLabel = entry.getKey();
                Term lhs = entry.getValue();
                if (rhsOfWriteCells.containsKey(cellLabel)) {
                    lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(lhs));
                } else {
                    lhsOfReadOnlyCell.add(lhs);
                }
            }
            /* add variables that occur in the key and value positions of data
             * structure lookup operations under read-write cells */
            for (Equality eq : lookups.equalities()) {
                if (DataStructures.isLookup(eq.leftHandSide())) {
                    if (!lhsOfReadOnlyCell.contains(DataStructures.getLookupBase(eq.leftHandSide()))) {
                        // do not double count base variable again
                        lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(DataStructures.getLookupKey(eq.leftHandSide())));
                        lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(eq.rightHandSide()));
                    }
                }
            }
        } else {
            lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(leftHandSide));
            for (Equality eq : lookups.equalities()) {
                if (DataStructures.isLookup(eq.leftHandSide())) {
                    // do not double count base variable again
                    lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(DataStructures.getLookupKey(eq.leftHandSide())));
                    lhsVariablesToReuse.addAll(VariableOccurrencesCounter.count(eq.rightHandSide()));
                }
            }
        }

        return lhsVariablesToReuse;
    }

    private boolean tempContainsKCell = false;

    public String label() {
        return label;
    }

    public ImmutableList<Term> requires() {
        return requires;
    }

    public ImmutableList<Term> ensures() {
        return ensures;
    }

    public ImmutableSet<Variable> freshConstants() {
        return freshConstants;
    }

    public ImmutableSet<Variable> freshVariables() {
        return freshVariables;
    }

    public Set<Variable> boundVariables() {
        return variableSet().stream()
                .filter(v -> !freshConstants.contains(v) && !freshVariables.contains(v))
                .collect(Collectors.toSet());
    }

    /**
     * @return {@code true} if this rule is a sort predicate rule; otherwise,
     *         {@code false}
     */
    public boolean isSortPredicate() {
        return isSortPredicate;
    }

    /**
     * Gets the predicate sort if this rule is a sort predicate rule.
     */
    public Sort predicateSort() {
        assert isSortPredicate;

        return predSort;
    }

    /**
     * Gets the argument of the sort predicate if this rule is a sort predicate rule.
     */
    public KItem sortPredicateArgument() {
        assert isSortPredicate;

        return sortPredArg;
    }

    public boolean isFunction() {
        return containsAttribute(Attribute.FUNCTION_KEY)
               && !containsAttribute(Attribute.PATTERN_FOLDING_KEY);
    }

    public boolean isAnywhere() {
        return containsAttribute(Attribute.ANYWHERE_KEY);
    }

    public boolean isPattern() {
        return containsAttribute(Attribute.PATTERN_KEY);
    }

    public boolean isLemma() {
        return containsAttribute(Attribute.LEMMA_KEY);
    }

    /**
     * Returns the KLabel constant defined by this rule (either a function or a pattern).
     */
    public KLabelConstant definedKLabel() {
        assert isFunction() || isPattern();

        return (KLabelConstant) ((KItem) leftHandSide).kLabel();
    }

    public KLabelConstant anywhereKLabel() {
        assert isAnywhere();

        return (KLabelConstant) ((KItem) leftHandSide).kLabel();
    }

    /**
     * Returns a copy of this {@code Rule} with each {@link Variable} renamed to a fresh name.
     */
    public Rule getFreshRule(TermContext context) {
        return this.substitute(Variable.getFreshSubstitution(variableSet()), context);
    }

    public IndexingPair indexingPair() {
        return indexingPair;
    }

    public Term leftHandSide() {
        return leftHandSide;
    }

    public UninterpretedConstraint lookups() {
        return lookups;
    }

    public boolean containsKCell() {
        return containsKCell;
    }

    public Term rightHandSide() {
        return rightHandSide;
    }

    public boolean isCompiledForFastRewriting() {
        return compiledForFastRewriting;
    }

    public Map<CellLabel, Term> lhsOfReadCell() {
        return lhsOfReadCells;
    }

    public Map<CellLabel, Term> rhsOfWriteCell() {
        return rhsOfWriteCells;
    }

    public Multiset<Variable> reusableVariables() {
        return reusableVariables;
    }

    public Set<CellLabel> cellsToCopy() {
        return groundCells;
    }

    public List<Instruction> instructions() {
        return instructions;
    }

    /**
     * Checks if this rule will modify the cell structure of the subject term.
     */
    public boolean modifyCellStructure() {
        return modifyCellStructure;
    }

    @Override
    public Rule substitute(Map<Variable, ? extends Term> substitution, TermContext context) {
        return (Rule) super.substitute(substitution, context);
    }

    /**
     * Returns a new {@code Rule} instance obtained from this rule by substituting variable with
     * term.
     */
    @Override
    public Rule substituteWithBinders(Variable variable, Term term, TermContext context) {
        return (Rule) super.substituteWithBinders(variable, term, context);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Rule)) {
            return false;
        }

        Rule rule = (Rule) object;
        return label.equals(rule.label)
                && leftHandSide.equals(rule.leftHandSide)
                && rightHandSide.equals(rule.rightHandSide)
                && requires.equals(rule.requires)
                && ensures.equals(rule.ensures)
                && lookups.equals(rule.lookups)
                && freshConstants.equals(rule.freshConstants);
    }

    @Override
    public int hashCode() {
        if (hashCode == Utils.NO_HASHCODE) {
            hashCode = 1;
            hashCode = hashCode * Utils.HASH_PRIME + label.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + leftHandSide.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + rightHandSide.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + requires.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + ensures.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + lookups.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + freshConstants.hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        String string = "rule ";
        if ((label != null) && (!label.isEmpty()))
            string += "[" + label + "]: ";
        string += leftHandSide + " => " + rightHandSide;
        if (requires != null) {
            string += " requires " + requires;
        }
        if (!lookups.equalities().isEmpty()) {
            if (requires == null) {
                string += " when ";
            } else {
                string += " " + SymbolicConstraint.SEPARATOR + " ";
            }
            string += lookups;
        }
        if (ensures != null) {
            string += " ensures " + ensures;
        }
        string += " [" + "Location: " + getLocation() + ", " + getSource() + "]";
        return string;
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
