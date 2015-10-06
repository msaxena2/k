// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.compile;

import com.google.common.collect.Sets;
import org.kframework.backend.java.kil.JavaBackendRuleData;
import org.kframework.compile.utils.KilProperty;
import org.kframework.kil.*;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KExceptionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;


/**
 * Transformer class compiling builtin data structure accesses into lookup and update operations.
 *
 * @see ListLookup
 * @see ListUpdate
 * @see MapLookup
 * @see MapUpdate
 * @see SetLookup
 * @see SetUpdate
 *
 * @author AndreiS
 */
@KilProperty.Requires({KilProperty.TOP_REWRITING, KilProperty.COMPILED_DATA_STRUCTURES})
public class DataStructureToLookupUpdate extends CopyOnWriteTransformer {

    private interface VariableCache {
        /**
         * Returns a {@code Set} of {@link Variable} instances that are not matched yet.
         */
        Set<Variable> unmatchedVariables();
    }

    private static class ExtendedListLookup extends ListLookup implements VariableCache {
        private final Set<Variable> variables;

        ExtendedListLookup(Variable list, int key, Term value, Sort kind) {
            super(list, key, value, kind);
            variables = new HashSet<>();
            variables.add(list);
        }

        @Override
        public Set<Variable> unmatchedVariables() {
            return variables;
        }
    }

    private static class ExtendedMapLookup extends MapLookup implements VariableCache {
        private final Set<Variable> variables;

        ExtendedMapLookup(Variable map, Term key, Term value, Sort kind) {
            super(map, key, value, kind, false);
            variables = new HashSet<>();
            variables.add(map);
            variables.addAll(key.variables());
        }

        @Override
        public Set<Variable> unmatchedVariables() {
            return variables;
        }
    }

    private static class ExtendedSetLookup extends SetLookup implements VariableCache {
        private final Set<Variable> variables;

        ExtendedSetLookup(Variable set, Term key) {
            super(set, key, false);
            variables = new HashSet<>();
            variables.add(set);
            variables.addAll(key.variables());
        }

        @Override
        public Set<Variable> unmatchedVariables() {
            return variables;
        }
    }

    private enum Status {LHS, RHS, CONDITION }

    private final Map<Variable, Term> reverseMap = new HashMap<>();
    private final Map<Variable, Integer> concreteSize = new HashMap<>();
    private final ArrayList<VariableCache> queue = new ArrayList<>();
    private Status status;
    private ASTNode location;

    public DataStructureToLookupUpdate(Context context) {
        super("Compile data-structure patterns into load and store operations", context);
    }

