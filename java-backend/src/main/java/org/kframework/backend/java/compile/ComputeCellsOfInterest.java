// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.compile;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kframework.backend.java.kil.JavaBackendRuleData;
import org.kframework.compile.utils.ConfigurationStructureVisitor;
import org.kframework.compile.utils.MetaK;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Bag;
import org.kframework.kil.Cell;
import org.kframework.kil.Configuration;
import org.kframework.kil.Definition;
import org.kframework.kil.KApp;
import org.kframework.kil.Rewrite;
import org.kframework.kil.Rule;
import org.kframework.kil.Syntax;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KExceptionManager;

import com.google.common.collect.Lists;

/**
 * For each non-function rule, compute all cells that we are interested in
 * during rewriting: <li>Human-written rules: all top level cells that are
 * mentioned in the original definition before any transformation;</li> <li>
 * Auto-generated rules: k cells for heating/cooling rules; and stream cells for
 * I/O rules.</li>
 * <p>
 * Therefore, this pass must be performed before {@link AddTopCellRules} but
 * after {@link AddKCell} & {@link AddStreamCells}.
 *
 * @author YilongL
 *
 */
public class ComputeCellsOfInterest extends CopyOnWriteTransformer {

    private boolean compiledForFastRewriting;
    private final Set<String> cellsOfInterest = new HashSet<>();
    private final Map<String, Term> readCell2LHS = new HashMap<>();
    private final Map<String, Term> writeCell2RHS = new HashMap<>();

    private int nestedCellCount;
    private boolean hasRewrite;
    private boolean topMentionedCellUnderRewrite;

    public ComputeCellsOfInterest(Context context) {
        super("compute information for fast rewriting", context);
    }

    @Override
    public ASTNode visit(Definition node, Void _void) {
        ConfigurationStructureVisitor cfgVisitor = new ConfigurationStructureVisitor(context);
        cfgVisitor.visitNode(node);
        return super.visit(node, _void);
    }