    @Override
    public ASTNode visit(Rule node, Void _void)  {
        assert node.getBody() instanceof Rewrite:
               "expected rewrite at the top of rule " + node + ". "
               + "DataStructureToLookupUpdate pass should be applied after ResolveRewrite pass.";

        if (node.containsAttribute(Attribute.PATTERN_KEY)
                || node.containsAttribute(Attribute.PATTERN_FOLDING_KEY)
                || node.containsAttribute(Attribute.LEMMA_KEY)) {
            return node;
        }

        reverseMap.clear();
        concreteSize.clear();
        queue.clear();
        location = node;

        Rewrite rewrite = (Rewrite) node.getBody();

        /*
         * Replace data structure patterns in the left-hand side with fresh variables, and populate
         * the {@code queue} with data structure lookup operations equivalent to the replaced
         * patterns.
         */
        status = Status.LHS;
        Term lhs = (Term) this.visitNode(rewrite.getLeft());

        /*
         * Update the data structure uses in the right-hand side and condition with update
         * operations on the map variables introduced in the left-hand side in the previous step.
         */
        status = Status.RHS;
        Term rhs = (Term) this.visitNode(rewrite.getRight());
        status = Status.CONDITION;
        Term requires = node.getRequires() != null ? (Term) this.visitNode(node.getRequires()) : null;
        Term ensures = node.getEnsures();
        //TODO: Handle Ensures as well.

        if (lhs == rewrite.getLeft() && rhs == rewrite.getRight()
                && requires == node.getRequires() && ensures == node.getEnsures()) {
            return node;
        }

        Set<Variable> variables = new HashSet<>(lhs.variables());
        if (requires!= null) {
            variables.addAll(requires.variables());
        }

        List<BuiltinLookup> lookups = new ArrayList<>(node.getAttribute(JavaBackendRuleData.class).getLookups());

        for (VariableCache item : queue) {
            item.unmatchedVariables().removeAll(lhs.variables());
        }

        /*
         * Order the lookup operations in the {@code queue} such that when an operation is
         * performed the variables required by the operation (the data structure, the element for
         * a set lookup, the key for a map lookup) are already bound either by the left-hand side,
         * or by previous lookup operations. This allows an efficient evaluation of the lookup
         * operations.
         */
        boolean change;
        do {
            change = false;
            for (int i = 0; i < queue.size(); ++i) {
                if (queue.get(i).unmatchedVariables().isEmpty()) {
                    change = true;
                    BuiltinLookup lookup = (BuiltinLookup) queue.remove(i);
                    --i;

                    for (VariableCache item : queue) {
                        item.unmatchedVariables().removeAll(lookup.variables());
                    }
                    variables.addAll(lookup.variables());

                    if (lookup instanceof ListLookup) {
                        ListLookup listLookup = (ListLookup) lookup;
                        lookups.add(new ListLookup(
                                listLookup.base(),
                                listLookup.key(),
                                listLookup.value(),
                                listLookup.kind()));
                    } else if (lookup instanceof MapLookup) {
                        MapLookup mapLookup = (MapLookup) lookup;
                        lookups.add(new MapLookup(
                                mapLookup.base(),
                                mapLookup.key(),
                                mapLookup.value(),
                                mapLookup.kind(),
                                false));
                    } else if (lookup instanceof SetLookup) {
                        SetLookup setLookup = (SetLookup) lookup;
                        lookups.add(new SetLookup(setLookup.base(), setLookup.key(), false));
                    } else {
                        assert false: "unexpected builtin data structure type";
                    }
                }
            }
        } while (change);

        /*
         * The remaining lookup operations must be iterations over builtin data structures (they
         * depend on variable that are not bound yet). Thus, these operations require the choice
         * of an element (for the case of sets) of a key (for the case of maps) in order to
         * evaluate successfully. The choice must be completely unrestricted, so these elements or
         * keys must not appear in the left-hand side, in the condition, or in other lookup
         * operations (they can be used only in the right-hand side).
         */
        for (int i = 0; i < queue.size(); ++i) {
            for (int j = i + 1; j < queue.size(); ++j) {
                Set<Variable> commonVariables = Sets.intersection(
                        ((BuiltinLookup) queue.get(i)).variables(),
                        ((BuiltinLookup) queue.get(j)).variables());
                if (!commonVariables.isEmpty()) {
                    throw KExceptionManager.criticalError("Unsupported map, list, or set pattern in the rule left-hand side", node);
                }
            }
        }

        for (int i = 0; i < queue.size(); ++i) {
            BuiltinLookup lookup = (BuiltinLookup) queue.get(i);
            if (lookup instanceof MapLookup) {
                MapLookup mapLookup = (MapLookup) lookup;
                if (mapLookup.key() instanceof Variable
                        && !variables.contains(mapLookup.key())
                        && mapLookup.value() instanceof Variable
                        && !variables.contains(mapLookup.value())) {
                    lookups.add(new MapLookup(
                            mapLookup.base(),
                            mapLookup.key(),
                            mapLookup.value(),
                            mapLookup.kind(),
                            true));
                } else {
                    throw KExceptionManager.criticalError("Unsupported map pattern in the rule left-hand side", node);
                }
            } else if (lookup instanceof SetLookup) {
                SetLookup setLookup = (SetLookup) lookup;
                if (setLookup.key() instanceof Variable && !variables.contains(setLookup.key())) {
                    lookups.add(new SetLookup(setLookup.base(), setLookup.key(), true));
                } else {
                    throw KExceptionManager.criticalError("Unsupported set pattern in the rule left-hand side", node);
                }
            } else {
                assert false: "unexpected builtin data structure type";
            }
        }

        Rule returnNode = node.shallowCopy();
        rewrite = rewrite.shallowCopy();
        rewrite.setLeft(lhs, context);
        rewrite.setRight(rhs, context);
        returnNode.setBody(rewrite);
        returnNode.setRequires(requires);
        returnNode.setEnsures(ensures);
        JavaBackendRuleData ruleData = returnNode.getAttribute(JavaBackendRuleData.class);
        ruleData = ruleData.setLookups(lookups);
        ruleData = ruleData.setConcreteDataStructureSize(concreteSize);
        returnNode.addAttribute(JavaBackendRuleData.class, ruleData);

        location = null;

        return returnNode;
    }

    @Override
    public ASTNode visit(ListBuiltin node, Void _void)  {
        node = (ListBuiltin) super.visit(node, _void);
        if (status == Status.LHS) {
            if (!node.isLHSView()) {
                throw KExceptionManager.criticalError(
                        "Unexpected non-list term found. Terms of sort List must consist only of "
                                + "list elements and at most one variable of sort List. Did you specify .K "
                                + "when you meant .List?",
                        this,
                        location);
            }

            if (node.elementsLeft().isEmpty() && node.elementsRight().isEmpty()
                    && node.hasViewBase()) {
                return node.viewBase();
            }

            Variable variable = Variable.getAnonVar(Sort.of(node.sort().name()));
            if (node.hasViewBase()) {
                /* TODO(AndreiS): check the uniqueness of list variables in the LHS */
                assert !reverseMap.containsKey(node.viewBase());

                reverseMap.put(
                        node.viewBase(),
                        new ListUpdate(variable, node.elementsLeft(), node.elementsRight()));
            } else {
                concreteSize.put(
                        variable,
                        node.elementsLeft().size() + node.elementsRight().size());
            }

            int key = 0;
            for (Term term : node.elementsLeft()) {
                queue.add(new ExtendedListLookup(
                        variable,
                        key,
                        term,
                        term.getSort().getKSort()));
                key++;
            }

            key = -node.elementsRight().size();
            for (Term term : node.elementsRight()) {
                queue.add(new ExtendedListLookup(
                        variable,
                        key,
                        term,
                        term.getSort().getKSort()));
                key++;
            }

            return variable;
        } else {
            /* status == Status.RHS || status == Status.CONDITION */
            List<Term> baseTerms = new ArrayList<>();
            java.util.List<Term> elementsLeft = new ArrayList<>(node.elementsLeft());
            java.util.List<Term> elementsRight = new ArrayList<>(node.elementsRight());
            for (Term term : node.baseTerms()) {
                if (!(term instanceof ListUpdate)) {
                    baseTerms.add(term);
                    continue;
                }
                ListUpdate listUpdate = (ListUpdate) term;

                List<Term> removeLeft = new ArrayList<>(listUpdate.removeLeft());
                List<Term> removeRight = new ArrayList<>(listUpdate.removeRight());

                ListIterator<Term> iteratorElem = elementsLeft.listIterator(elementsLeft.size());
                ListIterator<Term> iteratorRemove = removeLeft.listIterator(removeLeft.size());
                while (iteratorElem.hasPrevious() && iteratorRemove.hasPrevious() &&
                       iteratorElem.previous().equals(iteratorRemove.previous())) {
                    iteratorElem.remove();
                    iteratorRemove.remove();
                }
                iteratorElem = elementsRight.listIterator();
                iteratorRemove = removeRight.listIterator();
                while (iteratorElem.hasNext() && iteratorRemove.hasNext() &&
                       iteratorElem.next().equals(iteratorRemove.next())) {
                    iteratorElem.remove();
                    iteratorRemove.remove();
                }

                if (removeLeft.isEmpty() && removeRight.isEmpty()) {
                    baseTerms.add(listUpdate.base());
                } else {
                    baseTerms.add(new ListUpdate(listUpdate.base(), removeLeft, removeRight));
                }
            }

            if (baseTerms.size() == 1 && elementsLeft.isEmpty() && elementsRight.isEmpty()) {
                /* if the ListBuiltin instance consists of only one base term,
                 * return the base term instead */
                return baseTerms.get(0);
            } else {
                return ListBuiltin.of(node.sort(), baseTerms, elementsLeft, elementsRight);
            }
        }
    }