    @Override
    public ASTNode visit(Configuration node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(org.kframework.kil.Context node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(Syntax node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(Rule rule, Void _void)  {
        rule.addAttribute(JavaBackendRuleData.class, new JavaBackendRuleData());
        if (rule.containsAttribute(Attribute.FUNCTION_KEY)
                || rule.containsAttribute(Attribute.MACRO_KEY)
                || rule.containsAttribute(Attribute.ANYWHERE_KEY)
                || rule.containsAttribute(Attribute.PATTERN_KEY)
                || rule.containsAttribute(Attribute.PATTERN_FOLDING_KEY)) {
            rule.addAttribute(JavaBackendRuleData.class, rule.getAttribute(JavaBackendRuleData.class).setCompiledForFastRewriting(false));
            return rule;
        }

        compiledForFastRewriting = true;
        cellsOfInterest.clear();
        readCell2LHS.clear();
        writeCell2RHS.clear();
        nestedCellCount = 0;
        topMentionedCellUnderRewrite = false;
        rule = (Rule) super.visit(rule, _void);

        if (compiledForFastRewriting && topMentionedCellUnderRewrite) {
            /**
             * YilongL: Handle the following case where the parent cell of
             * <tasks>, i.e. <T>, is also the parent of <out>:
             * rule (<tasks> .Bag </tasks> => .)
             *      <out>... .List => ListItem("Type checked!\n") </out>
             */
            List<String> cellsToRemove = Lists.newArrayList();
            for (String cellLabel : cellsOfInterest) {
                if (!Collections.disjoint(context.getConfigurationStructureMap().get(cellLabel).ancestorIds, cellsOfInterest)) {
                    cellsToRemove.add(cellLabel);
                    readCell2LHS.remove(cellLabel);
                    writeCell2RHS.remove(cellLabel);
                }
            }
            cellsOfInterest.removeAll(cellsToRemove);
        }

        rule = rule.shallowCopy();
        JavaBackendRuleData ruleData = rule.getAttribute(JavaBackendRuleData.class);
        ruleData = ruleData.setCompiledForFastRewriting(compiledForFastRewriting);
        if (compiledForFastRewriting) {
            ruleData = ruleData.setCellsOfInterest(cellsOfInterest);
            ruleData = ruleData.setLhsOfReadCell(readCell2LHS);
            ruleData = ruleData.setRhsOfWriteCell(writeCell2RHS);
        }
        rule.addAttribute(JavaBackendRuleData.class, ruleData);

        return rule;
    }

    @Override
    public ASTNode visit(Cell cell, Void _void)  {
        if (!compiledForFastRewriting) {
            return cell;
        }

        /* TODO(YilongL): cannot handle duplicate cell labels for now */
        String cellLabel = cell.getLabel();
        if (cellsOfInterest.contains(cellLabel)) {
            compiledForFastRewriting = false;
            return cell;
        }

        /* top level cell mentioned in the rule */
        if (nestedCellCount == 0) {
            cellsOfInterest.add(cellLabel);
            readCell2LHS.put(cellLabel, null);
            hasRewrite = false;
        }

        nestedCellCount++;
        cell = (Cell) super.visit(cell, _void);
        nestedCellCount--;

        if (nestedCellCount == 0 && hasRewrite) {
            writeCell2RHS.put(cellLabel, null);
        }

        return cell;
    }

    @Override
    public ASTNode visit(KApp kApp, Void _void) {
        new BasicVisitor(context) {
            @Override
            public Void visit(Rewrite node, Void p) throws RuntimeException {
                /* dwightguth: handle the case where hasRewrite needs to be set
                 * in order to set writeCell2RHS, e.g.:
                 *   rule <k> foo(a => b) ...</k>
                 */
                hasRewrite = true;
                return null;
            }
        }.visitNode(kApp);

        /* YilongL: this prevents collecting cells injected inside KItems, e.g.:
         *   rule <k> loadObj(<threads> G:Bag </threads>) => . ...</k>  */
        return kApp;
    }

    @Override
    public ASTNode visit(Rewrite node, Void _void)  {
        if (nestedCellCount == 0) {
            topMentionedCellUnderRewrite = true;

            /* YilongL: handle the case where the top mentioned cell is inside a
             * rewrite, e.g.:
             *   rule (<thread>... <k>.</k> <holds>H</holds> <id>T</id> ...</thread> => .)
             *         <busy> Busy => Busy -Set keys(H) </busy>
             *         <terminated>... .Set => SetItem(T) ...</terminated>
             */
            Cell cell = null;
            if (node.getLeft() instanceof Cell) {
                cell = (Cell) node.getLeft();
            } else if (node.getRight() instanceof Cell) {
                cell = (Cell) node.getRight();
            } else if (node.getLeft() instanceof Bag && !((Bag) node.getLeft()).isEmpty()) {
                // TODO(YilongL): is it possible that the cells in the bag
                // are in different nested levels?
                cell = (Cell) ((Bag) node.getLeft()).getContents().get(0);
            } else {
                if (node.getRight() instanceof Bag) {
                    Bag bag = (Bag) node.getRight();
                    if (!bag.isEmpty()) {
                        cell = (Cell) bag.getContents().get(0);
                    }
                } else {
                    throw KExceptionManager.criticalError("Rewrite not between two cells??", node);
                }
            }

            assert cell != null : "could not determine cell under rewrite";
            String parentCellLabel = context.getConfigurationStructureMap().get(cell).parent.id;
            if (parentCellLabel.equals(MetaK.Constants.generatedCfgAbsTopCellLabel)) {
                compiledForFastRewriting = false;
            } else {
                cellsOfInterest.add(parentCellLabel);
                readCell2LHS.put(parentCellLabel, null);
                writeCell2RHS.put(parentCellLabel, null);
            }

            return node;
        }

        hasRewrite = true;
        return node;
    }
}