    @Override
    public ASTNode visit(MapBuiltin node, Void _void)  {
        node = (MapBuiltin) super.visit(node, _void);
        if (status == Status.LHS) {
            if (!node.isLHSView()) {
                throw KExceptionManager.criticalError(
                        "Unexpected non-map term found. Terms of sort Map must consist only of "
                                + "map elements and at most one variable of sort Map. Did you specify .K "
                                + "when you meant .Map?",
                        this, location);
            }

            if (node.elements().isEmpty() && node.hasViewBase()) {
                    return node.viewBase();
            }

            Variable variable = Variable.getAnonVar(Sort.of(node.sort().name()));
            if (node.hasViewBase()) {
                /* TODO(AndreiS): check the uniqueness of map variables in the LHS */
                assert !reverseMap.containsKey(node.viewBase());

                reverseMap.put(
                        node.viewBase(),
                        new MapUpdate(variable, node.elements(), Collections.<Term, Term>emptyMap()));
            } else {
                concreteSize.put(variable, node.elements().size());
            }

            for (Map.Entry<Term, Term> entry : node.elements().entrySet()) {
                queue.add(new ExtendedMapLookup(
                        variable,
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue().getSort().getKSort()));
            }

            return variable;
        } else {
            /* status == Status.RHS || status == Status.CONDITION */
            List<Term> baseTerms = new ArrayList<>();
            Map<Term, Term> elements = new HashMap<>(node.elements());
            for (Term term : node.baseTerms()) {
                if (!(term instanceof MapUpdate)) {
                    baseTerms.add(term);
                    continue;
                }
                MapUpdate mapUpdate = (MapUpdate) term;

                Map<Term, Term> removeEntries = new HashMap<>();
                Map<Term, Term> updateEntries = new HashMap<>();
                for (Map.Entry<Term, Term> entry : mapUpdate.removeEntries().entrySet()) {
                    if (elements.containsKey(entry.getKey())) {
                        if (elements.get(entry.getKey()).equals(entry.getValue())) {
                            elements.remove(entry.getKey());
                        } else {
                            updateEntries.put(entry.getKey(), elements.remove(entry.getKey()));
                        }
                    } else {
                        removeEntries.put(entry.getKey(), entry.getValue());
                    }
                }

                if (removeEntries.isEmpty() && updateEntries.isEmpty()) {
                    baseTerms.add(mapUpdate.map());
                } else {
                    baseTerms.add(new MapUpdate(mapUpdate.map(), removeEntries, updateEntries));
                }
            }

            if (baseTerms.size() == 1 && elements.isEmpty()) {
                /* if the MapBuiltin instance consists of only one base term,
                 * return the base term instead */
                return baseTerms.get(0);
            } else {
                return new MapBuiltin(node.sort(), baseTerms, elements);
            }
        }
    }

    @Override
    public ASTNode visit(SetBuiltin node, Void _void)  {
        node = (SetBuiltin) super.visit(node, _void);
        if (status == Status.LHS) {
            if (!node.isLHSView()) {
                throw KExceptionManager.criticalError(
                        "Unexpected non-set term found. Terms of sort Set must consist only of "
                                + "set elements and at most one variable of sort Set. Did you specify .K "
                                + "when you meant .Set?",
                        this, location);
            }

            if (node.elements().isEmpty() && node.hasViewBase()) {
                return node.viewBase();
            }

            Variable variable = Variable.getAnonVar(Sort.of(node.sort().name()));
            if (node.hasViewBase()) {
                /* TODO(AndreiS): check the uniqueness of map variables in the LHS */
                assert !reverseMap.containsKey(node.viewBase());

                reverseMap.put(
                        node.viewBase(),
                        new SetUpdate(variable, node.elements()));
            } else {
                concreteSize.put(variable, node.elements().size());
            }

            for (Term term : node.elements()) {
                queue.add(new ExtendedSetLookup(variable, term));
            }

            return variable;
        } else {
            /* status == Status.RHS || status == Status.CONDITION */
            List<Term> baseTerms = new ArrayList<>();
            Collection<Term> elements = new ArrayList<>(node.elements());
            for (Term term : node.baseTerms()) {
                if (!(term instanceof SetUpdate)) {
                    baseTerms.add(term);
                    continue;
                }
                SetUpdate setUpdate = (SetUpdate) term;

                Collection<Term> removeEntries = new ArrayList<>();
                for (Term key : setUpdate.removeEntries()) {
                    if (elements.contains(key)) {
                        elements.remove(key);
                    } else {
                        removeEntries.add(key);
                    }
                }

                if (removeEntries.isEmpty()) {
                    baseTerms.add(setUpdate.set());
                } else {
                    baseTerms.add(new SetUpdate(setUpdate.set(), removeEntries));
                }
            }

            if (baseTerms.size() == 1 && elements.isEmpty()) {
                /* if the SetBuiltin instance consists of only one base term,
                 * return the base term instead */
                return baseTerms.get(0);
            } else {
                return new SetBuiltin(node.sort(), baseTerms, elements);
            }
        }
    }

    @Override
    public ASTNode visit(Variable node, Void _void)  {
        if (status != Status.LHS && reverseMap.containsKey(node)) {
            return reverseMap.get(node);
        } else {
            return node;
        }
    }

}
